package com.securebank.model;

import com.securebank.exception.InsufficientFundsException;
import com.securebank.exception.InvalidAmountException;

import java.util.Map;

/** Current: may dip into an overdraft of up to Rs. 5000. */
public class CurrentAccount extends Account {
    public static final double OVERDRAFT_LIMIT = 5000.0;   // final

    public CurrentAccount(long no, String holder, double opening, String pinHash, String pinSalt) {
        super(no, holder, opening, pinHash, pinSalt);
    }

    public CurrentAccount(long no, String holder, double balance, String pinHash, String pinSalt,
                          int failed, boolean locked, long createdAt) {
        super(no, holder, balance, pinHash, pinSalt, failed, locked, createdAt);
    }

    @Override public String getType() { return "CURRENT"; }

    @Override
    public double getAvailableToWithdraw() {
        return Math.max(0, round2(getBalance() + OVERDRAFT_LIMIT));
    }

    @Override
    public String withdraw(double amount) throws InsufficientFundsException, InvalidAmountException {
        validateAmount(amount);
        double available = getAvailableToWithdraw();
        if (amount > available) {
            throw new InsufficientFundsException(
                    "Overdraft limit of " + money(OVERDRAFT_LIMIT) + " exceeded.", round2(amount - available));
        }
        boolean usesOverdraft = getBalance() - amount < 0;
        debit(amount);
        return (usesOverdraft ? "Withdrawn using overdraft. " : "Withdrawn " + money(amount) + ". ")
                + "New balance: " + money(getBalance());
    }

    public double getOverdraftUsed() { return Math.max(0, -getBalance()); }

    @Override
    public String displayDetails() {
        return super.displayDetails() + "\n"
             + "Overdraft    : " + money(OVERDRAFT_LIMIT) + " (used " + money(getOverdraftUsed()) + ")";
    }

    @Override
    public Map<String, Object> toView() {
        Map<String, Object> m = super.toView();
        m.put("overdraftLimit", OVERDRAFT_LIMIT);
        m.put("overdraftUsed", getOverdraftUsed());
        m.put("overdraftRemaining", Math.max(0, OVERDRAFT_LIMIT - getOverdraftUsed()));
        return m;
    }
}
