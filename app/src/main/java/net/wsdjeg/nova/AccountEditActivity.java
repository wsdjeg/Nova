package net.wsdjeg.nova;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 账号编辑界面
 * 用于添加新账号或编辑现有账号
 */
public class AccountEditActivity extends AppCompatActivity {
    
    public static final String EXTRA_ACCOUNT_ID = "account_id";
    public static final String EXTRA_ACCOUNT_NAME = "account_name";
    public static final String EXTRA_ACCOUNT_URL = "account_url";
    public static final String EXTRA_ACCOUNT_API_KEY = "account_api_key";
    
    private EditText etName;
    private EditText etUrl;
    private EditText etApiKey;
    private Button btnSave;
    private Button btnTest;
    private Button btnDelete;
    
    private AccountManager accountManager;
    private String accountId;  // 如果是编辑模式，保存账号ID
    private boolean isEditMode = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_edit);
        
        accountManager = AccountManager.getInstance(this);
        
        initViews();
        loadAccountData();
        setupListeners();
    }
    
    private void initViews() {
        etName = findViewById(R.id.et_account_name);
        etUrl = findViewById(R.id.et_account_url);
        etApiKey = findViewById(R.id.et_account_api_key);
        btnSave = findViewById(R.id.btn_save);
        btnTest = findViewById(R.id.btn_test);
        btnDelete = findViewById(R.id.btn_delete);
        
        // 设置返回按钮
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }
    
    private void loadAccountData() {
        Intent intent = getIntent();
        accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID);
        
        if (accountId != null && !accountId.isEmpty()) {
            // 编辑模式
            isEditMode = true;
            String name = intent.getStringExtra(EXTRA_ACCOUNT_NAME);
            String url = intent.getStringExtra(EXTRA_ACCOUNT_URL);
            String apiKey = intent.getStringExtra(EXTRA_ACCOUNT_API_KEY);
            
            etName.setText(name);
            etUrl.setText(url);
            etApiKey.setText(apiKey);
            
            setTitle("编辑账号");
            btnDelete.setVisibility(Button.VISIBLE);
        } else {
            // 添加模式
            isEditMode = false;
            setTitle("添加账号");
            btnDelete.setVisibility(Button.GONE);
            
            // 设置默认URL
            etUrl.setText("http://192.168.1.100:8080");
        }
    }
    
    private void setupListeners() {
        btnSave.setOnClickListener(v -> saveAccount());
        btnTest.setOnClickListener(v -> testConnection());
        btnDelete.setOnClickListener(v -> deleteAccount());
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.account_edit_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void saveAccount() {
        String name = etName.getText().toString().trim();
        String url = etUrl.getText().toString().trim();
        String apiKey = etApiKey.getText().toString().trim();
        
        // 验证输入
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
            etUrl.requestFocus();
            return;
        }
        
        // 验证URL格式
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(this, "服务器地址必须以 http:// 或 https:// 开头", Toast.LENGTH_SHORT).show();
            etUrl.requestFocus();
            return;
        }
        
        // 保存账号
        if (isEditMode) {
            // 更新现有账号
            Account account = accountManager.getAccount(accountId);
            if (account != null) {
                account.setName(name);
                account.setUrl(url);
                account.setApiKey(apiKey);
                accountManager.updateAccount(account);
                
                Toast.makeText(this, "账号已更新", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }
        } else {
            // 创建新账号
            Account account = new Account(name, url, apiKey);
            accountManager.addAccount(account);
            
            Toast.makeText(this, "账号已添加", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        }
    }
    
    private void testConnection() {
        String url = etUrl.getText().toString().trim();
        
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "正在测试连接...", Toast.LENGTH_SHORT).show();
        
        // 使用ApiClient测试连接
        ApiClient client = new ApiClient(this);
        client.setServerUrl(url);
        
        client.testConnection(new ApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                runOnUiThread(() -> {
                    new AlertDialog.Builder(AccountEditActivity.this)
                        .setTitle("连接成功")
                        .setMessage("服务器响应正常")
                        .setPositiveButton("确定", null)
                        .show();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    new AlertDialog.Builder(AccountEditActivity.this)
                        .setTitle("连接失败")
                        .setMessage(error)
                        .setPositiveButton("确定", null)
                        .show();
                });
            }
        });
    }
    
    private void deleteAccount() {
        if (!isEditMode || accountId == null) {
            return;
        }
        
        new AlertDialog.Builder(this)
            .setTitle("删除账号")
            .setMessage("确定要删除这个账号吗？\n相关会话数据将被保留，但需要重新关联账号。")
            .setPositiveButton("删除", (dialog, which) -> {
                accountManager.removeAccount(accountId);
                Toast.makeText(this, "账号已删除", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            })
            .setNegativeButton("取消", null)
            .show();
    }
}
