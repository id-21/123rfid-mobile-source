package com.zebra.demo.order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One SKU on an order, with the exact rolls (EPCs) that must be picked. */
public final class OrderLine {
    public final String sku;
    public final String description;
    /** Number of rolls required. Normally equals epcs.size(). */
    public final int qty;
    public final List<String> epcs;

    public OrderLine(String sku, String description, int qty, List<String> epcs) {
        this.sku = sku == null ? "" : sku;
        this.description = description == null ? "" : description;
        this.qty = qty;
        List<String> clean = new ArrayList<>();
        if (epcs != null) {
            for (String e : epcs) {
                String n = Epc.normalize(e);
                if (!n.isEmpty() && !clean.contains(n)) {
                    clean.add(n);
                }
            }
        }
        this.epcs = Collections.unmodifiableList(clean);
    }
}
