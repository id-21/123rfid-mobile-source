package com.zebra.demo.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class FulfillmentSessionTest {

    private static Order sampleOrder() {
        OrderLine a = new OrderLine("CM-2201", "Grey damask", 2,
                Arrays.asList("e280 1160 0000 0000 01", "E28011600000000002"));
        OrderLine b = new OrderLine("OP-1105", "Gold foil", 1,
                Collections.singletonList("E28011600000000003"));
        return new Order("1001", "Sample", Order.STATUS_NEW, "", "", Arrays.asList(a, b));
    }

    @Test
    public void normalizesEpcsOnTheOrder() {
        Order o = sampleOrder();
        assertEquals("E28011600000000001", o.lines.get(0).epcs.get(0));
        assertEquals(3, o.totalRolls());
    }

    @Test
    public void matchesRepeatsAndWrongTags() {
        FulfillmentSession s = new FulfillmentSession(sampleOrder());
        assertEquals(FulfillmentSession.ScanResult.MATCHED, s.onTag("e28011600000000001"));
        assertEquals(FulfillmentSession.ScanResult.ALREADY_SCANNED, s.onTag("E28011600000000001"));
        assertEquals(FulfillmentSession.ScanResult.NOT_IN_ORDER, s.onTag("BADBAD000000000000000000"));
        assertEquals(FulfillmentSession.ScanResult.IGNORED, s.onTag("   "));
        assertEquals(1, s.totalMatched());
        assertEquals(1, s.unexpectedEpcs().size());
        assertFalse(s.isComplete());
    }

    @Test
    public void completesOnlyWhenEveryRollIsScanned() {
        FulfillmentSession s = new FulfillmentSession(sampleOrder());
        assertEquals(3, s.missingEpcs().size());
        for (String epc : s.missingEpcs()) {
            assertEquals(FulfillmentSession.ScanResult.MATCHED, s.onTag(epc));
        }
        assertTrue(s.isComplete());
        assertEquals(0, s.missingEpcs().size());
        assertEquals(2, s.matchedFor(s.order().lines.get(0)));
    }

    @Test
    public void restoreKeepsProgressAndDropsForeignTags() {
        Order o = sampleOrder();
        FulfillmentSession s = new FulfillmentSession(o, 1234L,
                Arrays.asList("E28011600000000003", "NOT-IN-THIS-ORDER"),
                Arrays.asList("BADBAD000000000000000000"));
        assertEquals(1, s.totalMatched());
        assertEquals(1, s.unexpectedEpcs().size());
        assertEquals(1234L, s.startedAtMillis());
    }

    @Test
    public void emptyOrderIsNeverComplete() {
        Order o = new Order("x", "", Order.STATUS_NEW, "", "", Collections.<OrderLine>emptyList());
        assertFalse(new FulfillmentSession(o).isComplete());
    }
}
