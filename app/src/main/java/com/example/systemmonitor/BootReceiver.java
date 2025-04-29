
package com.example.systemmonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

    public class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(context, "Device Rebooted", Toast.LENGTH_SHORT).show();
            // You can start a service or perform another action here
        }
    }


