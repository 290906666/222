package com.example.sbtimeout;

import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.BufferedReader;
import java.io.FileReader;

public class Hook implements IXposedHookLoadPackage {
    private static final String TAG = "SBTimeout";
    private static final String TARGET = "com.getsurfboard";

    // 只影响测速线程（线程名含 gstatic，即 204 测试 URL 的 host）
    private static final String THREAD_FILTER = "gstatic";
    private static final long DEFAULT_OVERRIDE = 1000; // 默认1秒，sb_timeout.json 可覆盖
    private static boolean announced = false;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!TARGET.equals(lp.packageName)) return;
        String boot = "SBTimeout 已注入，测速线程过滤=" + THREAD_FILTER + " 默认超时=" + DEFAULT_OVERRIDE + "ms";
        Log.i(TAG, boot);
        XposedBridge.log("[SBTimeout] " + boot);

        XC_MethodHook rewriter = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                String tname = Thread.currentThread().getName();
                if (tname == null || !tname.contains(THREAD_FILTER)) return; // 非测速线程，零干预
                long ov = overrideMs();
                if (ov <= 0) return;
                if (p.args.length == 2 && p.args[1] instanceof Integer) {
                    p.args[1] = (int) ov; // Socket.connect(addr, timeout)
                } else if (p.args.length == 1 && p.args[0] instanceof Integer) {
                    p.args[0] = (int) ov; // Socket.setSoTimeout(timeout)
                }
                if (!announced) {
                    announced = true;
                    String s = "已改写测速线程超时 -> " + ov + "ms (线程=" + tname + ")";
                    Log.i(TAG, s);
                    XposedBridge.log("[SBTimeout] " + s);
                }
            }
        };

        XposedBridge.hookAllMethods(java.net.Socket.class, "connect", rewriter);
        XposedBridge.hookAllMethods(java.net.Socket.class, "setSoTimeout", rewriter);
    }

    private static long overrideMs() {
        String[] paths = {
                "/sdcard/Download/sb_timeout.json",
                "/storage/emulated/0/Download/sb_timeout.json"
        };
        for (String path : paths) {
            BufferedReader r = null;
            try {
                r = new BufferedReader(new FileReader(path));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = r.readLine()) != null) sb.append(l);
                String s = sb.toString();
                int i = s.indexOf("override_ms");
                if (i < 0) continue;
                int start = s.indexOf(':', i) + 1;
                while (start < s.length() && !Character.isDigit(s.charAt(start)) && s.charAt(start) != '-') start++;
                int end = start;
                while (end < s.length() && (Character.isDigit(s.charAt(end)) || s.charAt(end) == '-')) end++;
                long v = Long.parseLong(s.substring(start, end).trim());
                if (v > 0) return v;
            } catch (Throwable ignored) {
            } finally {
                if (r != null) try { r.close(); } catch (Throwable ignored) {}
            }
        }
        return DEFAULT_OVERRIDE;
    }
}
