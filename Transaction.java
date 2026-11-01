package com.securebank.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** One ledger line (immutable). Stored in SQL and in the 5-slot array of an Account. */
public record Transaction(long id, TxType type, double amount, double balanceAfter, String note, long timeMillis) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("type", type.name());
        m.put("credit", type.isCredit());
        m.put("amount", amount);
        m.put("balanceAfter", balanceAfter);
        m.put("note", note);
        m.put("time", timeMillis);
        return m;
    }
}
