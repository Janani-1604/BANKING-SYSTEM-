package com.securebank.service;

import com.securebank.db.AccountDao;
import com.securebank.db.Database;
import com.securebank.db.Insights;
import com.securebank.exception.*;
import com.securebank.model.*;
import com.securebank.util.PinHasher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import static com.securebank.model.Account.money;
import static com.securebank.model.Account.round2;

/** Business layer: stores accounts (in SQL), finds them by number and performs transfers atomically. */
public class Bank {

    public record Outcome(String message, Account account) { }
    public record Statement(Account account, List<Transaction> recent, List<Transaction> ledger) { }

    /** Demo accounts created on first start (shown as quick-login chips in the UI). */
    public static final List<Map<String, Object>> DEMO = List.of(
            Map.of("no", 1001, "name", "Aarav Kumar", "type", "SAVINGS", "pin", "1111"),
            Map.of("no", 1002, "name", "Priya Lakshmi", "type", "SAVINGS", "pin", "2222"),
            Map.of("no", 2001, "name", "Karthik Raj", "type", "CURRENT", "pin", "3333"),
            Map.of("no", 2002, "name", "Meena Sundaram", "type", "CURRENT", "pin", "4444"));

    private final AccountDao dao = new AccountDao();
    private final String adminKey = System.getenv().getOrDefault("ADMIN_KEY", "admin123");

    @FunctionalInterface
    private interface Work<T> {
        T run(Connection c) throws SQLException, BankException;
    }

