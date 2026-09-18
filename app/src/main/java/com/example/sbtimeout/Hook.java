package com.example.sbtimeout;

import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {
    private static final String TAG = "SBTimeout";
    private static final String TARGET = "com.getsurfboard";
    private static final String THREAD_FILTER = "gstatic"; // 测速线程名特征
    private static final int TIMEOUT_MS = 1000;            // 写死：1秒
    private static boolean announced = false;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!TARGET.equals(lp.packageName)) return;
        XposedBridge.log("[SBTimeout] 已注入，写死超时=" + TIMEOUT_MS + "ms，过滤线程=" + THREAD_FILTER);

        XC_MethodHook rewriter = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                String tname = Thread.currentThread().getName();
                if (tname == null || !tname.contains(THREAD_FILTER)) return;
                if (p.args.length == 2 && p.args[1] instanceof Integer) {
                    p.args[1] = TIMEOUT_MS;
                } else if (p.args.length == 1 && p.args[0] instanceof Integer) {
                    p.args[0] = TIMEOUT_MS;
                }
                if (!announced) {
                    announced = true;
                    XposedBridge.log("[SBTimeout] 已改写测速超时 -> " + TIMEOUT_MS + "ms (线程=" + tname + ")");
                }
            }
        };

        XposedBridge.hookAllMethods(java.net.Socket.class, "connect", rewriter);
        XposedBridge.hookAllMethods(java.net.Socket.class, "setSoTimeout", rewriter);
    }
}
