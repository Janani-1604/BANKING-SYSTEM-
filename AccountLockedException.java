package com.securebank.exception;

public final class AccountLockedException extends BankException {
    public AccountLockedException(long accountNo, boolean justLocked) {
        super(justLocked
                ? "Wrong PIN 3 times. Account #" + accountNo + " is now LOCKED. Ask an admin to unlock it."
                : "Account #" + accountNo + " is LOCKED. Ask an admin to unlock it.",
              "ACCOUNT_LOCKED", 423);
    }
}