    /** Runs the work in one SQL transaction: commit on success, rollback on any failure. */
    private <T> T inTransaction(Work<T> work) throws BankException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("[DB] " + e);
            throw new BankException("Database error. Please try again.", "DB_ERROR", 500);
        }
    }

    private Account require(Connection c, long no) throws SQLException, BankException {
        Account acc = dao.find(c, no, true);
        if (acc == null) throw new AccountNotFoundException(no);
        return acc;
    }

    private void record(Connection c, Account acc, TxType type, double amount, String note) throws SQLException {
        Transaction t = new Transaction(0, type, amount, acc.getBalance(), note, System.currentTimeMillis());
        dao.insertTransaction(c, acc.getAccountNumber(), t);
        acc.remember(t);
    }

    // ------------------------------------------------------------------ FR-6 PIN check
    /** Verifies the PIN in its own transaction so wrong attempts stay counted even if the caller fails. */
    public Account authenticate(long no, String pin) throws BankException {
        return inTransaction(c -> {
            Account acc = require(c, no);
            try {
                acc.verifyPin(pin);
            } catch (AccountLockedException | InvalidPinException e) {
                dao.updateSecurity(c, acc);
                c.commit();                       // keep the attempt counter / lock
                throw e;
            }
            dao.updateSecurity(c, acc);           // resets the counter after a good PIN
            return acc;
        });
    }

    // ------------------------------------------------------------------ FR-1
    public synchronized Outcome openAccount(String type, String holder, double opening, String pin) throws BankException {
        final String t = type == null ? "" : type.trim().toUpperCase();
        final String name = holder == null ? "" : holder.trim().replaceAll("\\s+", " ");
        if (!t.equals("SAVINGS") && !t.equals("CURRENT"))
            throw new BankException("Account type must be SAVINGS or CURRENT.");
        if (!name.matches("[\\p{L} .'-]{2,60}"))
            throw new BankException("Holder name must be 2-60 letters.");
        if (!PinHasher.isValidFormat(pin))
            throw new BankException("PIN must be exactly 4 digits.");
        if (Double.isNaN(opening) || Double.isInfinite(opening) || opening < 0 || opening > Account.MAX_AMOUNT)
            throw new InvalidAmountException("Opening balance must be between 0 and " + money(Account.MAX_AMOUNT) + ".");
        if (t.equals("SAVINGS") && opening < SavingsAccount.MIN_BALANCE)
            throw new InsufficientFundsException("Savings account needs an opening balance of at least "
                    + money(SavingsAccount.MIN_BALANCE) + ".", round2(SavingsAccount.MIN_BALANCE - opening));

        return inTransaction(c -> {
            long no = dao.nextAccountNumber(c, t);
            String salt = PinHasher.newSalt();
            String hash = PinHasher.hash(pin, salt);
            Account acc = t.equals("SAVINGS")
                    ? new SavingsAccount(no, name, round2(opening), hash, salt)
                    : new CurrentAccount(no, name, round2(opening), hash, salt);
            dao.insert(c, acc);
            record(c, acc, TxType.OPEN, acc.getBalance(), "Account opened");
            return new Outcome("Account #" + no + " created for " + name + ". Welcome to SecureBank!", acc);
        });
    }

    public Outcome login(long no, String pin) throws BankException {
        Account acc = authenticate(no, pin);
        return new Outcome("Welcome back, " + acc.getHolderName() + "!", acc);
    }

    // ------------------------------------------------------------------ FR-2
    public Outcome deposit(long no, String pin, double amount) throws BankException {
        authenticate(no, pin);
        return inTransaction(c -> {
            Account acc = require(c, no);
            String msg = acc.deposit(amount);
            dao.updateBalance(c, acc);
            record(c, acc, TxType.DEPOSIT, amount, "Cash deposit");
            return new Outcome(msg, acc);
        });
    }

    // ------------------------------------------------------------------ FR-3 / FR-4
    public Outcome withdraw(long no, String pin, double amount) throws BankException {
        authenticate(no, pin);
        return inTransaction(c -> {
            Account acc = require(c, no);
            String msg = acc.withdraw(amount);            // polymorphic call: Savings or Current rules
            dao.updateBalance(c, acc);
            record(c, acc, TxType.WITHDRAW, amount, acc.getBalance() < 0 ? "Withdrawal using overdraft" : "Cash withdrawal");
            return new Outcome(msg, acc);
        });
    }

    // ------------------------------------------------------------------ FR-5
    public Outcome transfer(long from, String pin, long to, double amount) throws BankException {
        if (from == to) throw new BankException("You cannot transfer to the same account.");
        authenticate(from, pin);
        return inTransaction(c -> {
            // lock rows in a fixed order (smaller number first) so two transfers can never deadlock
            Account x = require(c, Math.min(from, to));
            Account y = require(c, Math.max(from, to));
            Account src = x.getAccountNumber() == from ? x : y;
            Account dst = src == x ? y : x;

            src.withdraw(amount);                          // may throw InsufficientFundsException -> rollback
            dst.deposit(amount);
            dao.updateBalance(c, src);
            dao.updateBalance(c, dst);
            record(c, src, TxType.TRANSFER_OUT, amount, "Transfer to #" + dst.getAccountNumber() + " (" + dst.getHolderName() + ")");
            record(c, dst, TxType.TRANSFER_IN, amount, "Transfer from #" + src.getAccountNumber() + " (" + src.getHolderName() + ")");
            return new Outcome("Transferred " + money(amount) + " to " + dst.getHolderName() + " (#" + dst.getAccountNumber()
                    + "). Your new balance: " + money(src.getBalance()), src);
        });
    }

    // ------------------------------------------------------------------ FR-7
    public Outcome applyInterest(long no, String pin) throws BankException {
        authenticate(no, pin);
        return inTransaction(c -> {
            Account acc = require(c, no);
            if (!(acc instanceof SavingsAccount savings))   // method exists only on SavingsAccount
                throw new BankException("Interest is available only for Savings accounts.");
            double interest = savings.applyInterest();
            dao.updateBalance(c, acc);
            record(c, acc, TxType.INTEREST, interest, "4% yearly interest");
            return new Outcome("Interest of " + money(interest) + " credited. New balance: " + money(acc.getBalance()), acc);
        });
    }

    // ------------------------------------------------------------------ FR-8
    public Statement statement(long no, String pin) throws BankException {
        authenticate(no, pin);
        return inTransaction(c -> {
            Account acc = require(c, no);
            List<Transaction> recent = new ArrayList<>(Arrays.asList(acc.getRecentTransactions()));
            Collections.reverse(recent);                   // newest first for display
            return new Statement(acc, recent, dao.history(c, no, 50));
        });
    }

    // ------------------------------------------------------------------ admin + reports
    public Outcome unlock(long no, String key) throws BankException {
        boolean ok = key != null && MessageDigest.isEqual(
                key.getBytes(StandardCharsets.UTF_8), adminKey.getBytes(StandardCharsets.UTF_8));
        if (!ok) throw new BankException("Invalid admin key.", "FORBIDDEN", 403);
        return inTransaction(c -> {
            Account acc = require(c, no);
            acc.unlock();
            dao.updateSecurity(c, acc);
            return new Outcome("Account #" + no + " has been unlocked.", acc);
        });
    }

    /** Public directory: number, name, type and lock status only (no balances). */
    public List<Map<String, Object>> directory() throws BankException {
        return inTransaction(c -> {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Account a : dao.listAll(c)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("accountNumber", a.getAccountNumber());
                m.put("holderName", a.getHolderName());
                m.put("type", a.getType());
                m.put("locked", a.isLocked());
                list.add(m);
            }
            return list;
        });
    }

    public Map<String, Object> dashboard() throws BankException {
        return inTransaction(Insights::snapshot);
    }

    /** First start only: creates the demo accounts and a few transactions so the dashboard is alive. */
    public void seedDemoDataIfEmpty() {
        try {
            if (!directory().isEmpty()) return;
            openAccount("SAVINGS", "Aarav Kumar", 25000, "1111");
            openAccount("SAVINGS", "Priya Lakshmi", 8200, "2222");     // sample flow from the brief
            openAccount("CURRENT", "Karthik Raj", 8000, "3333");
            openAccount("CURRENT", "Meena Sundaram", 15000, "4444");
            deposit(1001, "1111", 5000);
            withdraw(2002, "4444", 3000);
            transfer(1001, "1111", 2002, 1500);
            applyInterest(1001, "1111");
            System.out.println("[Seed] Demo accounts created.");
        } catch (BankException e) {
            System.out.println("[Seed] skipped: " + e.getMessage());
        }
    }
}
