package com.example.myandroidapp;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    
    private EditText etUrl, etPort, etApiKey;
    private Button btnSave;
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
        btnSave = findViewById(R.id.btn_save);
        
        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void loadSettings() {
        etUrl.setText(settingsManager.getUrl());
        etPort.setText(settingsManager.getPort());
        etApiKey.setText(settingsManager.getApiKey());
    }

    private void saveSettings() {
        String url = etUrl.getText().toString().trim();
        String port = etPort.getText().toString().trim();
        String apiKey = etApiKey.getText().toString().trim();
        
        settingsManager.saveSettings(url, port, apiKey);
        finish();
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
