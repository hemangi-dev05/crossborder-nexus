
package com.fintech.controltower.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment_status_history",
        indexes = {
                @Index(name = "idx_history_uetr", columnList = "uetr")
        }
)
public class PaymentStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String uetr;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private TransactionLedger.TransactionStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionLedger.TransactionStatus newStatus;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime changedAt;

    protected PaymentStatusHistory() {
    }

    public PaymentStatusHistory(
            String uetr,
            TransactionLedger.TransactionStatus previousStatus,
            TransactionLedger.TransactionStatus newStatus,
            String reason
    ) {
        this.uetr = uetr;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.changedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getUetr() {
        return uetr;
    }

    public TransactionLedger.TransactionStatus getPreviousStatus() {
        return previousStatus;
    }

    public TransactionLedger.TransactionStatus getNewStatus() {
        return newStatus;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
