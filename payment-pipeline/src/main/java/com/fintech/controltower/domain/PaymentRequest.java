
package com.fintech.controltower.domain;

import java.math.BigDecimal;

public record PaymentRequest(
        String transactionId,

        // Sending customer
        String debtorName,
        String debtorAccountNumber,
        String debtorBic,
        String debtorClearingSystemCode,
        String debtorClearingMemberId,

        // Receiving beneficiary
        String creditorName,
        String creditorAccountNumber,
        String creditorBic,
        String creditorClearingSystemCode,
        String creditorClearingMemberId,

        // Payment details
        BigDecimal amount,
        String currency
) {}
