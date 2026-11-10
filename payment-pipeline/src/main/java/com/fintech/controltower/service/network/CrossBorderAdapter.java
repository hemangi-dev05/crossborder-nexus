
package com.fintech.controltower.service.network;

import com.fintech.controltower.domain.PaymentRequest;
import com.fintech.controltower.service.IsoMessageCompilerService;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class CrossBorderAdapter implements PaymentNetworkAdapter {

    private final IsoMessageCompilerService isoCompilerService;

    public CrossBorderAdapter(
            IsoMessageCompilerService isoCompilerService
    ) {
        this.isoCompilerService = isoCompilerService;
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

        // Simulate a successful correspondent-network acknowledgement.
        LocalDateTime respondedAt = LocalDateTime.now();

        return new NetworkSubmissionResult(
                getRouteType(),
                networkReference,
                NetworkSubmissionResult.SubmissionStatus.SIMULATED_ACKNOWLEDGED,
                submittedAt,
                respondedAt,
                "Simulated correspondent network acknowledged the ISO 20022 payment instruction.",
                isoXmlMessage
        );
    }
}
