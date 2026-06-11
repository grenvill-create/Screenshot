package com.grenvill.screenshot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;

public class AutoScrollAccessibilityService extends AccessibilityService {

    private boolean isScrolling = false;
    private Handler handler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver controlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.grenvill.screenshot.ACTION_START_SCROLL".equals(intent.getAction())) {
                isScrolling = true;
                performScroll();
            } else if ("com.grenvill.screenshot.ACTION_STOP_SCROLL".equals(intent.getAction())) {
                isScrolling = false;
                handler.removeCallbacks(scrollRunnable);
            }
        }
    };

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.grenvill.screenshot.ACTION_START_SCROLL");
        filter.addAction("com.grenvill.screenshot.ACTION_STOP_SCROLL");
        registerReceiver(controlReceiver, filter);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not actively listening to events for now, we trigger gestures manually via broadcast
    }

    @Override
    public void onInterrupt() {
        isScrolling = false;
    }

    private Runnable scrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (isScrolling) {
                dispatchSwipeUp();
                // Scroll every 2 seconds to allow screen capture and processing
                handler.postDelayed(this, 2000);
            }
        }
    };

    private void performScroll() {
        handler.post(scrollRunnable);
    }

    private void dispatchSwipeUp() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;

        Path swipePath = new Path();
        swipePath.moveTo(width / 2f, height * 0.7f); // Start near the bottom
        swipePath.lineTo(width / 2f, height * 0.3f); // End near the top

        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 500));
        
        dispatchGesture(gestureBuilder.build(), null, null);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        unregisterReceiver(controlReceiver);
        return super.onUnbind(intent);
    }
}
