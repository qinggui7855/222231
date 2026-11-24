package com.starfarmer.bot;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 模板图（灰度）与匹配算法。
 * 模板按「参考分辨率 → 当前分辨率」的比例缩放，见 Matcher。
 */
public final class Template {

    public final String file;
    public final int w;
    public final int h;
    public final byte[] p;
    public final double mean;
    public final double var;

    private Template(String file, int w, int h, byte[] p) {
        this.file = file;
        this.w = w;
        this.h = h;
        this.p = p;
        double sum = 0;
        for (int i = 0; i < p.length; i++) sum += p[i] & 0xff;
        double m = sum / p.length;
        double acc = 0;
        for (int i = 0; i < p.length; i++) {
            double d = (p[i] & 0xff) - m;
            acc += d * d;
        }
        this.mean = m;
        this.var = acc;
    }

    public static final class Result {
        public final double score;
        public final int cx;
        public final int cy;

        Result(double score, int cx, int cy) {
            this.score = score;
            this.cx = cx;
            this.cy = cy;
        }
    }

    private static final Map<String, Template> CACHE = new HashMap<>();

    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    /** factor: 模板相对参考截图的缩放比例。文件缺失返回 null。 */
    public static Template load(Context ctx, String file, double factor) {
        String key = file + "|" + String.format(Locale.US, "%.3f", factor);
        synchronized (CACHE) {
            if (CACHE.containsKey(key)) return CACHE.get(key);
        }
        Template t = null;
        try (InputStream in = ctx.getAssets().open("templates/" + file)) {
            Bitmap bmp = BitmapFactory.decodeStream(in);
            if (bmp != null) {
                int tw = Math.max(4, (int) Math.round(bmp.getWidth() * factor));
                int th = Math.max(4, (int) Math.round(bmp.getHeight() * factor));
                if (tw != bmp.getWidth() || th != bmp.getHeight()) {
                    Bitmap scaled = Bitmap.createScaledBitmap(bmp, tw, th, true);
                    if (scaled != bmp) bmp.recycle();
                    bmp = scaled;
                }
                int[] px = new int[tw * th];
                bmp.getPixels(px, 0, tw, 0, 0, tw, th);
                byte[] g = new byte[tw * th];
                for (int i = 0; i < g.length; i++) {
                    int c = px[i];
                    int r = (c >> 16) & 0xff;
                    int gg = (c >> 8) & 0xff;
                    int b = c & 0xff;
                    g[i] = (byte) ((r * 77 + gg * 151 + b * 28) >> 8);
                }
                bmp.recycle();
                t = new Template(file, tw, th, g);
            } else {
                Logs.add("模板解码失败（文件可能损坏）: " + file);
            }
        } catch (Throwable e) {
            Logs.add("模板缺失或损坏: " + file + " → " + e);
        }
        synchronized (CACHE) {
            CACHE.put(key, t);
        }
        return t;
    }

    /** 诊断用：上一次搜索里，扫描了多少个位置、多少个通过了预筛 */
    public static volatile int lastPositions = 0;
    public static volatile int lastPass = 0;

    /** 在 roi（帧坐标）内搜索；总是返回找到的最高分位置，阈值由 Matcher 判断。 */
    public static Result search(Gray f, Integral integ, Template t,
                                int rx, int ry, int rw, int rh) {
        lastPositions = 0;
        lastPass = 0;
        if (t == null) return null;
        if (t.w > f.w || t.h > f.h) return null;

        int x0 = Math.max(0, rx);
        int y0 = Math.max(0, ry);
        int x1 = Math.min(f.w, rx + rw);
        int y1 = Math.min(f.h, ry + rh);
        if (x1 - t.w < x0 || y1 - t.h < y0) {
            x0 = 0;
            y0 = 0;
            x1 = f.w;
            y1 = f.h;
        }
        int maxX = x1 - t.w;
        int maxY = y1 - t.h;

        final int coarse = 4;
        // 注意量纲：t.var 是模板的方差总和，vI 是每像素方差，
        // 所以先换算成「每像素」再比，否则永远判为低对比度（这曾是本项目的头号 bug）。
        final double varPerPx = t.var / (double) (t.w * t.h);
        double best = -2;
        int bx = x0;
        int by = y0;
        for (int y = y0; y <= maxY; y += coarse) {
            for (int x = x0; x <= maxX; x += coarse) {
                lastPositions++;
                double mI = integ.mean(x, y, t.w, t.h);
                if (Math.abs(mI - t.mean) > 25) continue;
                double vI = integ.variance(x, y, t.w, t.h, mI);
                if (vI < varPerPx * 0.75) continue;
                lastPass++;
                double s = zncc(f, t, x, y);
                if (s > best) {
                    best = s;
                    bx = x;
                    by = y;
                }
            }
        }
        if (best < -1.5) return new Result(best, bx + t.w / 2, by + t.h / 2);

        int rx0 = Math.max(x0, bx - coarse);
        int ry0 = Math.max(y0, by - coarse);
        int rx1 = Math.min(maxX, bx + coarse);
        int ry1 = Math.min(maxY, by + coarse);
        for (int y = ry0; y <= ry1; y++) {
            for (int x = rx0; x <= rx1; x++) {
                double s = zncc(f, t, x, y);
                if (s > best) {
                    best = s;
                    bx = x;
                    by = y;
                }
            }
        }
        return new Result(best, bx + t.w / 2, by + t.h / 2);
    }

    private static double zncc(Gray f, Template t, int ox, int oy) {
        final int tw = t.w;
        final int th = t.h;
        final int n = tw * th;
        long sumI = 0;
        long sumI2 = 0;
        long sumIT = 0;
        for (int y = 0; y < th; y++) {
            int fRow = (oy + y) * f.w + ox;
            int tRow = y * tw;
            for (int x = 0; x < tw; x++) {
                int iv = f.p[fRow + x] & 0xff;
                int tv = t.p[tRow + x] & 0xff;
                sumI += iv;
                sumI2 += (long) iv * iv;
                sumIT += (long) iv * tv;
            }
        }
        double meanI = sumI / (double) n;
        double varI = sumI2 - (double) sumI * sumI / n;
        if (varI <= 1e-6 || t.var <= 1e-6) return -1;
        double cov = sumIT - (double) sumI * t.mean;
        return cov / Math.sqrt(varI * t.var);
    }
}
