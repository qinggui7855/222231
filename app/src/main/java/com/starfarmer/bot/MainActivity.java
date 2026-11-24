package com.starfarmer.bot;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int REQ_PROJECTION = 1001;
    private static final int REQ_NOTIF = 1002;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView statusView;
    private TextView logView;
    private ScrollView scroller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
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
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150));
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
        if (scroller != null) {
            scroller.post(new Runnable() {
                @Override
                public void run() {
                    scroller.fullScroll(View.FOCUS_DOWN);
                }
            });
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
        Logs.setSink(null);
        super.onDestroy();
    }
}
