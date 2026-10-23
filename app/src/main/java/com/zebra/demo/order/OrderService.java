package com.zebra.demo.order;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The single entry point the screens use. Runs network work on one background thread
 * (so operations never overlap) and delivers results on the main thread.
 */
public final class OrderService {

    public interface Callback<T> {
        void onResult(T value);

        void onError(Exception e);
    }

    private static OrderService instance;

    private final OrderPrefs prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private OrderService(Context context) {
        prefs = new OrderPrefs(context);
    }

    public static synchronized OrderService get(Context context) {
        if (instance == null) {
            instance = new OrderService(context.getApplicationContext());
        }
        return instance;
    }

    // ---- settings / info ------------------------------------------------------------------

    OrderPrefs prefs() {
        return prefs;
    }

    public String deviceId() {
        return prefs.deviceId();
    }

    public boolean isMock() {
        return prefs.backendUrl().isEmpty();
    }

    public String backendUrl() {
        return prefs.backendUrl();
    }

    public String token() {
        return prefs.token();
    }

    public void setBackend(String url, String token) {
        prefs.setBackend(url, token);
    }

    public int pendingUploads() {
        return prefs.outbox().size();
    }

    private OrderRepository repo() {
        String url = prefs.backendUrl();
        if (url.isEmpty()) {
            return new MockOrderRepository(prefs);
        }
        return new HttpOrderRepository(url, prefs.token());
    }

    // ---- operations -----------------------------------------------------------------------

    /** Uploads any queued completions, then fetches open orders. */
    public void refresh(final Callback<List<Order>> cb) {
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    flushOutbox();
                    final List<Order> orders = repo().fetchOpenOrders();
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onResult(orders);
                        }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onError(e);
                        }
                    });
                }
            }
        });
    }

    /** Result is false if another device took the order first. */
    public void claim(final String orderId, final Callback<Boolean> cb) {
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final boolean ok = repo().claim(orderId, prefs.deviceId());
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onResult(ok);
                        }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onError(e);
                        }
                    });
                }
            }
        });
    }

    /**
     * Queues the completion locally first (so it can never be lost), then tries to upload.
     * Result is true if the server has it, false if it is queued for a later retry.
     */
    public void submit(final Completion completion, final Callback<Boolean> cb) {
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    prefs.addToOutbox(completion);
                    flushOutbox();
                    final boolean sent = !prefs.outboxHas(completion.completionId);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onResult(sent);
                        }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onError(e);
                        }
                    });
                }
            }
        });
    }

    /** Test helper for the built-in data. */
    public void addTestOrder(final Callback<Boolean> cb) {
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    new MockOrderRepository(prefs).addTestOrder();
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onResult(true);
                        }
                    });
                } catch (final IOException e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onError(e);
                        }
                    });
                }
            }
        });
    }

    /** Must run on the io thread. Stops at the first failure and keeps the rest queued. */
    private void flushOutbox() {
        OrderRepository repo = repo();
        for (Completion c : prefs.outbox()) {
            try {
                repo.complete(c);
                prefs.removeFromOutbox(c.completionId);
            } catch (IOException e) {
                return;
            }
        }
    }
}
