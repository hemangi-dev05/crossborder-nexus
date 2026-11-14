package com.fintech.controltower.api.dto;

import com.fintech.controltower.domain.entity.TransactionLedger;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentDetailsResponse(
        String uetr,
        String transactionId,
        String debtorName,
        String creditorName,
        BigDecimal amount,
        String currency,
        TransactionLedger.TransactionStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}