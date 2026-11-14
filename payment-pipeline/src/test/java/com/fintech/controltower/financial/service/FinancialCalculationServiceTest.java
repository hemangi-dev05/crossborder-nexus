
package com.fintech.controltower.financial.service;

import com.fintech.controltower.config.PaymentFinancialProperties;
import com.fintech.controltower.config.PaymentRoutingProperties;
import com.fintech.controltower.financial.model.AmountMode;
import com.fintech.controltower.financial.model.PaymentCalculationRequest;
import com.fintech.controltower.financial.model.PaymentCalculationResult;
import com.fintech.controltower.service.payment.PaymentRoutingService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FinancialCalculationServiceTest {

    private FinancialCalculationService calculationService;
    private PaymentFinancialProperties financialProperties;

    @BeforeEach
    void setUp() {
        PaymentRoutingProperties routingProperties =
                new PaymentRoutingProperties();

        PaymentRoutingProperties.Corridor indiaIndia =
                createCorridor("IN", "IN", "INR", "DOMESTIC_CLEARING");

        PaymentRoutingProperties.Corridor indiaUs =
                createCorridor(
                        "IN", "US", "USD",
                        "CROSS_BORDER_CORRESPONDENT"
                );

        routingProperties.setCorridors(
                List.of(indiaIndia, indiaUs)
        );

        financialProperties = new PaymentFinancialProperties();

        financialProperties.setFxRates(Map.of(
                "INR_USD", new BigDecimal("84.70")
        ));

        financialProperties.setCrossBorderFees(Map.of(
                "INR_USD", new BigDecimal("250.00"),
                "USD_USD", new BigDecimal("5.00")
        ));

        calculationService = new FinancialCalculationService(
                financialProperties,
                new PaymentRoutingService(routingProperties)
        );
    }

    @Test
    void shouldCalculateDomesticPaymentWithoutFee() {
        PaymentCalculationRequest request = new PaymentCalculationRequest(
                "IN",
                "IN",
                "INR",
                "INR",
                AmountMode.SOURCE_AMOUNT,
                new BigDecimal("10000.00")
        );

        PaymentCalculationResult result =
                calculationService.calculate(request);

        assertEquals(new BigDecimal("10000.00"), result.sourcePrincipal());
        assertEquals(new BigDecimal("10000.00"), result.destinationAmount());
        assertEquals(new BigDecimal("0.00"), result.transferFee());
        assertEquals(new BigDecimal("10000.00"), result.totalSenderDebit());
        assertEquals(BigDecimal.ONE, result.exchangeRate());
        assertEquals("DOMESTIC_CLEARING", result.routeType());
    }

    @Test
    void shouldCalculateCrossBorderSourceAmount() {
        PaymentCalculationRequest request = new PaymentCalculationRequest(
                "IN",
                "US",
                "INR",
                "USD",
                AmountMode.SOURCE_AMOUNT,
                new BigDecimal("85000.00")
        );

        PaymentCalculationResult result =
                calculationService.calculate(request);

        assertEquals(new BigDecimal("85000.00"), result.sourcePrincipal());
        assertEquals(new BigDecimal("1003.54"), result.destinationAmount());
        assertEquals(new BigDecimal("84.70"), result.exchangeRate());
        assertEquals(new BigDecimal("250.00"), result.transferFee());
        assertEquals("INR", result.feeCurrency());
        assertEquals(new BigDecimal("85250.00"), result.totalSenderDebit());
    }

    @Test
    void shouldCalculateCrossBorderReceiverAmount() {
        PaymentCalculationRequest request = new PaymentCalculationRequest(
                "IN",
                "US",
                "INR",
                "USD",
                AmountMode.RECEIVER_AMOUNT,
                new BigDecimal("1000.00")
        );

        PaymentCalculationResult result =
                calculationService.calculate(request);

        assertEquals(new BigDecimal("84700.00"), result.sourcePrincipal());
        assertEquals(new BigDecimal("1000.00"), result.destinationAmount());
        assertEquals(new BigDecimal("250.00"), result.transferFee());
        assertEquals(new BigDecimal("84950.00"), result.totalSenderDebit());
    }

    @Test
    void shouldRoundReceiverAmountSourcePrincipalUpward() {
        PaymentCalculationRequest request = new PaymentCalculationRequest(
                "IN",
                "US",
                "INR",
                "USD",
                AmountMode.RECEIVER_AMOUNT,
                new BigDecimal("1000.01")
        );

        PaymentCalculationResult result =
                calculationService.calculate(request);

        assertEquals(
                new BigDecimal("84700.85"),
                result.sourcePrincipal()
        );

        assertEquals(
                new BigDecimal("84950.85"),
                result.totalSenderDebit()
        );
    }

    @Test
    void shouldApplyFeeForSameCurrencyCrossBorderPayment() {
        PaymentCalculationRequest request = new PaymentCalculationRequest(
                "IN",
                "US",
                "USD",
                "USD",
                AmountMode.SOURCE_AMOUNT,
                new BigDecimal("1000.00")
        );

        PaymentCalculationResult result =
                calculationService.calculate(request);

        assertEquals("CROSS_BORDER_CORRESPONDENT", result.routeType());
        assertEquals(new BigDecimal("1000.00"), result.sourcePrincipal());
        assertEquals(new BigDecimal("1000.00"), result.destinationAmount());
        assertEquals(BigDecimal.ONE, result.exchangeRate());
        assertEquals(new BigDecimal("5.00"), result.transferFee());
        assertEquals("USD", result.feeCurrency());
        assertEquals(new BigDecimal("1005.00"), result.totalSenderDebit());
    }

    @Test
    void shouldRejectMissingFxRate() {
        financialProperties.setFxRates(Map.of());

        PaymentCalculationRequest request = new PaymentCalculationRequest(
                "IN",
                "US",
                "INR",
                "USD",
                AmountMode.SOURCE_AMOUNT,
                new BigDecimal("1000.00")
        );

        assertThrows(
                IllegalStateException.class,
                () -> calculationService.calculate(request)
        );
    }

    @Test
    void shouldRejectMissingTransferFee() {
        financialProperties.setCrossBorderFees(Map.of());

        PaymentCalculationRequest request = new PaymentCalculationRequest(
                "IN",
                "US",
                "INR",
                "USD",
                AmountMode.SOURCE_AMOUNT,
                new BigDecimal("1000.00")
        );

        assertThrows(
                IllegalStateException.class,
                () -> calculationService.calculate(request)
        );
    }

    @Test
    void shouldRejectZeroAmount() {
        PaymentCalculationRequest request = new PaymentCalculationRequest(
                "IN",
                "US",
                "INR",
                "USD",
                AmountMode.SOURCE_AMOUNT,
                BigDecimal.ZERO
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> calculationService.calculate(request)
        );
    }

    @Test
    void shouldRejectNegativeAmount() {
        PaymentCalculationRequest request = new PaymentCalculationRequest(
                "IN",
                "US",
                "INR",
                "USD",
                AmountMode.SOURCE_AMOUNT,
                new BigDecimal("-100.00")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> calculationService.calculate(request)
        );
    }

    @Test
    void shouldRejectInvalidCurrencyCode() {
        PaymentCalculationRequest request = new PaymentCalculationRequest(
                "IN",
                "US",
                "INVALID",
                "USD",
                AmountMode.SOURCE_AMOUNT,
                new BigDecimal("1000.00")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> calculationService.calculate(request)
        );
    }

    @Test
    void shouldRejectSourceAmountWithExcessFractionalDigits() {
        PaymentCalculationRequest request = new PaymentCalculationRequest(
                "IN",
                "US",
                "INR",
                "USD",
                AmountMode.SOURCE_AMOUNT,
                new BigDecimal("1000.001")
        );

        assertThrows(
                ArithmeticException.class,
                () -> calculationService.calculate(request)
        );
    }

    private PaymentRoutingProperties.Corridor createCorridor(
            String debtorCountry,
            String creditorCountry,
            String settlementCurrency,
            String routeType
    ) {
        PaymentRoutingProperties.Corridor corridor =
                new PaymentRoutingProperties.Corridor();

        corridor.setDebtorCountryCode(debtorCountry);
        corridor.setCreditorCountryCode(creditorCountry);
        corridor.setSettlementCurrency(settlementCurrency);
        corridor.setRouteType(routeType);
        corridor.setEnabled(true);

        return corridor;
    }
}
