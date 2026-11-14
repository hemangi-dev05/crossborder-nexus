
package com.fintech.controltower.service.network;

import com.fintech.controltower.service.payment.TransactionStatusService;

import com.fintech.controltower.domain.entity.NetworkResponse;
import com.fintech.controltower.domain.repository.NetworkResponseRepository;
import com.fintech.controltower.domain.entity.NetworkSubmission;
import com.fintech.controltower.domain.repository.NetworkSubmissionRepository;
import com.fintech.controltower.domain.entity.TransactionLedger;
import com.fintech.controltower.domain.repository.TransactionLedgerRepository;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class NetworkResponseService {

    private final NetworkSubmissionRepository submissionRepository;
    private final NetworkResponseRepository responseRepository;
    private final TransactionLedgerRepository ledgerRepository;
    private final TransactionStatusService statusService;

    public NetworkResponseService(
            NetworkSubmissionRepository submissionRepository,
            NetworkResponseRepository responseRepository,
            TransactionLedgerRepository ledgerRepository,
            TransactionStatusService statusService
    ) {
        this.submissionRepository = submissionRepository;
        this.responseRepository = responseRepository;
        this.ledgerRepository = ledgerRepository;
        this.statusService = statusService;
    }

    // Process a delayed ACK or NACK atomically
    @Transactional
    public NetworkResponse processDelayedResponse(
            Long submissionId,
            NetworkResponse.ResponseStatus responseStatus,
            String responseMessage
    ) {

        // 1. Validate request
        if (submissionId == null || submissionId <= 0) {
            throw new IllegalArgumentException(
                    "Valid network submission ID is required"
            );
        }

        if (responseStatus == null) {
            throw new IllegalArgumentException(
                    "Delayed response status is required"
            );
        }

        String normalizedMessage =
                responseMessage == null || responseMessage.isBlank()
                        ? null
                        : responseMessage.trim();

        if (normalizedMessage != null && normalizedMessage.length() > 1000) {
            throw new IllegalArgumentException(
                    "Response message must not exceed 1000 characters"
            );
        }

        // 2. Find the original network submission
        NetworkSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Network submission not found: " + submissionId
                        )
                );

        // 3. Verify that the original submission timed out
        if (submission.getStatus()
                != NetworkSubmission.SubmissionStatus.SIMULATED_TIMEOUT) {

            throw new IllegalStateException(
                    "Delayed responses are only allowed for timed-out submissions"
            );
        }

        // 4. Lock the associated payment row
        String uetr = submission.getTransactionLedger().getUetr();

        TransactionLedger ledger = ledgerRepository
                .findByUetrForUpdate(uetr)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Payment not found for UETR: " + uetr
                        )
                );

        // 5. Check whether a response was already recorded
        var existingResponse =
                responseRepository.findByNetworkSubmission_Id(submissionId);

        if (existingResponse.isPresent()) {

            NetworkResponse existing = existingResponse.get();

            boolean identicalResponse =
                    existing.getStatus() == responseStatus
                            && Objects.equals(
                            existing.getResponseMessage(),
                            normalizedMessage
                    );

            if (identicalResponse) {
                // Idempotent retry: return the previously stored response
                return existing;
            }

            throw new IllegalStateException(
                    "A different delayed response has already been recorded"
            );
        }

        // 6. Payment must still be waiting for a network response
        if (ledger.getStatus()
                != TransactionLedger.TransactionStatus.NETWORK_PENDING) {

            throw new IllegalStateException(
                    "Payment is not in NETWORK_PENDING status"
            );
        }

        // 7. Persist the delayed network response
        NetworkResponse networkResponse = new NetworkResponse(
                submission,
                responseStatus,
                LocalDateTime.now(),
                normalizedMessage
        );

        NetworkResponse savedResponse =
                responseRepository.save(networkResponse);

        // 8. Determine the corresponding payment status
        TransactionLedger.TransactionStatus newPaymentStatus;

        if (responseStatus
                == NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED) {

            newPaymentStatus =
                    TransactionLedger.TransactionStatus.PROCESSING;

        } else {

            newPaymentStatus =
                    TransactionLedger.TransactionStatus.REJECTED;
        }

        // 9. Update payment status and record status history
        String reason = "Delayed network "
                + (responseStatus
                == NetworkResponse.ResponseStatus.SIMULATED_ACKNOWLEDGED
                ? "ACK"
                : "NACK")
                + " received for submission ID: " + submissionId;

        statusService.updateStatus(
                uetr,
                newPaymentStatus,
                reason
        );

        return savedResponse;
    }
}
