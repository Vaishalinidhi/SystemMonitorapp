package com.example.systemmonitor;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Bundle;
import android.os.Build;
import android.content.Context;
import android.app.ActivityManager;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.StatFs;
import android.os.Environment;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private TextView rotatingHeader, totalMemoryText, availableMemoryText, totalStorageText, availableStorageText, batteryPercentageText, batteryHealthText, batteryCapacityText, batteryTemperatureText, cpuUsageText;
    private ProgressBar memoryProgressBar, batteryProgressBar, storageProgressBar;


    private TextView uploadSpeedText, downloadSpeedText;
    private long previousRxBytes = 0;
    private long previousTxBytes = 0;
    private Handler networkHandler = new Handler();


    private Handler cpuHandler;
    private Runnable cpuRunnable;
    private DecimalFormat decimalFormat = new DecimalFormat("#0.0");

    private ProgressBar cpuProgress;
    private TextView cpuPercentage;

    private TextView deviceUptime;

    private Handler uptimeHandler;
    private Runnable uptimeRunnable;

    private AppUsageHelper appUsageHelper;
    private Handler handler = new Handler();
    private Runnable usageUpdater;






    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Views
        rotatingHeader = findViewById(R.id.rotating_header);
        totalMemoryText = findViewById(R.id.text_total_memory);
        availableMemoryText = findViewById(R.id.text_available_memory);
        memoryProgressBar = findViewById(R.id.progress_memory);
        totalStorageText = findViewById(R.id.text_total_storage);
        availableStorageText = findViewById(R.id.text_available_storage);
        storageProgressBar = findViewById(R.id.progress_storage);
        batteryPercentageText = findViewById(R.id.battery_percentage);
        batteryProgressBar = findViewById(R.id.battery_progress);
        batteryHealthText = findViewById(R.id.battery_health);
        batteryCapacityText = findViewById(R.id.battery_capacity);
        batteryTemperatureText = findViewById(R.id.battery_temperature);
        uploadSpeedText = findViewById(R.id.text_upload_speed);
        downloadSpeedText = findViewById(R.id.text_download_speed);
        cpuProgress = findViewById(R.id.cpu_progress);
        cpuPercentage = findViewById(R.id.cpu_percentage);
        cpuUsageText = findViewById(R.id.cpu_usage_text);
        deviceUptime = findViewById(R.id.device_uptime);
        TextView appUsageTextView = findViewById(R.id.title_app_usage);  // This TextView must exist in your layout
        AppUsageHelper appUsageHelper = new AppUsageHelper(this, appUsageTextView);


