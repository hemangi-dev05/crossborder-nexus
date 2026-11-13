
package com.fintech.controltower.api.dto;

import com.fintech.controltower.domain.NetworkResponse;

public record DelayedNetworkResponseRequest(
        NetworkResponse.ResponseStatus status,
        String responseMessage
) {
}
