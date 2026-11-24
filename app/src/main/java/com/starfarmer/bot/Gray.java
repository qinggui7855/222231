package com.starfarmer.bot;

/** 一帧灰度图（已按比例缩小）。 */
public final class Gray {
    public final int w;
    public final int h;
    public final byte[] p;

    public Gray(int w, int h, byte[] p) {
        this.w = w;
        this.h = h;
        this.p = p;
    }

    public int at(int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return 0;
        return p[y * w + x] & 0xff;
    }
}
