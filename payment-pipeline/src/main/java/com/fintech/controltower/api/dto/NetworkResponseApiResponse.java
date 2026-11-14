
package com.fintech.controltower.api.dto;

import com.fintech.controltower.domain.entity.NetworkResponse;

import java.time.LocalDateTime;

public record NetworkResponseApiResponse(
        Long id,
        Long submissionId,
        NetworkResponse.ResponseStatus status,
        LocalDateTime receivedAt,
        String responseMessage
) {

    public static NetworkResponseApiResponse from(
            NetworkResponse response,
            Long submissionId
    ) {
        return new NetworkResponseApiResponse(
                response.getId(),
                submissionId,
                response.getStatus(),
                response.getReceivedAt(),
                response.getResponseMessage()
        );
    }
}
