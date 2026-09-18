package com.example.sbtimeout;

import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {
    private static final String TAG = "SBTimeout";
    private static final String TARGET = "com.getsurfboard";
    private static final int TIMEOUT_MS = 1000; // 写死：1秒

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!TARGET.equals(lp.packageName)) return;
        XposedBridge.log("[SBTimeout] 已注入，全量改写超时=" + TIMEOUT_MS + "ms");

        XC_MethodHook rewriter = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                String tname = String.valueOf(Thread.currentThread().getName());
                if (p.args.length == 2 && p.args[1] instanceof Integer) {
                    int orig = (Integer) p.args[1];
                    p.args[1] = TIMEOUT_MS;
                    String line = "Socket.connect 原始=" + orig + "ms 改写=" + TIMEOUT_MS + "ms [线程=" + tname + "]";
                    Log.i(TAG, line);
                    XposedBridge.log("[SBTimeout] " + line);
                } else if (p.args.length == 1 && p.args[0] instanceof Integer) {
                    int orig = (Integer) p.args[0];
                    p.args[0] = TIMEOUT_MS;
                    String line = "Socket.setSoTimeout 原始=" + orig + "ms 改写=" + TIMEOUT_MS + "ms [线程=" + tname + "]";
                    Log.i(TAG, line);
                    XposedBridge.log("[SBTimeout] " + line);
                }
            }
        };

        XposedBridge.hookAllMethods(java.net.Socket.class, "connect", rewriter);
        XposedBridge.hookAllMethods(java.net.Socket.class, "setSoTimeout", rewriter);
    }
}
