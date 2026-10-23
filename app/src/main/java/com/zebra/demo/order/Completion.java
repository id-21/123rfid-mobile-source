package com.zebra.demo.order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The result of picking an order, sent back to the backend.
 * completionId makes the upload idempotent: retrying the same completion never
 * completes an order twice.
 */
public final class Completion {
    public final String completionId;
    public final String orderId;
    public final String deviceId;
    public final long startedAtMillis;
    public final long completedAtMillis;
    public final List<String> matchedEpcs;
    public final List<String> unexpectedEpcs;

    public Completion(String completionId, String orderId, String deviceId,
                      long startedAtMillis, long completedAtMillis,
                      List<String> matchedEpcs, List<String> unexpectedEpcs) {
        this.completionId = completionId;
        this.orderId = orderId;
        this.deviceId = deviceId;
        this.startedAtMillis = startedAtMillis;
        this.completedAtMillis = completedAtMillis;
        this.matchedEpcs = Collections.unmodifiableList(new ArrayList<>(matchedEpcs));
        this.unexpectedEpcs = Collections.unmodifiableList(new ArrayList<>(unexpectedEpcs));
    }

    public static Completion create(FulfillmentSession session, String deviceId) {
        return new Completion(UUID.randomUUID().toString(), session.order().id, deviceId,
                session.startedAtMillis(), System.currentTimeMillis(),
                session.matchedEpcs(), session.unexpectedEpcs());
    }
}
