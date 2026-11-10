package com.fintech.controltower.api.dto;

import java.util.List;

public record PaymentTrackingResponse(
        PaymentDetailsResponse payment,
        List<NetworkSubmissionResponse> networkSubmissions,
        List<IsoMessageResponse> isoMessages,
        List<PaymentHistoryResponse> statusHistory
) {
}