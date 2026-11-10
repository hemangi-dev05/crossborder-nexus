
package com.fintech.controltower.service.network;

import java.time.LocalDateTime;

public record NetworkSubmissionResult(

        String routeType,

        String networkReference,

        SubmissionStatus status,

        LocalDateTime submittedAt,

        LocalDateTime respondedAt,

        String responseMessage,

        String isoXmlMessage

) {

    public enum SubmissionStatus {
        SIMULATED_ACKNOWLEDGED,
        SIMULATED_NACK,
        SIMULATED_TIMEOUT
    }

    // Backward-compatible constructor for existing adapters.
    public NetworkSubmissionResult(
            String routeType,
            String networkReference,
            SubmissionStatus status,
            LocalDateTime submittedAt,
            LocalDateTime respondedAt,
            String responseMessage
    ) {
        this(
                routeType,
                networkReference,
                status,
                submittedAt,
                respondedAt,
                responseMessage,
                null
        );
    }
}
