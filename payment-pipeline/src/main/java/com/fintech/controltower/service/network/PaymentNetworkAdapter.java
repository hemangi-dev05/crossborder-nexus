
package com.fintech.controltower.service.network;

import com.fintech.controltower.domain.model.PaymentRequest;
import com.fintech.controltower.financial.model.PaymentCalculationResult;

public interface PaymentNetworkAdapter {

    String getRouteType();

    NetworkSubmissionResult submit(
            PaymentRequest request,
            PaymentCalculationResult calculation,
            String uetr
    );
}
