package com.securebank.model;

public enum TxType {
    OPEN(true), DEPOSIT(true), WITHDRAW(false), TRANSFER_IN(true), TRANSFER_OUT(false), INTEREST(true);

    private final boolean credit;
    TxType(boolean credit) { this.credit = credit; }
    public boolean isCredit() { return credit; }
}
