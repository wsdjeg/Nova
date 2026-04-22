package net.wsdjeg.nova;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends AppCompatActivity {
    
    public static final String EXTRA_SETTINGS_SAVED = "settings_saved";
    private static final String UPDATE_URL = "https://github.com/wsdjeg/Nova/releases/download/prerelease/ChatApp.apk";
    
    private EditText etUrl, etPort, etApiKey;
    private Button btnSave, btnCheckUpdate, btnAbout;
    private SettingsManager settingsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        // 设置 Toolbar 作为 ActionBar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
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
        btnCheckUpdate = findViewById(R.id.btn_check_update);
        btnAbout = findViewById(R.id.btn_about);
        
        btnSave.setOnClickListener(v -> saveSettings());
        btnCheckUpdate.setOnClickListener(v -> checkUpdate());
        btnAbout.setOnClickListener(v -> openAbout());
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
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        
        // 设置结果，通知 SessionListActivity 刷新
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_SETTINGS_SAVED, true);
        setResult(RESULT_OK, resultIntent);
        
        finish();
    }

    private void checkUpdate() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(UPDATE_URL));
        startActivity(intent);
    }

    private void openAbout() {
        Intent intent = new Intent(this, AboutActivity.class);
        startActivity(intent);
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
