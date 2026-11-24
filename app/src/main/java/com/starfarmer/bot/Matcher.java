package com.starfarmer.bot;

import android.content.Context;

import java.util.List;

/**
 * 把模板匹配 + 分辨率换算封装起来：
 * 模板是在「参考分辨率」下裁的，运行时按 宽度比 与 高度比 两个候选比例各试一次。
 */
public final class Matcher {

    private final Context ctx;
    private final int scale;
    private final int refW;
    private final int refH;

    public Matcher(Context ctx, int scale, int refW, int refH) {
        this.ctx = ctx;
        this.scale = Math.max(1, scale);
        this.refW = Math.max(1, refW);
        this.refH = Math.max(1, refH);
    }

    /**
     * @param files 任一命中即可
     * @param roi   帧坐标；null 表示全屏
     */
    public Template.Result find(Gray f, Integral integ, List<String> files, int[] roi, double thr) {
        if (files == null || files.isEmpty()) return null;
        int[] r = roi == null ? new int[]{0, 0, f.w, f.h} : roi;
        double fw = f.w * (double) scale / refW;
        double fh = f.h * (double) scale / refH;

        Template.Result best = null;
        for (String file : files) {
            Template.Result a = one(f, integ, file, r, thr, fw);
            if (a != null && (best == null || a.score > best.score)) best = a;
            if (Math.abs(fh - fw) > 0.008) {
                Template.Result b = one(f, integ, file, r, thr, fh);
                if (b != null && (best == null || b.score > best.score)) best = b;
            }
        }
        return best;
    }

    private Template.Result one(Gray f, Integral integ, String file, int[] r, double thr, double factor) {
        if (factor <= 0.05 || factor > 8) return null;
        Template t = Template.load(ctx, file, factor);
        if (t == null) return null;
        return Template.search(f, integ, t, r[0], r[1], r[2], r[3], thr);
    }
}
