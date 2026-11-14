package app.firetvremote.remote;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import app.firetvremote.Link;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class RemoteActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor network = new ThreadPoolExecutor(1, 1, 0L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(6));
    private final Runnable singleHome = () -> {
        lastHomeTap = -1;
        send("HOME");
    };
    private long lastHomeTap = -1;
    private SharedPreferences settings;
    private LinearLayout panel;
    private TextView status;
    private String host;
    private String phoneId;
    private String key;
    private boolean pairing;
    private volatile int requestVersion;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = getSharedPreferences("tv-link", MODE_PRIVATE);
        host = settings.getString("host", null);
        phoneId = settings.getString("id", null);
        key = settings.getString("key", null);
        render();
    }

    private void render() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(20), dp(20), dp(20));
        panel.setBackgroundColor(0xff111923);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(panel);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("Family TV Remote");
        title.setTextSize(26);
        panel.addView(title);
        status = new TextView(this);
        status.setPadding(0, dp(8), 0, dp(12));
        panel.addView(status);
        if (key == null) {
            showPairingForm();
            return;
        }
        status.setText("Paired • Open Family TV on the stick to use the remote.");
        LinearLayout top = row();
        button(top, "Back", () -> send("BACK"));
        button(top, "Home", this::tapHome);
        button(top, "Options", () -> send("OPTIONS"));
        button(row(), "↑", "Up", () -> send("UP"));
        LinearLayout middle = row();
        button(middle, "←", "Left", () -> send("LEFT"));
        button(middle, "OK", () -> send("OK"));
        button(middle, "→", "Right", () -> send("RIGHT"));
        button(row(), "↓", "Down", () -> send("DOWN"));
        LinearLayout volume = row();
        button(volume, "Vol −", null);
        button(volume, "Mute", null);
        button(volume, "Vol +", null);
        TextView note = new TextView(this);
        note.setText("Use your TV remote for sound. Double-tap Home for Amazon Home.");
        note.setPadding(0, dp(12), 0, dp(8));
        panel.addView(note);
        button(row(), "Change TV address", this::changeAddress);
        button(row(), "Forget TV", () -> {
            ui.removeCallbacks(singleHome);
            lastHomeTap = -1;
            requestVersion++;
            network.getQueue().clear();
            settings.edit().clear().apply();
            host = phoneId = key = null;
            render();
        });
    }

    private void showPairingForm() {
        status.setText("On the TV, open Remote options → Pair a phone.");
        EditText address = field("TV address");
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText code = field("Pairing code");
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        button(row(), "Pair", () -> pair(address.getText().toString().trim(),
                code.getText().toString().replaceAll("\\s+", "").toUpperCase(Locale.ROOT)));
    }

    private void pair(String address, String code) {
        if (pairing) return;
        if (!validAddress(address) || !code.matches("[A-Z2-9]{16}")) {
            status.setText("Enter the TV’s IPv4 address and the 16-character code.");
            return;
        }
        pairing = true;
        status.setText("Pairing…");
        String id = Link.randomText(24);
        String derivedKey = Link.phoneKey(code, id);
        final int version = requestVersion;
        if (!runNetwork(() -> {
            if (version != requestVersion) return;
            try {
                Link.exchange(address, id, derivedKey, "PAIR");
                ui.post(() -> {
                    if (isDestroyed() || version != requestVersion) return;
                    pairing = false;
                    host = address;
                    phoneId = id;
                    key = derivedKey;
                    settings.edit().putString("host", host).putString("id", phoneId)
                            .putString("key", key).apply();
                    render();
                });
            } catch (IOException e) {
                ui.post(() -> {
                    if (isDestroyed() || version != requestVersion) return;
                    pairing = false;
                    status.setText("Pairing failed. Check Wi-Fi, the TV address, and the current code.");
                });
            }
        })) pairing = false;
    }

    private static boolean validAddress(String address) {
        return RemoteInput.validAddress(address);
    }

    private void changeAddress() {
        EditText address = new EditText(this);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setText(host);
        new android.app.AlertDialog.Builder(this).setTitle("TV address").setView(address)
                .setNegativeButton("Cancel", null).setPositiveButton("Save", (dialog, which) -> {
                    String value = address.getText().toString().trim();
                    if (!validAddress(value)) {
                        status.setText("Enter a valid IPv4 address.");
                        return;
                    }
                    ui.removeCallbacks(singleHome);
                    lastHomeTap = -1;
                    requestVersion++;
                    network.getQueue().clear();
                    host = value;
                    settings.edit().putString("host", host).apply();
                    status.setText("TV address updated.");
                }).show();
    }

    private void tapHome() {
        long now = SystemClock.elapsedRealtime();
        if (lastHomeTap >= 0 && now - lastHomeTap < 1000) {
            ui.removeCallbacks(singleHome);
            lastHomeTap = -1;
            send("AMAZON");
        } else {
            if (lastHomeTap >= 0) {
                ui.removeCallbacks(singleHome);
                send("HOME");
            }
            lastHomeTap = now;
            ui.postDelayed(singleHome, 1000);
        }
    }

    private void send(String action) {
        if (key == null) return;
        String address = host;
        String id = phoneId;
        String commandKey = key;
        final int version = requestVersion;
        final long queuedAt = SystemClock.elapsedRealtime();
        runNetwork(() -> {
            if (version != requestVersion) return;
            if (!RemoteInput.freshCommand(version, requestVersion, queuedAt, SystemClock.elapsedRealtime())) {
                ui.post(() -> {
                    if (!isDestroyed() && version == requestVersion) status.setText("Remote is busy. Press again.");
                });
                return;
            }
            try {
                Link.exchange(address, id, commandKey, action);
                ui.post(() -> {
                    if (!isDestroyed() && version == requestVersion && commandKey.equals(key)) status.setText("Connected");
                });
            } catch (IOException e) {
                ui.post(() -> {
                    if (!isDestroyed() && version == requestVersion && commandKey.equals(key)) status.setText("Open Family TV and check your Wi-Fi.");
                });
            }
        });
    }

    private boolean runNetwork(Runnable operation) {
        try {
            network.execute(operation);
            return true;
        } catch (RejectedExecutionException e) {
            status.setText("Remote is busy. Try again in a moment.");
            return false;
        }
    }

    @Override
    protected void onDestroy() {
        requestVersion++;
        ui.removeCallbacksAndMessages(null);
        network.shutdownNow();
        super.onDestroy();
    }

    private EditText field(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(true);
        panel.addView(field);
        return field;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        panel.addView(row);
        return row;
    }

    private void button(LinearLayout row, String text, Runnable action) {
        button(row, text, text, action);
    }

    private void button(LinearLayout row, String text, String description, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setContentDescription(description);
        button.setEnabled(action != null);
        if (action != null) button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, dp(62), 1f);
        size.setMargins(dp(3), dp(3), dp(3), dp(3));
        row.addView(button, size);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
