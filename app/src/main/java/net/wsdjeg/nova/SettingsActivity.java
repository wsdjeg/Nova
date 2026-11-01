package net.wsdjeg.nova;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用设置页面（新建会话默认 provider/model、外观、语言等）
 *
 * 模型列表延迟加载：chat.nvim 服务端的模型列表为异步加载，首次请求 GET /providers 时
 * 部分 provider 的 models 为空数组。选中此类 provider 时，model 下拉框显示占位项并
 * 静默轮询拉取，模型就绪后自动填充（不重置用户当前的选择）。
 */
public class SettingsActivity extends AppCompatActivity {
    
    private static final String TAG = "SettingsActivity";
    
    public static final String EXTRA_SETTINGS_SAVED = "settings_saved";
    public static final String EXTRA_THEME_CHANGED = "theme_changed";
    public static final String EXTRA_COLOR_CHANGED = "color_changed";
    public static final String EXTRA_LANGUAGE_CHANGED = "language_changed";
    
    // 表示"留空/服务端默认"的占位字符串
    private String getEmptyOption() { return getString(R.string.empty_option); } // Localized "empty" option
    
    private RadioGroup rgTheme;
    private RadioButton rbSystem, rbLight, rbDark;
    private RadioGroup rgLanguage;
    private RadioButton rbLangSystem, rbLangEnglish, rbLangChinese;
    private LinearLayout colorPickerContainer;
    private Spinner spinnerProvider, spinnerModel;
    private ProgressBar progressBar;
    private TextView tvProviderStatus;
    private Button btnWeChatLogin;
    private SettingsManager settingsManager;
    private AccountManager accountManager;
    private ApiClient apiClient;
    
    private TagColorPicker tagColorPicker;
    
    // Provider 和 Model 数据
    private List<Provider> providers;
    private Map<String, List<String>> providerModelsMap;
    private List<String> providerNames;
    private List<String> currentModels;
    
    private ArrayAdapter<String> providerAdapter;
    private ArrayAdapter<String> modelAdapter;
    
    private boolean isProviderLoaded = false;
    private boolean isInitializingSpinner = false;
    private boolean isInitializingLanguage = false;
    private int selectedProviderIndex = -1;
    private int selectedModelIndex = -1;
    
    // 保存的默认 provider/model（来自 SettingsManager）
    private String savedProvider;
    private String savedModel;
    
