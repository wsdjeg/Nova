package net.wsdjeg.nova;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

public class SettingsActivity extends AppCompatActivity {
    
    public static final String EXTRA_SETTINGS_SAVED = "settings_saved";
    public static final String EXTRA_THEME_CHANGED = "theme_changed";
    public static final String EXTRA_COLOR_CHANGED = "color_changed";
    
    private RadioGroup rgTheme;
    private RadioButton rbSystem, rbLight, rbDark;
    private LinearLayout colorPickerContainer;
    private SettingsManager settingsManager;
    
    private int selectedColorIndex = 2; // 默认蓝色
    private View[] colorViews;

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
        rgTheme = findViewById(R.id.rg_theme);
        rbSystem = findViewById(R.id.rb_theme_system);
        rbLight = findViewById(R.id.rb_theme_light);
        rbDark = findViewById(R.id.rb_theme_dark);
        colorPickerContainer = findViewById(R.id.color_picker_container);
        
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
        
        // 初始化颜色选择器
        initColorPicker();
    }
    
    private void initColorPicker() {
        String[] colors = SettingsManager.ACCOUNT_TAG_COLORS;
        colorViews = new View[colors.length];
        
        int size = (int) (40 * getResources().getDisplayMetrics().density);
        int margin = (int) (8 * getResources().getDisplayMetrics().density);
        
        for (int i = 0; i < colors.length; i++) {
            View colorView = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(margin, 0, margin, 0);
            colorView.setLayoutParams(params);
            
            // 设置圆形背景
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(Color.parseColor(colors[i]));
            colorView.setBackground(drawable);
            
            final int index = i;
            colorView.setOnClickListener(v -> selectColor(index));
            
            colorPickerContainer.addView(colorView);
            colorViews[i] = colorView;
        }
    }
    
    private void selectColor(int index) {
        selectedColorIndex = index;
        settingsManager.setAccountTagColorIndex(index);
        updateColorSelection();
        
        // 通知颜色已更改
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_COLOR_CHANGED, true);
        setResult(RESULT_OK, resultIntent);
    }
    
    private void updateColorSelection() {
        for (int i = 0; i < colorViews.length; i++) {
            View view = colorViews[i];
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(Color.parseColor(SettingsManager.ACCOUNT_TAG_COLORS[i]));
            
            if (i == selectedColorIndex) {
                // 选中的添加边框
                drawable.setStroke(4, ContextCompat.getColor(this, R.color.primary));
            }
            
            view.setBackground(drawable);
        }
    }

    private void loadSettings() {
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
        
        // 加载账户标签颜色设置
        selectedColorIndex = settingsManager.getAccountTagColorIndex();
        updateColorSelection();
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
