package com.securebank;

import com.securebank.console.ConsoleApp;
import com.securebank.db.Database;
import com.securebank.service.Bank;
import com.securebank.web.WebServer;

/** Entry point. No argument = web app (Render). Argument "console" = classic console menu. */
public class BankApp {
    public static void main(String[] args) throws Exception {
        Database.init();
        Bank bank = new Bank();

        boolean demo = !"false".equalsIgnoreCase(System.getenv("SEED_DEMO"));
        if (demo) bank.seedDemoDataIfEmpty();

        if (args.length > 0 && args[0].equalsIgnoreCase("console")) {
            new ConsoleApp(bank).run();
            System.exit(0);
        } else {
            int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
            new WebServer(bank, port, demo).start();
        }
    }
}
