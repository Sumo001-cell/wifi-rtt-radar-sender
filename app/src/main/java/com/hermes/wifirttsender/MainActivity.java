package com.hermes.wifirttsender;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.Uri;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 44;
    private static final int HISTORY_SIZE = 25;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Integer> distanceHistoryMm = new ArrayDeque<>();

    private WifiManager wifiManager;
    private WifiRttManager rttManager;
    private LinearLayout apList;
    private TextView statusView;
    private TextView selectedView;
    private TextView distanceView;
    private TextView qualityView;
    private TextView logView;
    private EditText bridgeInput;
    private EditText intervalInput;
    private Button scanButton;
    private Button startButton;
    private Button stopButton;

    private ScanResult selectedAp;
    private boolean measuring;
    private int requestCount;
    private int successCount;
    private BroadcastReceiver scanReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        rttManager = (WifiRttManager) getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
        buildUi();
        applyLaunchIntent(getIntent());
        requestNeededPermissions();
        updateCapabilityStatus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyLaunchIntent(intent);
    }

    @Override
    protected void onDestroy() {
        stopMeasuring();
        unregisterScanReceiver();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.rgb(247, 244, 237));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        scrollView.addView(root);

        TextView title = text("WiFi RTT Radar", 30, true);
        title.setTextColor(Color.rgb(25, 32, 36));
        root.addView(title);

        TextView subtitle = text("Dien thoai do khoang cach that toi modem/AP ho tro RTT va gui ve viewer.", 14, false);
        subtitle.setTextColor(Color.rgb(75, 84, 88));
        root.addView(subtitle);

        statusView = pill("Dang kiem tra thiet bi...");
        root.addView(statusView);

        bridgeInput = input("http://192.168.2.18:8791");
        root.addView(label("Bridge tren may tinh"));
        root.addView(bridgeInput);

        Button findBridgeButton = button("Tim bridge");
        findBridgeButton.setOnClickListener(v -> autoFindBridge());
        root.addView(findBridgeButton);

        intervalInput = input("1200");
        intervalInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(label("Tan suat do, millisecond"));
        root.addView(intervalInput);

        scanButton = button("Quet modem RTT");
        scanButton.setOnClickListener(v -> scanAccessPoints());
        root.addView(scanButton);

        selectedView = card("Chua chon modem/AP RTT.");
        root.addView(selectedView);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(8), 0, dp(8));
        startButton = button("Bat dau do");
        stopButton = button("Dung");
        startButton.setOnClickListener(v -> startMeasuring());
        stopButton.setOnClickListener(v -> stopMeasuring());
        controls.addView(startButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        controls.addView(stopButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(controls);

        distanceView = metric("Khoang cach: --");
        qualityView = metric("Chat luong do: --");
        root.addView(distanceView);
        root.addView(qualityView);

        root.addView(label("Modem/AP co the do"));
        apList = new LinearLayout(this);
        apList.setOrientation(LinearLayout.VERTICAL);
        root.addView(apList);

        root.addView(label("Nhat ky"));
        logView = card("San sang.");
        root.addView(logView);

        setContentView(scrollView);
    }

    private void applyLaunchIntent(Intent intent) {
        if (intent == null || intent.getData() == null || bridgeInput == null) return;
        Uri data = intent.getData();
        String bridge = data.getQueryParameter("bridge");
        if (bridge != null && bridge.length() > 0) {
            bridgeInput.setText(bridge);
            appendLog("Da nhan bridge tu viewer: " + bridge);
        }
    }

    private void updateCapabilityStatus() {
        boolean phoneSupportsRtt = getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT);
        if (!phoneSupportsRtt || rttManager == null) {
            statusView.setText("Dien thoai nay khong ho tro WiFi RTT.");
            return;
        }
        statusView.setText("Dien thoai ho tro WiFi RTT. Hay quet modem.");
    }

    private void requestNeededPermissions() {
        List<String> permissions = new ArrayList<>();
        Collections.addAll(permissions,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.INTERNET);
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }

        List<String> missing = new ArrayList<>();
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void scanAccessPoints() {
        if (!hasLocationPermission()) {
            requestNeededPermissions();
            appendLog("Can cap quyen vi tri de Android cho phep quet WiFi RTT.");
            return;
        }
        if (wifiManager == null) {
            appendLog("Khong doc duoc WiFi manager tren dien thoai.");
            return;
        }

        scanButton.setEnabled(false);
        apList.removeAllViews();
        apList.addView(card("Dang quet cac modem/AP ho tro RTT..."));

        unregisterScanReceiver();
        scanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                unregisterScanReceiver();
                scanButton.setEnabled(true);
                renderScanResults();
            }
        };
        registerReceiver(scanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        boolean started = wifiManager.startScan();
        if (!started) {
            scanButton.setEnabled(true);
            unregisterScanReceiver();
            renderScanResults();
        }
    }

    private void renderScanResults() {
        if (!hasLocationPermission()) return;

        List<ScanResult> results = wifiManager.getScanResults();
        apList.removeAllViews();
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (ScanResult result : results) {
            if (!result.is80211mcResponder() || result.BSSID == null || seen.contains(result.BSSID)) {
                continue;
            }
            seen.add(result.BSSID);
            count++;
            TextView row = card(String.format(Locale.US, "%s\n%s | RSSI %d dBm",
                    safeSsid(result), result.BSSID, result.level));
            row.setOnClickListener(v -> selectAccessPoint(result));
            apList.addView(row);
        }
        if (count == 0) {
            apList.addView(card("Khong thay modem/AP ho tro RTT. Modem nay khong tra du lieu khoang cach cho Android."));
        }
        appendLog("Tim thay " + count + " modem/AP RTT.");
    }

    private void selectAccessPoint(ScanResult result) {
        selectedAp = result;
        selectedView.setText(String.format(Locale.US, "Da chon: %s\n%s", safeSsid(result), result.BSSID));
        appendLog("Da chon modem/AP " + safeSsid(result));
    }

    private void startMeasuring() {
        if (selectedAp == null) {
            appendLog("Hay chon modem/AP RTT truoc.");
            return;
        }
        if (!hasLocationPermission()) {
            requestNeededPermissions();
            return;
        }
        measuring = true;
        requestCount = 0;
        successCount = 0;
        distanceHistoryMm.clear();
        appendLog("Bat dau do khoang cach that bang WiFi RTT.");
        scheduleNextRange(0);
    }

    private void stopMeasuring() {
        measuring = false;
        handler.removeCallbacksAndMessages(null);
        appendLog("Da dung do.");
    }

    private void scheduleNextRange(long delayMs) {
        handler.postDelayed(this::rangeOnce, delayMs);
    }

    private void rangeOnce() {
        if (!measuring || selectedAp == null || rttManager == null) return;
        if (!hasLocationPermission()) {
            stopMeasuring();
            return;
        }

        requestCount++;
        RangingRequest request = new RangingRequest.Builder()
                .addAccessPoint(selectedAp)
                .build();

        rttManager.startRanging(request, getMainExecutor(), new RangingResultCallback() {
            @Override
            public void onRangingFailure(int code) {
                appendLog("Do that bai, ma loi: " + code);
                scheduleNextRange(readIntervalMs());
            }

            @Override
            public void onRangingResults(List<RangingResult> results) {
                if (!results.isEmpty()) {
                    handleRangingResult(results.get(0));
                } else {
                    appendLog("Khong co ket qua do.");
                }
                scheduleNextRange(readIntervalMs());
            }
        });
    }

    private void handleRangingResult(RangingResult result) {
        if (result.getStatus() != RangingResult.STATUS_SUCCESS) {
            appendLog("AP khong tra ket qua thanh cong, status: " + result.getStatus());
            return;
        }

        successCount++;
        int distanceMm = result.getDistanceMm();
        int distanceStdDevMm = result.getDistanceStdDevMm();
        float previousMeanMm = meanDistanceMm();
        addDistance(distanceMm);
        float meters = distanceMm / 1000f;
        float meanMeters = meanDistanceMm() / 1000f;
        float successRatio = (successCount * 100f) / Math.max(1, requestCount);

        distanceView.setText(String.format(Locale.US, "Khoang cach: %.2f m | TB %.2f m", meters, meanMeters));
        qualityView.setText(String.format(Locale.US, "Chat luong do: %.0f%% | Sai so %.2f m | RSSI %d dBm",
                successRatio, distanceStdDevMm / 1000f, result.getRssi()));
        appendLog(String.format(Locale.US, "Do duoc %.2f m tu %s", meters, safeSsid(selectedAp)));
        postDistance(distanceMm, distanceStdDevMm, previousMeanMm, result);
    }

    private void postDistance(int distanceMm, int distanceStdDevMm, float previousMeanMm, RangingResult result) {
        String bridge = normalizedBridgeUrl();
        if (bridge.length() == 0) return;
        String endpoint = bridge + "/api/distance";
        float motionScore = previousMeanMm > 0 ? Math.min(100f, Math.abs(distanceMm - previousMeanMm) / 25f) : 0f;
        String payload = String.format(Locale.US,
                "{\"source\":\"android-wifi-rtt\",\"sourceRole\":\"rtt\",\"distanceMethod\":\"wifi-rtt\",\"distanceMeters\":%.3f,\"distanceStdDevMeters\":%.3f,\"rssiDbm\":%d,\"motionScore\":%.1f,\"ssid\":\"%s\",\"bssid\":\"%s\",\"numSuccessfulMeasurements\":%d,\"numAttemptedMeasurements\":%d,\"timestamp\":%d}",
                distanceMm / 1000f,
                distanceStdDevMm / 1000f,
                result.getRssi(),
                motionScore,
                json(safeSsid(selectedAp)),
                json(selectedAp.BSSID),
                result.getNumSuccessfulMeasurements(),
                result.getNumAttemptedMeasurements(),
                System.currentTimeMillis());

        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(1800);
                connection.setReadTimeout(1800);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setDoOutput(true);
                byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
                OutputStream outputStream = connection.getOutputStream();
                outputStream.write(bytes);
                outputStream.close();
                int code = connection.getResponseCode();
                connection.disconnect();
                runOnUiThread(() -> appendLog(code >= 200 && code < 300
                        ? "Da gui khoang cach ve viewer."
                        : "Bridge tu choi du lieu, HTTP " + code));
            } catch (Exception error) {
                runOnUiThread(() -> appendLog("Chua gui duoc ve bridge: " + error.getMessage()));
            }
        }).start();
    }

    private void autoFindBridge() {
        String prefix = localSubnetPrefix();
        if (prefix == null) {
            appendLog("Chua xac dinh duoc subnet WiFi cua dien thoai.");
            return;
        }
        appendLog("Dang tim bridge trong mang " + prefix + "x...");
        ExecutorService pool = Executors.newFixedThreadPool(32);
        AtomicBoolean found = new AtomicBoolean(false);
        for (int i = 1; i <= 254; i++) {
            final String bridge = "http://" + prefix + i + ":8791";
            pool.submit(() -> {
                if (found.get()) return;
                if (looksLikeBridge(bridge)) {
                    if (found.compareAndSet(false, true)) {
                        runOnUiThread(() -> {
                            bridgeInput.setText(bridge);
                            appendLog("Da tim thay bridge: " + bridge);
                        });
                    }
                }
            });
        }
        pool.shutdown();
        handler.postDelayed(() -> {
            if (!found.get()) appendLog("Chua tim thay bridge. Kiem tra may tinh da chay bridge va cung WiFi.");
        }, 6500);
    }

    private boolean looksLikeBridge(String bridge) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(bridge + "/api/status").openConnection();
            connection.setConnectTimeout(650);
            connection.setReadTimeout(650);
            int code = connection.getResponseCode();
            connection.disconnect();
            return code == 200;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String localSubnetPrefix() {
        if (wifiManager == null) return null;
        WifiInfo info = wifiManager.getConnectionInfo();
        int ip = info.getIpAddress();
        if (ip == 0) return null;
        return String.format(Locale.US, "%d.%d.%d.",
                ip & 0xff,
                (ip >> 8) & 0xff,
                (ip >> 16) & 0xff);
    }

    private void addDistance(int distanceMm) {
        if (distanceHistoryMm.size() >= HISTORY_SIZE) distanceHistoryMm.removeFirst();
        distanceHistoryMm.addLast(distanceMm);
    }

    private float meanDistanceMm() {
        if (distanceHistoryMm.isEmpty()) return 0f;
        int total = 0;
        for (int distance : distanceHistoryMm) total += distance;
        return total / (float) distanceHistoryMm.size();
    }

    private long readIntervalMs() {
        try {
            return Math.max(500, Long.parseLong(intervalInput.getText().toString().trim()));
        } catch (Exception ignored) {
            return 1200;
        }
    }

    private String normalizedBridgeUrl() {
        String bridge = bridgeInput.getText().toString().trim();
        while (bridge.endsWith("/")) bridge = bridge.substring(0, bridge.length() - 1);
        return bridge;
    }

    private void unregisterScanReceiver() {
        if (scanReceiver == null) return;
        try {
            unregisterReceiver(scanReceiver);
        } catch (Exception ignored) {
        }
        scanReceiver = null;
    }

    private String safeSsid(ScanResult result) {
        if (result == null || result.SSID == null || result.SSID.length() == 0) return "(SSID an)";
        return result.SSID;
    }

    private String json(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void appendLog(String message) {
        logView.setText(message + "\n" + logView.getText());
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setLineSpacing(0, 1.12f);
        textView.setPadding(0, dp(4), 0, dp(8));
        if (bold) textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return textView;
    }

    private TextView label(String value) {
        TextView label = text(value, 13, true);
        label.setTextColor(Color.rgb(45, 55, 58));
        label.setPadding(0, dp(16), 0, dp(6));
        return label;
    }

    private EditText input(String value) {
        EditText editText = new EditText(this);
        editText.setText(value);
        editText.setTextSize(15);
        editText.setSingleLine(true);
        editText.setPadding(dp(12), dp(10), dp(12), dp(10));
        editText.setBackground(box(Color.WHITE, Color.rgb(207, 197, 180), dp(8)));
        return editText;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        button.setBackground(box(Color.rgb(22, 80, 78), Color.rgb(22, 80, 78), dp(8)));
        return button;
    }

    private TextView card(String value) {
        TextView card = text(value, 15, false);
        card.setTextColor(Color.rgb(31, 37, 39));
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(box(Color.WHITE, Color.rgb(225, 217, 204), dp(8)));
        return card;
    }

    private TextView metric(String value) {
        TextView metric = card(value);
        metric.setTextSize(18);
        metric.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return metric;
    }

    private TextView pill(String value) {
        TextView pill = text(value, 14, true);
        pill.setTextColor(Color.rgb(18, 78, 74));
        pill.setPadding(dp(12), dp(10), dp(12), dp(10));
        pill.setBackground(box(Color.rgb(221, 239, 234), Color.rgb(169, 210, 201), dp(999)));
        return pill;
    }

    private GradientDrawable box(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
