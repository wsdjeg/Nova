package net.wsdjeg.nova;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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
    private RadioGroup rgColorPicker;
    private Button btnSave;
    private Button btnTest;
    private Button btnDelete;
    
    private AccountManager accountManager;
    private String accountId;  // 如果是编辑模式，保存账号ID
    private boolean isEditMode = false;
    
    private View[] colorViews;
    private int selectedColorIndex = -1; // -1 表示默认
    
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
        etName = findViewById(R.id.et_account_name);
        etHost = findViewById(R.id.et_host);
        etPort = findViewById(R.id.et_port);
        etApiKey = findViewById(R.id.et_api_key);
        rgColorPicker = findViewById(R.id.rg_color_picker);
        btnSave = findViewById(R.id.btn_save);
        btnTest = findViewById(R.id.btn_test_connection);
        btnDelete = findViewById(R.id.btn_delete);
        
        // 设置返回按钮
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }
    
    private void initColorPicker() {
        LinearLayout container = findViewById(R.id.color_picker_container);
        
        int size = (int) (36 * getResources().getDisplayMetrics().density);
        int margin = (int) (6 * getResources().getDisplayMetrics().density);
        
        colorViews = new View[9]; // 0=默认, 1-8=颜色
        
        // 创建默认选项（渐变圆形 + "A" 字母）
        View autoView = new View(this);
        LinearLayout.LayoutParams autoParams = new LinearLayout.LayoutParams(size, size);
        autoParams.setMargins(margin, margin, margin, margin);
        autoView.setLayoutParams(autoParams);
        
        GradientDrawable autoDrawable = new GradientDrawable();
        autoDrawable.setShape(GradientDrawable.OVAL);
        autoDrawable.setColors(new int[] {
            Color.parseColor("#FF6B6B"),
            Color.parseColor("#4ECDC4"),
            Color.parseColor("#45B7D1"),
            Color.parseColor("#F7DC6F")
        });
        autoDrawable.setGradientType(GradientDrawable.SWEEP_GRADIENT);
        autoView.setBackground(autoDrawable);
        autoView.setOnClickListener(v -> selectColor(-1));
        container.addView(autoView);
        colorViews[0] = autoView;
        
        // 创建 8 个颜色选项
        for (int i = 0; i < SettingsManager.ACCOUNT_TAG_COLORS.length; i++) {
            View colorView = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(margin, margin, margin, margin);
            colorView.setLayoutParams(params);
            
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(Color.parseColor(SettingsManager.ACCOUNT_TAG_COLORS[i]));
            colorView.setBackground(drawable);
            
            final int colorIndex = i;
            colorView.setOnClickListener(v -> selectColor(colorIndex));
            
            container.addView(colorView);
            colorViews[i + 1] = colorView;
        }
    }
    
    private void selectColor(int index) {
        selectedColorIndex = index;
        updateColorSelection();
    }
    
    private void updateColorSelection() {
        for (int i = 0; i < colorViews.length; i++) {
            View view = colorViews[i];
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            
            if (i == 0) {
                // 默认选项：渐变背景
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
            
            // 选中状态添加边框
            int colorIndex = i - 1; // 转换为实际颜色索引
            if (colorIndex == selectedColorIndex || (selectedColorIndex == -1 && i == 0)) {
                drawable.setStroke(4, ContextCompat.getColor(this, R.color.primary));
            }
            
            view.setBackground(drawable);
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
            selectedColorIndex = colorIndex;
            updateColorSelection();
            
            setTitle("编辑账号");
            btnDelete.setVisibility(Button.VISIBLE);
        } else {
            // 添加模式
            isEditMode = false;
            setTitle("添加账号");
            btnDelete.setVisibility(Button.GONE);
            
            // 设置默认端口
            etPort.setText("8080");
            // 默认选中"默认"选项
            selectedColorIndex = -1;
            updateColorSelection();
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
        String host = etHost.getText().toString().trim();
        String portStr = etPort.getText().toString().trim();
        String apiKey = etApiKey.getText().toString().trim();
        int colorIndex = selectedColorIndex;
        
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
