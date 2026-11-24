package com.starfarmer.bot;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** assets/program.json 的模型：一串可 goto 的指令。 */
public final class Program {

    public String name = "未命名";
    public int refW = 2510;
    public int refH = 1156;
    public int scale = 2;
    public long maxRunMs = 4 * 60 * 60 * 1000L;

    public final List<Ins> ins = new ArrayList<>();
    public final Map<String, Integer> labels = new HashMap<>();
    public final Map<String, Integer> vars = new HashMap<>();

    public static final class Ins {
        public String op = "";
        public String label = "";
        public String go = "";
        public final List<String> t = new ArrayList<>();
        public float[] roi;
        public double thr = 0.88;
        public long ms;
        public long timeoutMs;
        public float x;
        public float y;
        public float x2;
        public float y2;
        public String var = "";
        public int val;
        public String msg = "";
    }

    public static Program load(Context ctx) {
        Program p = new Program();
        String raw;
        try (InputStream in = ctx.getAssets().open("program.json")) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            raw = new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            Logs.add("读取 program.json 失败: " + e);
            return p;
        }
        try {
            p.parse(new JSONObject(raw));
        } catch (Throwable e) {
            Logs.add("解析 program.json 失败: " + e);
        }
        return p;
    }

    private void parse(JSONObject o) {
        name = o.optString("name", name);
        refW = Math.max(1, o.optInt("refW", refW));
        refH = Math.max(1, o.optInt("refH", refH));
        scale = Math.max(1, o.optInt("scale", scale));
        maxRunMs = o.optLong("maxRunMs", maxRunMs);

        JSONObject v = o.optJSONObject("vars");
        if (v != null) {
            JSONArray names = v.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String k = names.optString(i, "");
                    if (!k.isEmpty()) vars.put(k, v.optInt(k, 0));
                }
            }
        }

        JSONArray arr = o.optJSONArray("program");
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject io = arr.optJSONObject(i);
            if (io == null) continue;
            Ins in = new Ins();
            in.op = io.optString("op", "");
            in.label = io.optString("name", "");
            in.go = io.optString("goto", "");
            // goto 指令允许写 {"op":"goto","label":"x"}
            if (in.go.isEmpty() && "goto".equals(in.op)) {
                in.go = io.optString("label", "");
            }
            in.var = io.optString("var", "");
            in.msg = io.optString("msg", "");
            in.thr = io.optDouble("thr", in.thr);
            in.ms = io.optLong("ms", 0);
            in.timeoutMs = io.optLong("timeoutMs", 0);
            in.val = io.optInt("val", 0);
            in.x = (float) io.optDouble("x", 0.5);
            in.y = (float) io.optDouble("y", 0.5);
            in.x2 = (float) io.optDouble("x2", in.x);
            in.y2 = (float) io.optDouble("y2", in.y);

            JSONArray ta = io.optJSONArray("t");
            if (ta != null) {
                for (int k = 0; k < ta.length(); k++) {
                    String s = ta.optString(k, "");
                    if (!s.isEmpty()) in.t.add(s);
                }
            }
            JSONArray ra = io.optJSONArray("roi");
            if (ra != null && ra.length() >= 4) {
                in.roi = new float[]{
                        (float) ra.optDouble(0, 0),
                        (float) ra.optDouble(1, 0),
                        (float) ra.optDouble(2, 1),
                        (float) ra.optDouble(3, 1)
                };
            }
            if ("label".equals(in.op) && !in.label.isEmpty() && !labels.containsKey(in.label)) {
                labels.put(in.label, ins.size());
            }
            ins.add(in);
        }
        Logs.add("流程载入: " + name + " / " + ins.size() + " 条指令 / " + labels.size() + " 个标签");
    }

    public int labelIndex(String label) {
        Integer i = labels.get(label);
        return i == null ? -1 : i;
    }
}
