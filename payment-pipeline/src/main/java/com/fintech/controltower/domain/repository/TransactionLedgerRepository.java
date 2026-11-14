
package com.fintech.controltower.domain.repository;

import com.fintech.controltower.domain.entity.TransactionLedger;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

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

    // Retrieve and lock a payment row until the transaction completes
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransactionLedger t WHERE t.uetr = :uetr")
    Optional<TransactionLedger> findByUetrForUpdate(
            @Param("uetr") String uetr
    );
}
