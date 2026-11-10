
package com.fintech.controltower.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "payment-routing")
public class PaymentRoutingProperties {

    private List<Corridor> corridors = new ArrayList<>();

    public List<Corridor> getCorridors() {
        return corridors;
    }

    public void setCorridors(List<Corridor> corridors) {
        this.corridors = corridors;
    }

    public static class Corridor {

        private String debtorCountryCode;
        private String creditorCountryCode;
        private String currency;
        private String routeType;
        private boolean enabled;

        public String getDebtorCountryCode() {
            return debtorCountryCode;
        }

        public void setDebtorCountryCode(String debtorCountryCode) {
            this.debtorCountryCode = debtorCountryCode;
        }

        public String getCreditorCountryCode() {
            return creditorCountryCode;
        }

        public void setCreditorCountryCode(String creditorCountryCode) {
            this.creditorCountryCode = creditorCountryCode;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getRouteType() {
            return routeType;
        }

        public void setRouteType(String routeType) {
            this.routeType = routeType;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
