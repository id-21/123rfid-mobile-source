package com.zebra.demo.order;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.zebra.demo.DeviceDiscoverActivity;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lists open orders, refreshes itself every few seconds, and starts a pick on tap. */
public class OrderListActivity extends Activity {
    private static final long AUTO_REFRESH_MS = 20000L;
    private static final int MENU_REFRESH = 1;
    private static final int MENU_SERVER = 2;
    private static final int MENU_ADD_TEST = 3;
    private static final int MENU_RFID_TOOLS = 4;

    private OrderService service;
    private final List<Order> orders = new ArrayList<>();
    private final Set<String> knownIds = new HashSet<>();
    private boolean firstLoad = true;
    private ArrayAdapter<Order> adapter;
    private TextView statusView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable autoRefresh = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, AUTO_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        service = OrderService.get(this);
        setTitle("Daga Orders");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        statusView = new TextView(this);
        int pad = dp(12);
        statusView.setPadding(pad, pad, pad, pad);
        statusView.setTextSize(13f);
        statusView.setBackgroundColor(0xFFF3F3F3);
        root.addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ListView list = new ListView(this);
        adapter = new ArrayAdapter<Order>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, orders) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                Order o = getItem(position);
                ((TextView) v.findViewById(android.R.id.text1))
                        .setText("#" + o.id + "   " + o.customer);
                ((TextView) v.findViewById(android.R.id.text2)).setText(describe(o));
                return v;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onOrderTapped(orders.get(position));
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        updateStatus(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(autoRefresh);
        autoRefresh.run(); // refreshes now and schedules the next one
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(autoRefresh);
    }

    // ---- list behaviour -------------------------------------------------------------------

    private String describe(Order o) {
        String state;
        if (Order.STATUS_NEW.equals(o.status)) {
            state = "NEW";
        } else if (service.deviceId().equals(o.claimedBy)) {
            state = "IN PROGRESS (you)";
        } else {
            state = "IN PROGRESS - " + o.claimedBy;
        }
        return o.lines.size() + " SKU  |  " + o.totalRolls() + " rolls  |  " + state;
    }

    private void onOrderTapped(final Order o) {
        boolean mine = Order.STATUS_IN_PROGRESS.equals(o.status)
                && service.deviceId().equals(o.claimedBy);
        if (mine) {
            openOrder(o);
        } else if (Order.STATUS_NEW.equals(o.status)) {
            service.claim(o.id, new OrderService.Callback<Boolean>() {
                @Override
                public void onResult(Boolean ok) {
                    if (ok) {
                        openOrder(o.withStatus(Order.STATUS_IN_PROGRESS, service.deviceId()));
                    } else {
                        toast("Someone else just took this order");
                        refresh();
                    }
                }

                @Override
                public void onError(Exception e) {
                    toast("Could not start order: " + e.getMessage());
                }
            });
        } else {
            toast("Being picked by " + o.claimedBy);
        }
    }

    private void openOrder(Order o) {
        Intent i = new Intent(this, OrderFulfillmentActivity.class);
        i.putExtra(OrderFulfillmentActivity.EXTRA_ORDER_JSON, OrderJson.toJsonString(o));
        startActivity(i);
    }

    private void refresh() {
        service.refresh(new OrderService.Callback<List<Order>>() {
            @Override
            public void onResult(List<Order> result) {
                int newCount = 0;
                Set<String> ids = new HashSet<>();
                for (Order o : result) {
                    ids.add(o.id);
                    if (!firstLoad && !knownIds.contains(o.id)) {
                        newCount++;
                    }
                }
                knownIds.clear();
                knownIds.addAll(ids);
                firstLoad = false;
                orders.clear();
                orders.addAll(result);
                adapter.notifyDataSetChanged();
                if (newCount > 0) {
                    toast(newCount + " new order" + (newCount == 1 ? "" : "s"));
                }
                updateStatus(null);
            }

            @Override
            public void onError(Exception e) {
                updateStatus(e.getMessage());
            }
        });
    }

    private void updateStatus(String error) {
        StringBuilder sb = new StringBuilder();
        sb.append(service.isMock() ? "Test data (no server)" : service.backendUrl());
        sb.append("\nDevice: ").append(service.deviceId());
        int pending = service.pendingUploads();
        if (pending > 0) {
            sb.append("  |  ").append(pending).append(" waiting to upload");
        }
        if (error != null) {
            sb.append("\nOffline / error: ").append(error);
        } else {
            sb.append("\nUpdated ").append(DateFormat.getTimeInstance().format(new Date()))
                    .append("  |  ").append(orders.size()).append(" open");
        }
        statusView.setText(sb.toString());
    }

    // ---- menu -----------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_REFRESH, 0, "Refresh now");
        menu.add(Menu.NONE, MENU_ADD_TEST, 1, "Add test order");
        menu.add(Menu.NONE, MENU_SERVER, 2, "Server settings");
        menu.add(Menu.NONE, MENU_RFID_TOOLS, 3, "RFID reader tools");
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem add = menu.findItem(MENU_ADD_TEST);
        if (add != null) {
            add.setVisible(service.isMock());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_REFRESH:
                refresh();
                return true;
            case MENU_ADD_TEST:
                service.addTestOrder(new OrderService.Callback<Boolean>() {
                    @Override
                    public void onResult(Boolean value) {
                        refresh();
                    }

                    @Override
                    public void onError(Exception e) {
                        toast("Could not add test order: " + e.getMessage());
                    }
                });
                return true;
            case MENU_SERVER:
                showServerDialog();
                return true;
            case MENU_RFID_TOOLS:
                startActivity(new Intent(this, DeviceDiscoverActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showServerDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        box.setPadding(p, p, p, 0);

        final EditText url = new EditText(this);
        url.setHint("Server URL, e.g. http://192.168.1.20:8080 (empty = test data)");
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(service.backendUrl());
        box.addView(url);

        final EditText token = new EditText(this);
        token.setHint("Access token (optional)");
        token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        token.setText(service.token());
        box.addView(token);

        new AlertDialog.Builder(this)
                .setTitle("Order server")
                .setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        service.setBackend(url.getText().toString(), token.getText().toString());
                        knownIds.clear();
                        firstLoad = true;
                        orders.clear();
                        adapter.notifyDataSetChanged();
                        invalidateOptionsMenu();
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- helpers --------------------------------------------------------------------------

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
