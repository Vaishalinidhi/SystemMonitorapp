package com.example.systemmonitor;

import android.util.Log;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

public class AppLifecycleObserver implements LifecycleEventObserver {

    private TextView lifecycleTextView;

    // Constructor to pass the lifecycle TextView
    public AppLifecycleObserver(TextView lifecycleTextView) {
        this.lifecycleTextView = lifecycleTextView;
    }

    @Override
    public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
        String lifecycleStatus = "";

        // Switch case to handle different lifecycle events
        switch (event) {
            case ON_CREATE:
                lifecycleStatus = "App created";
                break;
            case ON_START:
                lifecycleStatus = "App started";
                break;
            case ON_RESUME:
                lifecycleStatus = "App resumed";
                break;
            case ON_PAUSE:
                lifecycleStatus = "App paused";
                break;
            case ON_STOP:
                lifecycleStatus = "App stopped";
                break;
            case ON_DESTROY:
                lifecycleStatus = "App destroyed";
                break;
            default:
                break;
        }

        // Update the TextView with the lifecycle status
        if (lifecycleTextView != null) {
            lifecycleTextView.setText("Lifecycle Status: " + lifecycleStatus);
        }

        // Log the lifecycle state for debugging purposes
        Log.d("Lifecycle", lifecycleStatus);
    }
}
