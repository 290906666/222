package com.example.sbtimeout;

import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.HashMap;
import java.util.Map;

public class Hook implements IXposedHookLoadPackage {
    private static final String TAG = "SBTimeout";
    private static final String TARGET = "com.getsurfboard";
    private static final int TIMEOUT_MS = 1000;

    // MODE: 0=全量 1=只gstatic 2=除gstatic外全部 3=只ce3 4=只DefaultDispatcher
    private static final int MODE = 4;

    private static final Map<String, int[]> stats = new HashMap<String, int[]>();
    private static long lastStats = 0;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!TARGET.equals(lp.packageName)) return;
        XposedBridge.log("[SBTimeout] 已注入 MODE=" + MODE + " 超时=" + TIMEOUT_MS + "ms");

        XC_MethodHook rewriter = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                String tname = String.valueOf(Thread.currentThread().getName());
                boolean isGstatic = tname.contains("gstatic");
                boolean hit;
                if (MODE == 0) hit = true;
                else if (MODE == 1) hit = isGstatic;
                else if (MODE == 2) hit = !isGstatic;
                else if (MODE == 3) hit = tname.startsWith("ce3 connect");
                else hit = tname.startsWith("DefaultDispatcher"); // MODE 4
                if (!hit) return;

                int orig;
                if (p.args.length == 2 && p.args[1] instanceof Integer) {
                    orig = (Integer) p.args[1];
                    p.args[1] = TIMEOUT_MS;
                } else if (p.args.length == 1 && p.args[0] instanceof Integer) {
                    orig = (Integer) p.args[0];
                    p.args[0] = TIMEOUT_MS;
                } else {
                    return;
                }

                String host = extractHost(tname);
                String line = p.method.getName() + " host=" + host + " 原始=" + orig + "ms 改写=" + TIMEOUT_MS + "ms";
                Log.i(TAG, line);
                XposedBridge.log("[SBTimeout] " + line);

                synchronized (stats) {
                    int[] c = stats.get(host);
                    if (c == null) { c = new int[2]; stats.put(host, c); }
                    c[0]++;
                    if (orig != TIMEOUT_MS) c[1]++; // 真正被改短的有效次数
                }
                long now = System.currentTimeMillis();
                if (now - lastStats > 5000) {
                    lastStats = now;
                    StringBuilder sb = new StringBuilder("[SBTimeout] 统计: ");
                    synchronized (stats) {
                        for (Map.Entry<String, int[]> e : stats.entrySet()) {
                            sb.append(e.getKey()).append("(调用").append(e.getValue()[0])
                              .append("/有效").append(e.getValue()[1]).append(") ");
                        }
                    }
                    XposedBridge.log(sb.toString());
                }
            }
        };

        XposedBridge.hookAllMethods(java.net.Socket.class, "connect", rewriter);
        XposedBridge.hookAllMethods(java.net.Socket.class, "setSoTimeout", rewriter);
    }

    // 线程名形如 "ce3 connect https://host/path..."，提取 host
    private static String extractHost(String tname) {
        int i = tname.indexOf("://");
        if (i < 0) return tname;
        String rest = tname.substring(i + 3);
        int end = rest.indexOf('/');
        return end < 0 ? rest : rest.substring(0, end);
    }
}
