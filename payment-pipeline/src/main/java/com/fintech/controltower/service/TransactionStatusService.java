
package com.fintech.controltower.service;

import com.fintech.controltower.domain.PaymentStatusHistory;
import com.fintech.controltower.domain.PaymentStatusHistoryRepository;
import com.fintech.controltower.domain.TransactionLedger;
import com.fintech.controltower.domain.TransactionLedgerRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionStatusService {

    private final TransactionLedgerRepository ledgerRepository;
    private final PaymentStatusHistoryRepository historyRepository;

    public TransactionStatusService(
            TransactionLedgerRepository ledgerRepository,
            PaymentStatusHistoryRepository historyRepository
    ) {
        this.ledgerRepository = ledgerRepository;
        this.historyRepository = historyRepository;
    }

    // Backward-compatible method for existing callers
    @Transactional
    public TransactionLedger updateStatus(
            String uetr,
            TransactionLedger.TransactionStatus newStatus
    ) {
        return updateStatus(
                uetr,
                newStatus,
                "Status updated by system"
        );
    }

    // Update payment status and record its history
    @Transactional
    public TransactionLedger updateStatus(
            String uetr,
            TransactionLedger.TransactionStatus newStatus,
            String reason
    ) {

        if (uetr == null || uetr.isBlank()) {
            throw new IllegalArgumentException("UETR is required");
        }

        if (newStatus == null) {
            throw new IllegalArgumentException("New status is required");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Status change reason is required"
            );
        }

        if (reason.length() > 500) {
            throw new IllegalArgumentException(
                    "Status change reason must not exceed 500 characters"
            );
        }

        // 1. Retrieve payment
        TransactionLedger ledger = ledgerRepository.findById(uetr)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found for UETR: " + uetr
                        )
                );

        TransactionLedger.TransactionStatus currentStatus =
                ledger.getStatus();

        // 2. Validate allowed status transition
        boolean allowed = switch (currentStatus) {

            case ACCEPTED ->
                    newStatus == TransactionLedger.TransactionStatus.PROCESSING
                            || newStatus == TransactionLedger.TransactionStatus.REJECTED
                            || newStatus == TransactionLedger.TransactionStatus.SUSPENDED_AML_HOLD
                            || newStatus == TransactionLedger.TransactionStatus.NETWORK_PENDING;

            case NETWORK_PENDING ->
                    newStatus == TransactionLedger.TransactionStatus.PROCESSING
                            || newStatus == TransactionLedger.TransactionStatus.REJECTED;

            case PROCESSING ->
                    newStatus == TransactionLedger.TransactionStatus.SETTLED
                            || newStatus == TransactionLedger.TransactionStatus.REJECTED;

            case SETTLED, REJECTED, SUSPENDED_AML_HOLD -> false;
        };

        if (!allowed) {
            throw new IllegalStateException(
                    "Invalid payment status transition: "
                            + currentStatus + " -> " + newStatus
            );
        }

        // 3. Update current payment status
        ledger.setStatus(newStatus);

        TransactionLedger savedLedger =
                ledgerRepository.save(ledger);

        // 4. Record the status transition
        PaymentStatusHistory history = new PaymentStatusHistory(
                uetr,
                currentStatus,
                newStatus,
                reason.trim()
        );

        historyRepository.save(history);

        return savedLedger;
    }
}
