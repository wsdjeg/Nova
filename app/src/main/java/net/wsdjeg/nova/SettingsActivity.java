package net.wsdjeg.nova;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends AppCompatActivity {
    
    public static final String EXTRA_SETTINGS_SAVED = "settings_saved";
    public static final String EXTRA_THEME_CHANGED = "theme_changed";
    
    private EditText etUrl, etPort, etApiKey;
    private RadioGroup rgTheme;
    private RadioButton rbSystem, rbLight, rbDark;
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
        rgTheme = findViewById(R.id.rg_theme);
        rbSystem = findViewById(R.id.rb_theme_system);
        rbLight = findViewById(R.id.rb_theme_light);
        rbDark = findViewById(R.id.rb_theme_dark);
        btnSave = findViewById(R.id.btn_save);
        
        // 主题选择监听
        rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            int themeMode;
            if (checkedId == R.id.rb_theme_system) {
                themeMode = SettingsManager.THEME_SYSTEM;
            } else if (checkedId == R.id.rb_theme_light) {
                themeMode = SettingsManager.THEME_LIGHT;
            } else {
                themeMode = SettingsManager.THEME_DARK;
            }
            settingsManager.setThemeMode(themeMode);
            applyTheme(themeMode);
            
            // 通知需要重建界面
            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_THEME_CHANGED, true);
            setResult(RESULT_OK, resultIntent);
        });
        
        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void loadSettings() {
        etUrl.setText(settingsManager.getUrl());
        etPort.setText(settingsManager.getPort());
        etApiKey.setText(settingsManager.getApiKey());
        
        // 加载主题设置
        int themeMode = settingsManager.getThemeMode();
        switch (themeMode) {
            case SettingsManager.THEME_LIGHT:
                rbLight.setChecked(true);
                break;
            case SettingsManager.THEME_DARK:
                rbDark.setChecked(true);
                break;
            default:
                rbSystem.setChecked(true);
                break;
        }
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
    
    private void applyTheme(int themeMode) {
        switch (themeMode) {
            case SettingsManager.THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case SettingsManager.THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
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
