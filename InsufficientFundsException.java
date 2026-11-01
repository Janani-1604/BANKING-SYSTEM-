package com.securebank.exception;

import java.util.Map;

/** Thrown when a withdrawal / transfer breaks an account rule. Carries the shortfall. */
public final class InsufficientFundsException extends BankException {
    private final double shortfall;

    public InsufficientFundsException(String message, double shortfall) {
        super(message, "INSUFFICIENT_FUNDS", 422);
        this.shortfall = shortfall;
    }

    public double getShortfall() { return shortfall; }

    @Override
    public Map<String, Object> details() { return Map.of("shortfall", shortfall); }
}
