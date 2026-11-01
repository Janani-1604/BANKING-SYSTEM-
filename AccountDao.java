package com.securebank.db;

import com.securebank.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** All SQL for accounts and transactions. Every method works inside a caller-owned transaction. */
public class AccountDao {

    private static final String COLS =
            "account_no, holder_name, account_type, balance, pin_hash, pin_salt, failed_attempts, locked, created_at";

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    /** Builds the right child class from the account_type column -> polymorphism. */
    private Account map(ResultSet rs) throws SQLException {
        long no = rs.getLong("account_no");
        String holder = rs.getString("holder_name");
        double balance = rs.getBigDecimal("balance").doubleValue();
        String hash = rs.getString("pin_hash");
        String salt = rs.getString("pin_salt");
        int failed = rs.getInt("failed_attempts");
        boolean locked = rs.getBoolean("locked");
        long created = rs.getTimestamp("created_at").getTime();
        String type = rs.getString("account_type");
        return switch (type) {
            case "SAVINGS" -> new SavingsAccount(no, holder, balance, hash, salt, failed, locked, created);
            case "CURRENT" -> new CurrentAccount(no, holder, balance, hash, salt, failed, locked, created);
            default -> throw new SQLException("Unknown account type: " + type);
        };
    }

    /** Loads an account (optionally row-locked) and refills its 5-slot transaction array. */
    public Account find(Connection c, long accountNo, boolean lock) throws SQLException {
        String sql = "SELECT " + COLS + " FROM accounts WHERE account_no = ?" + (lock ? " FOR UPDATE" : "");
        Account acc;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, accountNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                acc = map(rs);
            }
        }
        List<Transaction> last = history(c, accountNo, Account.RECENT_LIMIT);   // newest first
        for (int i = last.size() - 1; i >= 0; i--) acc.remember(last.get(i));      // oldest first into the array
        return acc;
    }

    public List<Account> listAll(Connection c) throws SQLException {
        List<Account> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLS + " FROM accounts ORDER BY account_no");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public long nextAccountNumber(Connection c, String type) throws SQLException {
        long base = type.equals("SAVINGS") ? 1001 : 2001;
        try (PreparedStatement ps = c.prepareStatement("SELECT MAX(account_no) FROM accounts WHERE account_type = ?")) {
            ps.setString(1, type);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long max = rs.getLong(1);
                    if (!rs.wasNull()) return max + 1;
                }
            }
        }
        return base;
    }

    public void insert(Connection c, Account a) throws SQLException {
        String sql = "INSERT INTO accounts (" + COLS + ") VALUES (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, a.getAccountNumber());
            ps.setString(2, a.getHolderName());
            ps.setString(3, a.getType());
            ps.setBigDecimal(4, bd(a.getBalance()));
            ps.setString(5, a.getPinHash());
            ps.setString(6, a.getPinSalt());
            ps.setInt(7, a.getFailedAttempts());
            ps.setBoolean(8, a.isLocked());
            ps.setTimestamp(9, new Timestamp(a.getCreatedAt()));
            ps.executeUpdate();
        }
    }

    public void updateBalance(Connection c, Account a) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE accounts SET balance = ? WHERE account_no = ?")) {
            ps.setBigDecimal(1, bd(a.getBalance()));
            ps.setLong(2, a.getAccountNumber());
            ps.executeUpdate();
        }
    }

    public void updateSecurity(Connection c, Account a) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE accounts SET failed_attempts = ?, locked = ? WHERE account_no = ?")) {
            ps.setInt(1, a.getFailedAttempts());
            ps.setBoolean(2, a.isLocked());
            ps.setLong(3, a.getAccountNumber());
            ps.executeUpdate();
        }
    }

    public void insertTransaction(Connection c, long accountNo, Transaction t) throws SQLException {
        String sql = "INSERT INTO transactions (account_no, tx_type, amount, balance_after, note, created_at) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, accountNo);
            ps.setString(2, t.type().name());
            ps.setBigDecimal(3, bd(t.amount()));
            ps.setBigDecimal(4, bd(t.balanceAfter()));
            ps.setString(5, t.note());
            ps.setTimestamp(6, new Timestamp(t.timeMillis()));
            ps.executeUpdate();
        }
    }

    /** Newest first. */
    public List<Transaction> history(Connection c, long accountNo, int limit) throws SQLException {
        String sql = "SELECT id, tx_type, amount, balance_after, note, created_at FROM transactions "
                   + "WHERE account_no = ? ORDER BY id DESC LIMIT ?";
        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, accountNo);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Transaction(rs.getLong("id"), TxType.valueOf(rs.getString("tx_type")),
                            rs.getBigDecimal("amount").doubleValue(), rs.getBigDecimal("balance_after").doubleValue(),
                            rs.getString("note"), rs.getTimestamp("created_at").getTime()));
                }
            }
        }
        return list;
    }
}
