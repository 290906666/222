package com.example.sbtimeout;

import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import android.util.Log;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.concurrent.TimeUnit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {

    private static final String TAG = "SBTimeout";
    private static final String TARGET = "com.getsurfboard";
    // 配置文件：/sdcard/Download/sb_timeout.json，内容 {"override_ms":3000}
    // 不写该文件或 override_ms=-1 时仅打印日志，不修改。
    private static final String CONF = "/sdcard/Download/sb_timeout.json";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        // 调试：无论是否目标包，都记录一次（只在 logcat 可见，避免刷 LSPosed 日志）
        Log.i(TAG, "[调试] 模块代码已运行，当前包=" + lp.packageName + " process=" + lp.processName);
        if (!TARGET.equals(lp.packageName)) return;
        Log.i(TAG, "[调试] 命中目标包 " + TARGET);
        final long ov = overrideMs();
        final String msg = "SBTimeout 已注入 Surfboard，override=" + (ov > 0 ? ov + "ms" : "未设置(仅记录日志)");
        Log.i(TAG, "hook 已加载，目标=" + TARGET + " 当前override=" + ov);
        XposedBridge.log("[SBTimeout] " + msg);

        hookOkHttpBuilder(lp);
        hookUrlConnection();
        hookOkHttpCtor(lp);
        showToast(lp, msg);
    }

    // 弹 Toast：优先反射 ActivityThread；失败则 Hook Activity.onCreate 弹
    private static void showToast(final XC_LoadPackage.LoadPackageParam lp, final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                android.content.Context ctx = null;
                try {
                    ctx = (android.content.Context) Class.forName("android.app.ActivityThread")
                            .getMethod("currentApplication").invoke(null);
                } catch (Throwable t) { Log.i(TAG, "ActivityThread 反射失败: " + t); }
                if (ctx != null) {
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
                } else {
                    hookActivityForToast(lp, msg);
                }
            }
        });
    }

    private static boolean toastHooked = false;
    private static synchronized void hookActivityForToast(XC_LoadPackage.LoadPackageParam lp, final String msg) {
        if (toastHooked) return;
        toastHooked = true;
        try {
            XposedBridge.hookAllMethods(android.app.Activity.class, "onCreate", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.thisObject != null && !toastShown) {
                        toastShown = true;
                        Toast.makeText((android.content.Context) p.thisObject, msg, Toast.LENGTH_LONG).show();
                    }
                }
            });
        } catch (Throwable t) { Log.e(TAG, "hook Activity.onCreate 失败", t); }
    }
    private static boolean toastShown = false;

    // 1) OkHttpClient.Builder 的四个超时方法（最常见入口）
    private void hookOkHttpBuilder(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> b = XposedHelpers.findClassIfExists("okhttp3.OkHttpClient$Builder", lp.classLoader);
        if (b == null) { Log.i(TAG, "未找到 OkHttpClient$Builder"); return; }
        String[] methods = {"callTimeout", "connectTimeout", "readTimeout", "writeTimeout"};
        for (String m : methods) {
            try {
                XposedBridge.hookAllMethods(b, m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        long orig = toMs(p.args[0], p.args[1]);
                        long ov = overrideMs();
                        Log.i(TAG, "OkHttp.Builder." + p.method.getName()
                                + " 原始=" + orig + "ms" + (ov > 0 ? "  改写为=" + ov + "ms" : "")
                                + "\n" + stack());
                        if (ov > 0) { p.args[0] = ov; p.args[1] = TimeUnit.MILLISECONDS; }
                    }
                });
            } catch (Throwable ignored) {}
        }
    }

    // 2) HttpURLConnection / URLConnection 超时（若引擎走这个）
    private void hookUrlConnection() {
        try {
            XposedBridge.hookAllMethods(java.net.URLConnection.class, "setConnectTimeout", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    long ov = overrideMs();
                    Log.i(TAG, "URLConnection.setConnectTimeout 原始=" + p.args[0]
                            + (ov > 0 ? "  改写为=" + ov : "") + "\n" + stack());
                    if (ov > 0) p.args[0] = (int) ov;
                }
            });
            XposedBridge.hookAllMethods(java.net.URLConnection.class, "setReadTimeout", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    long ov = overrideMs();
                    Log.i(TAG, "URLConnection.setReadTimeout 原始=" + p.args[0]
                            + (ov > 0 ? "  改写为=" + ov : "") + "\n" + stack());
                    if (ov > 0) p.args[0] = (int) ov;
                }
            });
        } catch (Throwable ignored) {}
    }

    // 3) OkHttpClient 构造器：抓 Builder 直接写字段的场景，打印实际生效值
    private void hookOkHttpCtor(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> cls = XposedHelpers.findClass("okhttp3.OkHttpClient", lp.classLoader);
            XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.args == null || p.args.length == 0 || p.args[0] == null) return;
                    Object builder = p.args[0];
                    String[] fields = {"callTimeoutMillis", "connectTimeoutMillis", "readTimeoutMillis", "writeTimeoutMillis"};
                    StringBuilder sb = new StringBuilder("OkHttpClient.ctor 实际值: ");
                    for (String f : fields) {
                        try { sb.append(f).append('=').append(XposedHelpers.getObjectField(builder, f)).append(' '); }
                        catch (Throwable ignored) {}
                    }
                    Log.i(TAG, sb.toString());
                }
            });
        } catch (Throwable ignored) {}
    }

    private static long toMs(Object dur, Object unit) {
        try { return ((TimeUnit) unit).toMillis((Long) dur); } catch (Throwable t) { return -1; }
    }

    private static long overrideMs() {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(CONF));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) sb.append(l);
            String s = sb.toString();
            int i = s.indexOf("override_ms");
            if (i < 0) return -1;
            int start = s.indexOf(':', i) + 1;
            while (start < s.length() && !Character.isDigit(s.charAt(start)) && s.charAt(start) != '-') start++;
            int end = start;
            while (end < s.length() && (Character.isDigit(s.charAt(end)) || s.charAt(end) == '-')) end++;
            return Long.parseLong(s.substring(start, end).trim());
        } catch (Throwable t) {
            return -1;
        } finally {
            if (r != null) try { r.close(); } catch (Throwable ignored) {}
        }
    }

    private static String stack() {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            String c = e.getClassName();
            if (c.startsWith("de.robv") || c.startsWith("com.example.sbtimeout") || c.startsWith("dalvik")) continue;
            sb.append("    at ").append(e).append('\n');
        }
        return sb.toString();
    }
}
