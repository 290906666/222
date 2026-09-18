package com.example.sbtimeout;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

// Surfboard测速超时调整（最终版·零日志零消耗）
// 唯一生效点：DefaultDispatcher 协程线程上 Socket.setSoTimeout(10000) -> 1000ms
// 判断顺序按淘汰率排列：绝大多数调用在第一步 int 比较就被拒绝，开销约等于无。
public class Hook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.getsurfboard";
    private static final int ORIG = 10000;
    private static final int TIMEOUT_MS = 1000;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!TARGET.equals(lp.packageName)) return;

        XposedBridge.hookAllMethods(java.net.Socket.class, "setSoTimeout", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!(p.args[0] instanceof Integer) || (Integer) p.args[0] != ORIG) return;
                String tname = Thread.currentThread().getName();
                if (tname == null || !tname.startsWith("DefaultDispatcher")) return;
                p.args[0] = TIMEOUT_MS;
            }
        });
    }
}
