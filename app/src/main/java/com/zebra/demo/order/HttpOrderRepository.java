package com.zebra.demo.order;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Talks to the REST contract documented in docs/API_CONTRACT.md:
 *   GET  /orders?status=new,in_progress
 *   POST /orders/{id}/claim      {"device": "..."}   -> 200 ok, 409 taken
 *   POST /orders/{id}/complete   {completion json}   -> 200 ok (idempotent), 409 already completed
 */
public final class HttpOrderRepository implements OrderRepository {
    private final String baseUrl;
    private final String token;

    HttpOrderRepository(String baseUrl, String token) {
        String b = baseUrl == null ? "" : baseUrl.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        this.baseUrl = b;
        this.token = token == null ? "" : token.trim();
    }

    private static final class Response {
        final int code;
        final String body;

        Response(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    private Response send(String method, String path, String json) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            c.setRequestProperty("Accept", "application/json");
            if (!token.isEmpty()) {
                c.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (json != null) {
                byte[] data = json.getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setFixedLengthStreamingMode(data.length);
                OutputStream os = c.getOutputStream();
                try {
                    os.write(data);
                } finally {
                    os.close();
                }
            }
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            return new Response(code, is == null ? "" : readAll(is));
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream is) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            is.close();
        }
    }

    private static IOException httpError(String what, Response r) {
        return new IOException(what + " failed: HTTP " + r.code);
    }

    @Override
    public List<Order> fetchOpenOrders() throws IOException {
        Response r = send("GET", "/orders?status=new,in_progress", null);
        if (r.code < 200 || r.code >= 300) {
            throw httpError("Fetching orders", r);
        }
        try {
            return OrderJson.parseOrderList(r.body);
        } catch (JSONException e) {
            throw new IOException("Unexpected server response: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean claim(String orderId, String deviceId) throws IOException {
        try {
            JSONObject body = new JSONObject();
            body.put("device", deviceId);
            Response r = send("POST", "/orders/" + URLEncoder.encode(orderId, "UTF-8") + "/claim",
                    body.toString());
            if (r.code == 409) {
                return false;
            }
            if (r.code < 200 || r.code >= 300) {
                throw httpError("Claiming order", r);
            }
            return true;
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void complete(Completion completion) throws IOException {
        try {
            Response r = send("POST",
                    "/orders/" + URLEncoder.encode(completion.orderId, "UTF-8") + "/complete",
                    OrderJson.toJson(completion).toString());
            // 409 = the server already holds a completion for this order; do not retry forever
            if (r.code == 409) {
                return;
            }
            if (r.code < 200 || r.code >= 300) {
                throw httpError("Submitting order", r);
            }
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }
}
