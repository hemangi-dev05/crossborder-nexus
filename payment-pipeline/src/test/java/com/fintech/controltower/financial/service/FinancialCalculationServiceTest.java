
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
import static org.mockito.Mockito.*;

class FinancialCalculationServiceTest {

    private FinancialCalculationService calculationService;
    private PaymentFinancialProperties financialProperties;
    private FxRateService fxRateService;

    @BeforeEach
    void setUp() {

        PaymentRoutingProperties routingProperties =
                new PaymentRoutingProperties();

        routingProperties.setCorridors(List.of(
                createCorridor("IN", "IN", "INR", "DOMESTIC_CLEARING"),
                createCorridor("IN", "US", "USD", "CROSS_BORDER_CORRESPONDENT"),
                createCorridor("IN", "GB", "GBP", "CROSS_BORDER_CORRESPONDENT"),
                createCorridor("IN", "SG", "SGD", "CROSS_BORDER_CORRESPONDENT"),
                createCorridor("US", "GB", "GBP", "CROSS_BORDER_CORRESPONDENT"),
                createCorridor("DE", "US", "USD", "CROSS_BORDER_CORRESPONDENT"),
                createCorridor("IN", "NP", "INR", "CROSS_BORDER_CORRESPONDENT")
        ));

        financialProperties = new PaymentFinancialProperties();

        financialProperties.setCrossBorderFees(Map.of(
                "INR_INR", new BigDecimal("250.00"),
                "INR_USD", new BigDecimal("250.00"),
                "INR_GBP", new BigDecimal("300.00"),
                "INR_SGD", new BigDecimal("200.00"),
                "USD_GBP", new BigDecimal("5.00"),
                "EUR_USD", new BigDecimal("5.00")
        ));

        fxRateService = mock(FxRateService.class);

        // Simulate the rates returned by the live FX service.
        when(fxRateService.getRate("INR", "USD"))
                .thenReturn(createQuote("84.70"));

        when(fxRateService.getRate("INR", "GBP"))
                .thenReturn(createQuote("110.00"));

        when(fxRateService.getRate("INR", "SGD"))
                .thenReturn(createQuote("63.00"));

        when(fxRateService.getRate("USD", "GBP"))
                .thenReturn(createQuote("0.79"));

        when(fxRateService.getRate("EUR", "USD"))
                .thenReturn(createQuote("1.08"));

        calculationService = new FinancialCalculationService(
                financialProperties,
                new PaymentRoutingService(routingProperties),
                fxRateService
        );
    }

