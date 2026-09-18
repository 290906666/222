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
        tv.setText("SBTimeout（写死版）\n\n"
                + "超时已硬编码为 1000ms，无需任何设置。\n\n"
                + "验证方式：\n"
                + "1. LSPosed 勾选作用域 Surfboard\n"
                + "2. 强停 Surfboard 后重新打开\n"
                + "3. LSPosed 模块日志应出现：\n"
                + "   [SBTimeout] 已改写测速超时 -> 1000ms\n\n"
                + "说明：测速超时控制位于 Surfboard native 层，\n"
                + "本模块只保证 Java Socket 层为 1 秒。");
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        setContentView(sv);
    }
}
