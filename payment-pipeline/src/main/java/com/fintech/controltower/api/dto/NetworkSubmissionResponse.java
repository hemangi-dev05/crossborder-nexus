package com.fintech.controltower.api.dto;

import com.fintech.controltower.domain.NetworkSubmission;

import java.time.LocalDateTime;

public record NetworkSubmissionResponse(
        Long id,
        String uetr,
        String routeType,
        String networkReference,
        NetworkSubmission.SubmissionStatus status,
        LocalDateTime submittedAt,
        LocalDateTime respondedAt,
        String responseMessage
) {

    public static NetworkSubmissionResponse from(
            NetworkSubmission submission,
            String uetr
    ) {
        return new NetworkSubmissionResponse(
                submission.getId(),
                uetr,
                submission.getRouteType(),
                submission.getNetworkReference(),
                submission.getStatus(),
                submission.getSubmittedAt(),
                submission.getRespondedAt(),
                submission.getResponseMessage()
        );
    }
}