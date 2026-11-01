package com.securebank.exception;

import java.util.Map;

public final class InvalidPinException extends BankException {
    private final int attemptsLeft;

    public InvalidPinException(int attemptsLeft) {
        super("Wrong PIN. " + attemptsLeft + (attemptsLeft == 1 ? " attempt" : " attempts") + " left before the account is locked.",
              "INVALID_PIN", 401);
        this.attemptsLeft = attemptsLeft;
    }

    public int getAttemptsLeft() { return attemptsLeft; }

    @Override
    public Map<String, Object> details() { return Map.of("attemptsLeft", attemptsLeft); }
}
