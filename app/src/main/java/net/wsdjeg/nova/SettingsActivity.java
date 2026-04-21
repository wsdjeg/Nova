package net.wsdjeg.nova;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends AppCompatActivity {
    
    private EditText etUrl, etPort, etApiKey;
    private Button btnSave;
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
        
        // Session 不在设置页面配置，使用主界面的下拉菜单选择
        settingsManager.saveSettings(url, port, apiKey);
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
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
