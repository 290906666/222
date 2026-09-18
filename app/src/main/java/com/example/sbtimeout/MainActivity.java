package com.example.sbtimeout;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView tv = new TextView(this);
        tv.setTextSize(14f);
        tv.setPadding(40, 40, 40, 40);
        tv.setText("SBTimeout 使用说明\n\n"
                + "1. 在 LSPosed 中启用本模块，作用域勾选 Surfboard\n"
                + "2. 强行停止 Surfboard 后重新打开\n"
                + "3. adb logcat -s SBTimeout 查看日志\n"
                + "4. LSPosed 管理器 → 日志 中查看 [SBTimeout] 前缀日志\n\n"
                + "修改超时：在 /sdcard/Download/ 创建 sb_timeout.json\n"
                + "内容：{\"override_ms\":3000}（单位毫秒）\n\n"
                + "如果 LSPosed 模块列表里看不到本模块，请确认：\n"
                + "- APK 已正常安装\n"
                + "- LSPosed 框架已激活（状态栏有通知）\n"
                + "- 重启手机后再试");
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        setContentView(sv);
    }
}
