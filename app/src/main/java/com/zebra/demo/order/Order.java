package com.zebra.demo.order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A customer order as served by the backend. */
public final class Order {
    public static final String STATUS_NEW = "new";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED = "completed";

    public final String id;
    public final String customer;
    public final String status;
    /** Device that claimed the order, "" if nobody. */
    public final String claimedBy;
    public final String updatedAt;
    public final List<OrderLine> lines;

    public Order(String id, String customer, String status, String claimedBy,
                 String updatedAt, List<OrderLine> lines) {
        this.id = id == null ? "" : id;
        this.customer = customer == null ? "" : customer;
        this.status = status == null ? STATUS_NEW : status;
        this.claimedBy = claimedBy == null ? "" : claimedBy;
        this.updatedAt = updatedAt == null ? "" : updatedAt;
        this.lines = Collections.unmodifiableList(
                lines == null ? new ArrayList<OrderLine>() : new ArrayList<>(lines));
    }

    /** Total number of rolls to pick across all lines. */
    public int totalRolls() {
        int n = 0;
        for (OrderLine l : lines) {
            n += l.epcs.size();
        }
        return n;
    }

    public Order withStatus(String newStatus, String newClaimedBy) {
        return new Order(id, customer, newStatus, newClaimedBy, updatedAt, lines);
    }
}
