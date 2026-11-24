package com.starfarmer.bot;

import android.content.Context;
import android.os.SystemClock;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 解释执行 program.json：抓帧 → 匹配 → 点击 / 滑动 / 跳转。
 * 所有屏幕交互都通过无障碍服务完成，不读窗口内容。
 */
public final class BotEngine {

    private static volatile boolean running;
    private static volatile Thread thread;

    private static final long FRAME_TTL_MS = 120;

    private final Context ctx;
    private CaptureService cap;
    private BotAccessibilityService acc;
    private Program prog;
    private Matcher matcher;
    private Map<String, Integer> vars = new HashMap<>();

    private Gray cachedFrame;
    private Integral cachedIntegral;
    private long cachedAt;
    private int miss;

    private BotEngine(Context ctx) {
        this.ctx = ctx;
    }

    public static boolean isRunning() {
        return running;
    }

    public static void start(Context ctx) {
        if (running) {
            Logs.add("已在运行中");
            return;
        }
        running = true;
        final Context app = ctx.getApplicationContext();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    new BotEngine(app).loop();
                } catch (Throwable e) {
                    Logs.add("引擎异常退出: " + e);
                } finally {
                    running = false;
                    thread = null;
                }
            }
        }, "sf-bot");
        t.setDaemon(true);
        thread = t;
        t.start();
        Logs.add("引擎已启动");
    }

    public static void stop() {
        if (!running) {
            Logs.add("引擎未在运行");
            return;
        }
        running = false;
        Logs.add("收到停止指令");
    }

    // ---------------- 主循环 ----------------

    private void loop() {
        for (int i = 0; i < 60 && running; i++) {
            acc = BotAccessibilityService.get();
            cap = CaptureService.get();
            if (acc != null && cap != null && cap.isReady()) break;
            SystemClock.sleep(200);
        }
        if (acc == null) {
            Logs.add("无障碍服务未连接，先授权再启动");
            return;
        }
        if (cap == null || !cap.isReady()) {
            Logs.add("屏幕捕获未就绪，先授权投屏再启动");
            return;
        }

        prog = Program.load(ctx);
        if (prog.ins.isEmpty()) {
            Logs.add("program.json 里没有指令");
            return;
        }
        // 启动时把模板清单打出来：目录空 = APK 里没打进图，一眼就能看出
        int tplCount = 0;
        try {
            String[] tpl = ctx.getAssets().list("templates");
            tplCount = tpl == null ? 0 : tpl.length;
            if (tplCount == 0) {
                Logs.add("⚠ assets/templates 是空的！模板图没打进 APK，脚本无法工作");
            } else {
                Logs.add("模板目录: " + tplCount + " 个文件（如 " + tpl[0] + "）");
            }
        } catch (Throwable e) {
            Logs.add("⚠ 读不到 assets/templates: " + e);
        }
        if (tplCount == 0) {
            Logs.add("已停止：请确认仓库里 app/src/main/assets/templates 有 15 个 png");
            return;
        }
        matcher = new Matcher(ctx, prog.scale, prog.refW, prog.refH);
        vars = new HashMap<>(prog.vars);

        Gray probe = cap.grab(prog.scale);
        if (probe != null) {
            int min = 255, max = 0;
            long sum = 0;
            for (int i = 0; i < probe.p.length; i++) {
                int v = probe.p[i] & 0xff;
                if (v < min) min = v;
                if (v > max) max = v;
                sum += v;
            }
            long mean = sum / probe.p.length;
            Logs.add(String.format(Locale.CHINA, "画面 %dx%d / 屏幕 %dx%d / 模板缩放 %.3f / 亮度均值 %d",
                    probe.w, probe.h, cap.screenWidth(), cap.screenHeight(),
                    probe.w / (double) prog.refW, mean));
            if (mean < 8 && max < 40) {
                Logs.add("⚠ 抓到的是黑屏！投屏范围选错了（选“整个屏幕”，或选“单个应用”并挑游戏）");
            }
        }

        int pc = 0;
        long startAt = System.currentTimeMillis();
        int guard = 0;
        Logs.add("▶ 开始执行");
        while (running && pc >= 0 && pc < prog.ins.size()) {
            if (System.currentTimeMillis() - startAt > prog.maxRunMs) {
                Logs.add("达到最大运行时长，停止");
                break;
            }
            Program.Ins in = prog.ins.get(pc);
            int next = pc + 1;
            try {
                next = exec(in, next);
            } catch (Throwable e) {
                Logs.add("指令异常[" + in.op + "]: " + e);
            }
            pc = next;
            if (++guard % 400 == 0) SystemClock.sleep(40);
        }
        Logs.add("■ 脚本结束");
    }

    private int exec(Program.Ins in, int next) {
        String op = in.op;

        if ("label".equals(op)) return next;
        if ("goto".equals(op)) return jump(in.go, next);
        if ("log".equals(op)) {
            Logs.add(in.msg);
            return next;
        }
        if ("stop".equals(op)) {
            Logs.add(in.msg.isEmpty() ? "程序主动停止" : in.msg);
            running = false;
            return next;
        }
        if ("sleep".equals(op)) {
            SystemClock.sleep(Math.max(1, in.ms));
            invalidate();
            return next;
        }
        if ("set".equals(op)) {
            vars.put(in.var, in.val);
            return next;
        }
        if ("add".equals(op)) {
            vars.put(in.var, get(in.var) + in.val);
            return next;
        }
        if ("iflt".equals(op)) return get(in.var) < in.val ? jump(in.go, next) : next;
        if ("ifgt".equals(op)) return get(in.var) > in.val ? jump(in.go, next) : next;

        if ("back".equals(op)) {
            acc.back();
            SystemClock.sleep(350);
            invalidate();
            return next;
        }
        if ("swipe".equals(op)) {
            int w = cap.screenWidth();
            int h = cap.screenHeight();
            long ms = in.ms > 0 ? in.ms : 350;
            acc.swipe((int) (in.x * w), (int) (in.y * h),
                    (int) (in.x2 * w), (int) (in.y2 * h), ms);
            SystemClock.sleep(ms + 350);
            invalidate();
            return next;
        }
        if ("tapxy".equals(op)) {
            int w = cap.screenWidth();
            int h = cap.screenHeight();
            acc.tap((int) (in.x * w), (int) (in.y * h));
            SystemClock.sleep(in.ms > 0 ? in.ms : 260);
            invalidate();
            return next;
        }

        if ("if".equals(op)) {
            Template.Result r = find(in, in.timeoutMs);
            return r != null ? jump(in.go, next) : next;
        }
        if ("ifnot".equals(op)) {
            Template.Result r = find(in, in.timeoutMs);
            return r == null ? jump(in.go, next) : next;
        }
        if ("wait".equals(op)) {
            Template.Result r = find(in, in.timeoutMs);
            return r != null ? next : jump(in.go, next);
        }
        if ("tap".equals(op)) {
            Template.Result r = find(in, in.timeoutMs);
            if (r == null) return jump(in.go, next);
            int px = r.cx * prog.scale;
            int py = r.cy * prog.scale;
            Logs.add("👆 " + in.t.get(0) + String.format(Locale.CHINA, " (%.3f)", r.score)
                    + " @(" + px + "," + py + ")");
            acc.tap(px, py);
            SystemClock.sleep(240);
            invalidate();
            return next;
        }
        Logs.add("未知指令: " + op);
        return next;
    }

    // ---------------- 工具 ----------------

    /** 记录一次未命中，每 40 次打一条诊断（含全场最高分与画面亮度），方便排查"什么都不认"。 */
    private void noteMiss(Gray g) {
        miss++;
        if (miss % 40 == 0) {
            long sum = 0;
            int cnt = 0;
            for (int i = 0; i < g.p.length; i += 8) {
                sum += g.p[i] & 0xff;
                cnt++;
            }
            Logs.add(String.format(Locale.CHINA,
                    "… 没匹配上（画面 %dx%d 亮度 %d，候选 %d/%d，最高分 %.3f %s）",
                    g.w, g.h, sum / Math.max(1, cnt),
                    Template.lastPass, Template.lastPositions, matcher.lastScore,
                    matcher.lastFile.isEmpty() ? "无" : matcher.lastFile));
        }
    }

    private int jump(String label, int fallback) {
        if (label == null || label.isEmpty()) return fallback;
        int idx = prog.labelIndex(label);
        if (idx < 0) {
            Logs.add("跳转目标不存在: " + label + "，停止");
            running = false;
            return fallback;
        }
        return idx;
    }

    private int get(String k) {
        Integer v = vars.get(k);
        return v == null ? 0 : v;
    }

    private void invalidate() {
        cachedFrame = null;
        cachedIntegral = null;
        cachedAt = 0;
    }

    private Gray frame(boolean fresh) {
        long now = SystemClock.uptimeMillis();
        if (!fresh && cachedFrame != null && now - cachedAt < FRAME_TTL_MS) return cachedFrame;
        Gray g = cap.grab(prog.scale);
        if (g == null) {
            invalidate();
            return null;
        }
        cachedFrame = g;
        cachedIntegral = Integral.build(g);
        cachedAt = now;
        return g;
    }

    /** timeoutMs=0 时只看当前帧；>0 时轮询到超时。 */
    private Template.Result find(Program.Ins in, long timeoutMs) {
        long end = SystemClock.uptimeMillis() + Math.max(0, timeoutMs);
        int tries = 0;
        while (running) {
            Gray g = frame(timeoutMs <= 0);
            if (g != null) {
                int[] roi = null;
                if (in.roi != null) {
                    roi = new int[]{
                            (int) (in.roi[0] * g.w),
                            (int) (in.roi[1] * g.h),
                            (int) (in.roi[2] * g.w),
                            (int) (in.roi[3] * g.h)
                    };
                }
                Template.Result r = matcher.find(g, cachedIntegral, in.t, roi, in.thr);
                if (r != null) {
                    miss = 0;
                    return r;
                }
                noteMiss(g);
            }
            if (timeoutMs <= 0) return null;
            if (SystemClock.uptimeMillis() >= end) return null;
            if (++tries % 20 == 0 && !in.t.isEmpty()) {
                Logs.add("… 仍在等待 " + in.t.get(0));
            }
            SystemClock.sleep(160);
        }
        return null;
    }
}
