package com.fintech.controltower.api.dto;

import com.fintech.controltower.domain.entity.TransactionLedger;
import java.time.LocalDateTime;

public record PaymentStatusResponse(
        String uetr,
        String transactionId,
        TransactionLedger.TransactionStatus status,
        LocalDateTime updatedAt
) {}