package net.wsdjeg.nova;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话设置页面
 * 显示当前会话的 provider 和 model，并允许从服务器获取可用的 provider/model 列表
 * 支持修改会话配置并调用 API 更新
 */
public class SessionSettingsActivity extends AppCompatActivity {
    
    private static final String TAG = "SessionSettingsActivity";
    
    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_ACCOUNT_ID = "account_id";
    public static final String EXTRA_PROVIDER = "provider";
    public static final String EXTRA_MODEL = "model";
    public static final String EXTRA_CWD = "cwd";
    
    // Result extras for returning data to caller
    public static final String RESULT_PROVIDER = "result_provider";
    public static final String RESULT_MODEL = "result_model";
    
    private Toolbar toolbar;
    private TextView tvSessionId;
    private TextView tvCwd;
    private Spinner spinnerProvider;
    private Spinner spinnerModel;
    private ProgressBar progressBar;
    private TextView tvStatus;
    
    private String sessionId;
    private String accountId;
    private String currentProvider;
    private String currentModel;
    private String cwd;
    
    private ApiClient apiClient;
    private SettingsManager settingsManager;
    private AccountManager accountManager;
    private SessionManager sessionManager;
    
    // Provider 和 Model 数据
    private List<ApiClient.Provider> providers;
    private Map<String, List<String>> providerModelsMap;  // provider -> models
    private List<String> providerNames;
    private List<String> currentModels;
    
    private ArrayAdapter<String> providerAdapter;
    private ArrayAdapter<String> modelAdapter;
    
