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

    private boolean isCapturingSession = false;

    private final BroadcastReceiver controlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.grenvill.screenshot.ACTION_PERFORM_SCROLL".equals(intent.getAction())) {
                isCapturingSession = true;
                dispatchSwipeUp();
            } else if ("com.grenvill.screenshot.ACTION_STOP_SCROLL".equals(intent.getAction())) {
                isCapturingSession = false;
            }
        }
    };

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.grenvill.screenshot.ACTION_PERFORM_SCROLL");
        filter.addAction("com.grenvill.screenshot.ACTION_STOP_SCROLL");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(controlReceiver, filter);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    private void dispatchSwipeUp() {
        if (!isCapturingSession) return;

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;

        Path swipePath = new Path();
        swipePath.moveTo(width / 2f, height * 0.7f); // 从底部 70% 处开始
        swipePath.lineTo(width / 2f, height * 0.3f); // 滑动到顶部 30% 处

        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 800)); // 滑动耗时 800ms
        
        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                // 延迟 500ms，等待页面惯性滚动动画完全结束，避免截出模糊画面
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (isCapturingSession) {
                        Intent broadcast = new Intent("com.grenvill.screenshot.ACTION_SCROLL_FINISHED");
                        sendBroadcast(broadcast);
                    }
                }, 500);
            }
        }, null);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        unregisterReceiver(controlReceiver);
        return super.onUnbind(intent);
    }
}
