
package com.fintech.controltower.service.network;

import com.fintech.controltower.exception.UnsupportedCorridorException;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NetworkAdapterRegistry {

    private final Map<String, PaymentNetworkAdapter> adapters;

    public NetworkAdapterRegistry(
            List<PaymentNetworkAdapter> adapterList
    ) {

        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(
                        PaymentNetworkAdapter::getRouteType,
                        Function.identity()
                ));
    }

    public PaymentNetworkAdapter getAdapter(String routeType) {

        if (routeType == null || routeType.isBlank()) {
            throw new IllegalArgumentException(
                    "Route type cannot be empty."
            );
        }

        PaymentNetworkAdapter adapter = adapters.get(routeType);

        if (adapter == null) {
            throw new UnsupportedCorridorException(
                    "No network adapter registered for route: " + routeType
            );
        }

        return adapter;
    }
}

