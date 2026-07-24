package net.wsdjeg.nova;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity {
    
    private static final String TAG = "SettingsActivity";
    
    public static final String EXTRA_SETTINGS_SAVED = "settings_saved";
    public static final String EXTRA_THEME_CHANGED = "theme_changed";
    public static final String EXTRA_COLOR_CHANGED = "color_changed";
    
    // 表示"留空/服务端默认"的占位字符串
    private String getEmptyOption() { return getString(R.string.empty_option); } // Localized "empty" option
    
    private RadioGroup rgTheme;
    private RadioButton rbSystem, rbLight, rbDark;
    private LinearLayout colorPickerContainer;
    private Spinner spinnerProvider, spinnerModel;
    private ProgressBar progressBar;
    private TextView tvProviderStatus;
    private SettingsManager settingsManager;
    private AccountManager accountManager;
    private ApiClient apiClient;
    
    private int selectedColorIndex = 2; // 默认蓝色
    private View[] colorViews;
    
    // Provider 和 Model 数据
    private List<ApiClient.Provider> providers;
    private Map<String, List<String>> providerModelsMap;
    private List<String> providerNames;
    private List<String> currentModels;
    
    private ArrayAdapter<String> providerAdapter;
    private ArrayAdapter<String> modelAdapter;
    
    private boolean isProviderLoaded = false;
    private boolean isInitializingSpinner = false;
    private int selectedProviderIndex = -1;
    private int selectedModelIndex = -1;
    
    // 保存的默认 provider/model（来自 SettingsManager）
    private String savedProvider;
    private String savedModel;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        // 设置 Toolbar 作为 ActionBar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        
        settingsManager = new SettingsManager(this);
        accountManager = AccountManager.getInstance(this);
        
        initViews();
        loadSettings();
    }
    
    private void initViews() {
        rgTheme = findViewById(R.id.rg_theme);
        rbSystem = findViewById(R.id.rb_theme_system);
        rbLight = findViewById(R.id.rb_theme_light);
        rbDark = findViewById(R.id.rb_theme_dark);
        colorPickerContainer = findViewById(R.id.color_picker_container);
        spinnerProvider = findViewById(R.id.spinner_provider);
        spinnerModel = findViewById(R.id.spinner_model);
        progressBar = findViewById(R.id.progress_bar);
        tvProviderStatus = findViewById(R.id.tv_provider_status);
        
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
        
        // 初始化 Spinner adapters
        providerNames = new ArrayList<>();
        currentModels = new ArrayList<>();
        providerModelsMap = new HashMap<>();
        
        providerAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, providerNames);
        providerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProvider.setAdapter(providerAdapter);
        
        modelAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, currentModels);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerModel.setAdapter(modelAdapter);
        
        // Provider 选择监听
        spinnerProvider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isProviderLoaded && !isInitializingSpinner && position >= 0 && position < providerNames.size()) {
                    if (position != selectedProviderIndex) {
                        String selectedProvider = providerNames.get(position);
                        // 如果选的是"留空"选项，model 也重置为"留空"
                        if (getEmptyOption().equals(selectedProvider)) {
                            currentModels.clear();
                            currentModels.add(getEmptyOption());
                            modelAdapter.notifyDataSetChanged();
                            spinnerModel.setSelection(0);
                            selectedModelIndex = 0;
                        } else {
                            updateModelSpinner(selectedProvider, null);
                        }
                        selectedProviderIndex = position;
                        Log.d(TAG, "User selected provider: " + selectedProvider);
                    }
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        
        // Model 选择监听
        spinnerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < currentModels.size()) {
                    selectedModelIndex = position;
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        
        // 初始化颜色选择器
        initColorPicker();
    }
    
    /**
     * 初始化颜色选择器
     */
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
        container.setOnClickListener(v -> selectColor(0));
        
        return container;
    }
    
    private void selectColor(int index) {
        selectedColorIndex = index;
        int storageIndex = (index == 0) ? SettingsManager.AUTO_COLOR_INDEX : index - 1;
        settingsManager.setAccountTagColorIndex(storageIndex);
        updateColorSelection();
        
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
                drawable.setColors(new int[] {
                    Color.parseColor("#FF6B6B"),
                    Color.parseColor("#4ECDC4"),
                    Color.parseColor("#45B7D1"),
                    Color.parseColor("#F7DC6F")
                });
                drawable.setGradientType(GradientDrawable.SWEEP_GRADIENT);
            } else {
                drawable.setColor(Color.parseColor(SettingsManager.ACCOUNT_TAG_COLORS[i - 1]));
            }
            
            if (i == selectedColorIndex) {
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
        selectedColorIndex = (storedIndex == SettingsManager.AUTO_COLOR_INDEX) ? 0 : storedIndex + 1;
        updateColorSelection();
        
        // 加载已保存的默认 provider 和 model
        savedProvider = settingsManager.getDefaultProvider();
        savedModel = settingsManager.getDefaultModel();
        
        // 获取当前活跃账号，创建 ApiClient 从服务器获取 provider/model 列表
        Account account = accountManager.getActiveAccount();
        if (account != null) {
            apiClient = new ApiClient(account.getUrl(), account.getApiKey());
            loadProviders();
        } else {
            // 没有账号时，仍然显示已保存的值
            tvProviderStatus.setText(getString(R.string.no_account_no_list));
            setupSpinnersWithoutApi();
        }
    }
    
    /**
     * 没有账号/API 不可用时，使用已保存的值初始化 spinner
     */
    private void setupSpinnersWithoutApi() {
        providerNames.clear();
        providerModelsMap.clear();
        
        // 添加"留空"选项
        providerNames.add(getEmptyOption());
        // 如果有已保存的 provider 且不是空，添加到列表
        if (savedProvider != null && !savedProvider.isEmpty()) {
            providerNames.add(savedProvider);
            List<String> models = new ArrayList<>();
            models.add(getEmptyOption());
            if (savedModel != null && !savedModel.isEmpty()) {
                models.add(savedModel);
            }
            providerModelsMap.put(savedProvider, models);
        }
        providerAdapter.notifyDataSetChanged();
        
        // 设置当前选择
        isInitializingSpinner = true;
        if (savedProvider != null && !savedProvider.isEmpty()) {
            int idx = providerNames.indexOf(savedProvider);
            if (idx >= 0) {
                spinnerProvider.setSelection(idx);
                selectedProviderIndex = idx;
                updateModelSpinner(savedProvider, savedModel);
            }
        } else {
            spinnerProvider.setSelection(0);
            selectedProviderIndex = 0;
            currentModels.clear();
            currentModels.add(getEmptyOption());
            modelAdapter.notifyDataSetChanged();
            spinnerModel.setSelection(0);
            selectedModelIndex = 0;
        }
        isProviderLoaded = true;
        isInitializingSpinner = false;
    }
    
    /**
     * 从服务器获取 providers 列表
     */
    private void loadProviders() {
        progressBar.setVisibility(View.VISIBLE);
        tvProviderStatus.setText(getString(R.string.loading_providers_models));
        
        apiClient.getProviders(new ApiClient.ProvidersCallback() {
            @Override
            public void onSuccess(List<ApiClient.Provider> providersList) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvProviderStatus.setText("");
                    
                    providers = providersList;
                    providerNames.clear();
                    providerModelsMap.clear();
                    
                    // 第一个选项是"留空 / 服务端默认"
                    providerNames.add(getEmptyOption());
                    
                    for (ApiClient.Provider provider : providers) {
                        providerNames.add(provider.name);
                        providerModelsMap.put(provider.name, provider.models);
                    }
                    
                    providerAdapter.notifyDataSetChanged();
                    
                    // 设置当前保存的 provider/model
                    setCurrentSelection();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvProviderStatus.setText(getString(R.string.load_providers_models_failed, error));
                    
                    // 使用已保存的值备用
                    setupSpinnersWithoutApi();
                });
            }
        });
    }
    
    /**
     * 设置当前保存的 provider/model 选择
     */
    private void setCurrentSelection() {
        isInitializingSpinner = true;
        
        Log.d(TAG, "setCurrentSelection: savedProvider=" + savedProvider + ", savedModel=" + savedModel);
        
        if (savedProvider != null && !savedProvider.isEmpty()) {
            int providerIndex = providerNames.indexOf(savedProvider);
            if (providerIndex >= 0) {
                selectedProviderIndex = providerIndex;
                spinnerProvider.setSelection(providerIndex);
                updateModelSpinner(savedProvider, savedModel);
                Log.d(TAG, "Set provider to index " + providerIndex + ": " + savedProvider);
            } else {
                // 保存的 provider 不在列表中，添加到列表
                providerNames.add(savedProvider);
                List<String> models = new ArrayList<>();
                models.add(getEmptyOption());
                if (savedModel != null && !savedModel.isEmpty()) {
                    models.add(savedModel);
                }
                providerModelsMap.put(savedProvider, models);
                providerAdapter.notifyDataSetChanged();
                
                selectedProviderIndex = providerNames.size() - 1;
                spinnerProvider.setSelection(selectedProviderIndex);
                updateModelSpinner(savedProvider, savedModel);
                Log.d(TAG, "Added missing provider: " + savedProvider);
            }
        } else {
            // 没有保存的 provider，选择"留空"
            selectedProviderIndex = 0;
            spinnerProvider.setSelection(0);
            currentModels.clear();
            currentModels.add(getEmptyOption());
            modelAdapter.notifyDataSetChanged();
            spinnerModel.setSelection(0);
            selectedModelIndex = 0;
        }
        
        isProviderLoaded = true;
        isInitializingSpinner = false;
        
        Log.d(TAG, "setCurrentSelection done: isProviderLoaded=" + isProviderLoaded);
    }
    
    /**
     * 更新 Model Spinner
     * @param providerName provider 名称（可能是 getEmptyOption()）
     * @param selectModel 要选择的 model，如果为 null 则选择第一个
     */
    private void updateModelSpinner(String providerName, String selectModel) {
        currentModels.clear();
        
        // 添加"留空"选项
        currentModels.add(getEmptyOption());
        
        if (!getEmptyOption().equals(providerName)) {
            List<String> models = providerModelsMap.get(providerName);
            if (models != null) {
                currentModels.addAll(models);
            }
        }
        modelAdapter.notifyDataSetChanged();
        
        // 选择指定的 model 或第一个
        if (selectModel != null && !selectModel.isEmpty()) {
            int modelIndex = currentModels.indexOf(selectModel);
            if (modelIndex >= 0) {
                spinnerModel.setSelection(modelIndex);
                selectedModelIndex = modelIndex;
            } else {
                // 保存的 model 不在列表中，添加到列表并选择
                currentModels.add(0, selectModel);
                modelAdapter.notifyDataSetChanged();
                spinnerModel.setSelection(0);
                selectedModelIndex = 0;
            }
        } else {
            spinnerModel.setSelection(0);
            selectedModelIndex = 0;
        }
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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.settings_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_save) {
            saveSettings();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * 保存设置
     */
    private void saveSettings() {
        String provider = "";
        String model = "";
        
        if (selectedProviderIndex >= 0 && selectedProviderIndex < providerNames.size()) {
            String selectedProvider = providerNames.get(selectedProviderIndex);
            if (!getEmptyOption().equals(selectedProvider)) {
                provider = selectedProvider;
            }
        }
        
        if (selectedModelIndex >= 0 && selectedModelIndex < currentModels.size()) {
            String selectedModel = currentModels.get(selectedModelIndex);
            if (!getEmptyOption().equals(selectedModel)) {
                model = selectedModel;
            }
        }
        
        // 如果 provider 为空，model 也清空
        if (provider.isEmpty()) {
            model = "";
        }
        
        settingsManager.setDefaultProvider(provider);
        settingsManager.setDefaultModel(model);
        
        // 通知设置已保存
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_SETTINGS_SAVED, true);
        setResult(RESULT_OK, resultIntent);
        
        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Saved: provider=" + provider + ", model=" + model);
    }
}

