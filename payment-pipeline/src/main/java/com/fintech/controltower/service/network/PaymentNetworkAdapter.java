package com.fintech.controltower.service.network;

import com.fintech.controltower.domain.PaymentRequest;

public interface PaymentNetworkAdapter {

    String getRouteType();

    NetworkSubmissionResult submit(
            PaymentRequest request,
            String uetr
    );
}