package net.wsdjeg.nova;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 账号管理界面
 * 支持添加、编辑、删除、切换账号
 */
public class AccountManagerActivity extends AppCompatActivity implements AccountAdapter.OnAccountClickListener {

    private RecyclerView recyclerView;
    private AccountAdapter adapter;
    private AccountManager accountManager;
    private LinearLayout emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_manager);
        
        // 设置标题
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("账号管理");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        accountManager = AccountManager.getInstance(this);
        
        initViews();
        loadAccounts();
    }
    
    private void initViews() {
        recyclerView = findViewById(R.id.recycler_view);
        emptyView = findViewById(R.id.empty_view);
        
        adapter = new AccountAdapter(this);
        adapter.setOnAccountClickListener(this);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        
        // 添加账号按钮
        Button btnAddAccount = findViewById(R.id.btn_add_account);
        btnAddAccount.setOnClickListener(v -> showAddAccountDialog());
    }
    
    private void loadAccounts() {
        adapter.setAccounts(accountManager.getAllAccounts());
        updateEmptyView();
    }
    
    private void updateEmptyView() {
        if (accountManager.getAccountCount() == 0) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * 显示添加账号对话框
     */
    private void showAddAccountDialog() {
        showAccountDialog(null);
    }
    
    /**
     * 显示编辑账号对话框
     */
    private void showEditAccountDialog(Account account) {
        showAccountDialog(account);
    }
    
    /**
     * 通用的账号编辑对话框
     */
    private void showAccountDialog(Account existingAccount) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(existingAccount == null ? "添加账号" : "编辑账号");
        
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_account, null);
        EditText etName = view.findViewById(R.id.et_account_name);
        EditText etUrl = view.findViewById(R.id.et_account_url);
        EditText etApiKey = view.findViewById(R.id.et_account_api_key);
        
        // 如果是编辑，填充现有数据
        if (existingAccount != null) {
            etName.setText(existingAccount.getName());
            etUrl.setText(existingAccount.getUrl());
            etApiKey.setText(existingAccount.getApiKey());
        } else {
            // 默认 URL
            etUrl.setText("http://192.168.1.100:8080");
        }
        
        builder.setView(view);
        
        builder.setPositiveButton(existingAccount == null ? "添加" : "保存", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String url = etUrl.getText().toString().trim();
            String apiKey = etApiKey.getText().toString().trim();
            
            // 验证
            if (TextUtils.isEmpty(url)) {
                Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 保存
            if (existingAccount == null) {
                // 添加新账号
                Account account = new Account(name, url, apiKey.isEmpty() ? null : apiKey);
                // 如果是第一个账号，自动设为激活
                if (accountManager.getAccountCount() == 0) {
                    account.setActive(true);
                }
                accountManager.addAccount(account);
                Toast.makeText(this, "账号已添加", Toast.LENGTH_SHORT).show();
            } else {
                // 更新现有账号
                existingAccount.setName(name);
                existingAccount.setUrl(url);
                if (!apiKey.isEmpty()) {
                    existingAccount.setApiKey(apiKey);
                }
                accountManager.updateAccount(existingAccount);
                Toast.makeText(this, "账号已更新", Toast.LENGTH_SHORT).show();
            }
            
            loadAccounts();
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    /**
     * 确认删除对话框
     */
    private void showDeleteConfirmDialog(Account account) {
        new AlertDialog.Builder(this)
            .setTitle("删除账号")
            .setMessage("确定要删除账号 \"" + account.getDisplayName() + "\" 吗？\n\n该账号的本地会话数据将被清除。")
            .setPositiveButton("删除", (dialog, which) -> {
                accountManager.removeAccount(account.getId());
                Toast.makeText(this, "账号已删除", Toast.LENGTH_SHORT).show();
                loadAccounts();
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    // ========== AccountAdapter.OnAccountClickListener 接口实现 ==========
    
    @Override
    public void onAccountClick(Account account) {
        // 点击账号，切换激活状态
        if (!account.isActive()) {
            accountManager.switchAccount(account.getId());
            Toast.makeText(this, "已切换到: " + account.getDisplayName(), Toast.LENGTH_SHORT).show();
            loadAccounts();
        }
    }
    
    @Override
    public void onAccountLongClick(Account account) {
        // 长按显示操作菜单
        String[] options = {"编辑", "切换到此账号", "删除"};
        new AlertDialog.Builder(this)
            .setTitle(account.getDisplayName())
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: // 编辑
                        showEditAccountDialog(account);
                        break;
                    case 1: // 切换
                        if (!account.isActive()) {
                            accountManager.switchAccount(account.getId());
                            Toast.makeText(this, "已切换到: " + account.getDisplayName(), Toast.LENGTH_SHORT).show();
                            loadAccounts();
                        } else {
                            Toast.makeText(this, "当前已是激活账号", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case 2: // 删除
                        showDeleteConfirmDialog(account);
                        break;
                }
            })
            .show();
    }
    
    @Override
    public void onEditClick(Account account) {
        showEditAccountDialog(account);
    }
    
    @Override
    public void onDeleteClick(Account account) {
        showDeleteConfirmDialog(account);
    }
    
    // ========== 菜单 ==========
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.account_manager_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.action_add_account) {
            showAddAccountDialog();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
}
