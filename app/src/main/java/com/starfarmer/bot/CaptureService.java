package com.starfarmer.bot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
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
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.WindowManager;

import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

/** 前台服务：持有 MediaProjection，按需抓帧并转成灰度图。 */
public class CaptureService extends Service {

    public static final String CHANNEL_ID = "sf_capture";
    public static final int NOTIF_ID = 1001;
    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_DATA = "data";

    private static volatile CaptureService sInstance;

    public static CaptureService get() {
        return sInstance;
    }

    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HandlerThread thread;
    private Handler handler;
    private int screenW;
    private int screenH;
    private int density;
    private volatile boolean ready;

    @Override
    public void onCreate() {
        super.onCreate();
        thread = new HandlerThread("sf-capture");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundInternal();
        if (intent == null) return START_NOT_STICKY;
        int code = intent.getIntExtra(EXTRA_CODE, 0);
        Intent data = intent.getParcelableExtra(EXTRA_DATA);
        if (data != null && projection == null) {
            startCapture(code, data);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public boolean isReady() {
        return ready && reader != null;
    }

    public int screenWidth() {
        return screenW;
    }

    public int screenHeight() {
        return screenH;
    }

    private void startForegroundInternal() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "屏幕捕获", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);

        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("星际农夫运行中")
                .setContentText("正在读取屏幕以驱动自动战斗")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void startCapture(int code, Intent data) {
        try {
            MediaProjectionManager mpm =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(code, data);
            if (projection == null) {
                Logs.add("投屏授权返回空，请重新授权");
                stopSelf();
                return;
            }
            // Android 14+ 要求先注册回调再创建 VirtualDisplay
            projection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    ready = false;
                    Logs.add("投屏被系统停止");
                }
            }, handler);

            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            android.graphics.Rect b = wm.getCurrentWindowMetrics().getBounds();
            screenW = b.width();
            screenH = b.height();
            density = getResources().getDisplayMetrics().densityDpi;

            reader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 4);
            display = projection.createVirtualDisplay(
                    "sf-capture",
                    screenW, screenH, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.getSurface(), null, handler);

            ready = true;
            sInstance = this;
            Logs.add("投屏已就绪 " + screenW + "x" + screenH);
        } catch (Throwable t) {
            Logs.add("投屏启动失败: " + t);
            stopSelf();
        }
    }

    private Image acquire(long timeoutMs) {
        ImageReader r = reader;
        if (r == null) return null;
        long end = SystemClock.uptimeMillis() + Math.max(200, timeoutMs);
        while (SystemClock.uptimeMillis() < end) {
            if (!ready) return null;
            try {
                Image img = r.acquireLatestImage();
                if (img != null) return img;
            } catch (Throwable ignored) {
            }
            SystemClock.sleep(35);
        }
        return null;
    }

    /** 抓一帧并缩放成灰度图；scale 建议 2（1080x2400 → 540x1200）。 */
    public Gray grab(int scale) {
        Image img = acquire(900);
        if (img == null) return null;
        try {
            Image.Plane plane = img.getPlanes()[0];
            ByteBuffer buf = plane.getBuffer();
            buf.rewind();
            int rs = plane.getRowStride();
            int ps = plane.getPixelStride();
            int limit = buf.limit();
            int s = Math.max(1, scale);
            int gw = screenW / s;
            int gh = screenH / s;
            byte[] out = new byte[gw * gh];
            for (int y = 0; y < gh; y++) {
                int row = (y * s) * rs;
                int o = y * gw;
                for (int x = 0; x < gw; x++) {
                    int idx = row + (x * s) * ps;
                    if (idx + 2 >= limit) {
                        out[o + x] = (byte) 128;
                        continue;
                    }
                    int r = buf.get(idx) & 0xff;
                    int g = buf.get(idx + 1) & 0xff;
                    int bl = buf.get(idx + 2) & 0xff;
                    out[o + x] = (byte) ((r * 77 + g * 151 + bl * 28) >> 8);
                }
            }
            return new Gray(gw, gh, out);
        } catch (Throwable t) {
            Logs.add("抓帧失败: " + t);
            return null;
        } finally {
            try {
                img.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /** 抓一帧原色截图，用于校准/裁剪模板。 */
    public Bitmap grabBitmap(long timeoutMs) {
        Image img = acquire(timeoutMs);
        if (img == null) return null;
        try {
            Image.Plane plane = img.getPlanes()[0];
            ByteBuffer buf = plane.getBuffer();
            buf.rewind();
            int rs = plane.getRowStride();
            int ps = plane.getPixelStride();
            int limit = buf.limit();
            int[] px = new int[screenW * screenH];
            for (int y = 0; y < screenH; y++) {
                int row = y * rs;
                int o = y * screenW;
                for (int x = 0; x < screenW; x++) {
                    int idx = row + x * ps;
                    if (idx + 2 >= limit) {
                        px[o + x] = 0xff000000;
                        continue;
                    }
                    int r = buf.get(idx) & 0xff;
                    int g = buf.get(idx + 1) & 0xff;
                    int b = buf.get(idx + 2) & 0xff;
                    px[o + x] = 0xff000000 | (r << 16) | (g << 8) | b;
                }
            }
            return Bitmap.createBitmap(px, screenW, screenH, Bitmap.Config.ARGB_8888);
        } catch (Throwable t) {
            Logs.add("截图失败: " + t);
            return null;
        } finally {
            try {
                img.close();
            } catch (Throwable ignored) {
            }
        }
    }

    public String saveCalibrationShot() {
        Bitmap bmp = grabBitmap(2000);
        if (bmp == null) return null;
        try {
            File base = getExternalFilesDir(null);
            if (base == null) base = getFilesDir();
            File dir = new File(base, "calib");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File out = new File(dir, "shot_" + System.currentTimeMillis() + ".png");
            try (OutputStream os = new FileOutputStream(out)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
            bmp.recycle();
            return out.getAbsolutePath();
        } catch (Throwable t) {
            Logs.add("保存截图失败: " + t);
            return null;
        }
    }

    @Override
    public void onDestroy() {
        ready = false;
        if (sInstance == this) sInstance = null;
        try {
            if (display != null) display.release();
        } catch (Throwable ignored) {
        }
        try {
            if (projection != null) projection.stop();
        } catch (Throwable ignored) {
        }
        try {
            if (reader != null) reader.close();
        } catch (Throwable ignored) {
        }
        display = null;
        projection = null;
        reader = null;
        if (thread != null) thread.quitSafely();
        Logs.add("捕获服务已停止");
        super.onDestroy();
    }
}
