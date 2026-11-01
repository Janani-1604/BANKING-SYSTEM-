package com.securebank.model;

import com.securebank.exception.*;
import com.securebank.util.PinHasher;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parent class of every account. It is abstract on purpose: a bank never holds a
 * "generic" account, only a SavingsAccount or a CurrentAccount (polymorphism).
 * The balance is private and can only be changed through methods.
 */
public abstract class Account {
    public static final int MAX_PIN_ATTEMPTS = 3;
    public static final int RECENT_LIMIT = 5;
    public static final double MAX_AMOUNT = 10_000_000.0;

    private final long accountNumber;
    private final String holderName;
    private double balance;                       // private: changed only via methods
    private final String pinHash;
    private final String pinSalt;
    private int failedAttempts;
    private boolean locked;
    private final long createdAt;

    // FR-8: fixed-size array that keeps only the last 5 transactions (oldest first)
    private final Transaction[] recent = new Transaction[RECENT_LIMIT];
    private int recentCount;

    /** Full constructor (used when an account is loaded from the database). */
    protected Account(long accountNumber, String holderName, double balance, String pinHash, String pinSalt,
                      int failedAttempts, boolean locked, long createdAt) {
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.balance = balance;
        this.pinHash = pinHash;
        this.pinSalt = pinSalt;
        this.failedAttempts = failedAttempts;
        this.locked = locked;
        this.createdAt = createdAt;
    }

    /** Constructor chaining: convenience constructor for brand-new accounts. */
    protected Account(long accountNumber, String holderName, double openingBalance, String pinHash, String pinSalt) {
        this(accountNumber, holderName, openingBalance, pinHash, pinSalt, 0, false, System.currentTimeMillis());
    }

    // ---------- rules that every child must define (method overriding) ----------
    public abstract String getType();
    public abstract double getAvailableToWithdraw();
    public abstract String withdraw(double amount) throws InsufficientFundsException, InvalidAmountException;

    // ---------- shared behaviour ----------
    public final String deposit(double amount) throws InvalidAmountException {
        validateAmount(amount);
        credit(amount);
        return "Deposited " + money(amount) + ". New balance: " + money(balance);
    }

    /** PIN check with lock-out after 3 wrong attempts (FR-6). */
    public void verifyPin(String pin) throws AccountLockedException, InvalidPinException {
        if (locked) throw new AccountLockedException(accountNumber, false);
        if (pin != null && PinHasher.matches(pin, pinSalt, pinHash)) {
            failedAttempts = 0;
            return;
        }
        failedAttempts++;
        if (failedAttempts >= MAX_PIN_ATTEMPTS) {
            locked = true;
            throw new AccountLockedException(accountNumber, true);
        }
        throw new InvalidPinException(MAX_PIN_ATTEMPTS - failedAttempts);
    }

    public void unlock() {
        locked = false;
        failedAttempts = 0;
    }

    // Children change the balance only through these two protected methods
    protected final void debit(double amount)  { balance = round2(balance - amount); }
    protected final void credit(double amount) { balance = round2(balance + amount); }

    protected static void validateAmount(double amount) throws InvalidAmountException {
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0)
            throw new InvalidAmountException("Amount must be a positive number.");
        if (amount > MAX_AMOUNT)
            throw new InvalidAmountException("Single transaction limit is " + money(MAX_AMOUNT) + ".");
    }

    /** Remember a transaction in the fixed-size array (drops the oldest when full). */
    public void remember(Transaction t) {
        if (recentCount < RECENT_LIMIT) {
            recent[recentCount++] = t;
        } else {
            System.arraycopy(recent, 1, recent, 0, RECENT_LIMIT - 1);
            recent[RECENT_LIMIT - 1] = t;
        }
    }

    public Transaction[] getRecentTransactions() { return Arrays.copyOf(recent, recentCount); }

    public String displayDetails() {
        return "Account No   : " + accountNumber + "\n"
             + "Holder       : " + holderName + "\n"
             + "Type         : " + getType() + "\n"
             + "Balance      : " + money(balance) + "\n"
             + "Withdrawable : " + money(getAvailableToWithdraw()) + "\n"
             + "Status       : " + (locked ? "LOCKED" : "ACTIVE");
    }

    /** JSON-friendly view. Children extend it with their own fields. */
    public Map<String, Object> toView() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("accountNumber", accountNumber);
        m.put("holderName", holderName);
        m.put("type", getType());
        m.put("balance", balance);
        m.put("available", getAvailableToWithdraw());
        m.put("locked", locked);
        m.put("failedAttempts", failedAttempts);
        m.put("attemptsLeft", Math.max(0, MAX_PIN_ATTEMPTS - failedAttempts));
        m.put("createdAt", createdAt);
        return m;
    }

    // ---------- helpers ----------
    public static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    public static String money(double v) {
        return "Rs. " + (v == Math.rint(v) ? String.format(Locale.US, "%.0f", v) : String.format(Locale.US, "%.2f", v));
    }

    // ---------- getters ----------
    public long getAccountNumber()  { return accountNumber; }
    public String getHolderName()   { return holderName; }
    public double getBalance()      { return balance; }
    public String getPinHash()      { return pinHash; }
    public String getPinSalt()      { return pinSalt; }
    public int getFailedAttempts()  { return failedAttempts; }
    public boolean isLocked()       { return locked; }
    public long getCreatedAt()      { return createdAt; }
}
