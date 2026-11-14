
package com.fintech.controltower.service.network;

import com.fintech.controltower.domain.model.PaymentRequest;
import com.fintech.controltower.financial.model.PaymentCalculationResult;
import com.fintech.controltower.service.iso20022.IsoMessageCompilerService;

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
            PaymentCalculationResult calculation,
            String uetr
    ) {

        LocalDateTime submittedAt = LocalDateTime.now();

        // Generate ISO 20022 using the calculated destination amount.
        String isoXmlMessage =
                isoCompilerService.compilePacs008(request, calculation, uetr);

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