    private boolean isProviderLoaded = false;
    private int selectedProviderIndex = -1;
    private int selectedModelIndex = -1;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_settings);
        
        // 获取传入的参数
        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        accountId = getIntent().getStringExtra(EXTRA_ACCOUNT_ID);
        currentProvider = getIntent().getStringExtra(EXTRA_PROVIDER);
        currentModel = getIntent().getStringExtra(EXTRA_MODEL);
        cwd = getIntent().getStringExtra(EXTRA_CWD);
        
        if (sessionId == null || sessionId.isEmpty()) {
            Toast.makeText(this, "无效的会话ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        settingsManager = new SettingsManager(this);
        accountManager = AccountManager.getInstance(this);
        sessionManager = SessionManager.getInstance(this);
        
        initViews();
        loadSessionInfo();
        loadProviders();
    }
    
    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("会话设置");
        
        tvSessionId = findViewById(R.id.tv_session_id);
        tvCwd = findViewById(R.id.tv_cwd);
        spinnerProvider = findViewById(R.id.spinner_provider);
        spinnerModel = findViewById(R.id.spinner_model);
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);
        
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
                if (isProviderLoaded && position >= 0 && position < providerNames.size()) {
                    String selectedProvider = providerNames.get(position);
                    updateModelSpinner(selectedProvider);
                    selectedProviderIndex = position;
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
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.session_settings_menu, menu);
        return true;
    }
    
    private void loadSessionInfo() {
        // 显示会话基本信息
        tvSessionId.setText("会话 ID: " + sessionId);
        tvCwd.setText("工作目录: " + (cwd != null ? cwd : "unknown"));
        
        // 获取账号信息并创建 ApiClient
        Account account = null;
        if (accountId != null && !accountId.isEmpty()) {
            account = accountManager.getAccountById(accountId);
        }
        if (account == null) {
            account = accountManager.getActiveAccount();
        }
        
        if (account == null) {
            tvStatus.setText("无法获取账号信息");
            return;
        }
        
        apiClient = new ApiClient(account.getUrl(), account.getApiKey());
    }
    
    private void loadProviders() {
        if (apiClient == null) {
            tvStatus.setText("未配置账号");
            return;
        }
        
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("正在加载 provider 列表...");
        
        apiClient.getProviders(new ApiClient.ProvidersCallback() {
            @Override
            public void onSuccess(List<ApiClient.Provider> providerList) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("");
                    
                    providers = providerList;
                    providerNames.clear();
                    
                    // 构建数据
                    for (ApiClient.Provider p : providers) {
                        providerNames.add(p.name);
                        providerModelsMap.put(p.name, p.models);
                    }
                    
                    providerAdapter.notifyDataSetChanged();
                    isProviderLoaded = true;
                    
                    // 设置当前 provider/model
                    setCurrentProviderModel();
                    
                    Log.d(TAG, "Loaded " + providers.size() + " providers");
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("加载失败: " + error);
                    
                    // 使用默认的 provider/model 显示
                    providerNames.clear();
                    if (currentProvider != null && !currentProvider.isEmpty()) {
                        providerNames.add(currentProvider);
                    }
                    providerAdapter.notifyDataSetChanged();
                    
                    currentModels.clear();
                    if (currentModel != null && !currentModel.isEmpty()) {
                        currentModels.add(currentModel);
                    }
                    modelAdapter.notifyDataSetChanged();
                });
            }
        });
    }
    
    /**
     * 设置当前的 provider 和 model
     */
    private void setCurrentProviderModel() {
        if (currentProvider != null && !currentProvider.isEmpty()) {
            int providerIndex = providerNames.indexOf(currentProvider);
            if (providerIndex >= 0) {
                spinnerProvider.setSelection(providerIndex);
                updateModelSpinner(currentProvider);
                
                // 设置 model
                if (currentModel != null && !currentModel.isEmpty()) {
                    int modelIndex = currentModels.indexOf(currentModel);
                    if (modelIndex >= 0) {
                        spinnerModel.setSelection(modelIndex);
                    }
                }
            } else {
                // 当前 provider 不在列表中，添加到开头
                providerNames.add(0, currentProvider);
                List<String> models = new ArrayList<>();
                if (currentModel != null && !currentModel.isEmpty()) {
                    models.add(currentModel);
                }
                providerModelsMap.put(currentProvider, models);
                providerAdapter.notifyDataSetChanged();
                spinnerProvider.setSelection(0);
                updateModelSpinner(currentProvider);
            }
        }
    }
    
    /**
     * 更新 Model Spinner
     */
    private void updateModelSpinner(String providerName) {
        List<String> models = providerModelsMap.get(providerName);
        currentModels.clear();
        if (models != null) {
            currentModels.addAll(models);
        }
        modelAdapter.notifyDataSetChanged();
        
        // 默认选择第一个 model
        if (currentModels.size() > 0) {
            spinnerModel.setSelection(0);
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // 返回
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_save) {
            // 保存设置
            saveSessionSettings();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * 保存会话设置
     */
    private void saveSessionSettings() {
        if (!isProviderLoaded) {
            Toast.makeText(this, "数据尚未加载完成", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (selectedProviderIndex < 0 || selectedProviderIndex >= providerNames.size()) {
            Toast.makeText(this, "请选择 Provider", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (selectedModelIndex < 0 || selectedModelIndex >= currentModels.size()) {
            Toast.makeText(this, "请选择 Model", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String newProvider = providerNames.get(selectedProviderIndex);
        String newModel = currentModels.get(selectedModelIndex);
        
        // 检查是否有变化
        if (newProvider.equals(currentProvider) && newModel.equals(currentModel)) {
            Toast.makeText(this, "配置未改变", Toast.LENGTH_SHORT).show();
            return;
        }
        
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("正在保存...");
        
        Log.d(TAG, "Saving session settings: provider=" + newProvider + ", model=" + newModel);
        
        // 调用 API 更新会话配置
        apiClient.updateSession(sessionId, newProvider, newModel, new ApiClient.UpdateSessionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("");
                    
                    // 更新本地 SessionManager
                    Session session = sessionManager.getSession(sessionId);
                    if (session != null) {
                        session.setProvider(newProvider);
                        session.setModel(newModel);
                        sessionManager.updateSession(session);
                    }
                    
                    // 返回结果给调用者
                    Intent result = new Intent();
                    result.putExtra(RESULT_PROVIDER, newProvider);
                    result.putExtra(RESULT_MODEL, newModel);
                    setResult(RESULT_OK, result);
                    
                    Toast.makeText(SessionSettingsActivity.this, "设置已保存", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("");
                    
                    // 如果 API 不支持，也允许保存到本地
                    if (error.contains("PATCH") || error.contains("not supported") || error.contains("404")) {
                        // 保存到本地 SessionManager
                        Session session = sessionManager.getSession(sessionId);
                        if (session != null) {
                            session.setProvider(newProvider);
                            session.setModel(newModel);
                            sessionManager.updateSession(session);
                        }
                        
                        // 返回结果给调用者
                        Intent result = new Intent();
                        result.putExtra(RESULT_PROVIDER, newProvider);
                        result.putExtra(RESULT_MODEL, newModel);
                        setResult(RESULT_OK, result);
                        
                        Toast.makeText(SessionSettingsActivity.this, 
                            "已保存到本地（服务端不支持远程更新）", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Toast.makeText(SessionSettingsActivity.this, 
                            "保存失败: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}
