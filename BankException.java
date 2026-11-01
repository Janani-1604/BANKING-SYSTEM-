package com.securebank.exception;

import java.util.Collections;
import java.util.Map;

/** Base class of every business error in SecureBank (checked exception). */
public class BankException extends Exception {
    private final String code;
    private final int status; // HTTP status used by the web layer

    public BankException(String message) {
        this(message, "BANK_ERROR", 400);
    }

    public BankException(String message, String code, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode()   { return code; }
    public int getStatus()    { return status; }

    /** Extra machine-readable fields sent to the UI (shortfall, attempts left ...). */
    public Map<String, Object> details() { return Collections.emptyMap(); }
}
