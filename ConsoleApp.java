package com.securebank.console;

import com.securebank.exception.*;
import com.securebank.model.*;
import com.securebank.service.Bank;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** Classic console menu (the original brief). Run with:  java -jar securebank.jar console */
public class ConsoleApp {
    private record Session(long no, String pin) { }

    private final Bank bank;
    private final Scanner in = new Scanner(System.in);

    public ConsoleApp(Bank bank) { this.bank = bank; }

    public void run() {
        System.out.println("\n===== SecureBank =====");
        boolean running = true;
        while (running) {
            System.out.println("\n1. Open Account  2. Deposit  3. Withdraw");
            System.out.println("4. Transfer      5. Statement  6. Exit");
            System.out.print("Enter choice: ");
            try {
                int choice = Integer.parseInt(in.nextLine().trim());
                switch (choice) {
                    case 1 -> openAccount();
                    case 2 -> deposit();
                    case 3 -> withdraw();
                    case 4 -> transfer();
                    case 5 -> statement();
                    case 6 -> running = false;
                    default -> System.out.println("Please choose 1 to 6.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Please type a number.");
            } catch (InsufficientFundsException e) {
                System.out.println("Transaction failed: " + e.getMessage());
                System.out.println("Shortfall: " + Account.money(e.getShortfall()));
            } catch (BankException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (NoSuchElementException e) {
                running = false;                       // input closed
            } finally {
                System.out.println("-------------------------------");
            }
        }
        System.out.println("Thank you for banking with SecureBank.");
    }

    private void openAccount() throws BankException {
        System.out.print("Type (1 = Savings, 2 = Current): ");
        String type = in.nextLine().trim().equals("1") ? "SAVINGS" : "CURRENT";
        System.out.print("Holder name: ");
        String name = in.nextLine();
        System.out.print("Opening balance: ");
        double amount = Double.parseDouble(in.nextLine().trim());
        System.out.print("Choose a 4-digit PIN: ");
        String pin = in.nextLine().trim();
        Bank.Outcome o = bank.openAccount(type, name, amount, pin);
        System.out.println(o.message());
        System.out.println(o.account().displayDetails());
    }

    private void deposit() throws BankException {
        Session s = login();
        System.out.print("Amount: ");
        System.out.println(bank.deposit(s.no(), s.pin(), Double.parseDouble(in.nextLine().trim())).message());
    }

    private void withdraw() throws BankException {
        Session s = login();
        System.out.print("Amount: ");
        System.out.println(bank.withdraw(s.no(), s.pin(), Double.parseDouble(in.nextLine().trim())).message());
    }

    private void transfer() throws BankException {
        Session s = login();
        System.out.print("Destination account no: ");
        long to = Long.parseLong(in.nextLine().trim());
        System.out.print("Amount: ");
        System.out.println(bank.transfer(s.no(), s.pin(), to, Double.parseDouble(in.nextLine().trim())).message());
    }

    private void statement() throws BankException {
        Session s = login();
        Bank.Statement st = bank.statement(s.no(), s.pin());
        System.out.println(st.account().displayDetails());
        System.out.println("\nLast 5 transactions:");
        List<Transaction> recent = st.recent();
        if (recent.isEmpty()) System.out.println("  (none)");
        for (Transaction t : recent)
            System.out.printf("  %-13s %12s   balance %s%n", t.type(), Account.money(t.amount()), Account.money(t.balanceAfter()));
    }

    /** PIN retry loop: 3 tries, then the account locks (FR-6). */
    private Session login() throws BankException {
        System.out.print("Account No: ");
        long no = Long.parseLong(in.nextLine().trim());
        for (int attempt = 1; attempt <= Account.MAX_PIN_ATTEMPTS; attempt++) {
            System.out.print("Enter PIN: ");
            String pin = in.nextLine().trim();
            try {
                bank.authenticate(no, pin);
                return new Session(no, pin);
            } catch (InvalidPinException e) {
                System.out.println(e.getMessage());
            }
        }
        throw new BankException("Too many wrong PIN attempts.");
    }
}
