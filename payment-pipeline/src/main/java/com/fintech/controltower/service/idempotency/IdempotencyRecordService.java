
package com.fintech.controltower.service.idempotency;

import com.fintech.controltower.domain.entity.IdempotencyRecord;
import com.fintech.controltower.domain.repository.IdempotencyRecordRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyRecordService {

    private final IdempotencyRecordRepository repository;

    public IdempotencyRecordService(
            IdempotencyRecordRepository repository
    ) {
        this.repository = repository;
    }

    public record Reservation(
            boolean created,
            IdempotencyRecord record
    ) {}

    // Reserve and commit the key and UETR independently.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(
            String idempotencyKey,
            String requestHash
    ) {

        Optional<IdempotencyRecord> existing =
                repository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            return new Reservation(false, existing.get());
        }

        String uetr = UUID.randomUUID().toString();

        IdempotencyRecord newRecord =
                new IdempotencyRecord(
                        idempotencyKey,
                        requestHash,
                        uetr
                );

        IdempotencyRecord saved =
                repository.saveAndFlush(newRecord);

        return new Reservation(true, saved);
    }

    // Acquire the lock inside the caller's active transaction.
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<IdempotencyRecord> lockByKey(
            String idempotencyKey
    ) {
        return repository.findByIdempotencyKeyForUpdate(
                idempotencyKey
        );
    }

    // Retain this method for non-locking lookups.
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true
    )
    public Optional<IdempotencyRecord> findByKey(
            String idempotencyKey
    ) {
        return repository.findByIdempotencyKey(idempotencyKey);
    }

    // Complete and commit the final state independently.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(
            String idempotencyKey,
            IdempotencyRecord.ProcessingState state,
            String uetr,
            String responseMessage
    ) {

        if (state == null
                || state == IdempotencyRecord.ProcessingState.IN_PROGRESS) {

            throw new IllegalArgumentException(
                    "A final processing state is required"
            );
        }

        IdempotencyRecord record =
                repository.findByIdempotencyKey(idempotencyKey)
                        .orElseThrow(() -> new IllegalStateException(
                                "Idempotency record not found"
                        ));

        if (record.getState()
                != IdempotencyRecord.ProcessingState.IN_PROGRESS) {

            throw new IllegalStateException(
                    "Idempotency record has already been completed"
            );
        }

        record.setState(state);

        if (uetr != null) {
            record.setUetr(uetr);
        }

        record.setResponseMessage(responseMessage);

        repository.save(record);
    }
}
