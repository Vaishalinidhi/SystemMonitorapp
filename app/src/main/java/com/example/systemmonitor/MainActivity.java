package com.example.systemmonitor;

import android.Manifest;
import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ProcessLifecycleOwner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView memoryText, cpuText, storageText, batteryText, lifecycleTextView, uptimeText, usageText;
    private static final int REQUEST_CODE = 1;
    private Handler handler = new Handler();

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

            float batteryPct = level * 100 / (float) scale;

            String statusString = getBatteryStatusString(status);
            String healthString = getBatteryHealthString(health);
            String powerSource = getBatteryPowerSourceString(plugged);

            batteryText.setText(String.format("Battery: %.2f%%\nStatus: %s\nHealth: %s\nPower Source: %s",
                    batteryPct, statusString, healthString, powerSource));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        memoryText = findViewById(R.id.memoryText);
        cpuText = findViewById(R.id.cpuText);
        storageText = findViewById(R.id.storageText);
        batteryText = findViewById(R.id.batteryText);
        lifecycleTextView = findViewById(R.id.lifecycleTextView);
        uptimeText = findViewById(R.id.uptimeText);
        usageText = findViewById(R.id.usageText);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE);
            }
        }

        if (!isUsagePermissionGranted()) {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
        }

        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new AppLifecycleObserver(lifecycleTextView));

        handler.postDelayed(updateRunnable, 2000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(batteryReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(batteryReceiver);
    }

    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            memoryText.setText(getMemoryInfo());
            cpuText.setText(getCpuUsage());
            storageText.setText(getStorageInfo());
            uptimeText.setText(getDeviceUptime());
            usageText.setText(getTopUsedApps());
            handler.postDelayed(this, 2000);
        }
    };

    public String getMemoryInfo() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);

        long availableMemory = memoryInfo.availMem / (1024 * 1024);
        long totalMemory = memoryInfo.totalMem / (1024 * 1024);

        return String.format("Available Memory: %d MB / Total Memory: %d MB", availableMemory, totalMemory);
    }

    public String getCpuUsage() {
        try {
            Process process = Runtime.getRuntime().exec("top -n 1");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().contains("cpu")) {
                    return parseCpuLine(line);
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "CPU Stats unavailable";
    }

    private String parseCpuLine(String line) {
        line = line.toLowerCase().replace("cpu:", "").trim();
        String[] parts = line.split("\\+");

        float totalUsage = 0;
        for (String part : parts) {
            part = part.trim();
            if (part.contains("idle")) continue;

            try {
                String[] tokens = part.trim().split("%");
                float value = Float.parseFloat(tokens[0].trim());
                totalUsage += value;
            } catch (Exception e) {
                e.printStackTrace();
                return "CPU Usage: Error";
            }
        }

        return String.format("CPU Usage: %.2f%%", totalUsage);
    }

    public String getStorageInfo() {
        StatFs statFs = new StatFs(Environment.getDataDirectory().getAbsolutePath());

        long blockSize = statFs.getBlockSizeLong();
        long totalBlocks = statFs.getBlockCountLong();
        long availableBlocks = statFs.getAvailableBlocksLong();

        long totalStorage = totalBlocks * blockSize / (1024 * 1024);
        long availableStorage = availableBlocks * blockSize / (1024 * 1024);

        return String.format("Available Storage: %d MB / Total Storage: %d MB", availableStorage, totalStorage);
    }

    private String getDeviceUptime() {
        long uptimeMillis = SystemClock.elapsedRealtime();
        long uptimeSeconds = uptimeMillis / 1000;
        long hours = uptimeSeconds / 3600;
        long minutes = (uptimeSeconds % 3600) / 60;
        long seconds = uptimeSeconds % 60;

        return String.format("Uptime: %02d:%02d:%02d", hours, minutes, seconds);
    }

    private String getBatteryStatusString(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return "Charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "Discharging";
            case BatteryManager.BATTERY_STATUS_FULL:
                return "Full";
            default:
                return "Unknown";
        }
    }

    private String getBatteryHealthString(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                return "Overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD:
                return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                return "Over Voltage";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                return "Failure";
            default:
                return "Unknown";
        }
    }

    private String getBatteryPowerSourceString(int plugged) {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_USB:
                return "USB";
            case BatteryManager.BATTERY_PLUGGED_AC:
                return "AC";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                return "Wireless";
            default:
                return "Battery";
        }
    }

    private boolean isUsagePermissionGranted() {
        try {
            UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long currentTime = System.currentTimeMillis();
            List<UsageStats> stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
                    currentTime - 1000 * 1000, currentTime);
            return stats != null && !stats.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public String getTopUsedApps() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long endTime = System.currentTimeMillis();
        long beginTime = endTime - 1000 * 60 * 60 * 24; // last 24 hours

        List<UsageStats> usageStatsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, beginTime, endTime);

        if (usageStatsList == null || usageStatsList.isEmpty()) return "No usage data";

        Collections.sort(usageStatsList, (a, b) -> Long.compare(b.getTotalTimeInForeground(), a.getTotalTimeInForeground()));

        StringBuilder usageInfo = new StringBuilder("Top Apps:\n");
        for (int i = 0; i < Math.min(5, usageStatsList.size()); i++) {
            UsageStats stats = usageStatsList.get(i);
            String appName = getAppName(stats.getPackageName());  // Get app name from package name
            usageInfo.append(appName)
                    .append(" - ")
                    .append(stats.getTotalTimeInForeground() / 1000).append("s\n");
        }

        return usageInfo.toString();
    }

    private String getAppName(String packageName) {
        PackageManager packageManager = getPackageManager();
        try {
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            // Check if the app is a system app
            if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                return packageName;  // Return the package name for system apps
            }
            return packageManager.getApplicationLabel(applicationInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return packageName;  // Return package name if app name is not found
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission denied!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}