package com.zebra.demo.order;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Small local store: server settings, device id, in-progress picks and the upload outbox. */
final class OrderPrefs {
    private static final String KEY_URL = "backend_url";
    private static final String KEY_TOKEN = "backend_token";
    private static final String KEY_DEVICE = "device_id";
    private static final String KEY_OUTBOX = "outbox";
    private static final String KEY_MOCK = "mock_orders";
    private static final String PROGRESS_PREFIX = "progress_";

    private final SharedPreferences sp;

    OrderPrefs(Context context) {
        sp = context.getApplicationContext()
                .getSharedPreferences("order_prefs", Context.MODE_PRIVATE);
    }

    // ---- settings -------------------------------------------------------------------------

    String backendUrl() {
        return sp.getString(KEY_URL, "").trim();
    }

    String token() {
        return sp.getString(KEY_TOKEN, "").trim();
    }

    void setBackend(String url, String token) {
        sp.edit().putString(KEY_URL, url == null ? "" : url.trim())
                .putString(KEY_TOKEN, token == null ? "" : token.trim()).apply();
    }

    synchronized String deviceId() {
        String id = sp.getString(KEY_DEVICE, null);
        if (id == null) {
            String model = Build.MODEL == null ? "android" : Build.MODEL.replace(' ', '-');
            id = model + "-" + UUID.randomUUID().toString().substring(0, 4);
            sp.edit().putString(KEY_DEVICE, id).apply();
        }
        return id;
    }

    // ---- mock backend state ---------------------------------------------------------------

    String mockOrdersJson() {
        return sp.getString(KEY_MOCK, null);
    }

    void setMockOrdersJson(String json) {
        sp.edit().putString(KEY_MOCK, json).apply();
    }

    // ---- outbox (completions waiting to be uploaded) -------------------------------------

    synchronized List<Completion> outbox() {
        List<Completion> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString(KEY_OUTBOX, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                out.add(OrderJson.parseCompletion(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            // corrupt outbox: start fresh rather than crash
        }
        return out;
    }

    private void saveOutbox(List<Completion> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Completion c : list) {
                arr.put(OrderJson.toJson(c));
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        sp.edit().putString(KEY_OUTBOX, arr.toString()).commit();
    }

    synchronized void addToOutbox(Completion c) {
        List<Completion> list = outbox();
        for (Completion existing : list) {
            if (existing.completionId.equals(c.completionId)) {
                return;
            }
        }
        list.add(c);
        saveOutbox(list);
    }

    synchronized void removeFromOutbox(String completionId) {
        List<Completion> list = outbox();
        List<Completion> keep = new ArrayList<>();
        for (Completion c : list) {
            if (!c.completionId.equals(completionId)) {
                keep.add(c);
            }
        }
        saveOutbox(keep);
    }

    synchronized boolean outboxHas(String completionId) {
        for (Completion c : outbox()) {
            if (c.completionId.equals(completionId)) {
                return true;
            }
        }
        return false;
    }

    // ---- in-progress pick -----------------------------------------------------------------

    void saveProgress(String orderId, FulfillmentSession s) {
        try {
            JSONObject o = new JSONObject();
            o.put("started", s.startedAtMillis());
            JSONArray matched = new JSONArray();
            for (String e : s.matchedEpcs()) {
                matched.put(e);
            }
            JSONArray unexpected = new JSONArray();
            for (String e : s.unexpectedEpcs()) {
                unexpected.put(e);
            }
            o.put("matched", matched);
            o.put("unexpected", unexpected);
            sp.edit().putString(PROGRESS_PREFIX + orderId, o.toString()).apply();
        } catch (JSONException e) {
            // progress is a convenience; ignore
        }
    }

    FulfillmentSession loadProgress(Order order) {
        String json = sp.getString(PROGRESS_PREFIX + order.id, null);
        if (json != null) {
            try {
                JSONObject o = new JSONObject(json);
                List<String> matched = new ArrayList<>();
                JSONArray ma = o.optJSONArray("matched");
                if (ma != null) {
                    for (int i = 0; i < ma.length(); i++) {
                        matched.add(ma.getString(i));
                    }
                }
                List<String> unexpected = new ArrayList<>();
                JSONArray ua = o.optJSONArray("unexpected");
                if (ua != null) {
                    for (int i = 0; i < ua.length(); i++) {
                        unexpected.add(ua.getString(i));
                    }
                }
                return new FulfillmentSession(order, o.optLong("started", System.currentTimeMillis()),
                        matched, unexpected);
            } catch (JSONException e) {
                // fall through to a fresh session
            }
        }
        return new FulfillmentSession(order);
    }

    void clearProgress(String orderId) {
        sp.edit().remove(PROGRESS_PREFIX + orderId).apply();
    }
}
