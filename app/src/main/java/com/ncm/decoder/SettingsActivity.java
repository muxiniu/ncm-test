package com.muxiniu.ncmtest;


import android.os.Bundle;
import android.widget.CheckBox;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        CheckBox cbCover = findViewById(R.id.cb_write_cover);
        CheckBox cbDedup = findViewById(R.id.cb_dedup);

        cbCover.setChecked(getSharedPreferences("settings", MODE_PRIVATE).getBoolean("write_cover", true));
        cbDedup.setChecked(getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dedup", true));

        cbCover.setOnCheckedChangeListener((v, c) -> getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("write_cover", c).apply());
        cbDedup.setOnCheckedChangeListener((v, c) -> getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("dedup", c).apply());
    }
}
