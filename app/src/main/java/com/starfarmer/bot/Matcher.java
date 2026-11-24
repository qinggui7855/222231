package com.starfarmer.bot;

import android.content.Context;

import java.util.List;

/**
 * 把模板匹配 + 分辨率换算封装起来。
 *
 * 模板是在「参考截图」refW x refH 下裁的；帧是按 scale 缩小过的灰度图。
 * 所以模板要缩放 (帧宽 / refW) 倍，才和画面同尺度。
 * 宽高比不一致时（不同机型），再用 高度比 试一次。
 */
public final class Matcher {

    private final Context ctx;
    private final int refW;
    private final int refH;

    /** 最近一次搜索里全场的最高分（不管有没有过阈值），用于排错。 */
    public volatile double lastScore = -1;
    public volatile String lastFile = "";

    public Matcher(Context ctx, int scale, int refW, int refH) {
        this.ctx = ctx;
        this.refW = Math.max(1, refW);
        this.refH = Math.max(1, refH);
    }

    public Template.Result find(Gray f, Integral integ, List<String> files, int[] roi, double thr) {
        if (files == null || files.isEmpty()) return null;
        int[] r = roi == null ? new int[]{0, 0, f.w, f.h} : roi;

        double fw = f.w / (double) refW;
        double fh = f.h / (double) refH;

        Template.Result best = null;
        double bestAny = -2;   // -2 = 一个候选都没评估过（不再是 -1，便于区分）
        String bestAnyFile = "";

        for (String file : files) {
            Template.Result a = one(f, integ, file, r, fw);
            if (a != null) {
                if (a.score > bestAny) {
                    bestAny = a.score;
                    bestAnyFile = file;
                }
                if (best == null || a.score > best.score) best = a;
            }
            if (Math.abs(fh - fw) > 0.008) {
                Template.Result b = one(f, integ, file, r, fh);
                if (b != null) {
                    if (b.score > bestAny) {
                        bestAny = b.score;
                        bestAnyFile = file;
                    }
                    if (best == null || b.score > best.score) best = b;
                }
            }
        }
        lastScore = bestAny;
        lastFile = bestAnyFile;
        if (best == null || best.score < thr) return null;
        return best;
    }

    private Template.Result one(Gray f, Integral integ, String file, int[] r, double factor) {
        if (factor <= 0.02 || factor > 8) return null;
        Template t = Template.load(ctx, file, factor);
        if (t == null) return null;
        return Template.search(f, integ, t, r[0], r[1], r[2], r[3]);
    }
}
