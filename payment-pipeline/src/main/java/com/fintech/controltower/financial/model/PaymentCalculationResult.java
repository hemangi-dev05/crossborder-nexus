package com.fintech.controltower.financial.model;

import java.math.BigDecimal;

public record PaymentCalculationResult(

        AmountMode amountMode,
        String routeType,
        String sourceCurrency,
        String destinationCurrency,
        BigDecimal sourcePrincipal,
        BigDecimal destinationAmount,
        BigDecimal exchangeRate,
        BigDecimal transferFee,
        String feeCurrency,
        BigDecimal totalSenderDebit

) {}