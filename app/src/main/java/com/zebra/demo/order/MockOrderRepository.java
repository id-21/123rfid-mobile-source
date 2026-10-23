package com.zebra.demo.order;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Built-in test data so the whole flow works on a phone with no server and no reader.
 * State is kept in local preferences. Use "Add test order" in the menu to simulate a
 * new order arriving.
 */
public final class MockOrderRepository implements OrderRepository {
    private static final Object LOCK = new Object();

    private final OrderPrefs prefs;

    MockOrderRepository(OrderPrefs prefs) {
        this.prefs = prefs;
    }

    /** 24 hex characters (96 bit), unique per (skuIndex, roll). */
    static String epc(int skuIndex, int roll) {
        return String.format(Locale.US, "E28011%02X%016X", skuIndex, roll);
    }

    private static OrderLine line(String sku, String desc, int skuIndex, int firstRoll, int qty) {
        List<String> epcs = new ArrayList<>();
        for (int i = 0; i < qty; i++) {
            epcs.add(epc(skuIndex, firstRoll + i));
        }
        return new OrderLine(sku, desc, qty, epcs);
    }

    private static List<Order> seed() {
        List<Order> out = new ArrayList<>();
        out.add(new Order("1001", "Sample Studio, Kolkata", Order.STATUS_NEW, "", "",
                Arrays.asList(
                        line("CM-2201", "Cristiana Masi - sample A", 1, 1, 3),
                        line("OP-1105", "OH POPSI - sample B", 2, 1, 2))));
        out.add(new Order("1002", "Sample Interiors, Mumbai", Order.STATUS_NEW, "", "",
                Arrays.asList(line("CO-3310", "Coordonne - sample C", 3, 1, 4))));
        out.add(new Order("1003", "Sample Design Co, Jaipur", Order.STATUS_NEW, "", "",
                Arrays.asList(
                        line("CM-2207", "Cristiana Masi - sample D", 4, 1, 2),
                        line("CO-3318", "Coordonne - sample E", 5, 1, 1),
                        line("OP-1120", "OH POPSI - sample F", 6, 1, 3))));
        return out;
    }

    private List<Order> load() throws IOException {
        String json = prefs.mockOrdersJson();
        if (json == null) {
            List<Order> seeded = seed();
            save(seeded);
            return seeded;
        }
        try {
            JSONArray arr = new JSONArray(json);
            List<Order> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                out.add(OrderJson.parseOrder(arr.getJSONObject(i)));
            }
            return out;
        } catch (JSONException e) {
            throw new IOException("Corrupt test data: " + e.getMessage(), e);
        }
    }

    private void save(List<Order> orders) throws IOException {
        try {
            JSONArray arr = new JSONArray();
            for (Order o : orders) {
                arr.put(OrderJson.toJson(o));
            }
            prefs.setMockOrdersJson(arr.toString());
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<Order> fetchOpenOrders() throws IOException {
        synchronized (LOCK) {
            List<Order> open = new ArrayList<>();
            for (Order o : load()) {
                if (!Order.STATUS_COMPLETED.equals(o.status)) {
                    open.add(o);
                }
            }
            return open;
        }
    }

    @Override
    public boolean claim(String orderId, String deviceId) throws IOException {
        synchronized (LOCK) {
            List<Order> all = load();
            for (int i = 0; i < all.size(); i++) {
                Order o = all.get(i);
                if (!o.id.equals(orderId)) {
                    continue;
                }
                if (Order.STATUS_NEW.equals(o.status)) {
                    all.set(i, o.withStatus(Order.STATUS_IN_PROGRESS, deviceId));
                    save(all);
                    return true;
                }
                return Order.STATUS_IN_PROGRESS.equals(o.status) && deviceId.equals(o.claimedBy);
            }
            return false;
        }
    }

    @Override
    public void complete(Completion completion) throws IOException {
        synchronized (LOCK) {
            List<Order> all = load();
            for (int i = 0; i < all.size(); i++) {
                Order o = all.get(i);
                if (o.id.equals(completion.orderId)) {
                    all.set(i, o.withStatus(Order.STATUS_COMPLETED, completion.deviceId));
                    save(all);
                    return;
                }
            }
        }
    }

    /** Test helper: simulates a new order arriving from the other app. */
    public void addTestOrder() throws IOException {
        synchronized (LOCK) {
            List<Order> all = load();
            int next = 1000;
            for (Order o : all) {
                try {
                    next = Math.max(next, Integer.parseInt(o.id));
                } catch (NumberFormatException ignored) {
                    // non-numeric ids are fine
                }
            }
            next++;
            int skuIndex = 7 + (next % 5);
            all.add(new Order(String.valueOf(next), "Sample Customer " + next, Order.STATUS_NEW, "", "",
                    Arrays.asList(line("TEST-" + skuIndex, "Test roll", skuIndex, next * 10, 2))));
            save(all);
        }
    }
}
