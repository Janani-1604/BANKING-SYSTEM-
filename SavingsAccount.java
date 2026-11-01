package com.securebank.model;

import com.securebank.exception.InsufficientFundsException;
import com.securebank.exception.InvalidAmountException;

import java.util.Map;

/** Savings: must always keep the minimum balance, earns 4% yearly interest. */
public class SavingsAccount extends Account {
    public static final double MIN_BALANCE = 1000.0;   // final: cannot be changed
    public static final double INTEREST_RATE = 0.04;   // 4% yearly

    public SavingsAccount(long no, String holder, double opening, String pinHash, String pinSalt) {
        super(no, holder, opening, pinHash, pinSalt);               // constructor chaining with super
    }

    public SavingsAccount(long no, String holder, double balance, String pinHash, String pinSalt,
                          int failed, boolean locked, long createdAt) {
        super(no, holder, balance, pinHash, pinSalt, failed, locked, createdAt);
    }

    @Override public String getType() { return "SAVINGS"; }

    @Override
    public double getAvailableToWithdraw() {
        return Math.max(0, round2(getBalance() - MIN_BALANCE));
    }

    @Override
    public String withdraw(double amount) throws InsufficientFundsException, InvalidAmountException {
        validateAmount(amount);
        double available = getAvailableToWithdraw();
        if (amount > available) {
            throw new InsufficientFundsException(
                    "Savings account must keep " + money(MIN_BALANCE) + " minimum.", round2(amount - available));
        }
        debit(amount);
        return "Withdrawn " + money(amount) + ". New balance: " + money(getBalance());
    }

    /** FR-7: only a SavingsAccount has this method. Returns the interest credited. */
    public double applyInterest() {
        double interest = round2(getBalance() * INTEREST_RATE);
        credit(interest);
        return interest;
    }

    @Override
    public String displayDetails() {
        return super.displayDetails() + "\n"
             + "Min balance  : " + money(MIN_BALANCE) + "\n"
             + "Interest     : " + (int) (INTEREST_RATE * 100) + "% yearly";
    }

    @Override
    public Map<String, Object> toView() {
        Map<String, Object> m = super.toView();
        m.put("minimumBalance", MIN_BALANCE);
        m.put("interestRate", INTEREST_RATE);
        m.put("yearlyInterest", round2(getBalance() * INTEREST_RATE));
        return m;
    }
}
