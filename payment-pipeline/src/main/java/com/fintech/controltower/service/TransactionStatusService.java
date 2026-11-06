
package com.fintech.controltower.service;

import com.fintech.controltower.domain.TransactionLedger;
import com.fintech.controltower.domain.TransactionLedgerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionStatusService {

    private final TransactionLedgerRepository ledgerRepository;

    public TransactionStatusService(TransactionLedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public TransactionLedger updateStatus(
            String uetr,
            TransactionLedger.TransactionStatus newStatus) {

        // 1. Find the payment
        TransactionLedger ledger = ledgerRepository.findById(uetr)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found for UETR: " + uetr
                        )
                );

        TransactionLedger.TransactionStatus currentStatus =
                ledger.getStatus();

        // 2. Validate the requested transition
        boolean allowed = switch (currentStatus) {
            case ACCEPTED ->
                    newStatus == TransactionLedger.TransactionStatus.PROCESSING
                            || newStatus == TransactionLedger.TransactionStatus.REJECTED
                            || newStatus == TransactionLedger.TransactionStatus.SUSPENDED_AML_HOLD;

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

        // 3. Update and persist the status
        ledger.setStatus(newStatus);

        return ledgerRepository.save(ledger);
    }
}
