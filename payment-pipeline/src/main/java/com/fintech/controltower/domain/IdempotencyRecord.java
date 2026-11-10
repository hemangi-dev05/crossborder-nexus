
package com.fintech.controltower.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment_idempotency",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_idempotency_key",
                        columnNames = "idempotency_key"
                )
        }
)
public class IdempotencyRecord {

    public enum ProcessingState {
        IN_PROGRESS,
        ACCEPTED,
        NETWORK_PENDING,
        AML_HOLD,
        REJECTED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 128
    )
    private String idempotencyKey;

    @Column(
            name = "request_hash",
            nullable = false,
            length = 64
    )
    private String requestHash;

    @Column(length = 36)
    private String uetr;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProcessingState state;

    @Column(length = 500)
    private String responseMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(
            String idempotencyKey,
            String requestHash,
            String uetr
    ) {
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.uetr = uetr;
        this.state = ProcessingState.IN_PROGRESS;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getUetr() {
        return uetr;
    }

    public ProcessingState getState() {
        return state;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUetr(String uetr) {
        this.uetr = uetr;
    }

    public void setState(ProcessingState state) {
        this.state = state;
    }

    public void setResponseMessage(String responseMessage) {
        this.responseMessage = responseMessage;
    }
}
