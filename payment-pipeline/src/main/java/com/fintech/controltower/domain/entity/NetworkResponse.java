
package com.fintech.controltower.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "network_response",
        indexes = {
                @Index(
                        name = "idx_network_response_submission",
                        columnList = "network_submission_id"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_network_response_submission",
                        columnNames = "network_submission_id"
                )
        }
)
public class NetworkResponse {

    public enum ResponseStatus {
        SIMULATED_ACKNOWLEDGED,
        SIMULATED_NACK
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "network_submission_id", nullable = false)
    private NetworkSubmission networkSubmission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ResponseStatus status;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    @Column(length = 1000)
    private String responseMessage;

    protected NetworkResponse() {
    }

    public NetworkResponse(
            NetworkSubmission networkSubmission,
            ResponseStatus status,
            LocalDateTime receivedAt,
            String responseMessage
    ) {
        this.networkSubmission = networkSubmission;
        this.status = status;
        this.receivedAt = receivedAt;
        this.responseMessage = responseMessage;
    }

    public Long getId() {
        return id;
    }

    public NetworkSubmission getNetworkSubmission() {
        return networkSubmission;
    }

    public ResponseStatus getStatus() {
        return status;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public String getResponseMessage() {
        return responseMessage;
    }
}