// Add your CPU usage TextView

        // Start updating stats in real-time
        updateRotatingHeader();
        updateMemoryStats();
        updateStorageStats();
        updateBatteryStats();
        startNetworkSpeedMonitoring();
        startCpuMonitoring();
        updateDeviceUptime();
        startUptimeMonitoring();
        appUsageHelper.displayTopUsedApps();



        // Update memory and storage stats every 2 seconds
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                updateMemoryStats();
                updateStorageStats();
                new Handler().postDelayed(this, 2000);
            }
        }, 2000);

        // Example: dummy internet access to trigger data
        new Thread(() -> {
            try {
                URL url = new URL("https://www.google.com");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();
                connection.getInputStream().close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void startUptimeMonitoring() {
        uptimeHandler = new Handler();
        uptimeRunnable = new Runnable() {
            @Override
            public void run() {
                updateDeviceUptime();
                uptimeHandler.postDelayed(this, 2000); // every 2 seconds
            }
        };
        uptimeHandler.post(uptimeRunnable);
    }


    private void updateRotatingHeader() {
        new Thread(() -> {
            try {
                String phoneName = Build.MODEL;
                String processorName = getProcessorInfo(); // Get processor info dynamically
                String osName = "Android " + Build.VERSION.RELEASE; // Use Android version for OS
                String[] headers = {
                        "Hi, " + phoneName,
                        "Processor: " + processorName,
                        "Operating System: " + osName
                };
                int index = 0;
                while (true) {
                    final int currentIndex = index;
                    runOnUiThread(() -> rotatingHeader.setText(headers[currentIndex])); // Updated to use rotatingHeader
                    index = (index + 1) % headers.length;
                    Thread.sleep(10000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private String getProcessorInfo() {
        String processorInfo = "Unknown Processor";

        // Try using Build.HARDWARE as a fallback
        try {
            String hardware = Build.HARDWARE;
            if (hardware != null && !hardware.isEmpty()) {
                processorInfo = hardware;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // If it's still unknown, fallback to reading /proc/cpuinfo
        if (processorInfo.equals("Unknown Processor")) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Hardware") || line.contains("Processor")) {
                        processorInfo = line.split(":")[1].trim();
                        break;
                    }
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return processorInfo;
    }

    private void updateMemoryStats() {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(memoryInfo);

        // Convert bytes to MB
        long totalMemory = memoryInfo.totalMem / (1024 * 1024); // MB
        long availableMemory = memoryInfo.availMem / (1024 * 1024); // MB

        // Update the UI dynamically
        totalMemoryText.setText("Total: " + totalMemory + " MB");
        availableMemoryText.setText("Available: " + availableMemory + " MB");

        // Update progress bar for memory usage
        int progress = (int) ((availableMemory / (float) totalMemory) * 100);
        memoryProgressBar.setProgress(progress);
    }

    private void updateStorageStats() {
        // Get storage stats
        StatFs statFs = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
        long totalStorage = statFs.getBlockSizeLong() * statFs.getBlockCountLong() / (1024 * 1024 * 1024); // GB
        long availableStorage = statFs.getBlockSizeLong() * statFs.getAvailableBlocksLong() / (1024 * 1024 * 1024); // GB

        // Update UI dynamically
        totalStorageText.setText("Total: " + totalStorage + " GB");
        availableStorageText.setText("Available: " + availableStorage + " GB");

        // Update progress bar for storage usage
        int progress = (int) ((availableStorage / (float) totalStorage) * 100);
        storageProgressBar.setProgress(progress);
    }

    private void updateBatteryStats() {
        // Get battery stats
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);

        // Get battery percentage
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int batteryPct = (int) ((level / (float) scale) * 100);
        batteryPercentageText.setText(batteryPct + "%");
        batteryProgressBar.setProgress(batteryPct);

        // Set color for battery percentage
        if (batteryPct < 15) {
            batteryProgressBar.setProgressDrawable(getResources().getDrawable(R.drawable.progress_circular_red));
        } else {
            batteryProgressBar.setProgressDrawable(getResources().getDrawable(R.drawable.progress_circular));
        }

        // Battery Health
        int health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
        String healthStatus = getHealthStatus(health);
        batteryHealthText.setText("Health: " + healthStatus);

        // Battery Capacity and Temperature
        batteryCapacityText.setText("Capacity: " + getBatteryCapacity() + " mAh");
        batteryTemperatureText.setText("Temperature: " + getBatteryTemperature() + "°C");
    }

    private String getHealthStatus(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                return "Overheating";
            case BatteryManager.BATTERY_HEALTH_DEAD:
                return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                return "Over Voltage";
            default:
                return "Unknown";
        }
    }

    private int getBatteryTemperature() {
        // Get battery temperature in tenths of a degree Celsius
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        return temperature / 10; // Convert from tenths of degree Celsius to degrees Celsius
    }

    private int getBatteryCapacity() {
        // For now, simulate the battery capacity. You can later fetch this from system APIs if available.
        return 5000; // Example: 5000 mAh
    }

    private void startNetworkSpeedMonitoring() {
        previousRxBytes = TrafficStats.getTotalRxBytes();
        previousTxBytes = TrafficStats.getTotalTxBytes();

        networkHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                long currentRxBytes = TrafficStats.getTotalRxBytes();
                long currentTxBytes = TrafficStats.getTotalTxBytes();

                long downloadSpeed = currentRxBytes - previousRxBytes; // in bytes
                long uploadSpeed = currentTxBytes - previousTxBytes;   // in bytes

                previousRxBytes = currentRxBytes;
                previousTxBytes = currentTxBytes;

                downloadSpeedText.setText("Download: " + formatSpeed(downloadSpeed));
                uploadSpeedText.setText("Upload: " + formatSpeed(uploadSpeed));

                networkHandler.postDelayed(this, 1000); // repeat every 1 second
            }
        }, 1000);
    }

    private String formatSpeed(long bytes) {
        double kb = bytes / 1024.0;
        double mb = kb / 1024.0;

        if (mb > 1) {
            return String.format("%.2f MB/s", mb);
        } else {
            return String.format("%.2f KB/s", kb);
        }
    }

    private void startCpuMonitoring() {
        cpuHandler = new Handler();
        cpuRunnable = new Runnable() {
            @Override
            public void run() {
                double cpuUsage = calculateCpuUsagePercent();
                cpuProgress.setProgress((int) cpuUsage);
                cpuPercentage.setText(decimalFormat.format(cpuUsage) + "%");

                String usageInfo = getCpuUsageAndCoreFreq();
                cpuUsageText.setText(usageInfo);

                cpuHandler.postDelayed(this, 2000);
            }
        };
        cpuHandler.post(cpuRunnable);
    }

    private double calculateCpuUsagePercent() {
        int cores = Runtime.getRuntime().availableProcessors();
        long totalCurrentFreq = 0;
        long totalMaxFreq = 0;

        for (int i = 0; i < cores; i++) {
            long curFreq = getCpuFrequency(i);
            long maxFreq = getMaxCpuFrequency(i);

            totalCurrentFreq += curFreq;
            totalMaxFreq += maxFreq;
        }

        if (totalMaxFreq > 0) {
            return (double) totalCurrentFreq / totalMaxFreq * 100;
        }
        return 0;
    }

    private String getCpuUsageAndCoreFreq() {
        int cores = Runtime.getRuntime().availableProcessors();
        StringBuilder result = new StringBuilder();

        long totalCurrentFreq = 0;
        long totalMaxFreq = 0;

        for (int i = 0; i < cores; i++) {
            long curFreq = getCpuFrequency(i);
            long maxFreq = getMaxCpuFrequency(i);

            totalCurrentFreq += curFreq;
            totalMaxFreq += maxFreq;
        }

        double cpuPercentage = 0;
        if (totalMaxFreq > 0) {
            cpuPercentage = (double) totalCurrentFreq / totalMaxFreq * 100;
        }

        result.append("CPU: ").append(decimalFormat.format(cpuPercentage)).append("%\n");
        result.append("Cores: ").append(cores).append("\n");

        for (int i = 0; i < cores; i++) {
            long curFreq = getCpuFrequency(i);
            result.append("Core ").append(i).append(": ").append(curFreq).append(" MHz\n");
        }

        return result.toString();
    }

    private long getCpuFrequency(int core) {
        long frequency = 0;
        try {
            File file = new File("/sys/devices/system/cpu/cpu" + core + "/cpufreq/scaling_cur_freq");
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line = reader.readLine();
                reader.close();
                if (line != null) {
                    frequency = Long.parseLong(line.trim()) / 1000; // MHz
                }
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }
        return frequency;
    }

    private long getMaxCpuFrequency(int core) {
        long frequency = 0;
        try {
            File file = new File("/sys/devices/system/cpu/cpu" + core + "/cpufreq/cpuinfo_max_freq");
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line = reader.readLine();
                reader.close();
                if (line != null) {
                    frequency = Long.parseLong(line.trim()) / 1000; // MHz
                }
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }
        return frequency;
    }



    private void updateDeviceUptime() {
        long uptimeMillis = SystemClock.elapsedRealtime();
        long seconds = uptimeMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds = seconds % 60;
        minutes = minutes % 60;

        String formatted = String.format("Device Uptime: %02d:%02d:%02d", hours, minutes, seconds);
        deviceUptime.setText(formatted);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Clean up CPU handler and runnable
        if (cpuHandler != null && cpuRunnable != null) {
            cpuHandler.removeCallbacks(cpuRunnable);
        }

        // Clean up uptime handler and runnable
        if (uptimeHandler != null && uptimeRunnable != null) {
            uptimeHandler.removeCallbacks(uptimeRunnable);
        }
    }

    public class AppUsageHelper {

        private Context context;
        private TextView appUsageTextView;

        public AppUsageHelper(Context context, TextView appUsageTextView) {
            this.context = context;
            this.appUsageTextView = appUsageTextView;
        }

        public void displayTopUsedApps() {
            if (!hasUsageStatsPermission()) {
                requestUsageStatsPermission();
                appUsageTextView.setText("Permission not granted.");
                return;
            }

            UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);

            long endTime = System.currentTimeMillis();
            long startTime = endTime - 1000 * 60 * 60 * 24; // Last 24 hours

            List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

            if (usageStatsList == null || usageStatsList.isEmpty()) {
                appUsageTextView.setText("No usage data available.");
                return;
            }

            // Filter out apps with 0 usage and sort
            List<UsageStats> filteredStats = new ArrayList<>();
            for (UsageStats usage : usageStatsList) {
                if (usage.getTotalTimeInForeground() > 0) {
                    filteredStats.add(usage);
                }
            }

            Collections.sort(filteredStats, (u1, u2) -> Long.compare(u2.getTotalTimeInForeground(), u1.getTotalTimeInForeground()));

            StringBuilder topAppsBuilder = new StringBuilder();
            topAppsBuilder.append("Top 2 Apps \n (Last 24hr):\n\n");

            int count = 0;
            for (UsageStats usageStats : filteredStats) {
                if (count >= 2) break;
                String packageName = usageStats.getPackageName();
                String appName = getAppNameFromPackage(packageName);
                long totalTime = usageStats.getTotalTimeInForeground();

                topAppsBuilder.append("• ").append(appName)
                        .append(" - ").append(formatMilliseconds(totalTime)).append("\n\n");

                count++;
            }

            if (count == 0) {
                appUsageTextView.setText("No app usage detected in last 24 hours.");
            } else {
                appUsageTextView.setText(topAppsBuilder.toString());
            }
        }

        private boolean hasUsageStatsPermission() {
            try {
                UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
                long currentTime = System.currentTimeMillis();
                List<UsageStats> stats = usageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY, currentTime - 1000 * 1000, currentTime);
                return stats != null && !stats.isEmpty();
            } catch (Exception e) {
                return false;
            }
        }

        private void requestUsageStatsPermission() {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            context.startActivity(intent);
        }

        private String getAppNameFromPackage(String packageName) {
            try {
                PackageManager pm = context.getPackageManager();
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                CharSequence label = pm.getApplicationLabel(appInfo);
                return label != null ? label.toString() : packageName;
            } catch (PackageManager.NameNotFoundException e) {
                // Fallback: manually map known packages
                switch (packageName) {
                    case "com.instagram.android":
                        return "Instagram";
                    case "com.whatsapp":
                        return "WhatsApp";
                    case "com.facebook.katana":
                        return "Facebook";
                    case "com.google.android.youtube":
                    case "com.youtube":
                        return "YouTube";
                    case "com.snapchat.android":
                        return "Snapchat";
                    case "com.twitter.android":
                        return "Twitter";
                    case "com.spotify.music":
                        return "Spotify";
                    case "com.netflix.mediaclient":
                        return "Netflix";
                    case "com.amazon.mShop.android.shopping":
                        return "Amazon";
                    case "com.google.android.gm":
                        return "Gmail";
                    case "com.google.android.apps.messaging":
                        return "Messages";
                    case "com.android.chrome":
                        return "Chrome";
                    case "org.mozilla.firefox":
                        return "Firefox";
                    case "com.microsoft.teams":
                        return "Microsoft Teams";
                    case "com.google.android.apps.photos":
                        return "Google Photos";
                    case "com.google.android.apps.docs":
                        return "Google Docs";
                    case "com.truecaller":
                        return "Truecaller";
                    case "com.phonepe.app":
                        return "PhonePe";
                    case "in.amazon.mShop.android.shopping":
                        return "Amazon India";
                    case "com.paytm.pgsdk":
                    case "net.one97.paytm":
                        return "Paytm";
                    default:
                        return packageName;
                }

            }
        }



        private String formatMilliseconds(long milliseconds) {
            long seconds = milliseconds / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;

            minutes = minutes % 60;
            seconds = seconds % 60;

            return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
        }
    }



}