    @Test
    void shouldCalculateDomesticPaymentWithoutFee() {

        PaymentCalculationRequest request = new PaymentCalculationRequest(
                "IN", "IN", "INR", "INR",
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

        verify(fxRateService, never()).getRate("INR", "INR");
    }

    @Test
    void shouldCalculateInrToUsdPayment() {
        assertSourceCalculation(
                "IN", "US", "INR", "USD",
                "85000.00", "84.70",
                "1003.54", "250.00", "85250.00"
        );
    }

    @Test
    void shouldCalculateInrToGbpPayment() {
        assertSourceCalculation(
                "IN", "GB", "INR", "GBP",
                "110000.00", "110.00",
                "1000.00", "300.00", "110300.00"
        );
    }

    @Test
    void shouldCalculateInrToSgdPayment() {
        assertSourceCalculation(
                "IN", "SG", "INR", "SGD",
                "63000.00", "63.00",
                "1000.00", "200.00", "63200.00"
        );
    }

    @Test
    void shouldCalculateUsdToGbpPayment() {
        assertSourceCalculation(
                "US", "GB", "USD", "GBP",
                "790.00", "0.79",
                "1000.00", "5.00", "795.00"
        );
    }

    @Test
    void shouldCalculateEurToUsdPayment() {
        assertSourceCalculation(
                "DE", "US", "EUR", "USD",
                "1080.00", "1.08",
                "1000.00", "5.00", "1085.00"
        );
    }

    @Test
    void shouldCalculateReceiverAmountForAllConfiguredFxPairs() {

        assertReceiverCalculation(
                "IN", "US", "INR", "USD",
                "84.70", "84700.00", "250.00", "84950.00"
        );

        assertReceiverCalculation(
                "IN", "GB", "INR", "GBP",
                "110.00", "110000.00", "300.00", "110300.00"
        );

        assertReceiverCalculation(
                "IN", "SG", "INR", "SGD",
                "63.00", "63000.00", "200.00", "63200.00"
        );

        assertReceiverCalculation(
                "US", "GB", "USD", "GBP",
                "0.79", "790.00", "5.00", "795.00"
        );

        assertReceiverCalculation(
                "DE", "US", "EUR", "USD",
                "1.08", "1080.00", "5.00", "1085.00"
        );
    }

    @Test
    void shouldRoundReceiverAmountSourcePrincipalUpward() {

        PaymentCalculationRequest request = new PaymentCalculationRequest(
                "IN", "US", "INR", "USD",
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
    void shouldCalculateIndiaToNepalSameCurrencyPayment() {

        PaymentCalculationRequest request = new PaymentCalculationRequest(
                "IN", "NP", "INR", "INR",
                AmountMode.SOURCE_AMOUNT,
                new BigDecimal("10000.00")
        );

        PaymentCalculationResult result =
                calculationService.calculate(request);

        assertEquals("CROSS_BORDER_CORRESPONDENT", result.routeType());
        assertEquals(new BigDecimal("10000.00"), result.sourcePrincipal());
        assertEquals(new BigDecimal("10000.00"), result.destinationAmount());
        assertEquals(BigDecimal.ONE, result.exchangeRate());
        assertEquals(new BigDecimal("250.00"), result.transferFee());
        assertEquals("INR", result.feeCurrency());
        assertEquals(new BigDecimal("10250.00"), result.totalSenderDebit());
    }

    @Test
    void shouldRejectMissingFxRate() {

        when(fxRateService.getRate("INR", "USD"))
                .thenThrow(new IllegalStateException(
                        "No valid FX rate available for INR_USD"
                ));

        PaymentCalculationRequest request = new PaymentCalculationRequest(
                "IN", "US", "INR", "USD",
                AmountMode.SOURCE_AMOUNT,
                new BigDecimal("1000.00")
        );

        assertThrows(
                IllegalStateException.class,
                () -> calculationService.calculate(request)
        );
    }

    @Test
    void shouldRejectInvalidFxRate() {

        when(fxRateService.getRate("INR", "USD"))
                .thenThrow(new IllegalStateException(
                        "No valid FX rate available for INR_USD"
                ));

        PaymentCalculationRequest request = new PaymentCalculationRequest(
                "IN", "US", "INR", "USD",
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
                "IN", "US", "INR", "USD",
                AmountMode.SOURCE_AMOUNT,
                new BigDecimal("1000.00")
        );

        assertThrows(
                IllegalStateException.class,
                () -> calculationService.calculate(request)
        );
    }

    @Test
    void shouldRejectNegativeTransferFee() {

        financialProperties.setCrossBorderFees(Map.of(
                "INR_USD", new BigDecimal("-10.00")
        ));

        PaymentCalculationRequest request = new PaymentCalculationRequest(
                "IN", "US", "INR", "USD",
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
                "IN", "US", "INR", "USD",
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
                "IN", "US", "INR", "USD",
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
                "IN", "US", "INVALID", "USD",
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
                "IN", "US", "INR", "USD",
                AmountMode.SOURCE_AMOUNT,
                new BigDecimal("1000.001")
        );

        assertThrows(
                ArithmeticException.class,
                () -> calculationService.calculate(request)
        );
    }

    private FxRateService.FxRateQuote createQuote(String rate) {
        return new FxRateService.FxRateQuote(
                new BigDecimal(rate),
                "TEST_PROVIDER",
                null
        );
    }

    private void assertSourceCalculation(
            String debtorCountry,
            String creditorCountry,
            String sourceCurrency,
            String destinationCurrency,
            String enteredAmount,
            String expectedRate,
            String expectedDestination,
            String expectedFee,
            String expectedTotalDebit
    ) {

        PaymentCalculationRequest request = new PaymentCalculationRequest(
                debtorCountry,
                creditorCountry,
                sourceCurrency,
                destinationCurrency,
                AmountMode.SOURCE_AMOUNT,
                new BigDecimal(enteredAmount)
        );

        PaymentCalculationResult result =
                calculationService.calculate(request);

        assertEquals(
                new BigDecimal(enteredAmount),
                result.sourcePrincipal()
        );

        assertEquals(
                new BigDecimal(expectedRate),
                result.exchangeRate()
        );

        assertEquals(
                new BigDecimal(expectedDestination),
                result.destinationAmount()
        );

        assertEquals(
                new BigDecimal(expectedFee),
                result.transferFee()
        );

        assertEquals(
                new BigDecimal(expectedTotalDebit),
                result.totalSenderDebit()
        );

        assertEquals(sourceCurrency, result.feeCurrency());

        verify(fxRateService).getRate(
                sourceCurrency,
                destinationCurrency
        );
    }

    private void assertReceiverCalculation(
            String debtorCountry,
            String creditorCountry,
            String sourceCurrency,
            String destinationCurrency,
            String expectedRate,
            String expectedSourcePrincipal,
            String expectedFee,
            String expectedTotalDebit
    ) {

        PaymentCalculationRequest request = new PaymentCalculationRequest(
                debtorCountry,
                creditorCountry,
                sourceCurrency,
                destinationCurrency,
                AmountMode.RECEIVER_AMOUNT,
                new BigDecimal("1000.00")
        );

        PaymentCalculationResult result =
                calculationService.calculate(request);

        assertEquals(
                new BigDecimal(expectedSourcePrincipal),
                result.sourcePrincipal()
        );

        assertEquals(
                new BigDecimal("1000.00"),
                result.destinationAmount()
        );

        assertEquals(
                new BigDecimal(expectedRate),
                result.exchangeRate()
        );

        assertEquals(
                new BigDecimal(expectedFee),
                result.transferFee()
        );

        assertEquals(
                new BigDecimal(expectedTotalDebit),
                result.totalSenderDebit()
        );

        assertEquals(sourceCurrency, result.feeCurrency());

        verify(fxRateService).getRate(
                sourceCurrency,
                destinationCurrency
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
