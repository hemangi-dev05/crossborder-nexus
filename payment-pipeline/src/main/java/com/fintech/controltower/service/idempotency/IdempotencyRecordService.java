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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(
            Long customerId,
            String idempotencyKey,
            String requestHash
    ) {
        Optional<IdempotencyRecord> existing =
                repository.findByCustomerIdAndIdempotencyKey(
                        customerId,
                        idempotencyKey
                );

        if (existing.isPresent()) {
            return new Reservation(false, existing.get());
        }

        String uetr = UUID.randomUUID().toString();

        IdempotencyRecord newRecord = new IdempotencyRecord(
                customerId,
                idempotencyKey,
                requestHash,
                uetr
        );

        IdempotencyRecord saved =
                repository.saveAndFlush(newRecord);

        return new Reservation(true, saved);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<IdempotencyRecord> lockByKey(
            Long customerId,
            String idempotencyKey
    ) {
        return repository.findByCustomerIdAndIdempotencyKeyForUpdate(
                customerId,
                idempotencyKey
        );
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true
    )
    public Optional<IdempotencyRecord> findByKey(
            Long customerId,
            String idempotencyKey
    ) {
        return repository.findByCustomerIdAndIdempotencyKey(
                customerId,
                idempotencyKey
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(
            Long customerId,
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
                repository.findByCustomerIdAndIdempotencyKeyForUpdate(
                        customerId,
                        idempotencyKey
                ).orElseThrow(() -> new IllegalStateException(
                        "Idempotency record not found for customer"
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