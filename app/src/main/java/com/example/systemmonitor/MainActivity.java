package com.example.systemmonitor;

import android.net.TrafficStats;
import android.os.Handler;
import android.os.Bundle;
import android.os.Build;
import android.content.Context;
import android.app.ActivityManager;
import android.os.Looper;
import android.os.SystemClock;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private TextView rotatingHeader, totalMemoryText, availableMemoryText, totalStorageText, availableStorageText, batteryPercentageText, batteryHealthText, batteryCapacityText, batteryTemperatureText, cpuUsageText;
    private ProgressBar memoryProgressBar, batteryProgressBar, storageProgressBar;


    private TextView uploadSpeedText, downloadSpeedText;
    private long previousRxBytes = 0;
    private long previousTxBytes = 0;
    private Handler networkHandler = new Handler();

    private String[] rotatingMessages = {"Hi, %s", "Processor: %s", "Operating System: %s"};
    private Handler cpuHandler;
    private Runnable cpuRunnable;
    private DecimalFormat decimalFormat = new DecimalFormat("#0.0");

    private long lastTotalCpuTime = 0;
    private long lastIdleCpuTime = 0;

    private long prevIdleTime = 0;
    private long prevTotalTime = 0;

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
        cpuUsageText = findViewById(R.id.text_cpu_usage); // Add your CPU usage TextView

        // Start updating stats in real-time
        updateRotatingHeader();
        updateMemoryStats();
        updateStorageStats();
        updateBatteryStats();
        startNetworkSpeedMonitoring();
        startCpuMonitoring(); // Start CPU monitoring

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
                String usageInfo = getCpuUsageAndCoreFreq();
                cpuUsageText.setText(usageInfo); // Display the information in your TextView
                cpuHandler.postDelayed(this, 2000); // Update every 2 seconds
            }
        };
        cpuHandler.post(cpuRunnable);
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

        // Avoid division by zero
        double cpuPercentage = 0;
        if (totalMaxFreq > 0) {
            cpuPercentage = (double) totalCurrentFreq / totalMaxFreq * 100;
        }

        // First line: CPU usage
        result.append("CPU: ").append(decimalFormat.format(cpuPercentage)).append("%\n");

        // Second line: Number of cores
        result.append("Cores: ").append(cores).append("\n");

        // Next lines: Each core frequency (current only)
        for (int i = 0; i < cores; i++) {
            long curFreq = getCpuFrequency(i);
            result.append("Core ").append(i).append(": ").append(curFreq).append(" MHz\n");
        }

        return result.toString();
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
                    frequency = Long.parseLong(line.trim()) / 1000; // Convert to MHz
                }
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }
        return frequency;
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
                    try {
                        frequency = Long.parseLong(line.trim()) / 1000; // Convert to MHz
                    } catch (NumberFormatException e) {
                        // Handle the exception.  Maybe log it, maybe return 0,
                        //  depending on your needs.  Here, we'll return 0.
                        e.printStackTrace();
                        return 0;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return frequency;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cpuHandler != null && cpuRunnable != null) {
            cpuHandler.removeCallbacks(cpuRunnable); // Stop CPU monitoring when the activity is destroyed
        }
    }


}
