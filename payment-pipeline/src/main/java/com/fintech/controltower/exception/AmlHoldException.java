package com.fintech.controltower.exception;

public class AmlHoldException extends SecurityException {

    private final String uetr;
    private final String reason;

    public AmlHoldException(String uetr, String reason) {
        super("Transaction blocked by AML compliance policy. UETR: "
                + uetr + ". Reason: " + reason);

        this.uetr = uetr;
        this.reason = reason;
    }

    public String getUetr() {
        return uetr;
    }

    public String getReason() {
        return reason;
    }
}