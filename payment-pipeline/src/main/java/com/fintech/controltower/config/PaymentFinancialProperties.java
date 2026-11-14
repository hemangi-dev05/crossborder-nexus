
package com.fintech.controltower.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "payment-financial")
public class PaymentFinancialProperties {

    private Map<String, BigDecimal> fxRates = new HashMap<>();

    private Map<String, BigDecimal> crossBorderFees = new HashMap<>();

    public Map<String, BigDecimal> getFxRates() {
        return fxRates;
    }

    public void setFxRates(Map<String, BigDecimal> fxRates) {
        this.fxRates = fxRates;
    }

    public Map<String, BigDecimal> getCrossBorderFees() {
        return crossBorderFees;
    }

    public void setCrossBorderFees(Map<String, BigDecimal> crossBorderFees) {
        this.crossBorderFees = crossBorderFees;
    }
}
