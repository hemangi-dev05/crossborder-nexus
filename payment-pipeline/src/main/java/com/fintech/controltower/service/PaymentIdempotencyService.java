
package com.fintech.controltower.service;

import com.fintech.controltower.domain.IdempotencyRecord;
import com.fintech.controltower.domain.PaymentRequest;
import com.fintech.controltower.exception.AmlHoldException;
import com.fintech.controltower.exception.IdempotencyConflictException;
import com.fintech.controltower.exception.UnsupportedCorridorException;

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
            IdempotencyRecord.ProcessingState state,
            String uetr,
            String message,
            boolean replayed
    ) {}

    public PaymentResult processPayment(
            String idempotencyKey,
            PaymentRequest request
    ) {

        validateKey(idempotencyKey);

        String requestHash =
                fingerprintService.generateFingerprint(request);

        IdempotencyRecordService.Reservation reservation;

        // 1. Reserve the idempotency key.
        try {

            reservation = recordService.reserve(
                    idempotencyKey,
                    requestHash
            );

        } catch (DataIntegrityViolationException exception) {

            // Another concurrent request may have reserved this key.
            Optional<IdempotencyRecord> existing =
                    recordService.findByKey(idempotencyKey);

            if (existing.isEmpty()) {
                throw exception;
            }

            reservation = new IdempotencyRecordService.Reservation(
                    false,
                    existing.get()
            );
        }

        IdempotencyRecord record = reservation.record();

        // 2. Handle an existing idempotency record.
        if (!reservation.created()) {

            if (!requestHash.equals(record.getRequestHash())) {

                throw new IdempotencyConflictException(
                        "This Idempotency-Key has already been used "
                                + "with different payment details."
                );
            }

            if (record.getState()
                    == IdempotencyRecord.ProcessingState.IN_PROGRESS) {

                throw new IdempotencyConflictException(
                        "A payment with this Idempotency-Key "
                                + "is already being processed."
                );
            }

            // Replay the previously stored result.
            return new PaymentResult(
                    record.getState(),
                    record.getUetr(),
                    record.getResponseMessage(),
                    true
            );
        }

        // 3. Use the UETR reserved before saga execution.
        String uetr = record.getUetr();

        if (uetr == null || uetr.isBlank()) {
            throw new IllegalStateException(
                    "Reserved idempotency record does not contain a UETR"
            );
        }

        // 4. Execute the payment saga.
        PaymentSagaResult sagaResult;

        try {

            sagaResult = paymentSagaOrchestrator.executePaymentSaga(
                    request,
                    uetr
            );

        } catch (AmlHoldException exception) {

            // AML ledger and history are already persisted by the saga.
            String message = exception.getMessage();

            recordService.complete(
                    idempotencyKey,
                    IdempotencyRecord.ProcessingState.AML_HOLD,
                    uetr,
                    message
            );

            return new PaymentResult(
                    IdempotencyRecord.ProcessingState.AML_HOLD,
                    uetr,
                    message,
                    false
            );

        } catch (UnsupportedCorridorException exception) {

            // The saga transaction rolls back for unsupported corridors.
            // Persist the business rejection independently.
            String message = exception.getMessage();

            log.warn(
                    "Payment rejected because the corridor is unsupported. UETR: {}",
                    uetr
            );

            recordService.complete(
                    idempotencyKey,
                    IdempotencyRecord.ProcessingState.REJECTED,
                    uetr,
                    message
            );

            return new PaymentResult(
                    IdempotencyRecord.ProcessingState.REJECTED,
                    uetr,
                    message,
                    false
            );

        } catch (RuntimeException exception) {

            log.error(
                    "Payment saga failed for idempotency key",
                    exception
            );

            String message =
                    "Payment processing failed. Please contact support.";

            recordService.complete(
                    idempotencyKey,
                    IdempotencyRecord.ProcessingState.FAILED,
                    uetr,
                    message
            );

            return new PaymentResult(
                    IdempotencyRecord.ProcessingState.FAILED,
                    uetr,
                    message,
                    false
            );
        }

        // 5. Map the saga outcome to the idempotency state.
        IdempotencyRecord.ProcessingState finalState =
                switch (sagaResult.outcome()) {

                    case ACKNOWLEDGED ->
                            IdempotencyRecord.ProcessingState.ACCEPTED;

                    case REJECTED ->
                            IdempotencyRecord.ProcessingState.REJECTED;

                    case NETWORK_PENDING ->
                            IdempotencyRecord.ProcessingState.NETWORK_PENDING;
                };

        // 6. Persist the outcome for future idempotent replays.
        String message = sagaResult.message();

        recordService.complete(
                idempotencyKey,
                finalState,
                uetr,
                message
        );

        return new PaymentResult(
                finalState,
                uetr,
                message,
                false
        );
    }

    private void validateKey(String idempotencyKey) {

        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128) {

            throw new IllegalArgumentException(
                    "Idempotency-Key must contain between "
                            + "1 and 128 characters."
            );
        }
    }
}
