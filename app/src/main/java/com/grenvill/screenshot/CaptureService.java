package com.grenvill.screenshot;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import java.nio.ByteBuffer;

public class CaptureService extends Service {

    private WindowManager windowManager;
    private View floatingView;
    private boolean isCapturingSession = false;

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    
    private Bitmap currentLongBitmap = null;
    private Handler handler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver scrollReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.grenvill.screenshot.ACTION_SCROLL_FINISHED".equals(intent.getAction())) {
                if (isCapturingSession) {
                    captureAndStitchFrame();
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = new Notification.Builder(this, "CaptureServiceChannel")
                .setContentTitle("长截屏服务运行中")
                .setContentText("点击悬浮按钮开始截屏")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build();
                
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1, notification);
        }

        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        
        IntentFilter filter = new IntentFilter("com.grenvill.screenshot.ACTION_SCROLL_FINISHED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scrollReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(scrollReceiver, filter);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
            Intent data = intent.getParcelableExtra("data");
            if (resultCode == Activity.RESULT_OK && data != null) {
                mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                setupImageReader();
                showFloatingWindow();
            }
        }
        return START_NOT_STICKY;
    }

    @SuppressLint("WrongConstant")
    private void setupImageReader() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;
        
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);
    }

    private void showFloatingWindow() {
        if (floatingView != null) return;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        floatingView = inflater.inflate(R.layout.floating_window, null);

        int layoutFlag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        Button btnAction = floatingView.findViewById(R.id.btn_floating_action);
        btnAction.setOnClickListener(v -> {
            if (!isCapturingSession) {
                btnAction.setText("停止...");
                btnAction.setEnabled(false);
                startCaptureSequence();
                
                // 防抖延迟，防止误触
                handler.postDelayed(() -> btnAction.setEnabled(true), 1000);
            } else {
                stopCaptureSequence();
                btnAction.setText("开始");
            }
        });

        windowManager.addView(floatingView, params);
    }

    private void startCaptureSequence() {
        isCapturingSession = true;
        currentLongBitmap = null;
        Toast.makeText(this, "正在开始长截屏，请勿操作屏幕...", Toast.LENGTH_SHORT).show();
        
        // 给 ImageReader 缓冲一下的时间，然后开始第一次截屏
        handler.postDelayed(this::captureAndStitchFrame, 500);
    }
    
    private void captureAndStitchFrame() {
        if (!isCapturingSession) return;
        
        Bitmap newFrame = getLatestBitmap();
        if (newFrame != null) {
            if (currentLongBitmap == null) {
                currentLongBitmap = newFrame;
            } else {
                // 调用手写的像素比对拼接算法
                currentLongBitmap = ImageStitcher.stitch(currentLongBitmap, newFrame);
            }
            
            // 通知无障碍服务进行滑动（向下滚动页面）
            Intent broadcast = new Intent("com.grenvill.screenshot.ACTION_PERFORM_SCROLL");
            sendBroadcast(broadcast);
        } else {
            // 如果 ImageReader 还没准备好帧，稍微延迟重试
            handler.postDelayed(this::captureAndStitchFrame, 200);
        }
    }

    private Bitmap getLatestBitmap() {
        Image image = imageReader.acquireLatestImage();
        if (image == null) return null;
        
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * image.getWidth();
        
        Bitmap bitmap = Bitmap.createBitmap(image.getWidth() + rowPadding / pixelStride,
                image.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        image.close();
        
        // 剪裁掉无效的 Padding 区域
        if (rowPadding > 0) {
            return Bitmap.createBitmap(bitmap, 0, 0, image.getWidth(), image.getHeight());
        }
        return bitmap;
    }

    private void stopCaptureSequence() {
        isCapturingSession = false;
        Intent broadcast = new Intent("com.grenvill.screenshot.ACTION_STOP_SCROLL");
        sendBroadcast(broadcast);
        
        Toast.makeText(this, "停止截图，正在保存长图...", Toast.LENGTH_SHORT).show();
        
        if (currentLongBitmap != null) {
            // 保存最终拼接的长图到系统相册
            BitmapUtils.saveBitmapToGallery(this, currentLongBitmap);
            currentLongBitmap = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) windowManager.removeView(floatingView);
        if (virtualDisplay != null) virtualDisplay.release();
        if (imageReader != null) imageReader.close();
        if (mediaProjection != null) mediaProjection.stop();
        unregisterReceiver(scrollReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "CaptureServiceChannel", "Capture Service Channel", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(serviceChannel);
        }
    }
}
