package net.wsdjeg.nova;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
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
    private EditText etDefaultProvider, etDefaultModel;
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
        etDefaultProvider = findViewById(R.id.et_default_provider);
        etDefaultModel = findViewById(R.id.et_default_model);
        
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
        
        // Provider 输入监听 - 保存设置
        etDefaultProvider.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String provider = etDefaultProvider.getText().toString().trim();
                settingsManager.setDefaultProvider(provider);
            }
        });
        
        // Model 输入监听 - 保存设置
        etDefaultModel.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String model = etDefaultModel.getText().toString().trim();
                settingsManager.setDefaultModel(model);
            }
        });
        
        // 初始化颜色选择器
        initColorPicker();
    }
    
    private void initColorPicker() {
        int size = (int) (40 * getResources().getDisplayMetrics().density);
        int margin = (int) (8 * getResources().getDisplayMetrics().density);
        
        // 创建9个选项：8个颜色 + 1个自动
        colorViews = new View[9];
        
        // 第一个选项：自动分配
        View autoView = createAutoColorView(size, margin);
        colorPickerContainer.addView(autoView);
        colorViews[0] = autoView;
        
        // 后面8个颜色选项
        for (int i = 0; i < SettingsManager.ACCOUNT_TAG_COLORS.length; i++) {
            View colorView = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(margin, 0, margin, 0);
            colorView.setLayoutParams(params);
            
            // 设置圆形背景
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(Color.parseColor(SettingsManager.ACCOUNT_TAG_COLORS[i]));
            colorView.setBackground(drawable);
            
            final int index = i + 1; // 偏移1，因为第一个是自动
            colorView.setOnClickListener(v -> selectColor(index));
            
            colorPickerContainer.addView(colorView);
            colorViews[i + 1] = colorView;
        }
    }
    
    /**
     * 创建"自动"颜色选项视图
     */
    private View createAutoColorView(int size, int margin) {
        // 使用 FrameLayout 包含图标和背景
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(margin, 0, margin, 0);
        container.setLayoutParams(params);
        
        // 创建渐变背景（彩虹效果表示自动）
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColors(new int[] {
            Color.parseColor("#FF6B6B"),
            Color.parseColor("#4ECDC4"),
            Color.parseColor("#45B7D1"),
            Color.parseColor("#F7DC6F")
        });
        drawable.setGradientType(GradientDrawable.SWEEP_GRADIENT);
        container.setBackground(drawable);
        
        // 添加自动图标（使用文本 "A" 表示）
        TextView autoText = new TextView(this);
        autoText.setText("A");
        autoText.setTextColor(Color.WHITE);
        autoText.setTextSize(14);
        autoText.setGravity(android.view.Gravity.CENTER);
        autoText.setTypeface(null, android.graphics.Typeface.BOLD);
        
        android.widget.FrameLayout.LayoutParams textParams = 
            new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            );
        textParams.gravity = android.view.Gravity.CENTER;
        autoText.setLayoutParams(textParams);
        
        container.addView(autoText);
        container.setOnClickListener(v -> selectColor(0)); // 0 表示自动
        
        return container;
    }
    
    private void selectColor(int index) {
        selectedColorIndex = index;
        // 存储颜色索引（0 表示自动，存储为 -1）
        int storageIndex = (index == 0) ? SettingsManager.AUTO_COLOR_INDEX : index - 1;
        settingsManager.setAccountTagColorIndex(storageIndex);
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
            
            if (i == 0) {
                // 自动选项：渐变背景
                drawable.setColors(new int[] {
                    Color.parseColor("#FF6B6B"),
                    Color.parseColor("#4ECDC4"),
                    Color.parseColor("#45B7D1"),
                    Color.parseColor("#F7DC6F")
                });
                drawable.setGradientType(GradientDrawable.SWEEP_GRADIENT);
            } else {
                // 颜色选项
                drawable.setColor(Color.parseColor(SettingsManager.ACCOUNT_TAG_COLORS[i - 1]));
            }
            
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
        int storedIndex = settingsManager.getAccountTagColorIndex();
        // 转换存储索引到显示索引：-1 -> 0（自动），0-7 -> 1-8
        selectedColorIndex = (storedIndex == SettingsManager.AUTO_COLOR_INDEX) ? 0 : storedIndex + 1;
        updateColorSelection();
        
        // 加载默认 provider 和 model
        etDefaultProvider.setText(settingsManager.getDefaultProvider());
        etDefaultModel.setText(settingsManager.getDefaultModel());
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
            // 保存 provider 和 model 设置
            String provider = etDefaultProvider.getText().toString().trim();
            String model = etDefaultModel.getText().toString().trim();
            settingsManager.setDefaultProvider(provider);
            settingsManager.setDefaultModel(model);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // 确保在离开时保存设置
        if (etDefaultProvider != null && etDefaultModel != null) {
            String provider = etDefaultProvider.getText().toString().trim();
            String model = etDefaultModel.getText().toString().trim();
            settingsManager.setDefaultProvider(provider);
            settingsManager.setDefaultModel(model);
        }
    }
}
