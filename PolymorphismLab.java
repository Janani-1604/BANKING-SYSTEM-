package com.securebank.service;

import com.securebank.exception.BankException;
import com.securebank.exception.InsufficientFundsException;
import com.securebank.model.Account;
import com.securebank.model.CurrentAccount;
import com.securebank.model.SavingsAccount;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Polymorphism demo: the SAME call account.withdraw(amount) on an Account[] that holds a
 * SavingsAccount and a CurrentAccount. Pure in-memory objects, nothing is saved.
 */
public final class PolymorphismLab {
    private PolymorphismLab() { }

    public static List<Map<String, Object>> run(double balance, double amount) throws BankException {
        if (Double.isNaN(balance) || balance < 0 || balance > Account.MAX_AMOUNT)
            throw new BankException("Balance must be between 0 and " + Account.money(Account.MAX_AMOUNT) + ".");
        Account[] accounts = {
                new SavingsAccount(0, "Demo", balance, "", ""),
                new CurrentAccount(0, "Demo", balance, "", "")
        };
        List<Map<String, Object>> results = new ArrayList<>();
        for (Account a : accounts) {                       // Account reference -> child object
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("className", a.getClass().getSimpleName());
            r.put("type", a.getType());
            r.put("before", balance);
            try {
                String msg = a.withdraw(amount);
                r.put("ok", true);
                r.put("message", msg);
            } catch (InsufficientFundsException e) {
                r.put("ok", false);
                r.put("exception", "InsufficientFundsException");
                r.put("message", e.getMessage());
                r.put("shortfall", e.getShortfall());
            } catch (BankException e) {
                r.put("ok", false);
                r.put("exception", e.getClass().getSimpleName());
                r.put("message", e.getMessage());
            } finally {
                r.put("after", a.getBalance());
                r.put("details", a.displayDetails());
            }
            results.add(r);
        }
        return results;
    }
}
