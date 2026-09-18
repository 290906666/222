package com.example.sbtimeout;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

// SBTimeout 最终版
// 排除法结论：Surfboard 测速的真实读取超时是 DefaultDispatcher 协程线程上的
// Socket.setSoTimeout(10000)（10秒）。仅此一处改写为 1000ms，其他全部零干预。
public class Hook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.getsurfboard";
    private static final int ORIG = 10000;   // 只命中原始10秒的调用
    private static final int TIMEOUT_MS = 1000;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!TARGET.equals(lp.packageName)) return;
        XposedBridge.log("[SBTimeout] 已注入：setSoTimeout(" + ORIG + ") -> " + TIMEOUT_MS + "ms");

        XposedBridge.hookAllMethods(java.net.Socket.class, "setSoTimeout", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!(p.args[0] instanceof Integer)) return;
                String tname = Thread.currentThread().getName();
                if (tname == null || !tname.startsWith("DefaultDispatcher")) return;
                if ((Integer) p.args[0] != ORIG) return;
                p.args[0] = TIMEOUT_MS;
                XposedBridge.log("[SBTimeout] 改写 setSoTimeout(" + ORIG + ") -> " + TIMEOUT_MS
                        + "ms @ " + tname);
            }
        });
    }
}
