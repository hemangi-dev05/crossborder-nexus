
package com.fintech.controltower.service;

import com.fintech.controltower.config.PaymentRoutingProperties;
import com.fintech.controltower.exception.UnsupportedCorridorException;

import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class PaymentRoutingService {

    private final PaymentRoutingProperties routingProperties;

    public PaymentRoutingService(
            PaymentRoutingProperties routingProperties
    ) {
        this.routingProperties = routingProperties;
    }

    public String determineRoute(
            String debtorCountryCode,
            String creditorCountryCode,
            String currency
    ) {

        String debtorCountry = normalize(debtorCountryCode);
        String creditorCountry = normalize(creditorCountryCode);
        String paymentCurrency = normalize(currency);

        return routingProperties.getCorridors()
                .stream()

                // Ignore disabled corridors.
                .filter(PaymentRoutingProperties.Corridor::isEnabled)

                // Match debtor country.
                .filter(corridor ->
                        normalize(corridor.getDebtorCountryCode())
                                .equals(debtorCountry))

                // Match creditor country.
                .filter(corridor ->
                        normalize(corridor.getCreditorCountryCode())
                                .equals(creditorCountry))

                // Match payment currency.
                .filter(corridor ->
                        normalize(corridor.getCurrency())
                                .equals(paymentCurrency))

                // Return the configured route type.
                .map(PaymentRoutingProperties.Corridor::getRouteType)

                .findFirst()

                // Reject unsupported or disabled corridors.
                .orElseThrow(() -> new UnsupportedCorridorException(
                        "Unsupported or disabled payment corridor: "
                                + debtorCountry + " -> "
                                + creditorCountry + " in "
                                + paymentCurrency
                ));
    }

    private String normalize(String value) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Country codes and currency must not be blank."
            );
        }

        return value.trim().toUpperCase(Locale.ROOT);
    }
}
