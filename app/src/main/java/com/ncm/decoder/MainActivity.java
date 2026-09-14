package com.muxiniu.ncmtest;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private Button btnPick, btnPickMulti, btnSettings, btnCancel;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CrashHandler.init(getApplicationContext());

        tvStatus = findViewById(R.id.tv_status);
        btnPick = findViewById(R.id.btn_pick_single);
        btnPickMulti = findViewById(R.id.btn_pick_multi);
        btnSettings = findViewById(R.id.btn_settings);
        btnCancel = findViewById(R.id.btn_cancel);
        progressBar = findViewById(R.id.progress);

        btnPick.setOnClickListener(v -> openPicker(false));
        btnPickMulti.setOnClickListener(v -> openPicker(true));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnCancel.setOnClickListener(v -> {
            tvStatus.setText("已取消");
            btnCancel.setEnabled(false);
            stopService(new Intent(this, DecryptService.class));
        });

        SharedPreferences prefs = getSharedPreferences("app", MODE_PRIVATE);
        if (!prefs.getBoolean("disclaimer_shown", false)) {
            new AlertDialog.Builder(this)
                    .setTitle("免责声明")
                    .setMessage("本工具仅用于解密您自己购买/下载的音乐文件，请勿用于传播版权内容。继续使用即表示您同意此条款。")
                    .setPositiveButton("同意", (d, w) -> prefs.edit().putBoolean("disclaimer_shown", true).apply())
                    .setNegativeButton("退出", (d, w) -> finish())
                    .setCancelable(false)
                    .show();
        }
    }

    private void openPicker(boolean multiple) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
        startActivityForResult(Intent.createChooser(intent, multiple ? "选择多个 NCM 文件" : "选择 NCM 文件"), 1001);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 1001 && res == RESULT_OK && data != null) {
            ArrayList<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) uris.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (uris.isEmpty()) { tvStatus.setText("未选择文件"); return; }
            tvStatus.setText("已选择 " + uris.size() + " 个文件");
            progressBar.setVisibility(android.view.View.VISIBLE);
            progressBar.setProgress(0);
            btnCancel.setEnabled(true);

            Intent service = new Intent(this, DecryptService.class);
            service.putParcelableArrayListExtra("uris", uris);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(service);
            } else {
                startService(service);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                .registerReceiver(progressReceiver, new android.content.IntentFilter("DECRYPT_PROGRESS"));
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                .registerReceiver(resultReceiver, new android.content.IntentFilter("DECRYPT_RESULT"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(progressReceiver);
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(resultReceiver);
    }

    private final android.content.BroadcastReceiver progressReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, android.content.Intent i) {
            int current = i.getIntExtra("current", 0);
            int total = i.getIntExtra("total", 1);
            String name = i.getStringExtra("name");
            progressBar.setProgress((current * 100) / total);
            tvStatus.setText("进度: " + current + "/
