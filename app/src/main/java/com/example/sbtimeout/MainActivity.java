package com.example.sbtimeout;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class MainActivity extends Activity {

    private static final File CONF = new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "sb_timeout.json");

    private EditText et;
    private TextView tvStatus;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("SBTimeout — 超时设置（毫秒）");
        tvTitle.setTextSize(18f);
        tvTitle.setGravity(Gravity.CENTER);
        root.addView(tvTitle);

        et = new EditText(this);
        et.setHint("例如 1000 = 1秒");
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        root.addView(et);

        Button btnSave = new Button(this);
        btnSave.setText("保存并生效");
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
        root.addView(btnSave);

        tvStatus = new TextView(this);
        tvStatus.setTextSize(14f);
        tvStatus.setPadding(0, pad, 0, 0);
        root.addView(tvStatus);

        TextView tvHelp = new TextView(this);
        tvHelp.setTextSize(12f);
        tvHelp.setPadding(0, pad, 0, 0);
        tvHelp.setText("使用方法：\n"
                + "1. 输入超时毫秒数，点保存\n"
                + "2. 强行停止 Surfboard 后重新打开\n"
                + "3. adb logcat -s SBTimeout 查看日志\n\n"
                + "LSPosed 中请勾选作用域：Surfboard");
        root.addView(tvHelp);

        setContentView(root);

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        } else {
            refresh();
        }
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        refresh();
    }

    private void refresh() {
        String cur = readOverride();
        tvStatus.setText("当前配置：override=" + (cur == null ? "未设置" : cur + "ms")
                + "\n配置文件：" + CONF.getAbsolutePath());
        if (cur != null) et.setText(cur);
    }

    private void save() {
        String s = et.getText().toString().trim();
        if (s.isEmpty()) { toast("请输入数字"); return; }
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            toast("请先授予存储权限"); return;
        }
        try {
            FileWriter w = new FileWriter(CONF);
            w.write("{\"override_ms\":" + Long.parseLong(s) + "}");
            w.close();
            toast("已保存：" + s + "ms");
            refresh();
        } catch (Throwable t) {
            toast("保存失败：" + t.getMessage());
        }
    }

    private String readOverride() {
        try {
            BufferedReader r = new BufferedReader(new FileReader(CONF));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) sb.append(l);
            r.close();
            String str = sb.toString();
            int i = str.indexOf("override_ms");
            if (i < 0) return null;
            int start = str.indexOf(':', i) + 1;
            while (start < str.length() && !Character.isDigit(str.charAt(start)) && str.charAt(start) != '-') start++;
            int end = start;
            while (end < str.length() && (Character.isDigit(str.charAt(end)) || str.charAt(end) == '-')) end++;
            return str.substring(start, end).trim();
        } catch (Throwable t) {
            return null;
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
