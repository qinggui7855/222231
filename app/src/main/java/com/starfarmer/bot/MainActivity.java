package com.starfarmer.bot;

import android.Manifest;
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.util.Rational;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int REQ_PROJECTION = 1001;
    private static final int REQ_NOTIF = 1002;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private HandlerThread bgThread;
    private Handler bg;
    private TextView statusView;
    private TextView logView;
    private ImageView previewView;
    private ScrollView scroller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        bgThread = new HandlerThread("sf-ui-bg");
        bgThread.start();
        bg = new Handler(bgThread.getLooper());
        setContentView(buildUi());
        Logs.setSink(new Logs.Sink() {
            @Override
            public void onLog(final String line) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        appendLog(line);
                    }
                });
            }
        });
        appendLog(Logs.dump());
        requestNotifPermission();
        // 预览常驻刷新：即使 App 在小窗/后台，也能看到抓到的画面
        ui.postDelayed(previewTick, 1500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    /** 每 3 秒刷新一次「机器人看到的画面」。 */
    private final Runnable previewTick = new Runnable() {
        @Override
        public void run() {
            refreshPreview();
            ui.postDelayed(this, 3000);
        }
    };

    private void refreshPreview() {
        if (bg == null) return;
        bg.post(new Runnable() {
            @Override
            public void run() {
                final CaptureService cap = CaptureService.get();
                if (cap == null || !cap.isReady()) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            previewView.setImageDrawable(null);
                        }
                    });
                    return;
                }
                Gray g = cap.grab(4);
                if (g == null) return;
                final Bitmap bmp = grayToBitmap(g);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        previewView.setImageBitmap(bmp);
                    }
                });
            }
        });
    }

    private Bitmap grayToBitmap(Gray g) {
        int[] px = new int[g.w * g.h];
        for (int i = 0; i < px.length; i++) {
            int v = g.p[i] & 0xff;
            px[i] = 0xff000000 | (v << 16) | (v << 8) | v;
        }
        return Bitmap.createBitmap(px, g.w, g.h, Bitmap.Config.ARGB_8888);
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(250, 250, 252));

        TextView title = new TextView(this);
        title.setText("星际农夫 · 情系刀塔自动刷图");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTextColor(Color.rgb(20, 24, 40));
        root.addView(title);

        statusView = new TextView(this);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusView.setTextColor(Color.rgb(60, 66, 90));
        statusView.setPadding(0, dp(4), 0, dp(6));
        root.addView(statusView);

        // 实时预览：机器人实际看到的灰度画面
        previewView = new ImageView(this);
        previewView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewView.setBackgroundColor(Color.rgb(24, 24, 28));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(96));
        plp.setMargins(0, 0, 0, dp(6));
        root.addView(previewView, plp);

        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.addView(button("① 无障碍", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    appendLog("请在列表里找到「星际农夫 · 自动操作」并打开");
                } catch (Throwable e) {
                    appendLog("打不开无障碍设置: " + e);
                }
            }
        }), weight());
        r1.addView(button("② 投屏授权", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaProjectionManager mpm =
                        (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION);
            }
        }), weight());
        r1.addView(button("③ 开始", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startBot();
            }
        }), weight());
        r1.addView(button("■ 停止", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BotEngine.stop();
                ui.postDelayed(delayedStatus, 400);
            }
        }), weight());
        root.addView(r1);

        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.addView(button("校准截图", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final CaptureService cap = CaptureService.get();
                if (cap == null || !cap.isReady()) {
                    appendLog("还没授权投屏，先点 ②");
                    return;
                }
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String p = cap.saveCalibrationShot();
                        Logs.add(p == null ? "截图失败" : "已保存: " + p);
                    }
                }, "calib").start();
            }
        }), weight());
        r2.addView(button("查看流程", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Program p = Program.load(MainActivity.this);
                Logs.add("流程: " + p.name + " | 指令 " + p.ins.size() + " 条 | 标签 " + p.labels.size());
            }
        }), weight());
        r2.addView(button("自检", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selfCheck();
            }
        }), weight());
        r2.addView(button("小窗", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enterPip();
            }
        }), weight());
        r2.addView(button("清屏", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logView.setText("");
            }
        }), weight());
        r2.addView(button("帮助", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Logs.add("顺序：① 打开无障碍 → ② 允许投屏 → 切到游戏停在副本详情(图一) → ③ 开始");
            }
        }), weight());
        root.addView(r2);

        logView = new TextView(this);
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        logView.setTextColor(Color.rgb(30, 34, 44));
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setBackgroundColor(Color.rgb(234, 236, 243));
        logView.setPadding(dp(8), dp(6), dp(8), dp(6));
        logView.setMovementMethod(new ScrollingMovementMethod());
        logView.setGravity(Gravity.TOP | Gravity.START);
        LinearLayout.LayoutParams logLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(190));
        logLp.setMargins(0, dp(8), 0, 0);
        root.addView(logView, logLp);

        scroller = new ScrollView(this);
        scroller.addView(root);
        scroller.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                android.graphics.Insets in = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(in.left, in.top, in.right, in.bottom);
                return insets;
            }
        });
        return scroller;
    }

    private final Runnable delayedStatus = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
        }
    };

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        return lp;
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(4), dp(4), dp(4), dp(4));
        b.setOnClickListener(l);
        return b;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void appendLog(String line) {
        if (logView == null) return;
        logView.append(line + "\n");
        // 自动滚到最新一行：日志区是固定高度，不滚就只看得到开头
        logView.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (logView.getLayout() == null) return;
                    int bottom = logView.getLayout().getLineTop(logView.getLineCount());
                    int max = Math.max(0, bottom - logView.getHeight());
                    logView.scrollTo(0, max);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /** 一键自检：资产目录、每个模板能否载入、当前帧亮度、几个关键模板的最高分。 */
    private void selfCheck() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String[] files = getAssets().list("templates");
                    int n = files == null ? 0 : files.length;
                    Logs.add("=== 自检开始 ===");
                    Logs.add("assets/templates: " + n + " 个文件");
                    if (n > 0) {
                        for (String f : files) {
                            if (!f.endsWith(".png")) continue;
                            Template t = Template.load(MainActivity.this, f, 0.5);
                            Logs.add("  " + f + " → " + (t == null ? "载入失败" : (t.w + "x" + t.h)));
                        }
                    }
                    CaptureService cap = CaptureService.get();
                    if (cap == null || !cap.isReady()) {
                        Logs.add("投屏未就绪，跳过画面检查");
                        Logs.add("=== 自检结束 ===");
                        return;
                    }
                    Logs.add("5 秒后抓取屏幕，请立刻切到游戏画面…");
                    SystemClock.sleep(5000);
                    Gray g = cap.grab(2);
                    if (g == null) {
                        Logs.add("抓帧失败（null）");
                        Logs.add("=== 自检结束 ===");
                        return;
                    }
                    int min = 255, max = 0;
                    long sum = 0;
                    for (int i = 0; i < g.p.length; i++) {
                        int v = g.p[i] & 0xff;
                        if (v < min) min = v;
                        if (v > max) max = v;
                        sum += v;
                    }
                    long mean = sum / g.p.length;
                    Logs.add("画面 " + g.w + "x" + g.h + " 亮度 均值=" + mean
                            + " 最暗=" + min + " 最亮=" + max);
                    if (mean < 8 && max < 40) {
                        Logs.add("⚠ 抓到的是黑屏：投屏范围选错了（要选“整个屏幕”，或选“单个应用”并挑游戏）");
                    }
                    Integral ig = Integral.build(g);
                    double factor = g.w / 2510.0;
                    String[] probe = {"stage_play.png", "back_arrow.png", "campaign_btn.png",
                            "merchant_fig.png", "result_refresh.png"};
                    for (String f : probe) {
                        Template t = Template.load(MainActivity.this, f, factor);
                        if (t == null) {
                            Logs.add("  探测 " + f + " 模板载入失败");
                            continue;
                        }
                        Template.Result r = Template.search(g, ig, t, 0, 0, g.w, g.h);
                        if (r == null) {
                            Logs.add("  探测 " + f + " 无结果");
                        } else {
                            Logs.add(String.format(java.util.Locale.CHINA,
                                    "  探测 %s 最高分 %.3f @(%d,%d)", f, r.score, r.cx, r.cy));
                        }
                    }
                    Logs.add("=== 自检结束 ===");
                } catch (Throwable e) {
                    Logs.add("自检异常: " + e);
                }
            }
        }, "selfcheck").start();
        appendLog("自检已启动，结果看日志");
    }

    /** 缩成系统画中画小窗，可以拖到屏幕角落，不挡游戏画面。 */
    private void enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            appendLog("系统版本太低，不支持小窗");
            return;
        }
        try {
            PictureInPictureParams.Builder b = new PictureInPictureParams.Builder();
            b.setAspectRatio(new Rational(16, 9));
            enterPictureInPictureMode(b.build());
            appendLog("已缩成小窗，可拖动到角落");
        } catch (Throwable e) {
            appendLog("小窗失败: " + e);
        }
    }

    private void startBot() {
        CaptureService cap = CaptureService.get();
        if (cap == null || !cap.isReady()) {
            appendLog("还没授权投屏，先点 ②");
            return;
        }
        if (BotAccessibilityService.get() == null) {
            appendLog("还没授权无障碍，先点 ①");
            return;
        }
        BotEngine.start(this);
        ui.postDelayed(delayedStatus, 800);
    }

    private void refreshStatus() {
        boolean acc = BotAccessibilityService.get() != null;
        CaptureService cap = CaptureService.get();
        boolean proj = cap != null && cap.isReady();
        String s = (acc ? "无障碍 ✓" : "无障碍 ✗")
                + "   " + (proj ? "投屏 ✓" : "投屏 ✗")
                + "   " + (BotEngine.isRunning() ? "引擎 ▶ 运行中" : "引擎 ■ 停止");
        if (proj) s += "   " + cap.screenWidth() + "x" + cap.screenHeight();
        statusView.setText(s);
    }

    private void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PROJECTION) return;
        if (resultCode != RESULT_OK || data == null) {
            appendLog("投屏授权被取消");
            return;
        }
        Intent svc = new Intent(this, CaptureService.class);
        svc.putExtra(CaptureService.EXTRA_CODE, resultCode);
        svc.putExtra(CaptureService.EXTRA_DATA, data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
        appendLog("已请求启动捕获服务…");
        ui.postDelayed(delayedStatus, 1200);
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(previewTick);
        Logs.setSink(null);
        if (bgThread != null) bgThread.quitSafely();
        super.onDestroy();
    }
}
