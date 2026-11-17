
package com.fintech.controltower.api.dto;

import com.fintech.controltower.financial.model.AmountMode;

import java.math.BigDecimal;

public record PaymentInitiationRequest(

        Long accountId,

        String creditorName,
        String creditorAccountNumber,
        String creditorBic,
        String creditorCountryCode,

        String destinationCurrency,

        AmountMode amountMode,
        BigDecimal enteredAmount

) {
}
