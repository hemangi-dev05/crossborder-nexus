package com.fintech.controltower.financial.model;

import java.math.BigDecimal;

public record PaymentCalculationRequest(

        String debtorCountryCode,

        String creditorCountryCode,

        String sourceCurrency,

        String destinationCurrency,

        AmountMode amountMode,

        BigDecimal enteredAmount

) {}