package com.fintech.controltower.domain.repository;

import com.fintech.controltower.domain.entity.IdempotencyRecord;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByCustomerIdAndIdempotencyKey(
            Long customerId,
            String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT record
            FROM IdempotencyRecord record
            WHERE record.customerId = :customerId
              AND record.idempotencyKey = :idempotencyKey
            """)
    Optional<IdempotencyRecord> findByCustomerIdAndIdempotencyKeyForUpdate(
            @Param("customerId") Long customerId,
            @Param("idempotencyKey") String idempotencyKey
    );
}