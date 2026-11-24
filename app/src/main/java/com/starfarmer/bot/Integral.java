package com.starfarmer.bot;

/** 积分图，用于 O(1) 求任意矩形窗口的均值/方差。 */
public final class Integral {
    private final int w;
    private final int h;
    private final long[] s1;
    private final long[] s2;

    private Integral(int w, int h, long[] s1, long[] s2) {
        this.w = w;
        this.h = h;
        this.s1 = s1;
        this.s2 = s2;
    }

    public static Integral build(Gray g) {
        int w = g.w;
        int h = g.h;
        int sw = w + 1;
        long[] s1 = new long[sw * (h + 1)];
        long[] s2 = new long[sw * (h + 1)];
        for (int y = 0; y < h; y++) {
            long rowSum = 0;
            long rowSq = 0;
            int cur = (y + 1) * sw;
            int prev = y * sw;
            int base = y * w;
            for (int x = 0; x < w; x++) {
                int v = g.p[base + x] & 0xff;
                rowSum += v;
                rowSq += (long) v * v;
                s1[cur + x + 1] = s1[prev + x + 1] + rowSum;
                s2[cur + x + 1] = s2[prev + x + 1] + rowSq;
            }
        }
        return new Integral(w, h, s1, s2);
    }

    private long rect(long[] s, int x, int y, int ww, int hh) {
        int sw = w + 1;
        int x1 = x + ww;
        int y1 = y + hh;
        return s[y1 * sw + x1] - s[y * sw + x1] - s[y1 * sw + x] + s[y * sw + x];
    }

    public double mean(int x, int y, int ww, int hh) {
        long sum = rect(s1, x, y, ww, hh);
        return sum / (double) (ww * hh);
    }

    public double variance(int x, int y, int ww, int hh, double mean) {
        double sum2 = rect(s2, x, y, ww, hh);
        double n = ww * hh;
        double v = sum2 / n - mean * mean;
        return v < 0 ? 0 : v;
    }
}
