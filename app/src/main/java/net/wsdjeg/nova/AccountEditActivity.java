package net.wsdjeg.nova;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * 账号编辑界面
 * 用于添加新账号或编辑现有账号
 */
public class AccountEditActivity extends AppCompatActivity {
    
    public static final String EXTRA_ACCOUNT_ID = "account_id";
    public static final String EXTRA_ACCOUNT_NAME = "account_name";
    public static final String EXTRA_ACCOUNT_HOST = "account_host";
    public static final String EXTRA_ACCOUNT_PORT = "account_port";
    public static final String EXTRA_ACCOUNT_API_KEY = "account_api_key";
    public static final String EXTRA_ACCOUNT_COLOR_INDEX = "account_color_index";
    
    private Toolbar toolbar;
    private EditText etName;
    private EditText etHost;
    private EditText etPort;
    private EditText etApiKey;
    private Button btnSave;
    private Button btnTest;
    private Button btnDelete;
    
    private AccountManager accountManager;
    private String accountId;  // 如果是编辑模式，保存账号ID
    private boolean isEditMode = false;
    
    private TagColorPicker tagColorPicker;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_edit);
        
        accountManager = AccountManager.getInstance(this);
        
        initViews();
        initColorPicker();
        loadAccountData();
        setupListeners();
    }
    
    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.title_account_add);
        
        etName = findViewById(R.id.et_account_name);
        etHost = findViewById(R.id.et_host);
        etPort = findViewById(R.id.et_port);
        etApiKey = findViewById(R.id.et_api_key);
        btnSave = findViewById(R.id.btn_save);
        btnTest = findViewById(R.id.btn_test_connection);
        btnDelete = findViewById(R.id.btn_delete);
    }
    
    /**
     * 初始化标签颜色取色器
     * 颜色索引使用存储语义：-1 = 跟随全局设置，0-4 = 固定颜色
     * （与 SettingsManager.ACCOUNT_TAG_COLORS 的下标一致）
     */
    private void initColorPicker() {
        LinearLayout container = findViewById(R.id.color_picker_container);
        // 保存时通过 tagColorPicker.getSelected() 取值，无需监听回调
        tagColorPicker = new TagColorPicker(this, container, null, 36, 6, null);
    }
    
    private void loadAccountData() {
        Intent intent = getIntent();
        accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID);
        
        if (accountId != null && !accountId.isEmpty()) {
            // 根据 accountId 从 AccountManager 获取账号信息
            Account account = accountManager.getAccountById(accountId);
            
            if (account != null) {
                isEditMode = true;
                getSupportActionBar().setTitle(R.string.title_account_edit);
                
                etName.setText(account.getName());
                etHost.setText(account.getHost());
                
                int port = account.getPort();
                if (port > 0) {
                    etPort.setText(String.valueOf(port));
                }
                
                etApiKey.setText(account.getApiKey());
                tagColorPicker.setSelected(account.getColorIndex());
                
                btnDelete.setVisibility(View.VISIBLE);
            } else {
                // 账号不存在，回退到 Intent 数据（兼容旧方式）
                isEditMode = true;
                getSupportActionBar().setTitle(R.string.title_account_edit);
                
                etName.setText(intent.getStringExtra(EXTRA_ACCOUNT_NAME));
                etHost.setText(intent.getStringExtra(EXTRA_ACCOUNT_HOST));
                
                int port = intent.getIntExtra(EXTRA_ACCOUNT_PORT, 0);
                if (port > 0) {
                    etPort.setText(String.valueOf(port));
                }
                
                etApiKey.setText(intent.getStringExtra(EXTRA_ACCOUNT_API_KEY));
                
                tagColorPicker.setSelected(intent.getIntExtra(EXTRA_ACCOUNT_COLOR_INDEX, -1));
                
                btnDelete.setVisibility(View.VISIBLE);
            }
        }
    }
    
    private void setupListeners() {
        btnSave.setOnClickListener(v -> saveAccount());
        btnTest.setOnClickListener(v -> testConnection());
        btnDelete.setOnClickListener(v -> showDeleteConfirmDialog());
    }
    
    private void saveAccount() {
        String name = etName.getText().toString().trim();
        String host = etHost.getText().toString().trim();
        String portStr = etPort.getText().toString().trim();
        String apiKey = etApiKey.getText().toString().trim();
        
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, getString(R.string.please_enter_name), Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (TextUtils.isEmpty(host)) {
            Toast.makeText(this, getString(R.string.please_enter_server), Toast.LENGTH_SHORT).show();
            return;
        }
        
        int port = 8080; // 默认端口
        if (!TextUtils.isEmpty(portStr)) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, getString(R.string.port_format_error), Toast.LENGTH_SHORT).show();
                return;
            }
        }
        
        // 取色器返回的已归一化颜色索引：-1（跟随全局）或 0-4
        int colorIndex = tagColorPicker.getSelected();
        
        if (isEditMode) {
            // 编辑模式：更新现有账号
            Account account = accountManager.getAccountById(accountId);
            if (account != null) {
                account.setName(name);
                account.setHost(host);
                account.setPort(port);
                account.setApiKey(apiKey);
                account.setColorIndex(colorIndex);
                accountManager.updateAccount(account);
                Toast.makeText(this, getString(R.string.account_updated), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.account_not_exist), Toast.LENGTH_SHORT).show();
            }
        } else {
            // 新增模式：创建新账号
            Account account = new Account();
            account.setName(name);
            account.setHost(host);
            account.setPort(port);
            account.setApiKey(apiKey);
            account.setColorIndex(colorIndex);
            accountManager.addAccount(account);
            
            // addAccount 已经会自动处理第一个账号为默认的情况
            Toast.makeText(this, getString(R.string.account_added), Toast.LENGTH_SHORT).show();
        }
        
        setResult(RESULT_OK);
        finish();
    }
    
    private void testConnection() {
        String host = etHost.getText().toString().trim();
        String portStr = etPort.getText().toString().trim();
        String apiKey = etApiKey.getText().toString().trim();
        
        if (TextUtils.isEmpty(host)) {
            Toast.makeText(this, getString(R.string.please_enter_server), Toast.LENGTH_SHORT).show();
            return;
        }
        
        int port = 8080;
        if (!TextUtils.isEmpty(portStr)) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, getString(R.string.port_format_error), Toast.LENGTH_SHORT).show();
                return;
            }
        }
        
        // 构建完整 URL
        String url = host;
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        if (port != 80 && port != 443 && !host.contains(":")) {
            url = url + ":" + port;
        }

        // 使用 ApiCallback 而不是 TestConnectionCallback
        ApiClient.testConnection(url, apiKey, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String message) {
                runOnUiThread(() -> {
                    btnTest.setEnabled(true);
                    btnTest.setText(getString(R.string.test_connection));
                    Toast.makeText(AccountEditActivity.this, 
                        getString(R.string.connection_success, message), Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    btnTest.setEnabled(true);
                    btnTest.setText(getString(R.string.test_connection));
                    Toast.makeText(AccountEditActivity.this, 
                        getString(R.string.connection_failed, error), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void showDeleteConfirmDialog() {
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_account_title))
            .setMessage(getString(R.string.delete_account_confirm))
            .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                accountManager.removeAccount(accountId);
                Toast.makeText(this, getString(R.string.account_deleted), Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            })
            .setNegativeButton(getString(R.string.cancel), null)
            .show();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.account_edit_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
}

