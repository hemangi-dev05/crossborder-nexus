
package com.fintech.controltower.api.dto;

import com.fintech.controltower.domain.entity.NetworkResponse;

public record DelayedNetworkResponseRequest(
        NetworkResponse.ResponseStatus status,
        String responseMessage
) {
}
