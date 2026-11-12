
package com.fintech.controltower.service.network;

import com.fintech.controltower.domain.PaymentRequest;
import com.fintech.controltower.service.IsoMessageCompilerService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class CrossBorderAdapter implements PaymentNetworkAdapter {

    private final IsoMessageCompilerService isoCompilerService;

    private final NetworkSubmissionResult.SubmissionStatus simulatedOutcome;

    public CrossBorderAdapter(
            IsoMessageCompilerService isoCompilerService,
            @Value("${payment-network.simulation.cross-border-outcome:SIMULATED_ACKNOWLEDGED}")
            NetworkSubmissionResult.SubmissionStatus simulatedOutcome
    ) {
        this.isoCompilerService = isoCompilerService;
        this.simulatedOutcome = simulatedOutcome;
    }

    @Override
    public String getRouteType() {
        return "CROSS_BORDER_CORRESPONDENT";
    }

    @Override
    public NetworkSubmissionResult submit(
            PaymentRequest request,
            String uetr
    ) {

        LocalDateTime submittedAt = LocalDateTime.now();

        // Generate and validate the ISO 20022 payment instruction.
        String isoXmlMessage =
                isoCompilerService.compilePacs008(request, uetr);

        if (isoXmlMessage == null || isoXmlMessage.isBlank()) {
            throw new IllegalStateException(
                    "ISO 20022 message generation failed."
            );
        }

        String networkReference =
                "SIM-CB-" + UUID.randomUUID();

        LocalDateTime respondedAt =
                simulatedOutcome == NetworkSubmissionResult.SubmissionStatus.SIMULATED_TIMEOUT
                        ? null
                        : LocalDateTime.now();

        String responseMessage = switch (simulatedOutcome) {
            case SIMULATED_ACKNOWLEDGED ->
                    "Simulated correspondent network acknowledged the ISO 20022 payment instruction.";

            case SIMULATED_NACK ->
                    "Simulated correspondent network rejected the ISO 20022 payment instruction.";

            case SIMULATED_TIMEOUT ->
                    "No response received from the simulated correspondent network.";
        };

        return new NetworkSubmissionResult(
                getRouteType(),
                networkReference,
                simulatedOutcome,
                submittedAt,
                respondedAt,
                responseMessage,
                isoXmlMessage
        );
    }
}
