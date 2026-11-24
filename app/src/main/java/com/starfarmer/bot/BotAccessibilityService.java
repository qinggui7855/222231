package com.starfarmer.bot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

/** 只负责「动手」：点击、滑动、返回、回桌面。不读窗口内容。 */
public class BotAccessibilityService extends AccessibilityService {

    private static volatile BotAccessibilityService sInstance;

    public static BotAccessibilityService get() {
        return sInstance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        Logs.add("无障碍服务已连接");
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        Logs.add("无障碍服务已断开");
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不需要事件
    }

    @Override
    public void onInterrupt() {
    }

    public boolean tap(int x, int y) {
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.StrokeDescription sd =
                new GestureDescription.StrokeDescription(p, 0, 70);
        return dispatchGesture(new GestureDescription.Builder().addStroke(sd).build(), null, null);
    }

    public boolean longTap(int x, int y, long ms) {
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.StrokeDescription sd =
                new GestureDescription.StrokeDescription(p, 0, Math.max(100, ms));
        return dispatchGesture(new GestureDescription.Builder().addStroke(sd).build(), null, null);
    }

    public boolean swipe(int x1, int y1, int x2, int y2, long ms) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        GestureDescription.StrokeDescription sd =
                new GestureDescription.StrokeDescription(p, 0, Math.max(80, ms));
        return dispatchGesture(new GestureDescription.Builder().addStroke(sd).build(), null, null);
    }

    public boolean back() {
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public boolean home() {
        return performGlobalAction(GLOBAL_ACTION_HOME);
    }
}
