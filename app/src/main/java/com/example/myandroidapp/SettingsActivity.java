package com.example.myandroidapp;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    
    private EditText etUrl, etPort, etApiKey, etSession;
    private Button btnSave, btnGetSessions;
    private TextView tvStatus;
    private SettingsManager settingsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("设置");
        
        settingsManager = new SettingsManager(this);
        
        initViews();
        loadSettings();
    }

    private void initViews() {
        etUrl = findViewById(R.id.et_url);
        etPort = findViewById(R.id.et_port);
        etApiKey = findViewById(R.id.et_api_key);
        etSession = findViewById(R.id.et_session);
        btnSave = findViewById(R.id.btn_save);
        btnGetSessions = findViewById(R.id.btn_get_sessions);
        tvStatus = findViewById(R.id.tv_status);
        
        btnSave.setOnClickListener(v -> saveSettings());
        btnGetSessions.setOnClickListener(v -> fetchSessions());
    }

    private void loadSettings() {
        etUrl.setText(settingsManager.getUrl());
        etPort.setText(settingsManager.getPort());
        etApiKey.setText(settingsManager.getApiKey());
        etSession.setText(settingsManager.getSession());
    }

    private void saveSettings() {
        String url = etUrl.getText().toString().trim();
        String port = etPort.getText().toString().trim();
        String apiKey = etApiKey.getText().toString().trim();
        String session = etSession.getText().toString().trim();
        
        settingsManager.saveSettings(url, port, apiKey, session);
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void fetchSessions() {
        ApiClient client = new ApiClient(settingsManager);
        tvStatus.setText("正在获取会话列表...");
        
        client.getSessions(new ApiClient.SessionsCallback() {
            @Override
            public void onSuccess(String[] sessions) {
                runOnUiThread(() -> {
                    if (sessions.length > 0) {
                        // Use first session
                        etSession.setText(sessions[0]);
                        tvStatus.setText("已获取 " + sessions.length + " 个会话，使用第一个");
                    } else {
                        tvStatus.setText("没有活动的会话");
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    tvStatus.setText("获取失败: " + error);
                    Toast.makeText(SettingsActivity.this, "获取失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
