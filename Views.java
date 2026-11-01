package com.securebank.web;

import com.securebank.model.*;
import com.securebank.service.Bank;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.securebank.web.Html.*;

/** Every page body of SecureBank, written in plain Java (no template engine, no JavaScript). */
public final class Views {
    private Views() { }

    private record Meta(String label, String color, String icon) { }

    private static final Map<String, Meta> TX = Map.of(
            "OPEN", new Meta("Account opened", "#6ec1ff", "✨"),
            "DEPOSIT", new Meta("Deposit", "#4ade9b", "⬇"),
            "WITHDRAW", new Meta("Withdrawal", "#ff7eb6", "⬆"),
            "TRANSFER_IN", new Meta("Transfer in", "#8be9fd", "↘"),
            "TRANSFER_OUT", new Meta("Transfer out", "#ffd23f", "↗"),
            "INTEREST", new Meta("Interest", "#b79cff", "%"));

    private static Meta meta(String type) { return TX.getOrDefault(type, new Meta(type, "#ffffff", "•")); }
    private static boolean credit(String type) { return type.equals("OPEN") || type.equals("DEPOSIT") || type.equals("TRANSFER_IN") || type.equals("INTEREST"); }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> d, String key) {
        return (List<Map<String, Object>>) ((Map<String, Object>) d.get(key)).get("rows");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> panel(Map<String, Object> d, String key) {
        return (Map<String, Object>) d.get(key);
    }

    // ================================================================== HOME
    public static String home(Map<String, Object> d) {
        @SuppressWarnings("unchecked") Map<String, Object> t = (Map<String, Object>) d.get("totals");
        double accounts = num(t.get("total_accounts")), holdings = num(t.get("total_holdings")), txns = num(t.get("total_txns"));
        StringBuilder sb = new StringBuilder();

        sb.append("""
            <div class="hero"><div class="hero-text">
              <span class="sticker">☕ Java 17 · JDBC · SQL</span>
              <h1>Banking modelled with <span class="grad">Java OOP</span> and stored in <span class="grad2">SQL</span>.</h1>
              <p>Savings &amp; Current accounts built with inheritance, polymorphism and custom exceptions. Even this screen is drawn by Java code. Every rupee lives in a real database.</p>
              <div class="row"><a class="btn primary" href="/open">✨ Open an account</a><a class="btn" href="/account">🔑 Sign in</a><a class="btn ghost" href="/lab">🧪 OOP Lab</a></div>
            </div><div class="hero-art" aria-hidden="true">
              <div class="mini m1">💎<small>Savings</small></div><div class="mini m2">🚀<small>Current</small></div><div class="mini m3">🗄️<small>SQL</small></div>
            </div></div>
            <div class="steps">
              <div><b>1</b><span>Open an account<br><small>Savings or Current</small></span></div>
              <div><b>2</b><span>Sign in with PIN<br><small>3 wrong = locked</small></span></div>
              <div><b>3</b><span>Deposit · Withdraw · Transfer<br><small>Java rules apply</small></span></div>
              <div><b>4</b><span>Watch the SQL<br><small>Live queries below</small></span></div>
            </div>
            """);

        sb.append("<div class=\"kpis\">")
          .append(kpi("k1", "Total accounts", String.valueOf((long) accounts)))
          .append(kpi("k2", "Bank holdings", inr(holdings)))
          .append(kpi("k3", "Transactions", String.valueOf((long) txns)))
          .append(kpi("k4", "Average balance", inr(accounts > 0 ? holdings / accounts : 0)))
          .append("</div>");

        sb.append("<div class=\"grid2\">");
        sb.append("<div class=\"glass\"><h3>🍩 Holdings by account type</h3><div class=\"center\">").append(donut(rows(d, "holdings"))).append("</div></div>");
        sb.append("<div class=\"glass\"><h3>💸 Money flow</h3>").append(flow(rows(d, "flow"))).append("</div>");
        sb.append("<div class=\"glass\"><h3>🏆 Top balances</h3>").append(top(rows(d, "top"))).append("</div>");
        sb.append("<div class=\"glass\"><h3>⚡ Live activity <small>SQL JOIN</small></h3><div class=\"feed\">").append(feed(rows(d, "feed"))).append("</div></div>");
        sb.append("</div>");

        sb.append("<div class=\"glass\"><h3>🧠 SQL Insights <small>the real queries behind this page</small></h3>");
        for (String key : new String[]{"holdings", "flow", "top", "feed"}) sb.append(sqlBox(panel(d, key)));
        sb.append("</div>");
        return sb.toString();
    }

    private static String kpi(String cls, String label, String value) {
        return "<div class=\"kpi " + cls + "\"><small>" + label + "</small><b>" + esc(value) + "</b></div>";
    }

    private static String donut(List<Map<String, Object>> rows) {
        double r = 70, c = 2 * Math.PI * r, sum = 0, off = 0;
        for (Map<String, Object> row : rows) sum += Math.max(0, num(row.get("total")));
        StringBuilder segs = new StringBuilder(), legend = new StringBuilder();
        for (Map<String, Object> row : rows) {
            String type = String.valueOf(row.get("account_type"));
            String color = type.equals("SAVINGS") ? "#4ade9b" : "#b79cff";
            double total = num(row.get("total")), len = sum > 0 ? Math.max(0, total) / sum * c : 0;
            segs.append(String.format(Locale.US,
                    "<circle r=\"70\" cx=\"100\" cy=\"100\" fill=\"none\" stroke=\"%s\" stroke-width=\"26\" stroke-dasharray=\"%.2f %.2f\" stroke-dashoffset=\"%.2f\" transform=\"rotate(-90 100 100)\"/>",
                    color, len, c - len, -off));
            off += len;
            legend.append("<span><i style=\"background:").append(color).append("\"></i>").append(esc(type)).append(" · ")
                  .append((long) num(row.get("accounts"))).append(" acc · ").append(inr(total)).append("</span>");
        }
        return "<div><svg viewBox=\"0 0 200 200\" width=\"220\" height=\"220\"><circle r=\"70\" cx=\"100\" cy=\"100\" fill=\"none\" stroke=\"#e8dfc8\" stroke-width=\"26\"/>"
             + segs
             + "<circle r=\"83\" cx=\"100\" cy=\"100\" fill=\"none\" stroke=\"#14110f\" stroke-width=\"3\"/><circle r=\"57\" cx=\"100\" cy=\"100\" fill=\"none\" stroke=\"#14110f\" stroke-width=\"3\"/>"
             + "<text x=\"100\" y=\"96\" text-anchor=\"middle\" fill=\"#6b6358\" font-size=\"10\" font-family=\"Space Mono\" font-weight=\"700\">TOTAL</text>"
             + "<text x=\"100\" y=\"118\" text-anchor=\"middle\" fill=\"#14110f\" font-size=\"16\" font-weight=\"700\" font-family=\"Space Mono\">" + esc(inr(sum)) + "</text></svg>"
             + "<div class=\"legend\">" + legend + "</div></div>";
    }

    private static String flow(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "<p class=\"muted\">No transactions yet.</p>";
        double max = 1;
        for (Map<String, Object> r : rows) max = Math.max(max, num(r.get("total")));
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> r : rows) {
            Meta m = meta(String.valueOf(r.get("tx_type")));
            sb.append("<div class=\"bar\"><div class=\"lab\"><span>").append(m.icon()).append(' ').append(m.label())
              .append(" <small class=\"muted\">×").append((long) num(r.get("txns"))).append("</small></span><b>").append(inr(num(r.get("total"))))
              .append("</b></div><div class=\"track\"><div class=\"fill\" style=\"")
              .append(String.format(Locale.US, "width:%.1f%%", num(r.get("total")) / max * 100)).append(";background:").append(m.color()).append("\"></div></div></div>");
        }
        return sb.toString();
    }

    private static String top(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "<p class=\"muted\">No accounts yet.</p>";
        String[] medals = {"🥇", "🥈", "🥉"};
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map<String, Object> r : rows) {
            double bal = num(r.get("balance"));
            sb.append("<div class=\"rank\"><span class=\"medal\">").append(medals[Math.min(i++, 2)]).append("</span><div class=\"who\">")
              .append(esc(r.get("holder_name"))).append("<small>#").append((long) num(r.get("account_no"))).append(" · ").append(esc(r.get("account_type")))
              .append("</small></div><b class=\"").append(bal < 0 ? "out" : "").append("\">").append(inr(bal)).append("</b></div>");
        }
        return sb.toString();
    }

    private static String feed(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "<p class=\"muted\">Quiet for now…</p>";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> r : rows) {
            String type = String.valueOf(r.get("tx_type"));
            Meta m = meta(type);
            boolean cr = credit(type);
            sb.append(txLine(m, esc(r.get("holder_name")) + " · #" + (long) num(r.get("account_no")), cr, num(r.get("amount")), ago((long) num(r.get("created_at")))));
        }
        return sb.toString();
    }

    /** One transaction line (used by the feed and by statements). */
    private static String txLine(Meta m, String sub, boolean cr, double amount, String small) {
        return "<div class=\"tx\"><div class=\"ic\" style=\"background:" + m.color() + "\">" + m.icon() + "</div><div class=\"mid\"><b>" + m.label()
             + "</b><small>" + sub + "</small></div><div class=\"amt " + (cr ? "in" : "out") + "\">" + (cr ? "+" : "-") + inr(amount)
             + "<small>" + small + "</small></div></div>";
    }

    private static String sqlBox(Map<String, Object> p) {
        @SuppressWarnings("unchecked") List<Map<String, Object>> rows = (List<Map<String, Object>>) p.get("rows");
        String sql = esc(p.get("sql"))
                .replaceAll("\\b(SELECT|FROM|WHERE|GROUP BY|ORDER BY|LIMIT|JOIN|ON|AS|DESC|ASC)\\b", "<span class=\"kw\">$1</span>")
                .replaceAll("\\b(COUNT|SUM|COALESCE|MAX)\\b(?=\\()", "<span class=\"fn\">$1</span>");
        StringBuilder sb = new StringBuilder("<details class=\"sql\"><summary>").append(esc(p.get("title"))).append("</summary><pre>").append(sql).append("</pre>");
        if (!rows.isEmpty()) {
            sb.append("<table class=\"tbl\"><tr>");
            for (String col : rows.get(0).keySet()) sb.append("<th>").append(esc(col)).append("</th>");
            sb.append("</tr>");
            for (Map<String, Object> r : rows) {
                sb.append("<tr>");
                for (Map.Entry<String, Object> e : r.entrySet()) {
                    Object v = e.getValue();
                    String cell;
                    if (e.getKey().equals("created_at")) cell = ago((long) num(v));
                    else if (v instanceof Number n && !e.getKey().matches(".*(_no|^id)$")) cell = n.doubleValue() == Math.rint(n.doubleValue()) ? String.format(Locale.US, "%,d", n.longValue()) : String.format(Locale.US, "%,.2f", n.doubleValue());
                    else cell = esc(v);
                    sb.append("<td>").append(cell).append("</td>");
                }
                sb.append("</tr>");
            }
            sb.append("</table>");
        }
        return sb.append("</details>").toString();
    }

    // ================================================================== SIGN IN
    public static String signIn(String no, String pin, boolean demo) {
        StringBuilder sb = new StringBuilder("""
            <div class="glass narrow"><h2>🔑 Sign in</h2>
            <p class="muted">Every transaction is PIN-protected. 3 wrong PINs lock the account.</p>
            <form method="post" action="/login">
            """);
        sb.append("<label class=\"field\"><span>Account number</span><input name=\"accountNo\" inputmode=\"numeric\" placeholder=\"e.g. 1001\" autocomplete=\"off\" required value=\"").append(esc(no)).append("\"></label>")
          .append("<label class=\"field\"><span>4-digit PIN</span><input name=\"pin\" type=\"password\" inputmode=\"numeric\" maxlength=\"4\" pattern=\"[0-9]{4}\" placeholder=\"••••\" autocomplete=\"off\" required value=\"").append(esc(pin)).append("\"></label>")
          .append("<button class=\"btn primary wide\" type=\"submit\">Unlock account</button></form>");
        if (demo) {
            sb.append("<p class=\"muted small\" style=\"margin-top:16px\">Quick demo accounts (click to fill)</p><div class=\"chips\">");
            for (Map<String, Object> d : Bank.DEMO) {
                boolean sav = "SAVINGS".equals(d.get("type"));
                sb.append("<a class=\"chip ").append(sav ? "sav" : "cur").append("\" href=\"/account?no=").append(d.get("no")).append("&amp;pin=").append(d.get("pin")).append("\">")
                  .append(sav ? "💎" : "🚀").append(" #").append(d.get("no")).append(' ').append(esc(d.get("name"))).append("</a>");
            }
            sb.append("</div>");
        }
        return sb.append("</div>").toString();
    }

    // ================================================================== ACCOUNT
    public static String account(Bank.Statement st, String tab, String stTab, List<Map<String, Object>> directory) {
        Account a = st.account();
        Map<String, Object> v = a.toView();
        boolean sav = a instanceof SavingsAccount;
        StringBuilder sb = new StringBuilder();

        sb.append("<div class=\"toprow\"><h2>Hello, <span class=\"grad\">").append(esc(a.getHolderName())).append("</span> 👋</h2>")
          .append("<form method=\"post\" action=\"/logout\"><button class=\"btn ghost\" type=\"submit\">Sign out</button></form></div><div class=\"cols\"><div>");

        // bank card (the CSS class comes from the polymorphic getType())
        sb.append("<div class=\"bankcard ").append(a.getType()).append("\">")
          .append(a.isLocked() ? "<div class=\"lockov\">🔒 ACCOUNT LOCKED</div>" : "")
          .append("<div class=\"top\"><span>SECUREBANK</span><span class=\"badge\">").append(sav ? "💎 SAVINGS" : "🚀 CURRENT").append("</span></div><div class=\"chipsvg\"></div>")
          .append("<div class=\"bal\"><small>Available balance</small><b").append(a.getBalance() < 0 ? " class=\"neg\"" : "").append(">").append(inr(a.getBalance())).append("</b></div>")
          .append("<div class=\"bot\"><span>#").append(a.getAccountNumber()).append("</span><span>").append(esc(a.getHolderName().toUpperCase())).append("</span></div></div>");

        // rules + meter
        double pct;
        String label, color;
        if (sav) {
            pct = a.getBalance() > 0 ? a.getAvailableToWithdraw() / a.getBalance() * 100 : 0;
            color = "#4ade9b";
            label = inr(a.getAvailableToWithdraw()) + " withdrawable · " + inr(num(v.get("minimumBalance"))) + " protected minimum";
        } else {
            pct = num(v.get("overdraftUsed")) / num(v.get("overdraftLimit")) * 100;
            color = pct > 70 ? "#ff4d4d" : "#b79cff";
            label = "Overdraft used " + inr(num(v.get("overdraftUsed"))) + " of " + inr(num(v.get("overdraftLimit")));
        }
        sb.append("<div class=\"glass\"><h3>📌 Account rules <small>(overridden per class)</small></h3>")
          .append(stat("Class", sav ? "SavingsAccount" : "CurrentAccount"))
          .append(stat("Withdrawable now", inr(a.getAvailableToWithdraw())));
        if (sav) {
            sb.append(stat("Minimum balance (final)", inr(num(v.get("minimumBalance")))))
              .append("<div class=\"stat\"><span>Interest next year (4%)</span><b class=\"in\">+").append(inr(num(v.get("yearlyInterest")))).append("</b></div>");
        } else {
            sb.append(stat("Overdraft limit (final)", inr(num(v.get("overdraftLimit")))))
              .append(stat("Overdraft left", inr(num(v.get("overdraftRemaining")))));
        }
        sb.append(stat("PIN attempts left", v.get("attemptsLeft") + " / 3"))
          .append("<div class=\"meter\"><div class=\"track\"><div class=\"fill\" style=\"").append(String.format(Locale.US, "width:%.1f%%", Math.min(100, Math.max(0, pct))))
          .append(";background:").append(color).append("\"></div></div><small>").append(esc(label)).append("</small></div></div></div><div>");

        // actions
        if (!tab.matches("deposit|withdraw|transfer|interest") || (tab.equals("interest") && !sav)) tab = "deposit";
        sb.append("<div class=\"glass\"><h3>💳 Transactions <small>(PIN checked on every request)</small></h3><div class=\"atabs\">")
          .append(tabLink("deposit", "⬇ Deposit", tab)).append(tabLink("withdraw", "⬆ Withdraw", tab)).append(tabLink("transfer", "🔁 Transfer", tab));
        if (sav) sb.append(tabLink("interest", "📈 Interest", tab));
        sb.append("</div><form method=\"post\" action=\"/tx\"><input type=\"hidden\" name=\"kind\" value=\"").append(tab).append("\">");
        switch (tab) {
            case "interest" -> sb.append("<p class=\"muted\">Credits <b>4%</b> yearly interest. This method exists <b>only</b> in <code>SavingsAccount</code>.</p>")
                    .append("<p style=\"margin:12px 0\">You will receive <b class=\"in\">+").append(inr(num(v.get("yearlyInterest")))).append("</b></p>")
                    .append("<button class=\"btn primary wide\" type=\"submit\">📈 Apply interest</button>");
            case "transfer" -> {
                sb.append("<label class=\"field\"><span>To account</span><select name=\"to\" required><option value=\"\">Choose a receiver…</option>");
                for (Map<String, Object> d : directory) {
                    if (((Number) d.get("accountNumber")).longValue() == a.getAccountNumber()) continue;
                    sb.append("<option value=\"").append(d.get("accountNumber")).append("\">#").append(d.get("accountNumber")).append(" · ").append(esc(d.get("holderName"))).append(" (").append(d.get("type")).append(")</option>");
                }
                sb.append("</select></label>").append(amountField()).append("<button class=\"btn primary wide\" type=\"submit\">Send money</button>");
            }
            case "withdraw" -> sb.append(amountField()).append("<button class=\"btn bad wide\" type=\"submit\">Withdraw money</button>");
            default -> sb.append(amountField()).append("<button class=\"btn good wide\" type=\"submit\">Deposit money</button>");
        }
        sb.append("</form></div>");

        // statement
        boolean recent = !"ledger".equals(stTab);
        sb.append("<div class=\"glass\"><h3>🧾 Statement</h3><div class=\"stmt-tabs\"><a class=\"atab").append(recent ? " on" : "").append("\" href=\"/account?tab=").append(tab).append("&amp;st=recent\">Last 5 · Java array</a>")
          .append("<a class=\"atab").append(recent ? "" : " on").append("\" href=\"/account?tab=").append(tab).append("&amp;st=ledger\">Full ledger · SQL</a></div>")
          .append("<p class=\"muted small\" style=\"margin-bottom:8px\">")
          .append(recent ? "Kept in a fixed-size <code>Transaction[5]</code> inside the Account object." : "<code>SELECT … FROM transactions WHERE account_no = ? ORDER BY id DESC LIMIT 50</code>")
          .append("</p>");
        List<Transaction> list = recent ? st.recent() : st.ledger();
        if (!recent) sb.append(spark(st.ledger()));
        if (list.isEmpty()) sb.append("<p class=\"muted\">No transactions yet.</p>");
        for (Transaction t : list) {
            Meta m = meta(t.type().name());
            sb.append(txLine(m, esc(t.note()) + " · " + ago(t.timeMillis()), t.type().isCredit(), t.amount(), "bal " + inr(t.balanceAfter())));
        }
        return sb.append("</div></div></div>").toString();
    }

    private static String stat(String k, String v) {
        return "<div class=\"stat\"><span>" + esc(k) + "</span><b>" + esc(v) + "</b></div>";
    }

    private static String amountField() {
        return "<label class=\"field\"><span>Amount (₹)</span><input name=\"amount\" inputmode=\"decimal\" placeholder=\"0\" required></label>";
    }

    private static String tabLink(String key, String text, String current) {
        return "<a class=\"atab" + (key.equals(current) ? " on" : "") + "\" href=\"/account?tab=" + key + "\">" + text + "</a>";
    }

    /** Balance history as an inline SVG line (drawn by Java). */
    private static String spark(List<Transaction> ledger) {
        if (ledger.size() < 2) return "";
        int n = ledger.size();
        double w = 400, h = 70, mn = Double.MAX_VALUE, mx = -Double.MAX_VALUE;
        for (Transaction t : ledger) { mn = Math.min(mn, t.balanceAfter()); mx = Math.max(mx, t.balanceAfter()); }
        double rg = mx - mn == 0 ? 1 : mx - mn;
        StringBuilder pts = new StringBuilder();
        for (int i = 0; i < n; i++) {                       // ledger is newest first -> draw oldest to newest
            Transaction t = ledger.get(n - 1 - i);
            pts.append(String.format(Locale.US, "%.1f,%.1f ", i / (double) (n - 1) * w, h - 8 - (t.balanceAfter() - mn) / rg * (h - 16)));
        }
        String line = pts.toString().trim();
        return "<svg class=\"spark\" viewBox=\"0 0 400 70\" preserveAspectRatio=\"none\"><polygon points=\"0,70 " + line + " 400,70\" fill=\"#ffd9ea\"/>"
             + "<polyline points=\"" + line + "\" fill=\"none\" stroke=\"#14110f\" stroke-width=\"3\" stroke-linejoin=\"round\" stroke-linecap=\"round\" vector-effect=\"non-scaling-stroke\"/></svg>";
    }

    // ================================================================== OPEN ACCOUNT
    public static String open(String type, String name, String amount) {
        boolean cur = "CURRENT".equals(type);
        return "<div class=\"glass narrow wide2\"><h2>✨ Open a new account</h2><form method=\"post\" action=\"/open\"><div class=\"types\">"
             + "<label class=\"tc\"><input type=\"radio\" name=\"type\" value=\"SAVINGS\"" + (cur ? "" : " checked") + "><span class=\"typecard sav\"><b>💎 Savings</b><span>Keep min <em>₹1,000</em><br>Earns <em>4%</em> yearly interest</span></span></label>"
             + "<label class=\"tc\"><input type=\"radio\" name=\"type\" value=\"CURRENT\"" + (cur ? " checked" : "") + "><span class=\"typecard cur\"><b>🚀 Current</b><span>Overdraft up to <em>₹5,000</em><br>Perfect for business</span></span></label></div>"
             + "<label class=\"field\"><span>Holder name</span><input name=\"holder\" placeholder=\"Your full name\" required value=\"" + esc(name) + "\"></label>"
             + "<div class=\"two\"><label class=\"field\"><span>Opening balance (₹)</span><input name=\"amount\" inputmode=\"decimal\" placeholder=\"min 1000 for Savings\" required value=\"" + esc(amount) + "\"></label>"
             + "<label class=\"field\"><span>Choose a 4-digit PIN</span><input name=\"pin\" type=\"password\" inputmode=\"numeric\" maxlength=\"4\" pattern=\"[0-9]{4}\" placeholder=\"••••\" required></label></div>"
             + "<button class=\"btn primary wide\" type=\"submit\">Create account</button></form></div>";
    }

    // ================================================================== LAB
    public static String lab(double balance, double amount, List<Map<String, Object>> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"glass\"><h2>🧪 OOP Lab: polymorphism, live</h2>")
          .append("<p class=\"muted\">One Java array <code>Account[]</code> holds a <code>SavingsAccount</code> and a <code>CurrentAccount</code>. The <b>same call</b> <code>account.withdraw(amount)</code> behaves differently. Nothing is saved.</p>")
          .append("<form method=\"get\" action=\"/lab\"><div class=\"two labin\">")
          .append("<label class=\"field\"><span>Starting balance (₹)</span><input name=\"balance\" value=\"").append(plain(balance)).append("\" inputmode=\"decimal\"></label>")
          .append("<label class=\"field\"><span>Withdraw amount (₹)</span><input name=\"amount\" value=\"").append(plain(amount)).append("\" inputmode=\"decimal\"></label></div>")
          .append("<button class=\"btn primary\" type=\"submit\">▶ Run withdraw() on both</button></form><div class=\"two labres\">");
        for (Map<String, Object> x : results) {
            boolean ok = Boolean.TRUE.equals(x.get("ok"));
            sb.append("<div class=\"res ").append(x.get("type")).append("\"><h4>").append(esc(x.get("className"))).append("<span class=\"pill ").append(ok ? "ok" : "no").append("\">")
              .append(ok ? "✔ allowed" : "✖ " + esc(x.get("exception"))).append("</span></h4><p>").append(esc(x.get("message"))).append("</p>")
              .append("<p class=\"muted small\">Balance: ").append(inr(num(x.get("before")))).append(" → <b>").append(inr(num(x.get("after")))).append("</b>");
            if (x.get("shortfall") != null) sb.append(" · shortfall <b class=\"out\">").append(inr(num(x.get("shortfall")))).append("</b>");
            sb.append("</p><pre>").append(esc(x.get("details"))).append("</pre></div>");
        }
        sb.append("</div></div><div class=\"grid2\">");
        sb.append("""
            <div class="glass"><h3>🧬 Class diagram</h3><div class="uml">
              <div class="box abs"><b>«abstract» Account</b><i>- balance : double</i><i>+ deposit() final</i><i>+ withdraw() abstract</i><i>+ displayDetails()</i></div>
              <div class="arrows">▲ &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; ▲</div>
              <div class="two">
                <div class="box sav"><b>SavingsAccount</b><i>MIN_BALANCE = 1000 (final)</i><i>+ applyInterest()</i><i>@Override withdraw()</i></div>
                <div class="box cur"><b>CurrentAccount</b><i>OVERDRAFT_LIMIT = 5000 (final)</i><i>@Override withdraw()</i><i>@Override displayDetails()</i></div>
              </div>
              <div class="box exc"><b>InsufficientFundsException</b><i>extends BankException (checked) · final</i></div>
            </div></div>
            <div class="glass"><h3>📚 Concept → where it is used</h3>
            """);
        String[][] concepts = {
                {"Inheritance", "Account → SavingsAccount, CurrentAccount"}, {"Method overriding", "withdraw(), displayDetails(), toView()"},
                {"super + constructor chaining", "SavingsAccount(...) → super(...) → this(...)"}, {"Polymorphism", "Account[] / AccountDao.map() returns the right child"},
                {"Abstract class", "Account cannot be instantiated directly"}, {"final keyword", "MIN_BALANCE, OVERDRAFT_LIMIT, deposit(), exception class"},
                {"Custom exception", "InsufficientFundsException (+ shortfall)"}, {"try-catch-finally", "ConsoleApp loop, PolymorphismLab, transactions"},
                {"Arrays", "Transaction[5]: last 5 transactions"}, {"PIN retry logic", "verifyPin() + 3 attempts → lock"},
                {"SQL + JDBC", "PreparedStatement, JOIN, GROUP BY, FOR UPDATE"}, {"ACID transaction", "Transfer = commit or full rollback"},
                {"Java-only UI", "Views.java builds every page, no JavaScript"}};
        for (String[] c : concepts) sb.append("<div class=\"crow\"><b>").append(esc(c[0])).append("</b><span>").append(esc(c[1])).append("</span></div>");
        return sb.append("</div></div>").toString();
    }

    private static String plain(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    // ================================================================== ADMIN
    public static String admin(List<Map<String, Object>> directory) {
        StringBuilder sb = new StringBuilder("<div class=\"glass\"><h2>🔐 Admin desk</h2><p class=\"muted\">Locked accounts (3 wrong PINs) can only be released with the admin key.</p>");
        if (directory.isEmpty()) sb.append("<p class=\"muted\">No accounts.</p>");
        for (Map<String, Object> d : directory) {
            boolean locked = Boolean.TRUE.equals(d.get("locked"));
            sb.append("<div class=\"adrow\"><span style=\"font-size:1.4rem\">").append("SAVINGS".equals(d.get("type")) ? "💎" : "🚀").append("</span><div class=\"who\"><b>")
              .append(esc(d.get("holderName"))).append("</b><small>#").append(d.get("accountNumber")).append(" · ").append(d.get("type")).append("</small></div>")
              .append("<span class=\"state ").append(locked ? "lock" : "ok").append("\">").append(locked ? "🔒 LOCKED" : "● ACTIVE").append("</span>");
            if (locked) {
                sb.append("<form method=\"post\" action=\"/admin/unlock\" class=\"inline\"><input type=\"hidden\" name=\"accountNo\" value=\"").append(d.get("accountNumber"))
                  .append("\"><input name=\"adminKey\" type=\"password\" placeholder=\"admin key\" required><button class=\"btn good\" type=\"submit\">Unlock</button></form>");
            }
            sb.append("</div>");
        }
        return sb.append("</div>").toString();
    }
}
