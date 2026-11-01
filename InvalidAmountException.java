package com.securebank.exception;

public final class InvalidAmountException extends BankException {
    public InvalidAmountException(String message) {
        super(message, "INVALID_AMOUNT", 400);
    }
}
