
package com.fintech.controltower.service.network;

import com.fintech.controltower.domain.PaymentRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class DomesticClearingAdapter implements PaymentNetworkAdapter {

    private final NetworkSubmissionResult.SubmissionStatus simulatedOutcome;

    public DomesticClearingAdapter(
            @Value("${payment-network.simulation.domestic-outcome:SIMULATED_ACKNOWLEDGED}")
            NetworkSubmissionResult.SubmissionStatus simulatedOutcome
    ) {
        this.simulatedOutcome = simulatedOutcome;
    }

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

        LocalDateTime respondedAt =
                simulatedOutcome == NetworkSubmissionResult.SubmissionStatus.SIMULATED_TIMEOUT
                        ? null
                        : LocalDateTime.now();

        String responseMessage = switch (simulatedOutcome) {
            case SIMULATED_ACKNOWLEDGED ->
                    "Simulated domestic clearing network acknowledged the payment instruction.";

            case SIMULATED_NACK ->
                    "Simulated domestic clearing network rejected the payment instruction.";

            case SIMULATED_TIMEOUT ->
                    "No response received from the simulated domestic clearing network.";
        };

        return new NetworkSubmissionResult(
                getRouteType(),
                networkReference,
                simulatedOutcome,
                submittedAt,
                respondedAt,
                responseMessage,
                null
        );
    }
}
