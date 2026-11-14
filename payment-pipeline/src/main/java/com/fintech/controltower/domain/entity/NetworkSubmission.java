
package com.fintech.controltower.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "network_submission",
        indexes = {
                @Index(
                        name = "idx_network_submission_uetr",
                        columnList = "uetr"
                )
        }
)
public class NetworkSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Multiple network submissions can belong to one payment.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uetr", nullable = false)
    private TransactionLedger transactionLedger;

    @Column(nullable = false, length = 50)
    private String routeType;

    @Column(length = 100)
    private String networkReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SubmissionStatus status;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    private LocalDateTime respondedAt;

    @Column(length = 1000)
    private String responseMessage;

    public enum SubmissionStatus {
        SIMULATED_ACKNOWLEDGED,
        SIMULATED_NACK,
        SIMULATED_TIMEOUT
    }

    protected NetworkSubmission() {
    }

    public NetworkSubmission(
            TransactionLedger transactionLedger,
            String routeType,
            String networkReference,
            SubmissionStatus status,
            LocalDateTime submittedAt,
            LocalDateTime respondedAt,
            String responseMessage
    ) {
        this.transactionLedger = transactionLedger;
        this.routeType = routeType;
        this.networkReference = networkReference;
        this.status = status;
        this.submittedAt = submittedAt;
        this.respondedAt = respondedAt;
        this.responseMessage = responseMessage;
    }

    public Long getId() {
        return id;
    }

    public TransactionLedger getTransactionLedger() {
        return transactionLedger;
    }

    public String getRouteType() {
        return routeType;
    }

    public String getNetworkReference() {
        return networkReference;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }

    public String getResponseMessage() {
        return responseMessage;
    }
}
