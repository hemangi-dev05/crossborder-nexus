
package com.fintech.controltower.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "iso_message")
public class IsoMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Multiple ISO messages can belong to one payment.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uetr", nullable = false)
    private TransactionLedger transactionLedger;

    @Column(nullable = false, length = 30)
    private String messageType;

    @Column(nullable = false, length = 20)
    private String schemaVersion;

    // PostgreSQL TEXT stores the complete XML document.
    @Column(name = "message_content", nullable = false,
            columnDefinition = "TEXT")
    private String messageContent;

    @Column(nullable = false, length = 30)
    private String validationStatus;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public TransactionLedger getTransactionLedger() {
        return transactionLedger;
    }

    public void setTransactionLedger(TransactionLedger transactionLedger) {
        this.transactionLedger = transactionLedger;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getMessageContent() {
        return messageContent;
    }

    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
