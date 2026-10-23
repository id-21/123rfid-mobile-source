package com.zebra.demo.order;

import java.util.Locale;

/** EPC helpers. Readers report EPCs as upper-case hex; the database may not. */
public final class Epc {
    private Epc() {
    }

    /** Trim, drop whitespace/colons/dashes, upper-case. Returns "" for null. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[\\s:\\-]", "").toUpperCase(Locale.US);
    }
}
