package com.zebra.demo.order;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** JSON <-> model conversion. The same shapes are served by tools/mock-server/server.js. */
final class OrderJson {
    private OrderJson() {
    }

    static String iso(long millis) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(millis));
    }

    static long parseIso(String s) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = f.parse(s);
            return d == null ? 0L : d.getTime();
        } catch (ParseException | NullPointerException e) {
            return 0L;
        }
    }

    private static List<String> stringList(JSONArray arr) throws JSONException {
        List<String> out = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                out.add(arr.getString(i));
            }
        }
        return out;
    }

    private static JSONArray toArray(List<String> values) {
        JSONArray arr = new JSONArray();
        for (String v : values) {
            arr.put(v);
        }
        return arr;
    }

    static Order parseOrder(JSONObject o) throws JSONException {
        List<OrderLine> lines = new ArrayList<>();
        JSONArray la = o.optJSONArray("lines");
        if (la != null) {
            for (int i = 0; i < la.length(); i++) {
                JSONObject l = la.getJSONObject(i);
                List<String> epcs = stringList(l.optJSONArray("epcs"));
                lines.add(new OrderLine(l.getString("sku"), l.optString("description", ""),
                        l.optInt("qty", epcs.size()), epcs));
            }
        }
        String claimedBy = o.isNull("claimedBy") ? "" : o.optString("claimedBy", "");
        return new Order(o.getString("id"), o.optString("customer", ""),
                o.optString("status", Order.STATUS_NEW), claimedBy,
                o.optString("updatedAt", ""), lines);
    }

    static List<Order> parseOrderList(String body) throws JSONException {
        JSONObject root = new JSONObject(body);
        JSONArray arr = root.optJSONArray("orders");
        List<Order> out = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                out.add(parseOrder(arr.getJSONObject(i)));
            }
        }
        return out;
    }

    static JSONObject toJson(Order order) throws JSONException {
        JSONArray lines = new JSONArray();
        for (OrderLine l : order.lines) {
            JSONObject lo = new JSONObject();
            lo.put("sku", l.sku);
            lo.put("description", l.description);
            lo.put("qty", l.qty);
            lo.put("epcs", toArray(l.epcs));
            lines.put(lo);
        }
        JSONObject o = new JSONObject();
        o.put("id", order.id);
        o.put("customer", order.customer);
        o.put("status", order.status);
        o.put("claimedBy", order.claimedBy);
        o.put("updatedAt", order.updatedAt);
        o.put("lines", lines);
        return o;
    }

    static String toJsonString(Order order) {
        try {
            return toJson(order).toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    static JSONObject toJson(Completion c) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("completionId", c.completionId);
        o.put("orderId", c.orderId);
        o.put("device", c.deviceId);
        o.put("startedAt", iso(c.startedAtMillis));
        o.put("completedAt", iso(c.completedAtMillis));
        o.put("matchedEpcs", toArray(c.matchedEpcs));
        o.put("unexpectedEpcs", toArray(c.unexpectedEpcs));
        return o;
    }

    static Completion parseCompletion(JSONObject o) throws JSONException {
        return new Completion(o.getString("completionId"), o.getString("orderId"),
                o.optString("device", ""), parseIso(o.optString("startedAt", "")),
                parseIso(o.optString("completedAt", "")),
                stringList(o.optJSONArray("matchedEpcs")),
                stringList(o.optJSONArray("unexpectedEpcs")));
    }
}
