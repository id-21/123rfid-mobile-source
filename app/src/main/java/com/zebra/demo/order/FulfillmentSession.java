package com.zebra.demo.order;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure logic for picking one order: feed it every EPC the reader (or a test button)
 * reports and it tracks what matched, what was a repeat and what does not belong.
 * No Android dependencies, so it is unit-testable and can be driven by any scan source.
 */
public final class FulfillmentSession {

    public enum ScanResult {
        /** EPC belongs to this order and was not scanned before. */
        MATCHED,
        /** EPC belongs to this order but was already counted. */
        ALREADY_SCANNED,
        /** EPC is not part of this order. */
        NOT_IN_ORDER,
        /** Empty input, nothing happened. */
        IGNORED
    }

    private final Order order;
    private final long startedAtMillis;
    private final Map<String, OrderLine> expected = new HashMap<>();
    private final Set<String> matched = new LinkedHashSet<>();
    private final Set<String> unexpected = new LinkedHashSet<>();

    public FulfillmentSession(Order order) {
        this(order, System.currentTimeMillis(), null, null);
    }

    /** Restores a session that was in progress (e.g. after the app was killed). */
    public FulfillmentSession(Order order, long startedAtMillis,
                              Collection<String> alreadyMatched,
                              Collection<String> alreadyUnexpected) {
        this.order = order;
        this.startedAtMillis = startedAtMillis;
        for (OrderLine line : order.lines) {
            for (String epc : line.epcs) {
                expected.put(epc, line);
            }
        }
        if (alreadyMatched != null) {
            for (String e : alreadyMatched) {
                String n = Epc.normalize(e);
                if (expected.containsKey(n)) {
                    matched.add(n);
                }
            }
        }
        if (alreadyUnexpected != null) {
            for (String e : alreadyUnexpected) {
                String n = Epc.normalize(e);
                if (!n.isEmpty() && !expected.containsKey(n)) {
                    unexpected.add(n);
                }
            }
        }
    }

    public synchronized ScanResult onTag(String rawEpc) {
        String epc = Epc.normalize(rawEpc);
        if (epc.isEmpty()) {
            return ScanResult.IGNORED;
        }
        if (expected.containsKey(epc)) {
            return matched.add(epc) ? ScanResult.MATCHED : ScanResult.ALREADY_SCANNED;
        }
        unexpected.add(epc);
        return ScanResult.NOT_IN_ORDER;
    }

    public Order order() {
        return order;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    public synchronized int totalExpected() {
        return expected.size();
    }

    public synchronized int totalMatched() {
        return matched.size();
    }

    public synchronized boolean isComplete() {
        return !expected.isEmpty() && matched.size() == expected.size();
    }

    public synchronized boolean isMatched(String epc) {
        return matched.contains(Epc.normalize(epc));
    }

    public synchronized int matchedFor(OrderLine line) {
        int n = 0;
        for (String epc : line.epcs) {
            if (matched.contains(epc)) {
                n++;
            }
        }
        return n;
    }

    /** Expected EPCs not yet scanned, in order-line order. */
    public synchronized List<String> missingEpcs() {
        List<String> out = new ArrayList<>();
        for (OrderLine line : order.lines) {
            for (String epc : line.epcs) {
                if (!matched.contains(epc)) {
                    out.add(epc);
                }
            }
        }
        return out;
    }

    public synchronized List<String> matchedEpcs() {
        return Collections.unmodifiableList(new ArrayList<>(matched));
    }

    public synchronized List<String> unexpectedEpcs() {
        return Collections.unmodifiableList(new ArrayList<>(unexpected));
    }
}
