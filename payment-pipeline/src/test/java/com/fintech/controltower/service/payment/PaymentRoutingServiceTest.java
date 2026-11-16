
package com.fintech.controltower.service.payment;

import com.fintech.controltower.config.PaymentRoutingProperties;
import com.fintech.controltower.exception.UnsupportedCorridorException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaymentRoutingServiceTest {

    private PaymentRoutingService routingService;

    @BeforeEach
    void setUp() {
        PaymentRoutingProperties properties = new PaymentRoutingProperties();

        PaymentRoutingProperties.Corridor indiaIndia = createCorridor(
                "IN", "IN", "INR", "DOMESTIC_CLEARING", true);

        PaymentRoutingProperties.Corridor indiaUs = createCorridor(
                "IN", "US", "USD", "CROSS_BORDER_CORRESPONDENT", true);

        PaymentRoutingProperties.Corridor indiaUk = createCorridor(
                "IN", "GB", "GBP", "CROSS_BORDER_CORRESPONDENT", true);

        PaymentRoutingProperties.Corridor indiaSingapore = createCorridor(
                "IN", "SG", "SGD", "CROSS_BORDER_CORRESPONDENT", true);

        PaymentRoutingProperties.Corridor usUk = createCorridor(
                "US", "GB", "GBP", "CROSS_BORDER_CORRESPONDENT", true);

        PaymentRoutingProperties.Corridor germanyUs = createCorridor(
                "DE", "US", "USD", "CROSS_BORDER_CORRESPONDENT", true);

        PaymentRoutingProperties.Corridor indiaNepal = createCorridor(
                "IN", "NP", "INR", "CROSS_BORDER_CORRESPONDENT", true);

        properties.setCorridors(List.of(
                indiaIndia,
                indiaUs,
                indiaUk,
                indiaSingapore,
                usUk,
                germanyUs,
                indiaNepal
        ));

        routingService = new PaymentRoutingService(properties);
    }

    @Test
    void shouldRouteIndiaDomesticPayment() {
        assertEquals(
                "DOMESTIC_CLEARING",
                routingService.determineRoute("IN", "IN", "INR")
        );
    }

    @Test
    void shouldRouteIndiaToUsPayment() {
        assertEquals(
                "CROSS_BORDER_CORRESPONDENT",
                routingService.determineRoute("IN", "US", "USD")
        );
    }

    @Test
    void shouldRouteIndiaToUkPayment() {
        assertEquals(
                "CROSS_BORDER_CORRESPONDENT",
                routingService.determineRoute("IN", "GB", "GBP")
        );
    }

    @Test
    void shouldRouteIndiaToSingaporePayment() {
        assertEquals(
                "CROSS_BORDER_CORRESPONDENT",
                routingService.determineRoute("IN", "SG", "SGD")
        );
    }

    @Test
    void shouldRouteUsToUkPayment() {
        assertEquals(
                "CROSS_BORDER_CORRESPONDENT",
                routingService.determineRoute("US", "GB", "GBP")
        );
    }

    @Test
    void shouldRouteGermanyToUsPayment() {
        assertEquals(
                "CROSS_BORDER_CORRESPONDENT",
                routingService.determineRoute("DE", "US", "USD")
        );
    }

    @Test
    void shouldRouteIndiaToNepalPayment() {
        assertEquals(
                "CROSS_BORDER_CORRESPONDENT",
                routingService.determineRoute("IN", "NP", "INR")
        );
    }

    @Test
    void shouldRejectUnsupportedCorridor() {
        assertThrows(
                UnsupportedCorridorException.class,
                () -> routingService.determineRoute("IN", "US", "INR")
        );
    }

    @Test
    void shouldRejectDisabledCorridor() {
        PaymentRoutingProperties disabledProperties =
                new PaymentRoutingProperties();

        disabledProperties.setCorridors(List.of(
                createCorridor(
                        "IN", "US", "USD",
                        "CROSS_BORDER_CORRESPONDENT", false)
        ));

        PaymentRoutingService disabledRoutingService =
                new PaymentRoutingService(disabledProperties);

        assertThrows(
                UnsupportedCorridorException.class,
                () -> disabledRoutingService.determineRoute("IN", "US", "USD")
        );
    }

    @Test
    void shouldNormalizeLowercaseInputs() {
        assertEquals(
                "CROSS_BORDER_CORRESPONDENT",
                routingService.determineRoute("in", "us", "usd")
        );
    }

    @Test
    void shouldRejectNullCountryCode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> routingService.determineRoute(null, "US", "USD")
        );
    }

    @Test
    void shouldRejectBlankSettlementCurrency() {
        assertThrows(
                IllegalArgumentException.class,
                () -> routingService.determineRoute("IN", "US", " ")
        );
    }

    private PaymentRoutingProperties.Corridor createCorridor(
            String debtorCountry,
            String creditorCountry,
            String settlementCurrency,
            String routeType,
            boolean enabled) {

        PaymentRoutingProperties.Corridor corridor =
                new PaymentRoutingProperties.Corridor();

        corridor.setDebtorCountryCode(debtorCountry);
        corridor.setCreditorCountryCode(creditorCountry);
        corridor.setSettlementCurrency(settlementCurrency);
        corridor.setRouteType(routeType);
        corridor.setEnabled(enabled);

        return corridor;
    }
}

