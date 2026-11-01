package com.securebank.exception;

public final class AccountNotFoundException extends BankException {
    public AccountNotFoundException(long accountNo) {
        super("Account #" + accountNo + " was not found.", "ACCOUNT_NOT_FOUND", 404);
    }
}
