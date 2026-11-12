package com.fintech.controltower.service;

public record PaymentSagaResult(
        Outcome outcome,
        String message
) {

    public enum Outcome {
        ACKNOWLEDGED,
        REJECTED,
        NETWORK_PENDING
    }

    public PaymentSagaResult {
        if (outcome == null) {
            throw new IllegalArgumentException(
                    "Saga outcome cannot be null."
            );
        }

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "Saga result message cannot be empty."
            );
        }
    }
}