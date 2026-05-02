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
        getSupportActionBar().setTitle(isEditMode ? "编辑账号" : "添加账号");
        toolbar.setNavigationIconTint(Color.WHITE);
        
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
        
        // 创建颜色选项
        int[] colors = {
            R.color.account_color_1,
            R.color.account_color_2,
            R.color.account_color_3,
            R.color.account_color_4,
            R.color.account_color_5,
            R.color.account_color_6,
            R.color.account_color_7,
            R.color.account_color_8
        };
        
        int[] drawables = {
            R.drawable.color_circle_1,
            R.drawable.color_circle_2,
            R.drawable.color_circle_3,
            R.drawable.color_circle_4,
            R.drawable.color_circle_5,
            R.drawable.color_circle_6,
            R.drawable.color_circle_7,
            R.drawable.color_circle_8
        };
        
        for (int i = 0; i < 8; i++) {
            View colorView = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(0, 0, margin, 0);
            colorView.setLayoutParams(params);
            colorView.setBackgroundResource(drawables[i]);
            final int colorIndex = i + 1;
            colorView.setOnClickListener(v -> selectColor(colorIndex));
            container.addView(colorView);
            colorViews[i + 1] = colorView;
        }
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
    }
    
    private void loadAccountData() {
        Intent intent = getIntent();
        accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID);
        
        if (accountId != null && !accountId.isEmpty()) {
            isEditMode = true;
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
            accountManager.updateAccount(accountId, name, host, port, apiKey, selectedColorIndex);
            Toast.makeText(this, "账号已更新", Toast.LENGTH_SHORT).show();
        } else {
            Account account = accountManager.addAccount(name, host, port, apiKey, selectedColorIndex);
            if (accountManager.getAccountCount() == 1) {
                accountManager.setDefaultAccount(account.getId());
            }
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
        
        btnTest.setEnabled(false);
        btnTest.setText("测试中...");
        
        ApiClient.testConnection(host, port, apiKey, new ApiClient.TestConnectionCallback() {
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
