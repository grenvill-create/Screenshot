package com.grenvill.screenshot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

public class CaptureService extends Service {

    private WindowManager windowManager;
    private View floatingView;
    private boolean isCapturing = false;

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, new Notification.Builder(this, "CaptureServiceChannel")
                .setContentTitle("长截屏服务运行中")
                .setContentText("准备截屏...")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build());

        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
            Intent data = intent.getParcelableExtra("data");
            if (resultCode == Activity.RESULT_OK && data != null) {
                mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                showFloatingWindow();
            }
        }
        return START_NOT_STICKY;
    }

    private void showFloatingWindow() {
        if (floatingView != null) return;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        floatingView = inflater.inflate(R.layout.floating_window, null);

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        Button btnAction = floatingView.findViewById(R.id.btn_floating_action);
        btnAction.setOnClickListener(v -> {
            if (!isCapturing) {
                startCaptureSequence();
                btnAction.setText("停止");
            } else {
                stopCaptureSequence();
                btnAction.setText("开始");
            }
            isCapturing = !isCapturing;
        });

        windowManager.addView(floatingView, params);
    }

    private void startCaptureSequence() {
        // TODO: Initialize ImageReader, VirtualDisplay, and notify AccessibilityService to scroll
        Intent broadcast = new Intent("com.grenvill.screenshot.ACTION_START_SCROLL");
        sendBroadcast(broadcast);
    }

    private void stopCaptureSequence() {
        // TODO: Stop ImageReader, stop VirtualDisplay, process stitched image, save to storage
        Intent broadcast = new Intent("com.grenvill.screenshot.ACTION_STOP_SCROLL");
        sendBroadcast(broadcast);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) {
            windowManager.removeView(floatingView);
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "CaptureServiceChannel",
                    "Capture Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
