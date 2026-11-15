
package com.fintech.controltower.financial.service;

import com.fintech.controltower.config.PaymentFinancialProperties;
import com.fintech.controltower.financial.model.AmountMode;
import com.fintech.controltower.financial.model.PaymentCalculationRequest;
import com.fintech.controltower.financial.model.PaymentCalculationResult;
import com.fintech.controltower.service.payment.PaymentRoutingService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;

@Service
public class FinancialCalculationService {

    private static final String DOMESTIC_CLEARING = "DOMESTIC_CLEARING";
    private static final String CROSS_BORDER_CORRESPONDENT =
            "CROSS_BORDER_CORRESPONDENT";

    private final PaymentFinancialProperties financialProperties;
    private final PaymentRoutingService routingService;
    private final FxRateService fxRateService;

    public FinancialCalculationService(
            PaymentFinancialProperties financialProperties,
            PaymentRoutingService routingService,
            FxRateService fxRateService
    ) {
        this.financialProperties = financialProperties;
        this.routingService = routingService;
        this.fxRateService = fxRateService;
    }

    public PaymentCalculationResult calculate(
            PaymentCalculationRequest request
    ) {
        validateRequest(request);

        String sourceCurrency = normalize(request.sourceCurrency());
        String destinationCurrency = normalize(request.destinationCurrency());

        int sourceScale = getCurrencyScale(sourceCurrency);
        int destinationScale = getCurrencyScale(destinationCurrency);

        String routeType = routingService.determineRoute(
                request.debtorCountryCode(),
                request.creditorCountryCode(),
                destinationCurrency
        );

        if (DOMESTIC_CLEARING.equalsIgnoreCase(routeType)
                && !sourceCurrency.equals(destinationCurrency)) {
            throw new IllegalArgumentException(
                    "Domestic payments must use the same source and destination currency."
            );
        }

        if (!DOMESTIC_CLEARING.equalsIgnoreCase(routeType)
                && !CROSS_BORDER_CORRESPONDENT.equalsIgnoreCase(routeType)) {
            throw new IllegalStateException(
                    "Unsupported payment route type: " + routeType
            );
        }

        BigDecimal exchangeRate = getExchangeRate(
                sourceCurrency,
                destinationCurrency
        );

        BigDecimal transferFee = getTransferFee(
                routeType,
                sourceCurrency,
                destinationCurrency,
                sourceScale
        );

        BigDecimal sourcePrincipal;
        BigDecimal destinationAmount;

        if (request.amountMode() == AmountMode.SOURCE_AMOUNT) {

            sourcePrincipal = request.enteredAmount()
                    .setScale(sourceScale, RoundingMode.UNNECESSARY);

            destinationAmount = sourcePrincipal.divide(
                    exchangeRate,
                    destinationScale,
                    RoundingMode.HALF_UP
            );

        } else {

            destinationAmount = request.enteredAmount()
                    .setScale(destinationScale, RoundingMode.UNNECESSARY);

            sourcePrincipal = destinationAmount
                    .multiply(exchangeRate)
                    .setScale(sourceScale, RoundingMode.CEILING);
        }

        BigDecimal totalSenderDebit = sourcePrincipal
                .add(transferFee)
                .setScale(sourceScale, RoundingMode.UNNECESSARY);

        return new PaymentCalculationResult(
                request.amountMode(),
                routeType,
                sourceCurrency,
                destinationCurrency,
                sourcePrincipal,
                destinationAmount,
                exchangeRate,
                transferFee,
                sourceCurrency,
                totalSenderDebit
        );
    }


    private BigDecimal getExchangeRate(
            String sourceCurrency,
            String destinationCurrency
    ) {
        if (sourceCurrency.equals(destinationCurrency)) {
            return BigDecimal.ONE;
        }

        return fxRateService.getRate(
                sourceCurrency,
                destinationCurrency
        ).rate();
    }


    private BigDecimal getTransferFee(
            String routeType,
            String sourceCurrency,
            String destinationCurrency,
            int sourceScale
    ) {
        if (DOMESTIC_CLEARING.equalsIgnoreCase(routeType)) {
            return BigDecimal.ZERO.setScale(sourceScale);
        }

        String key = sourceCurrency + "_" + destinationCurrency;

        BigDecimal fee = findConfiguredValue(
                financialProperties.getCrossBorderFees(),
                key
        );

        if (fee == null || fee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "Missing or invalid transfer fee configuration for " + key
            );
        }

        return fee.setScale(sourceScale, RoundingMode.UNNECESSARY);
    }

    private BigDecimal findConfiguredValue(
            Map<String, BigDecimal> values,
            String key
    ) {
        if (values == null) {
            return null;
        }

        return values.entrySet()
                .stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(key))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private int getCurrencyScale(String currencyCode) {
        try {
            int scale = Currency.getInstance(currencyCode)
                    .getDefaultFractionDigits();

            if (scale < 0) {
                throw new IllegalArgumentException(
                        "Unsupported currency precision: " + currencyCode
                );
            }

            return scale;

        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid currency code: " + currencyCode,
                    exception
            );
        }
    }

    private void validateRequest(PaymentCalculationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Payment calculation request must not be null."
            );
        }

        if (request.amountMode() == null) {
            throw new IllegalArgumentException(
                    "Amount mode must not be null."
            );
        }

        if (request.enteredAmount() == null
                || request.enteredAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Entered amount must be greater than zero."
            );
        }

        normalize(request.sourceCurrency());
        normalize(request.destinationCurrency());
    }

    private String normalize(String value) {
        if (value == null || !value.matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException(
                    "Currency must be a valid three-letter code."
            );
        }

        return value.toUpperCase(Locale.ROOT);
    }
}
