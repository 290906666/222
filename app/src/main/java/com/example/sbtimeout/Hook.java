package com.example.sbtimeout;

import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.concurrent.TimeUnit;

public class Hook implements IXposedHookLoadPackage {
    private static final String TAG = "SBTimeout";
    private static final String TARGET = "com.getsurfboard";
    private static final String CONF = "/sdcard/Download/sb_timeout.json";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!TARGET.equals(lp.packageName)) {
            return;
        }
        long ov = overrideMs();
        XposedBridge.log("[SBTimeout] 模块已注入 Surfboard，override=" + ov);
        Log.i(TAG, "模块已注入 Surfboard，override=" + ov);
        diagConf();

        Log.i(TAG, "OkHttpClient$Builder 类存在=" + (XposedHelpers.findClassIfExists("okhttp3.OkHttpClient$Builder", lp.classLoader) != null));
        hookOkHttpBuilder(lp);
        hookUrlConnection();
        hookOkHttpCtor(lp);
        hookSocket(lp);
    }

    // Socket 层：connect(SocketAddress,int) 和 setSoTimeout(int)
    private void hookSocket(XC_LoadPackage.LoadPackageParam lp) {
        try {
            XposedBridge.hookAllMethods(java.net.Socket.class, "connect", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.args.length == 2 && p.args[1] instanceof Integer) {
                        int orig = (Integer) p.args[1];
                        long ov = overrideMs();
                        String s = "Socket.connect timeout 原始=" + orig + "ms" + (ov > 0 ? " 改写=" + ov + "ms" : "") + "\n" + stack();
                        Log.i(TAG, s);
                        XposedBridge.log("[SBTimeout] " + s);
                        if (ov > 0) p.args[1] = (int) ov;
                    }
                }
            });
            XposedBridge.hookAllMethods(java.net.Socket.class, "setSoTimeout", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    int orig = (Integer) p.args[0];
                    long ov = overrideMs();
                    Log.i(TAG, "Socket.setSoTimeout 原始=" + orig + "ms" + (ov > 0 ? " 改写=" + ov + "ms" : ""));
                    XposedBridge.log("[SBTimeout] Socket.setSoTimeout timeout=" + orig);
                    if (ov > 0) p.args[0] = (int) ov;
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "hook Socket 失败", t);
        }
    }

    private void hookOkHttpBuilder(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> b = XposedHelpers.findClassIfExists("okhttp3.OkHttpClient$Builder", lp.classLoader);
        if (b == null) {
            XposedBridge.log("[SBTimeout] 未找到 OkHttpClient$Builder");
            return;
        }
        String[] methods = {"callTimeout", "connectTimeout", "readTimeout", "writeTimeout"};
        for (String m : methods) {
            try {
                XposedBridge.hookAllMethods(b, m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        long orig = toMs(p.args[0], p.args[1]);
                        long ov = overrideMs();
                        String line = "OkHttp.Builder." + p.method.getName()
                                + " 原始=" + orig + "ms" + (ov > 0 ? " 改写=" + ov + "ms" : "")
                                + "\n" + stack();
                        Log.i(TAG, line);
                        XposedBridge.log("[SBTimeout] " + line);
                        if (ov > 0) {
                            p.args[0] = ov;
                            p.args[1] = TimeUnit.MILLISECONDS;
                        }
                    }
                });
            } catch (Throwable ignored) {}
        }
    }

    private void hookUrlConnection() {
        try {
            XposedBridge.hookAllMethods(java.net.URLConnection.class, "setConnectTimeout", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    long ov = overrideMs();
                    Log.i(TAG, "URLConnection.setConnectTimeout 原始=" + p.args[0] + (ov > 0 ? " 改写=" + ov : ""));
                    XposedBridge.log("[SBTimeout] URLConnection.setConnectTimeout 原始=" + p.args[0] + (ov > 0 ? " 改写=" + ov : ""));
                    if (ov > 0) p.args[0] = (int) ov;
                }
            });
            XposedBridge.hookAllMethods(java.net.URLConnection.class, "setReadTimeout", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    long ov = overrideMs();
                    Log.i(TAG, "URLConnection.setReadTimeout 原始=" + p.args[0] + (ov > 0 ? " 改写=" + ov : ""));
                    XposedBridge.log("[SBTimeout] URLConnection.setReadTimeout 原始=" + p.args[0] + (ov > 0 ? " 改写=" + ov : ""));
                    if (ov > 0) p.args[0] = (int) ov;
                }
            });
        } catch (Throwable ignored) {}
    }

    private void hookOkHttpCtor(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> cls = XposedHelpers.findClass("okhttp3.OkHttpClient", lp.classLoader);
            XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.args == null || p.args.length == 0 || p.args[0] == null) return;
                    Object builder = p.args[0];
                    String[] fields = {"callTimeoutMillis", "connectTimeoutMillis", "readTimeoutMillis", "writeTimeoutMillis"};
                    StringBuilder sb = new StringBuilder("[SBTimeout] OkHttpClient.ctor 实际值: ");
                    for (String f : fields) {
                        try {
                            sb.append(f).append('=').append(XposedHelpers.getObjectField(builder, f)).append(' ');
                        } catch (Throwable ignored) {}
                    }
                    Log.i(TAG, sb.toString());
                    XposedBridge.log(sb.toString());
                }
            });
        } catch (Throwable ignored) {}
    }

    private static long toMs(Object dur, Object unit) {
        try {
            return ((TimeUnit) unit).toMillis((Long) dur);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static final String[] CONF_PATHS = {
            "/sdcard/Download/sb_timeout.json",
            "/storage/emulated/0/Download/sb_timeout.json"
    };

    private static void diagConf() {
        for (String p : CONF_PATHS) {
            try {
                java.io.File f = new java.io.File(p);
                Log.i(TAG, "配置诊断: " + p + " 存在=" + f.exists() + (f.exists() ? " 内容=" + readFile(f) : ""));
                XposedBridge.log("[SBTimeout] conf " + p + " exists=" + f.exists());
            } catch (Throwable t) {
                Log.e(TAG, "配置诊断失败 " + p, t);
            }
        }
    }

    private static String readFile(java.io.File f) throws Exception {
        BufferedReader r = new BufferedReader(new FileReader(f));
        StringBuilder sb = new StringBuilder();
        String l;
        while ((l = r.readLine()) != null) sb.append(l);
        r.close();
        return sb.toString();
    }

    private static long overrideMs() {
        for (String p : CONF_PATHS) {
            long v = overrideMs(p);
            if (v > 0) return v;
        }
        return -1;
    }

    private static long overrideMs(String path) {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(path));
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
