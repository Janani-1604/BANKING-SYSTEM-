package com.securebank.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Server-side session. The PIN lives only in server memory, never in the browser. */
public final class Session {
    private final String id;
    private volatile long accountNo = -1;
    private volatile String pin;
    private volatile long lastUsed = System.currentTimeMillis();
    private final List<Html.Flash> flashes = Collections.synchronizedList(new ArrayList<>());

    public Session(String id) { this.id = id; }

    public String id()              { return id; }
    public boolean loggedIn()       { return accountNo > 0; }
    public long accountNo()         { return accountNo; }
    public String pin()             { return pin; }
    public long lastUsed()          { return lastUsed; }
    public void touch()             { lastUsed = System.currentTimeMillis(); }

    public void login(long no, String pin) { this.accountNo = no; this.pin = pin; }
    public void logout()                   { this.accountNo = -1; this.pin = null; }

    public void flash(String kind, String text, String sub) { flashes.add(new Html.Flash(kind, text, sub)); }

    /** Returns the pending messages once and clears them (flash-message pattern). */
    public List<Html.Flash> drainFlashes() {
        synchronized (flashes) {
            List<Html.Flash> copy = new ArrayList<>(flashes);
            flashes.clear();
            return copy;
        }
    }
}
