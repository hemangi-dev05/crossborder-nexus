
package com.fintech.controltower.domain;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdempotencyRecordRepository
        extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByIdempotencyKey(
            String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT record
            FROM IdempotencyRecord record
            WHERE record.idempotencyKey = :idempotencyKey
            """)
    Optional<IdempotencyRecord> findByIdempotencyKeyForUpdate(
            @Param("idempotencyKey") String idempotencyKey
    );
}
