
package com.fintech.controltower.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionLedgerRepository
        extends JpaRepository<TransactionLedger, String> {

    // Retrieve all payments with pagination
    Page<TransactionLedger> findAll(Pageable pageable);

    // Filter payments by status with pagination
    Page<TransactionLedger> findByStatus(
            TransactionLedger.TransactionStatus status,
            Pageable pageable
    );

    // Search payments by UETR or transaction ID with pagination
    Page<TransactionLedger> findByUetrOrTransactionId(
            String uetr,
            String transactionId,
            Pageable pageable
    );
}
