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
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("添加账号");
        
        etName = findViewById(R.id.et_account_name);
        etHost = findViewById(R.id.et_host);
        etPort = findViewById(R.id.et_port);
        etApiKey = findViewById(R.id.et_api_key);
        btnSave = findViewById(R.id.btn_save);
        btnTest = findViewById(R.id.btn_test_connection);
        btnDelete = findViewById(R.id.btn_delete);
    }
    
    private void initColorPicker() {
        LinearLayout container = findViewById(R.id.color_picker_container);
        
        int size = (int) (36 * getResources().getDisplayMetrics().density);
        int margin = (int) (6 * getResources().getDisplayMetrics().density);
        
        colorViews = new View[9]; // 0=默认, 1-8=颜色
        
        // 创建默认选项（渐变圆形）
        View autoView = new View(this);
        LinearLayout.LayoutParams autoParams = new LinearLayout.LayoutParams(size, size);
        autoParams.setMargins(0, 0, margin, 0);
        autoView.setLayoutParams(autoParams);
        autoView.setBackgroundResource(R.drawable.color_circle_0);
        autoView.setOnClickListener(v -> selectColor(-1));
        container.addView(autoView);
        colorViews[0] = autoView;
        
        // 创建颜色选项 - 使用 SettingsManager 中定义的颜色
        String[] colors = SettingsManager.ACCOUNT_TAG_COLORS;
        
        for (int i = 0; i < colors.length; i++) {
            View colorView = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(0, 0, margin, 0);
            colorView.setLayoutParams(params);
            
            // 创建圆形 drawable
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(Color.parseColor(colors[i]));
            colorView.setBackground(drawable);
            
            final int colorIndex = i + 1;
            colorView.setOnClickListener(v -> selectColor(colorIndex));
            container.addView(colorView);
            colorViews[i + 1] = colorView;
        }
        
        // 添加选中状态边框
        updateColorSelection();
    }
    
    private void selectColor(int index) {
        // 取消之前的选中状态
        for (View view : colorViews) {
            view.setSelected(false);
        }
        
        selectedColorIndex = index;
        
        // 设置新的选中状态
        if (index == -1) {
            colorViews[0].setSelected(true);
        } else {
            colorViews[index].setSelected(true);
        }
        
        updateColorSelection();
    }
    
    private void updateColorSelection() {
        for (int i = 0; i < colorViews.length; i++) {
            View view = colorViews[i];
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            
            if (i == 0) {
                // 默认选项使用渐变
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
            
            // 选中的添加边框
            int selectedIndex = (selectedColorIndex == -1) ? 0 : selectedColorIndex;
            if (i == selectedIndex) {
                drawable.setStroke(4, ContextCompat.getColor(this, R.color.primary));
            }
            
            view.setBackground(drawable);
        }
    }
    
    private void loadAccountData() {
        Intent intent = getIntent();
        accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID);
        
        if (accountId != null && !accountId.isEmpty()) {
            // 根据 accountId 从 AccountManager 获取账号信息
            Account account = accountManager.getAccountById(accountId);
            
            if (account != null) {
                isEditMode = true;
                getSupportActionBar().setTitle("编辑账号");
                
                etName.setText(account.getName());
                etHost.setText(account.getHost());
                
                int port = account.getPort();
                if (port > 0) {
                    etPort.setText(String.valueOf(port));
                }
                
                etApiKey.setText(account.getApiKey());
                selectColor(account.getColorIndex());
                
                btnDelete.setVisibility(View.VISIBLE);
            } else {
                // 账号不存在，回退到 Intent 数据（兼容旧方式）
                isEditMode = true;
                getSupportActionBar().setTitle("编辑账号");
                
                etName.setText(intent.getStringExtra(EXTRA_ACCOUNT_NAME));
                etHost.setText(intent.getStringExtra(EXTRA_ACCOUNT_HOST));
                
                int port = intent.getIntExtra(EXTRA_ACCOUNT_PORT, 0);
                if (port > 0) {
                    etPort.setText(String.valueOf(port));
                }
                
                etApiKey.setText(intent.getStringExtra(EXTRA_ACCOUNT_API_KEY));
                
                int colorIndex = intent.getIntExtra(EXTRA_ACCOUNT_COLOR_INDEX, -1);
                selectColor(colorIndex);
                
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
            Toast.makeText(this, "请输入账号名称", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (TextUtils.isEmpty(host)) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }
        
        int port = 8080; // 默认端口
        if (!TextUtils.isEmpty(portStr)) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "端口格式错误", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        
        if (isEditMode) {
            // 编辑模式：更新现有账号
            Account account = accountManager.getAccountById(accountId);
            if (account != null) {
                account.setName(name);
                account.setHost(host);
                account.setPort(port);
                account.setApiKey(apiKey);
                account.setColorIndex(selectedColorIndex);
                accountManager.updateAccount(account);
                Toast.makeText(this, "账号已更新", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "账号不存在", Toast.LENGTH_SHORT).show();
            }
        } else {
            // 新增模式：创建新账号
            Account account = new Account();
            account.setName(name);
            account.setHost(host);
            account.setPort(port);
            account.setApiKey(apiKey);
            account.setColorIndex(selectedColorIndex);
            accountManager.addAccount(account);
            
            // addAccount 已经会自动处理第一个账号为默认的情况
            Toast.makeText(this, "账号已添加", Toast.LENGTH_SHORT).show();
        }
        
        setResult(RESULT_OK);
        finish();
    }
    
    private void testConnection() {
        String host = etHost.getText().toString().trim();
        String portStr = etPort.getText().toString().trim();
        String apiKey = etApiKey.getText().toString().trim();
        
        if (TextUtils.isEmpty(host)) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }
        
        int port = 8080;
        if (!TextUtils.isEmpty(portStr)) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "端口格式错误", Toast.LENGTH_SHORT).show();
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
                    btnTest.setText("测试连接");
                    Toast.makeText(AccountEditActivity.this, 
                        "连接成功: " + message, Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    btnTest.setEnabled(true);
                    btnTest.setText("测试连接");
                    Toast.makeText(AccountEditActivity.this, 
                        "连接失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void showDeleteConfirmDialog() {
        new AlertDialog.Builder(this)
            .setTitle("删除账号")
            .setMessage("确定要删除此账号吗？")
            .setPositiveButton("删除", (dialog, which) -> {
                accountManager.removeAccount(accountId);
                Toast.makeText(this, "账号已删除", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            })
            .setNegativeButton("取消", null)
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
