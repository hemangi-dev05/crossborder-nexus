
package com.fintech.controltower.service.idempotency;

import com.fintech.controltower.domain.entity.IdempotencyRecord;
import com.fintech.controltower.domain.model.PaymentRequest;
import com.fintech.controltower.exception.AmlHoldException;
import com.fintech.controltower.exception.IdempotencyConflictException;
import com.fintech.controltower.exception.UnsupportedCorridorException;
import com.fintech.controltower.service.payment.PaymentSagaOrchestrator;
import com.fintech.controltower.service.payment.PaymentSagaResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PaymentIdempotencyService {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentIdempotencyService.class);

    private final IdempotencyRecordService recordService;
    private final RequestFingerprintService fingerprintService;
    private final PaymentSagaOrchestrator paymentSagaOrchestrator;

    public PaymentIdempotencyService(
            IdempotencyRecordService recordService,
            RequestFingerprintService fingerprintService,
            PaymentSagaOrchestrator paymentSagaOrchestrator
    ) {
        this.recordService = recordService;
        this.fingerprintService = fingerprintService;
        this.paymentSagaOrchestrator = paymentSagaOrchestrator;
    }

    public record PaymentResult(
            ProcessingState state,
            String uetr,
            String message,
            boolean replayed
    ) {}

    public enum ProcessingState {
        IN_PROGRESS,
        ACCEPTED,
        NETWORK_PENDING,
        AML_HOLD,
        REJECTED,
        FAILED
    }

    public PaymentResult processPayment(
            Long customerId,
            Long accountId,
            String idempotencyKey,
            PaymentRequest request
    ) {

        if (customerId == null) {
            throw new IllegalArgumentException(
                    "Customer ID is required"
            );
        }

        if (accountId == null) {
            throw new IllegalArgumentException(
                    "Account ID is required"
            );
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key header is required"
            );
        }

        if (idempotencyKey.length() > 128) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must not exceed 128 characters"
            );
        }

        if (request == null) {
            throw new IllegalArgumentException(
                    "Payment request is required"
            );
        }

        String requestHash =
                fingerprintService.generateFingerprint(request);

        IdempotencyRecordService.Reservation reservation;

        try {
            reservation = recordService.reserve(
                    customerId,
                    idempotencyKey,
                    requestHash
            );

        } catch (DataIntegrityViolationException exception) {

            // Another concurrent request may have inserted
            // the same customer's idempotency key.
            Optional<IdempotencyRecord> existing =
                    recordService.findByKey(
                            customerId,
                            idempotencyKey
                    );

            if (existing.isEmpty()) {
                throw exception;
            }

            reservation = new IdempotencyRecordService.Reservation(
                    false,
                    existing.get()
            );
        }

        IdempotencyRecord record = reservation.record();

        if (record == null) {
            throw new IllegalStateException(
                    "Idempotency reservation returned no record"
            );
        }

        // Same key with a different payment request is not allowed.
        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(
                    "Idempotency key has already been used for a different payment request"
            );
        }

        // Existing request: never execute the payment again.
        if (!reservation.created()) {

            if (record.getState()
                    == IdempotencyRecord.ProcessingState.IN_PROGRESS) {

                throw new IdempotencyConflictException(
                        "A payment with this idempotency key is already being processed"
                );
            }

            return new PaymentResult(
                    ProcessingState.valueOf(record.getState().name()),
                    record.getUetr(),
                    record.getResponseMessage(),
                    true
            );
        }

        String uetr = record.getUetr();

        if (uetr == null || uetr.isBlank()) {
            throw new IllegalStateException(
                    "Idempotency record does not contain a UETR"
            );
        }

        PaymentSagaResult sagaResult;

        try {

            sagaResult = paymentSagaOrchestrator.executePaymentSaga(
                    request,
                    uetr,
                    customerId,
                    accountId
            );

        } catch (AmlHoldException exception) {

            recordService.complete(
                    customerId,
                    idempotencyKey,
                    IdempotencyRecord.ProcessingState.AML_HOLD,
                    uetr,
                    exception.getMessage()
            );

            return new PaymentResult(
                    ProcessingState.AML_HOLD,
                    uetr,
                    exception.getMessage(),
                    false
            );

        } catch (UnsupportedCorridorException exception) {

            log.warn(
                    "Unsupported payment corridor for UETR {}: {}",
                    uetr,
                    exception.getMessage()
            );

            recordService.complete(
                    customerId,
                    idempotencyKey,
                    IdempotencyRecord.ProcessingState.REJECTED,
                    uetr,
                    exception.getMessage()
            );

            return new PaymentResult(
                    ProcessingState.REJECTED,
                    uetr,
                    exception.getMessage(),
                    false
            );

        } catch (RuntimeException exception) {

            log.error(
                    "Payment saga failed for UETR {}",
                    uetr,
                    exception
            );

            recordService.complete(
                    customerId,
                    idempotencyKey,
                    IdempotencyRecord.ProcessingState.FAILED,
                    uetr,
                    "Payment processing failed"
            );

            throw exception;
        }

        ProcessingState resultState = switch (sagaResult.outcome()) {
            case ACKNOWLEDGED -> ProcessingState.ACCEPTED;
            case REJECTED -> ProcessingState.REJECTED;
            case NETWORK_PENDING -> ProcessingState.NETWORK_PENDING;
        };

        IdempotencyRecord.ProcessingState recordState =
                IdempotencyRecord.ProcessingState.valueOf(
                        resultState.name()
                );

        recordService.complete(
                customerId,
                idempotencyKey,
                recordState,
                uetr,
                sagaResult.message()
        );

        return new PaymentResult(
                resultState,
                uetr,
                sagaResult.message(),
                false
        );
    }
}
