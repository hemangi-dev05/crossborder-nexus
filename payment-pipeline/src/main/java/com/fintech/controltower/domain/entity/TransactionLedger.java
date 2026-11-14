
package com.fintech.controltower.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_ledger")
public class TransactionLedger {

    public enum TransactionStatus {
        ACCEPTED,
        PROCESSING,
        NETWORK_PENDING,
        SETTLED,
        REJECTED,
        SUSPENDED_AML_HOLD
    }

    @Id
    private String uetr;

    private String transactionId;

    // Sending customer
    private String debtorName;
    private String debtorAccountNumber;
    private String debtorBic;

    @Column(name = "debtor_country_code", length = 2)
    private String debtorCountryCode;

    private String debtorClearingSystemCode;
    private String debtorClearingMemberId;

    // Receiving beneficiary
    private String creditorName;
    private String creditorAccountNumber;
    private String creditorBic;

    @Column(name = "creditor_country_code", length = 2)
    private String creditorCountryCode;

    private String creditorClearingSystemCode;
    private String creditorClearingMemberId;

    // Payment details
    private BigDecimal amount;
    private String currency;

    // Payment lifecycle
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private TransactionStatus status = TransactionStatus.ACCEPTED;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (status == null) {
            status = TransactionStatus.ACCEPTED;
        }

        if (createdAt == null) {
            createdAt = now;
        }

        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters

    public String getUetr() { return uetr; }
    public void setUetr(String uetr) { this.uetr = uetr; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getDebtorName() { return debtorName; }
    public void setDebtorName(String debtorName) { this.debtorName = debtorName; }

    public String getDebtorAccountNumber() { return debtorAccountNumber; }
    public void setDebtorAccountNumber(String debtorAccountNumber) { this.debtorAccountNumber = debtorAccountNumber; }

    public String getDebtorBic() { return debtorBic; }
    public void setDebtorBic(String debtorBic) { this.debtorBic = debtorBic; }

    public String getDebtorCountryCode() { return debtorCountryCode; }
    public void setDebtorCountryCode(String debtorCountryCode) { this.debtorCountryCode = debtorCountryCode; }

    public String getDebtorClearingSystemCode() { return debtorClearingSystemCode; }
    public void setDebtorClearingSystemCode(String debtorClearingSystemCode) { this.debtorClearingSystemCode = debtorClearingSystemCode; }

    public String getDebtorClearingMemberId() { return debtorClearingMemberId; }
    public void setDebtorClearingMemberId(String debtorClearingMemberId) { this.debtorClearingMemberId = debtorClearingMemberId; }

    public String getCreditorName() { return creditorName; }
    public void setCreditorName(String creditorName) { this.creditorName = creditorName; }

    public String getCreditorAccountNumber() { return creditorAccountNumber; }
    public void setCreditorAccountNumber(String creditorAccountNumber) { this.creditorAccountNumber = creditorAccountNumber; }

    public String getCreditorBic() { return creditorBic; }
    public void setCreditorBic(String creditorBic) { this.creditorBic = creditorBic; }

    public String getCreditorCountryCode() { return creditorCountryCode; }
    public void setCreditorCountryCode(String creditorCountryCode) { this.creditorCountryCode = creditorCountryCode; }

    public String getCreditorClearingSystemCode() { return creditorClearingSystemCode; }
    public void setCreditorClearingSystemCode(String creditorClearingSystemCode) { this.creditorClearingSystemCode = creditorClearingSystemCode; }

    public String getCreditorClearingMemberId() { return creditorClearingMemberId; }
    public void setCreditorClearingMemberId(String creditorClearingMemberId) { this.creditorClearingMemberId = creditorClearingMemberId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
