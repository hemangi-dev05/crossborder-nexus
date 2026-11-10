
package com.fintech.controltower.api.dto;

import com.fintech.controltower.domain.PaymentStatusHistory;
import com.fintech.controltower.domain.TransactionLedger;

import java.time.LocalDateTime;

public record PaymentHistoryResponse(
        Long id,
        String uetr,
        TransactionLedger.TransactionStatus previousStatus,
        TransactionLedger.TransactionStatus newStatus,
        String reason,
        LocalDateTime changedAt
) {
    public static PaymentHistoryResponse from(
            PaymentStatusHistory history) {

        return new PaymentHistoryResponse(
                history.getId(),
                history.getUetr(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                history.getReason(),
                history.getChangedAt()
        );
    }
}
