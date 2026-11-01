package com.securebank.db;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Analytics queries (GROUP BY, JOIN, aggregates). The SQL text is sent to the UI so it can be shown live. */
public final class Insights {
    private Insights() { }

    private static final String TOTALS =
            "SELECT (SELECT COUNT(*) FROM accounts) AS total_accounts, "
          + "(SELECT COALESCE(SUM(balance), 0) FROM accounts) AS total_holdings, "
          + "(SELECT COUNT(*) FROM transactions) AS total_txns";

    private static final String HOLDINGS =
            "SELECT account_type, COUNT(*) AS accounts, COALESCE(SUM(balance), 0) AS total\n"
          + "FROM accounts\nGROUP BY account_type\nORDER BY account_type";

    private static final String FLOW =
            "SELECT tx_type, COUNT(*) AS txns, COALESCE(SUM(amount), 0) AS total\n"
          + "FROM transactions\nGROUP BY tx_type\nORDER BY total DESC";

    private static final String TOP =
            "SELECT account_no, holder_name, account_type, balance\n"
          + "FROM accounts\nORDER BY balance DESC\nLIMIT 3";

    private static final String FEED =
            "SELECT t.id, t.account_no, a.holder_name, t.tx_type, t.amount, t.created_at\n"
          + "FROM transactions t\nJOIN accounts a ON a.account_no = t.account_no\nORDER BY t.id DESC\nLIMIT 8";

    public static Map<String, Object> snapshot(Connection c) throws SQLException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totals", run(c, TOTALS).get(0));
        out.put("holdings", panel("Holdings by account type", HOLDINGS, run(c, HOLDINGS)));
        out.put("flow", panel("Money flow by transaction type", FLOW, run(c, FLOW)));
        out.put("top", panel("Top 3 balances", TOP, run(c, TOP)));
        out.put("feed", panel("Live activity feed (JOIN)", FEED, run(c, FEED)));
        return out;
    }

    private static Map<String, Object> panel(String title, String sql, List<Map<String, Object>> rows) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("title", title);
        p.put("sql", sql);
        p.put("rows", rows);
        return p;
    }

    /** Runs a fixed query and converts the ResultSet into a list of maps. */
    private static List<Map<String, Object>> run(Connection c, String sql) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    Object v = rs.getObject(i);
                    if (v instanceof BigDecimal b) v = b.doubleValue();
                    else if (v instanceof Timestamp t) v = t.getTime();
                    row.put(md.getColumnLabel(i).toLowerCase(), v);
                }
                rows.add(row);
            }
        }
        return rows;
    }
}
