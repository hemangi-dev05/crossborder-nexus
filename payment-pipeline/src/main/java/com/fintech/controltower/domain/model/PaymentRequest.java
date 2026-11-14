
package com.fintech.controltower.domain.model;

import com.fintech.controltower.financial.model.AmountMode;

import java.math.BigDecimal;

public record PaymentRequest(

        String transactionId,

        // Sending customer
        String debtorName,
        String debtorAccountNumber,
        String debtorBic,
        String debtorCountryCode,
        String debtorClearingSystemCode,
        String debtorClearingMemberId,

        // Receiving beneficiary
        String creditorName,
        String creditorAccountNumber,
        String creditorBic,
        String creditorCountryCode,
        String creditorClearingSystemCode,
        String creditorClearingMemberId,

        // Payment details
        String sourceCurrency,
        String destinationCurrency,
        AmountMode amountMode,
        BigDecimal enteredAmount

) {}
