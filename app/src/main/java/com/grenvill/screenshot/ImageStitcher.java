package com.grenvill.screenshot;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Log;

public class ImageStitcher {
    private static final String TAG = "ImageStitcher";

    // 假设顶部和底部固定的状态栏/导航栏区域，需要避开比较。
    private static final int HEADER_OFFSET = 200;
    private static final int FOOTER_OFFSET = 200;

    public static Bitmap stitch(Bitmap bmp1, Bitmap bmp2) {
        if (bmp1 == null) return bmp2;
        if (bmp2 == null) return bmp1;

        int width = bmp1.getWidth();
        int h1 = bmp1.getHeight();
        int h2 = bmp2.getHeight();

        // 提取的特征行高度，不需要太高，200像素足以找到特征
        int compareHeight = 200; 

        if (h1 <= compareHeight + FOOTER_OFFSET || h2 <= compareHeight + HEADER_OFFSET) {
            return bmp1; // 图太小，无法拼接
        }

        // 从 bmp1 的底部提取特征块（避开导航栏）
        int startY1 = h1 - FOOTER_OFFSET - compareHeight;
        int[] pixels1 = new int[width * compareHeight];
        bmp1.getPixels(pixels1, 0, width, 0, startY1, width, compareHeight);

        // 在 bmp2 的上半部分寻找匹配块（避开状态栏）
        int maxSearchY = h2 - compareHeight - FOOTER_OFFSET;
        
        int bestMatchY = -1;
        long minDiff = Long.MAX_VALUE;

        int[] pixels2 = new int[width]; 
        
        // 步长为2或4可以大幅加快速度，且精度足够
        for (int y = HEADER_OFFSET; y < maxSearchY; y += 4) { 
            long diff = 0;
            // 每隔10行采样一次
            for (int r = 0; r < compareHeight; r += 10) { 
                bmp2.getPixels(pixels2, 0, width, 0, y + r, width, 1);
                // 每隔10列采样一次
                for (int x = 0; x < width; x += 10) {
                    int p1 = pixels1[r * width + x];
                    int p2 = pixels2[x];
                    
                    int r1 = (p1 >> 16) & 0xff;
                    int g1 = (p1 >> 8) & 0xff;
                    int b1 = p1 & 0xff;
                    int r2 = (p2 >> 16) & 0xff;
                    int g2 = (p2 >> 8) & 0xff;
                    int b2 = p2 & 0xff;
                    
                    diff += Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
                }
            }
            if (diff < minDiff) {
                minDiff = diff;
                bestMatchY = y;
            }
        }

        Log.d(TAG, "Best match found at Y=" + bestMatchY + " with diff=" + minDiff);

        // 如果找到了比较完美的匹配 (diff 需要根据屏幕分辨率调整阈值，这里假设 1000000 算合理范围)
        if (bestMatchY != -1 && minDiff < 1500000) { 
            // bmp1 保留上半部分直到特征块的末尾
            int crop1Bottom = startY1 + compareHeight;
            // bmp2 提供匹配点下方的新内容
            int crop2Top = bestMatchY + compareHeight;
            
            if (crop2Top < h2) {
                return combineBitmaps(bmp1, crop1Bottom, bmp2, crop2Top, h2);
            }
        }
        
        // 如果没有找到明显重叠（可能滑动到底部或者动态页面变化太大），返回原长图
        Log.w(TAG, "Failed to find a strong overlap. Stitching ignored for this frame.");
        return bmp1;
    }

    private static Bitmap combineBitmaps(Bitmap b1, int h1, Bitmap b2, int startY2, int endY2) {
        int w = b1.getWidth();
        int addedHeight = endY2 - startY2;
        if (addedHeight <= 0) return b1; 
        
        Bitmap combined = Bitmap.createBitmap(w, h1 + addedHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(combined);
        
        Bitmap p1 = Bitmap.createBitmap(b1, 0, 0, w, h1);
        canvas.drawBitmap(p1, 0, 0, null);
        
        Bitmap p2 = Bitmap.createBitmap(b2, 0, startY2, w, addedHeight);
        canvas.drawBitmap(p2, 0, h1, null);
        
        return combined;
    }
}
