package com.example.sbtimeout;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Toast.makeText(this, "SBTimeout 模块运行正常，请在 LSPosed 中确认作用域已勾选 Surfboard", Toast.LENGTH_LONG).show();
        TextView tv = new TextView(this);
        tv.setTextSize(14f);
        tv.setPadding(40, 40, 40, 40);
        tv.setText("SBTimeout 使用说明\n\n"
                + "1. 在 LSPosed 中勾选作用域为 Surfboard，并强行停止 Surfboard\n"
                + "2. 打开 Surfboard 点测速按钮\n"
                + "3. adb logcat -s SBTimeout 查看超时设置的调用栈（定位阶段）\n\n"
                + "修改超时：\n"
                + "在 /sdcard/Download/ 下创建 sb_timeout.json，内容：\n"
                + "  {\"override_ms\":3000}\n"
                + "单位毫秒。删除该文件即恢复默认。\n\n"
                + "注意：如果点测速时 logcat 完全没有 SBTimeout 日志，"
                + "说明测速超时发生在 native 层，Java Hook 覆盖不到，"
                + "需要改用 Frida hook .so。");
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        setContentView(sv);
    }
}
