
package com.fintech.controltower.service.network;

import com.fintech.controltower.domain.PaymentRequest;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class DomesticClearingAdapter implements PaymentNetworkAdapter {

    @Override
    public String getRouteType() {
        return "DOMESTIC_CLEARING";
    }

    @Override
    public NetworkSubmissionResult submit(
            PaymentRequest request,
            String uetr
    ) {

        LocalDateTime submittedAt = LocalDateTime.now();

        String networkReference =
                "SIM-DOM-" + UUID.randomUUID();

        // Simulate a successful domestic network acknowledgement.
        LocalDateTime respondedAt = LocalDateTime.now();

        return new NetworkSubmissionResult(
                getRouteType(),
                networkReference,
                NetworkSubmissionResult.SubmissionStatus.SIMULATED_ACKNOWLEDGED,
                submittedAt,
                respondedAt,
                "Simulated domestic clearing network acknowledged the payment instruction.",
                null
        );
    }
}
