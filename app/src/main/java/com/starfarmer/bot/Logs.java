package com.starfarmer.bot;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;

/** 运行日志：内存环形缓冲 + 转发给界面。 */
public final class Logs {
    public interface Sink {
        void onLog(String line);
    }

    private static final int CAP = 400;
    private static final Deque<String> BUF = new ArrayDeque<>();
    private static final SimpleDateFormat FMT = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
    private static Sink sink;

    private Logs() {
    }

    public static void setSink(Sink s) {
        sink = s;
    }

    public static synchronized void add(String msg) {
        String line = FMT.format(new Date()) + "  " + msg;
        BUF.addLast(line);
        while (BUF.size() > CAP) BUF.removeFirst();
        Log.i("StarFarmer", msg);
        if (sink != null) {
            try {
                sink.onLog(line);
            } catch (Throwable ignored) {
            }
        }
    }

    public static synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        for (String s : BUF) sb.append(s).append('\n');
        return sb.toString();
    }

    public static synchronized void clear() {
        BUF.clear();
    }
}
