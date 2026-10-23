package com.zebra.demo.order;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.zebra.demo.BuildConfig;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Picks one order: shows every SKU with the exact rolls (EPCs) to collect, ticks them off
 * as tags are reported, and submits when everything is matched.
 *
 * Every tag source ends up in {@link #onTagScanned(String)}: typed EPCs and the TEST buttons
 * today, the Zebra reader's tag-read event once the hardware is connected.
 */
public class OrderFulfillmentActivity extends Activity {
    public static final String EXTRA_ORDER_JSON = "order_json";

    private static final int GREEN = 0xFF2E7D32;
    private static final int RED = 0xFFC62828;
    private static final int GREY = 0xFF616161;

    private static final int ROW_HEADER = 0;
    private static final int ROW_PENDING = 1;
    private static final int ROW_DONE = 2;
    private static final int ROW_BAD = 3;

    private static final class Row {
        final int kind;
        final String text;

        Row(int kind, String text) {
            this.kind = kind;
            this.text = text;
        }
    }

    private Order order;
    private FulfillmentSession session;
    private OrderService service;

    private TextView progressText;
    private TextView lastScanText;
    private ProgressBar progressBar;
    private Button submitButton;
    private EditText epcInput;
    private final List<Row> rows = new ArrayList<>();
    private RowAdapter adapter;
    private final Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        service = OrderService.get(this);
        try {
            order = OrderJson.parseOrder(new JSONObject(getIntent().getStringExtra(EXTRA_ORDER_JSON)));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open order", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        session = service.prefs().loadProgress(order);
        setTitle("Order #" + order.id);
        buildUi();
        rebuildRows();
        updateSummary();
    }

    // ---- UI -------------------------------------------------------------------------------

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // top: progress
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(12), dp(10), dp(12), dp(10));
        top.setBackgroundColor(0xFFF3F3F3);
        TextView customer = new TextView(this);
        customer.setText(order.customer);
        customer.setTextSize(15f);
        customer.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(customer);
        progressText = new TextView(this);
        progressText.setTextSize(14f);
        top.addView(progressText);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(Math.max(1, session.totalExpected()));
        top.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        lastScanText = new TextView(this);
        lastScanText.setTextSize(13f);
        lastScanText.setTypeface(Typeface.MONOSPACE);
        lastScanText.setText("Scan a roll, or type an EPC below");
        lastScanText.setTextColor(GREY);
        top.addView(lastScanText);
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // middle: SKUs and rolls
        ListView list = new ListView(this);
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // bottom: manual entry, test helpers, submit
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(8), dp(8), dp(8), dp(8));
        bottom.setBackgroundColor(0xFFF3F3F3);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        epcInput = new EditText(this);
        epcInput.setHint("Type or paste EPC");
        epcInput.setSingleLine(true);
        epcInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        epcInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        epcInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                submitTypedEpc();
                return true;
            }
        });
        inputRow.addView(epcInput, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button addButton = new Button(this);
        addButton.setText("Add");
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitTypedEpc();
            }
        });
        inputRow.addView(addButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bottom.addView(inputRow);

        if (BuildConfig.DEBUG) {
            LinearLayout testRow = new LinearLayout(this);
            testRow.setOrientation(LinearLayout.HORIZONTAL);
            Button next = new Button(this);
            next.setText("TEST: next roll");
            next.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    simulateNextRoll();
                }
            });
            Button wrong = new Button(this);
            wrong.setText("TEST: wrong roll");
            wrong.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    simulateWrongRoll();
                }
            });
            testRow.addView(next, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            testRow.addView(wrong, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            bottom.addView(testRow);
        }

        submitButton = new Button(this);
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit();
            }
        });
        bottom.addView(submitButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(bottom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    private final class RowAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean isEnabled(int position) {
            return false;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView t = convertView instanceof TextView
                    ? (TextView) convertView : new TextView(parent.getContext());
            Row r = rows.get(position);
            if (r.kind == ROW_HEADER) {
                t.setPadding(dp(12), dp(14), dp(12), dp(6));
                t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setTextSize(16f);
                t.setTextColor(0xFF222222);
                t.setBackgroundColor(0xFFEAEAEA);
            } else {
                t.setPadding(dp(12), dp(6), dp(12), dp(6));
                t.setTypeface(Typeface.MONOSPACE);
                t.setTextSize(13f);
                t.setBackgroundColor(Color.TRANSPARENT);
                t.setTextColor(r.kind == ROW_DONE ? GREEN : r.kind == ROW_BAD ? RED : 0xFF444444);
            }
            t.setText(r.text);
            return t;
        }
    }

    private void rebuildRows() {
        rows.clear();
        for (OrderLine line : order.lines) {
            String head = line.sku + (line.description.isEmpty() ? "" : "  -  " + line.description)
                    + "   (" + session.matchedFor(line) + "/" + line.epcs.size() + ")";
            rows.add(new Row(ROW_HEADER, head));
            for (String epc : line.epcs) {
                boolean done = session.isMatched(epc);
                rows.add(new Row(done ? ROW_DONE : ROW_PENDING, (done ? "✓ " : "○ ") + epc));
            }
        }
        List<String> bad = session.unexpectedEpcs();
        if (!bad.isEmpty()) {
            rows.add(new Row(ROW_HEADER, "Not in this order (" + bad.size() + ")"));
            for (String epc : bad) {
                rows.add(new Row(ROW_BAD, "✗ " + epc));
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void updateSummary() {
        int total = session.totalExpected();
        int done = session.totalMatched();
        progressText.setText(done + " of " + total + " rolls scanned");
        progressBar.setMax(Math.max(1, total));
        progressBar.setProgress(done);
        boolean complete = session.isComplete();
        submitButton.setEnabled(complete);
        submitButton.setText(complete ? "Submit order"
                : "Submit order (" + (total - done) + " left)");
    }

    // ---- scan handling --------------------------------------------------------------------

    /** Single entry point for every tag source. Safe to call from the UI thread. */
    public void onTagScanned(String rawEpc) {
        FulfillmentSession.ScanResult result = session.onTag(rawEpc);
        if (result == FulfillmentSession.ScanResult.IGNORED) {
            return;
        }
        service.prefs().saveProgress(order.id, session);
        String epc = Epc.normalize(rawEpc);
        switch (result) {
            case MATCHED:
                showLastScan("✓ Matched  " + epc, GREEN);
                break;
            case ALREADY_SCANNED:
                showLastScan("Already scanned  " + epc, GREY);
                break;
            default:
                showLastScan("✗ Not in this order  " + epc, RED);
                break;
        }
        rebuildRows();
        updateSummary();
    }

    private void showLastScan(String text, int color) {
        lastScanText.setText(text);
        lastScanText.setTextColor(color);
    }

    private void submitTypedEpc() {
        String text = epcInput.getText().toString();
        epcInput.setText("");
        onTagScanned(text);
    }

    private void simulateNextRoll() {
        List<String> missing = session.missingEpcs();
        if (missing.isEmpty()) {
            Toast.makeText(this, "Every roll is already scanned", Toast.LENGTH_SHORT).show();
            return;
        }
        onTagScanned(missing.get(0));
    }

    private void simulateWrongRoll() {
        long r = random.nextLong() & 0xFFFFFFFFFFFFFFL;
        onTagScanned(String.format(Locale.US, "BADBAD%018X", r));
    }

    // ---- submit ---------------------------------------------------------------------------

    private void submit() {
        if (!session.isComplete()) {
            return;
        }
        submitButton.setEnabled(false);
        Completion completion = Completion.create(session, service.deviceId());
        service.submit(completion, new OrderService.Callback<Boolean>() {
            @Override
            public void onResult(Boolean sent) {
                service.prefs().clearProgress(order.id);
                Toast.makeText(OrderFulfillmentActivity.this,
                        sent ? "Order submitted"
                                : "No connection - saved, will upload automatically",
                        Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(Exception e) {
                submitButton.setEnabled(true);
                Toast.makeText(OrderFulfillmentActivity.this,
                        "Could not save: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