    // 模型列表延迟加载轮询（见类注释）
    private static final int MODELS_MAX_RETRY = 10;
    private static final long MODELS_RETRY_INTERVAL_MS = 2000L;
    private final Handler modelsRetryHandler = new Handler(Looper.getMainLooper());
    private int modelsRetryCount = 0;
    
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
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelModelsRetry();
    }
    
    private void initViews() {
        rgTheme = findViewById(R.id.rg_theme);
        rbSystem = findViewById(R.id.rb_theme_system);
        rbLight = findViewById(R.id.rb_theme_light);
        rbDark = findViewById(R.id.rb_theme_dark);
        rgLanguage = findViewById(R.id.rg_language);
        rbLangSystem = findViewById(R.id.rb_language_system);
        rbLangEnglish = findViewById(R.id.rb_language_english);
        rbLangChinese = findViewById(R.id.rb_language_chinese);
        colorPickerContainer = findViewById(R.id.color_picker_container);
        spinnerProvider = findViewById(R.id.spinner_provider);
        spinnerModel = findViewById(R.id.spinner_model);
        progressBar = findViewById(R.id.progress_bar);
        tvProviderStatus = findViewById(R.id.tv_provider_status);
        btnWeChatLogin = findViewById(R.id.btn_weixin_login);

        // 微信登录按钮
        btnWeChatLogin.setOnClickListener(v -> {
            Intent intent = new Intent(this, WeChatLoginActivity.class);
            startActivity(intent);
        });
        
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
        
        // 语言选择监听
        rgLanguage.setOnCheckedChangeListener((group, checkedId) -> {
            if (isInitializingLanguage) return;
            int language;
            if (checkedId == R.id.rb_language_system) {
                language = SettingsManager.LANGUAGE_SYSTEM;
            } else if (checkedId == R.id.rb_language_english) {
                language = SettingsManager.LANGUAGE_ENGLISH;
            } else {
                language = SettingsManager.LANGUAGE_CHINESE;
            }
            settingsManager.setLanguage(language);
            
            // 通知需要重建界面
            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_LANGUAGE_CHANGED, true);
            setResult(RESULT_OK, resultIntent);
            
            // applyLanguage 内部调用 AppCompatDelegate.setApplicationLocales()
            // 该方法会自动触发 Activity 重建，无需手动调用 recreate()
            // 手动 recreate() 会与 setApplicationLocales() 的重建冲突，
            // 导致重建时 locale 尚未更新，UI 仍显示旧语言
            SettingsManager.applyLanguage(language);
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
                            cancelModelsRetry();
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
     * 圆点尺寸与 AccountEditActivity 保持一致（36dp / 6dp 间距），
     * 保证"自动 + 5 色"在任何屏宽下都能完整显示
     */
    private void initColorPicker() {
        tagColorPicker = new TagColorPicker(this, colorPickerContainer, "A", 36, 6,
                this::onTagColorSelected);
    }
    
    /**
     * 标签颜色选中回调（存储索引语义：-1 = 自动，0-4 = 固定颜色）
     */
    private void onTagColorSelected(int colorIndex) {
        settingsManager.setAccountTagColorIndex(colorIndex);
        
        // 通知需要刷新界面
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_COLOR_CHANGED, true);
        setResult(RESULT_OK, resultIntent);
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
        
        // 加载语言设置
        isInitializingLanguage = true;
        int language = settingsManager.getLanguage();
        switch (language) {
            case SettingsManager.LANGUAGE_ENGLISH:
                rbLangEnglish.setChecked(true);
                break;
            case SettingsManager.LANGUAGE_CHINESE:
                rbLangChinese.setChecked(true);
                break;
            default:
                rbLangSystem.setChecked(true);
                break;
        }
        isInitializingLanguage = false;
        
        // 加载账户标签颜色设置
        tagColorPicker.setSelected(settingsManager.getAccountTagColorIndex());
        
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
            public void onSuccess(List<Provider> providersList) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvProviderStatus.setText("");
                    
                    providers = providersList;
                    providerNames.clear();
                    providerModelsMap.clear();
                    
                    // 第一个选项是"留空 / 服务端默认"
                    providerNames.add(getEmptyOption());
                    
                    for (Provider provider : providers) {
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
     *
     * 服务端模型列表为延迟加载：首次请求 /providers 时部分 provider 的 models 为空数组。
     * 列表为空时显示占位项并静默轮询拉取，模型就绪后自动填充。
     */
    private void updateModelSpinner(String providerName, String selectModel) {
        // 取消进行中的模型轮询（用户可能已切换 provider）
        cancelModelsRetry();
        
        currentModels.clear();
        
        // 添加"留空"选项
        currentModels.add(getEmptyOption());
        
        if (!getEmptyOption().equals(providerName)) {
            List<String> models = providerModelsMap.get(providerName);
            if (models != null) {
                currentModels.addAll(models);
            }
            
            // 除"留空"外没有任何模型：模型列表延迟加载中，显示占位项并轮询
            if (currentModels.size() == 1) {
                currentModels.add(getString(R.string.models_loading));
                modelAdapter.notifyDataSetChanged();
                if (selectModel != null && !selectModel.isEmpty()) {
                    // 已保存的 model 排在占位项之后，优先选中它
                    currentModels.add(selectModel);
                    modelAdapter.notifyDataSetChanged();
                    spinnerModel.setSelection(2);
                    selectedModelIndex = 2;
                } else {
                    spinnerModel.setSelection(0);
                    selectedModelIndex = 0;
                }
                scheduleModelsRetry(providerName, 0);
                return;
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
    
    /**
     * 静默重新拉取 providers，只合并各 provider 的 models 数据，不重置用户当前的选择
     * 用于服务端模型列表延迟加载的场景
     */
    private void fetchModelsSilently(final String providerName) {
        if (apiClient == null) {
            return;
        }
        apiClient.getProviders(new ApiClient.ProvidersCallback() {
            @Override
            public void onSuccess(List<Provider> providersList) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    boolean hasModels = false;
                    for (Provider p : providersList) {
                        if (p.models != null && !p.models.isEmpty()) {
                            providerModelsMap.put(p.name, p.models);
                            if (providerName != null && providerName.equals(p.name)) {
                                hasModels = true;
                            }
                        }
                    }
                    if (!hasModels) {
                        // 模型仍未就绪，继续轮询
                        scheduleModelsRetry(providerName, MODELS_RETRY_INTERVAL_MS);
                        return;
                    }
                    modelsRetryCount = 0;
                    // 仅当轮询目标仍是当前选中的 provider 时刷新，避免覆盖用户已切换的选择
                    if (selectedProviderIndex >= 0 && selectedProviderIndex < providerNames.size()
                            && providerName.equals(providerNames.get(selectedProviderIndex))) {
                        String preserveModel = null;
                        if (selectedModelIndex >= 0 && selectedModelIndex < currentModels.size()) {
                            String m = currentModels.get(selectedModelIndex);
                            if (!getEmptyOption().equals(m)
                                    && !getString(R.string.models_loading).equals(m)) {
                                preserveModel = m;
                            }
                        }
                        updateModelSpinner(providerName, preserveModel);
                        Log.d(TAG, "Models loaded for provider: " + providerName);
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    // 网络错误时静默继续轮询
                    scheduleModelsRetry(providerName, MODELS_RETRY_INTERVAL_MS);
                });
            }
        });
    }
    
    private void scheduleModelsRetry(final String providerName, long delayMs) {
        if (apiClient == null || isFinishing() || isDestroyed()) {
            return;
        }
        if (modelsRetryCount >= MODELS_MAX_RETRY) {
            Log.d(TAG, "Models retry exhausted for provider: " + providerName);
            return;
        }
        modelsRetryCount++;
        modelsRetryHandler.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            fetchModelsSilently(providerName);
        }, delayMs);
    }
    
    private void cancelModelsRetry() {
        modelsRetryHandler.removeCallbacksAndMessages(null);
        modelsRetryCount = 0;
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
            // 占位项（模型列表延迟加载中）不可保存
            if (getString(R.string.models_loading).equals(selectedModel)) {
                Toast.makeText(this, getString(R.string.models_loading_wait), Toast.LENGTH_SHORT).show();
                return;
            }
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

