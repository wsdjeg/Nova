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
import android.widget.RadioButton;
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
    public static final String EXTRA_ACCOUNT_HOST = "account_host";
    public static final String EXTRA_ACCOUNT_PORT = "account_port";
    public static final String EXTRA_ACCOUNT_API_KEY = "account_api_key";
    public static final String EXTRA_ACCOUNT_COLOR_INDEX = "account_color_index";
    
    private EditText etName;
    private EditText etHost;
    private EditText etPort;
    private EditText etApiKey;
    private RadioButton rbColorDefault;
    private RadioButton[] rbColors = new RadioButton[8];
    private LinearLayout[] colorLayouts = new LinearLayout[9]; // 0-7 for colors, 8 for default
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
        etHost = findViewById(R.id.et_host);
        etPort = findViewById(R.id.et_port);
        etApiKey = findViewById(R.id.et_api_key);
        
        // 默认选项
        rbColorDefault = findViewById(R.id.rb_color_default);
        colorLayouts[8] = findViewById(R.id.color_default);
        
        // 颜色选项
        rbColors[0] = findViewById(R.id.rb_color_0);
        rbColors[1] = findViewById(R.id.rb_color_1);
        rbColors[2] = findViewById(R.id.rb_color_2);
        rbColors[3] = findViewById(R.id.rb_color_3);
        rbColors[4] = findViewById(R.id.rb_color_4);
        rbColors[5] = findViewById(R.id.rb_color_5);
        rbColors[6] = findViewById(R.id.rb_color_6);
        rbColors[7] = findViewById(R.id.rb_color_7);
        
        colorLayouts[0] = findViewById(R.id.color_0);
        colorLayouts[1] = findViewById(R.id.color_1);
        colorLayouts[2] = findViewById(R.id.color_2);
        colorLayouts[3] = findViewById(R.id.color_3);
        colorLayouts[4] = findViewById(R.id.color_4);
        colorLayouts[5] = findViewById(R.id.color_5);
        colorLayouts[6] = findViewById(R.id.color_6);
        colorLayouts[7] = findViewById(R.id.color_7);
        
        btnSave = findViewById(R.id.btn_save);
        btnTest = findViewById(R.id.btn_test_connection);
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
            String host = intent.getStringExtra(EXTRA_ACCOUNT_HOST);
            int port = intent.getIntExtra(EXTRA_ACCOUNT_PORT, 8080);
            String apiKey = intent.getStringExtra(EXTRA_ACCOUNT_API_KEY);
            int colorIndex = intent.getIntExtra(EXTRA_ACCOUNT_COLOR_INDEX, -1);
            
            etName.setText(name);
            etHost.setText(host);
            etPort.setText(String.valueOf(port));
            etApiKey.setText(apiKey);
            
            // 设置颜色选择
            selectColorRadioButton(colorIndex);
            
            setTitle("编辑账号");
            btnDelete.setVisibility(Button.VISIBLE);
        } else {
            // 添加模式
            isEditMode = false;
            setTitle("添加账号");
            btnDelete.setVisibility(Button.GONE);
            
            // 设置默认端口
            etPort.setText("8080");
        }
    }
    
    private void selectColorRadioButton(int colorIndex) {
        clearAllColorSelections();
        if (colorIndex < 0 || colorIndex >= 8) {
            rbColorDefault.setChecked(true);
        } else {
            rbColors[colorIndex].setChecked(true);
        }
    }
    
    private void clearAllColorSelections() {
        rbColorDefault.setChecked(false);
        for (int i = 0; i < rbColors.length; i++) {
            rbColors[i].setChecked(false);
        }
    }
    
    private void setupListeners() {
        btnSave.setOnClickListener(v -> saveAccount());
        btnTest.setOnClickListener(v -> testConnection());
        btnDelete.setOnClickListener(v -> deleteAccount());
        
        // 点击默认布局
        colorLayouts[8].setOnClickListener(v -> selectColorRadioButton(-1));
        
        // 点击颜色布局
        for (int i = 0; i < 8; i++) {
            final int index = i;
            colorLayouts[i].setOnClickListener(v -> selectColorRadioButton(index));
        }
        
        // 颜色选择监听 - 点击颜色按钮时取消默认选择
        for (int i = 0; i < rbColors.length; i++) {
            final int index = i;
            rbColors[i].setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    rbColorDefault.setChecked(false);
                    // 清除其他颜色选择
                    for (int j = 0; j < rbColors.length; j++) {
                        if (j != index) {
                            rbColors[j].setChecked(false);
                        }
                    }
                }
            });
        }
        
        // 点击默认时清除颜色选择
        rbColorDefault.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                for (int i = 0; i < rbColors.length; i++) {
                    rbColors[i].setChecked(false);
                }
            }
        });
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
    
    private int getSelectedColorIndex() {
        if (rbColorDefault.isChecked()) {
            return -1; // 使用全局设置
        }
        for (int i = 0; i < rbColors.length; i++) {
            if (rbColors[i].isChecked()) {
                return i;
            }
        }
        return -1;
    }
    
    private void saveAccount() {
        String name = etName.getText().toString().trim();
        String host = etHost.getText().toString().trim();
        String portStr = etPort.getText().toString().trim();
        String apiKey = etApiKey.getText().toString().trim();
        int colorIndex = getSelectedColorIndex();
        
        // 验证输入
        if (TextUtils.isEmpty(host)) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
            etHost.requestFocus();
            return;
        }
        
        // 验证端口
        int port = 8080;
        if (!TextUtils.isEmpty(portStr)) {
            try {
                port = Integer.parseInt(portStr);
                if (port < 1 || port > 65535) {
                    Toast.makeText(this, "端口范围应为 1-65535", Toast.LENGTH_SHORT).show();
                    etPort.requestFocus();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "请输入有效的端口号", Toast.LENGTH_SHORT).show();
                etPort.requestFocus();
                return;
            }
        }
        
        // 保存账号
        if (isEditMode) {
            // 更新现有账号
            Account account = accountManager.getAccount(accountId);
            if (account != null) {
                account.setName(name);
                account.setHost(host);
                account.setPort(port);
                account.setApiKey(apiKey);
                account.setColorIndex(colorIndex);
                accountManager.updateAccount(account);
                
                Toast.makeText(this, "账号已更新", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }
        } else {
            // 创建新账号
            Account account = new Account(name, host, port, apiKey);
            account.setColorIndex(colorIndex);
            accountManager.addAccount(account);
            
            Toast.makeText(this, "账号已添加", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        }
    }
    
    private void testConnection() {
        String host = etHost.getText().toString().trim();
        String portStr = etPort.getText().toString().trim();
        String apiKey = etApiKey.getText().toString().trim();
        
        if (TextUtils.isEmpty(host)) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 构建URL
        int port = 8080;
        if (!TextUtils.isEmpty(portStr)) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                // 使用默认端口
            }
        }
        
        String url = "http://" + host + ":" + port;
        
        Toast.makeText(this, "正在测试连接...", Toast.LENGTH_SHORT).show();
        
        // 使用 ApiClient 静态方法测试连接
        ApiClient.testConnection(url, apiKey, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response) {
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
