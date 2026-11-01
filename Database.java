package com.securebank.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.net.URI;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Connection pool + schema.
 *  - DATABASE_URL set (Render PostgreSQL)  -> PostgreSQL
 *  - otherwise                             -> embedded H2 file database (zero setup for local runs)
 * The same portable SQL runs on both.
 */
public final class Database {
    private static HikariDataSource ds;
    private static boolean postgres;

    private Database() { }

    public static synchronized void init() {
        if (ds != null) return;

        String url;
        String user = null;
        String pass = null;
        String env = System.getenv("DATABASE_URL");

        if (env != null && !env.isBlank()) {
            env = env.trim();
            if (env.startsWith("jdbc:")) {                       // already a JDBC url
                url = env;
                user = System.getenv("DB_USER");
                pass = System.getenv("DB_PASSWORD");
            } else {                                              // postgres://user:pass@host:port/db  (Render style)
                try {
                    URI u = new URI(env);
                    String[] ui = u.getUserInfo().split(":", 2);
                    user = ui[0];
                    pass = ui.length > 1 ? ui[1] : "";
                    int port = u.getPort() == -1 ? 5432 : u.getPort();
                    url = "jdbc:postgresql://" + u.getHost() + ":" + port + u.getPath();
                } catch (Exception e) {
                    throw new IllegalArgumentException("DATABASE_URL is not valid: " + e.getMessage(), e);
                }
            }
            postgres = true;
        } else {
            String custom = System.getenv("DATA_DIR");
            String base = (custom == null || custom.isBlank())
                    ? "./data"                                   // explicit relative path (works with spaces in folder names)
                    : Path.of(custom).toAbsolutePath().toString().replace('\\', '/');
            url = "jdbc:h2:" + base + "/securebank;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
            user = "sa";
            pass = "";
            postgres = false;
        }

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        if (user != null) cfg.setUsername(user);
        if (pass != null) cfg.setPassword(pass);
        cfg.setPoolName("securebank-pool");
        cfg.setMaximumPoolSize(5);
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(10_000);
        cfg.setInitializationFailTimeout(-1);                     // we retry ourselves below
        ds = new HikariDataSource(cfg);
        final String fUrl = url, fUser = user, fPass = pass;

        // The database can need a few seconds to wake up on free hosting -> retry
        SQLException last = null;
        for (int attempt = 1; attempt <= 10; attempt++) {
            try {
                createSchema();
                System.out.println("[DB] Connected to " + label() + " and schema is ready.");
                return;
            } catch (SQLException e) {
                last = e;
                System.out.println("[DB] Attempt " + attempt + " failed: " + e.getMessage());
                if (attempt == 1) explainFailure(fUrl, fUser, fPass);
                try { Thread.sleep(3000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        throw new IllegalStateException("Could not connect to the database", last);
    }

    public static Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    public static String label() {
        return postgres ? "PostgreSQL" : "H2 (local file)";
    }

    /** Hikari only says "request timed out"; this prints the real reason from the JDBC driver. */
    private static void explainFailure(String url, String user, String pass) {
        System.out.println("[DB] Trying: " + url);
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            System.out.println("[DB] Direct connection works, so the pool will recover.");
        } catch (SQLException ex) {
            System.out.println("[DB] REAL CAUSE: " + ex.getMessage());
            if (String.valueOf(ex.getMessage()).contains("No suitable driver"))
                System.out.println("[DB] HINT: the H2/PostgreSQL driver jar is not on the classpath. Run with Maven:  mvn clean package  then  java -jar target/securebank.jar");
        }
    }

    private static void createSchema() throws SQLException {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS accounts ("
                    + " account_no BIGINT PRIMARY KEY,"
                    + " holder_name VARCHAR(80) NOT NULL,"
                    + " account_type VARCHAR(10) NOT NULL CHECK (account_type IN ('SAVINGS','CURRENT')),"
                    + " balance NUMERIC(14,2) NOT NULL,"
                    + " pin_hash VARCHAR(64) NOT NULL,"
                    + " pin_salt VARCHAR(32) NOT NULL,"
                    + " failed_attempts INT NOT NULL DEFAULT 0,"
                    + " locked BOOLEAN NOT NULL DEFAULT FALSE,"
                    + " created_at TIMESTAMP NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS transactions ("
                    + " id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,"
                    + " account_no BIGINT NOT NULL REFERENCES accounts(account_no),"
                    + " tx_type VARCHAR(20) NOT NULL,"
                    + " amount NUMERIC(14,2) NOT NULL,"
                    + " balance_after NUMERIC(14,2) NOT NULL,"
                    + " note VARCHAR(200),"
                    + " created_at TIMESTAMP NOT NULL)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_tx_account ON transactions (account_no, id)");
        }
    }
}
