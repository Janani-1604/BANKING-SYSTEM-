package com.securebank.web;

import com.securebank.db.Database;
import com.securebank.exception.*;
import com.securebank.service.Bank;
import com.securebank.service.PolymorphismLab;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Server-side rendered web app on the JDK's own HttpServer.
 * Pages are built in Java (Views.java); forms use POST + redirect; there is no JavaScript at all.
 */
public class WebServer {
    private record Resp(int status, String html, String redirect) {
        static Resp ok(String html)       { return new Resp(200, html, null); }
        static Resp redirect(String url)  { return new Resp(303, "", url); }
    }

    private static final long SESSION_TTL_MS = 30 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Bank bank;
    private final boolean demo;
    private final HttpServer server;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public WebServer(Bank bank, int port, boolean demo) throws IOException {
        this.bank = bank;
        this.demo = demo;
        server = HttpServer.create(new InetSocketAddress(port), 0);   // binds to 0.0.0.0 (needed on Render)
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
    }

    public void start() {
        server.start();
        System.out.println("[Web] SecureBank is live on port " + server.getAddress().getPort());
    }

    // ------------------------------------------------------------------ request entry
    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        try {
            if (path.equals("/style.css"))                        { sendStatic(ex, "/web/style.css", "text/css; charset=utf-8"); return; }
            if (path.equals("/health") || path.equals("/api/health")) {
                send(ex, 200, "application/json", "{\"status\":\"UP\",\"database\":\"" + Database.label() + "\"}", null, null);
                return;
            }
            if (path.equals("/favicon.ico"))                      { send(ex, 204, "text/plain", "", null, null); return; }

            boolean fresh = false;
            Session s = findSession(ex);
            if (s == null) { s = newSession(); fresh = true; }
            s.touch();

            Map<String, String> query = parseForm(ex.getRequestURI().getRawQuery());
            Map<String, String> form = method.equals("POST")
                    ? parseForm(new String(ex.getRequestBody().readNBytes(16_384), StandardCharsets.UTF_8)) : Map.of();

            Resp r;
            try {
                r = route(method, path, query, form, s);
            } catch (BankException e) {
                System.err.println("[Web] " + e.getMessage());
                r = new Resp(e.getStatus() >= 500 ? 500 : e.getStatus(), Html.page("home", "Oops", s.drainFlashes(), Html.errorBody(e.getMessage()), false), null);
            }
            System.out.println("[Web] " + method + " " + path + " -> " + r.status());
            send(ex, r.status(), "text/html; charset=utf-8", r.html(), r.redirect(), fresh || s.loggedIn() ? cookie(ex, s) : null);
        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, "text/html; charset=utf-8",
                    Html.page("home", "Oops", List.of(), Html.errorBody("Something went wrong on the server."), false), null, null);
        }
    }

    // ------------------------------------------------------------------ routing
    private Resp route(String method, String path, Map<String, String> q, Map<String, String> f, Session s) throws BankException {
        switch (method + " " + path) {
            case "GET /":
                return Resp.ok(Html.page("home", "📊 Dashboard", s.drainFlashes(), Views.home(bank.dashboard()), true));
            case "GET /account":
                return accountPage(s, q);
            case "POST /login":
                return login(s, f);
            case "POST /logout":
                s.logout();
                s.flash("success", "Signed out safely 👋", "");
                return Resp.redirect("/account");
            case "GET /open":
                return Resp.ok(openPage(s, "SAVINGS", "", ""));
            case "POST /open":
                return open(s, f);
            case "POST /tx":
                return transaction(s, f);
            case "GET /lab":
                return lab(s, q);
            case "GET /admin":
                return Resp.ok(Html.page("admin", "🔐 Admin desk", s.drainFlashes(), Views.admin(bank.directory()), false));
            case "POST /admin/unlock":
                return unlock(s, f);
            default:
                return new Resp(404, Html.page("home", "Not found", s.drainFlashes(), Html.errorBody("That page does not exist."), false), null);
        }
    }

    private Resp accountPage(Session s, Map<String, String> q) throws BankException {
        if (!s.loggedIn()) {
            return Resp.ok(Html.page("account", "👤 My Account", s.drainFlashes(), Views.signIn(q.getOrDefault("no", ""), q.getOrDefault("pin", ""), demo), false));
        }
        try {
            Bank.Statement st = bank.statement(s.accountNo(), s.pin());
            String body = Views.account(st, q.getOrDefault("tab", "deposit"), q.getOrDefault("st", "recent"), bank.directory());
            return Resp.ok(Html.page("account", "👤 My Account", s.drainFlashes(), body, false));
        } catch (BankException e) {                       // e.g. account got locked meanwhile
            s.logout();
            s.flash("warn", e.getMessage(), "");
            return Resp.redirect("/account");
        }
    }

    private Resp login(Session s, Map<String, String> f) {
        long no = toLong(f.get("accountNo"));
        String pin = f.getOrDefault("pin", "");
        try {
            Bank.Outcome o = bank.login(no, pin);
            s.login(no, pin);
            s.flash("success", o.message(), "");
            return Resp.redirect("/account");
        } catch (BankException e) {
            s.flash(kind(e), e.getMessage(), "");
            return Resp.ok(Html.page("account", "👤 My Account", s.drainFlashes(), Views.signIn(f.getOrDefault("accountNo", ""), "", demo), false));
        }
    }

    private Resp open(Session s, Map<String, String> f) {
        String type = f.getOrDefault("type", "SAVINGS"), holder = f.getOrDefault("holder", ""), amount = f.getOrDefault("amount", ""), pin = f.getOrDefault("pin", "");
        try {
            Bank.Outcome o = bank.openAccount(type, holder, toDouble(amount), pin);
            s.login(o.account().getAccountNumber(), pin);
            s.flash("success", o.message(), "Your account number is #" + o.account().getAccountNumber() + ". Remember your PIN!");
            return Resp.redirect("/account");
        } catch (BankException e) {
            s.flash(kind(e), e.getMessage(), e instanceof InsufficientFundsException i ? "Shortfall: " + Html.inr(i.getShortfall()) : "");
            return Resp.ok(openPage(s, type, holder, amount));
        }
    }

    private String openPage(Session s, String type, String name, String amount) {
        return Html.page("open", "✨ Open Account", s.drainFlashes(), Views.open(type, name, amount), false);
    }

    private Resp transaction(Session s, Map<String, String> f) {
        if (!s.loggedIn()) {
            s.flash("warn", "Please sign in first.", "");
            return Resp.redirect("/account");
        }
        String kind = f.getOrDefault("kind", "deposit");
        long no = s.accountNo();
        try {
            double amount = toDouble(f.get("amount"));
            Bank.Outcome o = switch (kind) {
                case "deposit"  -> bank.deposit(no, s.pin(), amount);
                case "withdraw" -> bank.withdraw(no, s.pin(), amount);
                case "transfer" -> bank.transfer(no, s.pin(), toLong(f.get("to")), amount);
                case "interest" -> bank.applyInterest(no, s.pin());
                default -> throw new BankException("Unknown action.");
            };
            s.flash("success", o.message(), "");
        } catch (BankException e) {
            s.flash(kind(e), e.getMessage(), e instanceof InsufficientFundsException i ? "Shortfall: " + Html.inr(i.getShortfall()) : "");
            if (e instanceof AccountLockedException) s.logout();
        }
        return Resp.redirect("/account?tab=" + kind);
    }

    private Resp lab(Session s, Map<String, String> q) throws BankException {
        double balance = q.containsKey("balance") ? toDouble(q.get("balance")) : 8200;     // sample numbers from the brief
        double amount = q.containsKey("amount") ? toDouble(q.get("amount")) : 9500;
        List<Map<String, Object>> results = List.of();
        try {
            results = PolymorphismLab.run(balance, amount);
        } catch (BankException e) {
            s.flash("error", e.getMessage(), "");
        }
        return Resp.ok(Html.page("lab", "🧪 OOP Lab", s.drainFlashes(), Views.lab(Double.isNaN(balance) ? 0 : balance, Double.isNaN(amount) ? 0 : amount, results), false));
    }

    private Resp unlock(Session s, Map<String, String> f) throws BankException {
        try {
            s.flash("success", bank.unlock(toLong(f.get("accountNo")), f.get("adminKey")).message(), "");
        } catch (BankException e) {
            s.flash("error", e.getMessage(), "");
        }
        return Resp.redirect("/admin");
    }

    private static String kind(BankException e) {
        return e instanceof InvalidPinException || e instanceof AccountLockedException ? "warn" : "error";
    }

    // ------------------------------------------------------------------ sessions + cookies
    private Session findSession(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Cookie");
        if (header == null) return null;
        for (String part : header.split(";")) {
            String p = part.trim();
            if (p.startsWith("sid=")) {
                Session s = sessions.get(p.substring(4));
                if (s != null && System.currentTimeMillis() - s.lastUsed() < SESSION_TTL_MS) return s;
            }
        }
        return null;
    }

    private Session newSession() {
        long now = System.currentTimeMillis();
        sessions.values().removeIf(x -> now - x.lastUsed() > SESSION_TTL_MS);       // purge expired
        byte[] raw = new byte[24];
        RANDOM.nextBytes(raw);
        Session s = new Session(Base64.getUrlEncoder().withoutPadding().encodeToString(raw));
        sessions.put(s.id(), s);
        return s;
    }

    private static String cookie(HttpExchange ex, Session s) {
        boolean https = "https".equalsIgnoreCase(ex.getRequestHeaders().getFirst("X-Forwarded-Proto"));
        return "sid=" + s.id() + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=1800" + (https ? "; Secure" : "");
    }

    // ------------------------------------------------------------------ http helpers
    private static void send(HttpExchange ex, int status, String type, String body, String location, String cookie) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        var h = ex.getResponseHeaders();
        h.set("Content-Type", type);
        h.set("Cache-Control", "no-store");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        // The app has no JavaScript, so scripts are blocked completely.
        h.set("Content-Security-Policy", "default-src 'none'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                + "font-src https://fonts.gstatic.com; img-src data:; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
        if (location != null) h.set("Location", location);
        if (cookie != null) h.add("Set-Cookie", cookie);
        if (status == 204 || status == 303) {
            ex.sendResponseHeaders(status, -1);
            ex.close();
            return;
        }
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendStatic(HttpExchange ex, String resource, String type) throws IOException {
        try (InputStream in = WebServer.class.getResourceAsStream(resource)) {
            if (in == null) { send(ex, 404, "text/plain", "Not found", null, null); return; }
            byte[] bytes = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", type);
            ex.getResponseHeaders().set("Cache-Control", "public, max-age=300");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }
    }

    private static Map<String, String> parseForm(String raw) {
        Map<String, String> m = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return m;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            String k = i < 0 ? pair : pair.substring(0, i);
            String v = i < 0 ? "" : pair.substring(i + 1);
            m.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8).trim());
        }
        return m;
    }

    private static double toDouble(String s) {
        try { return s == null ? Double.NaN : Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return Double.NaN; }
    }

    private static long toLong(String s) {
        try { return s == null ? -1 : Long.parseLong(s.trim()); } catch (NumberFormatException e) { return -1; }
    }
}
