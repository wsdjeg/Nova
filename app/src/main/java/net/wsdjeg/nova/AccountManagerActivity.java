package net.wsdjeg.nova;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 号管理界面
 * 支持添加、编辑、删除、设置默认账号
 */
public class AccountManagerActivity extends AppCompatActivity implements AccountAdapter.OnAccountClickListener {

    private RecyclerView recyclerView;
    private AccountAdapter adapter;
    private AccountManager accountManager;
    private LinearLayout emptyView;
    
    private static final int REQUEST_ADD_ACCOUNT = 100;
    private static final int REQUEST_EDIT_ACCOUNT = 101;

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
        recyclerView = findViewById(R.id.recycler_accounts);
        emptyView = findViewById(R.id.empty_view);
        
        adapter = new AccountAdapter(this);
        adapter.setOnAccountClickListener(this);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        
        // 添加账号按钮 (FAB)
        findViewById(R.id.fab_add_account).setOnClickListener(v -> {
            Intent intent = new Intent(this, AccountEditActivity.class);
            startActivityForResult(intent, REQUEST_ADD_ACCOUNT);
        });
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
     * 认删除对话框
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
        // 点击账号，设为默认
        if (!account.isActive()) {
            accountManager.setDefaultAccount(account.getId());
            Toast.makeText(this, "已设为默认: " + account.getDisplayName(), Toast.LENGTH_SHORT).show();
            loadAccounts();
        }
    }
    
    @Override
    public void onAccountLongClick(Account account) {
        // 长按显示操作菜单
        String[] options = {"编辑", "设为默认账号", "删除"};
        new AlertDialog.Builder(this)
            .setTitle(account.getDisplayName())
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: // 编辑
                        openEditAccount(account);
                        break;
                    case 1: // 设为默认
                        if (!account.isActive()) {
                            accountManager.setDefaultAccount(account.getId());
                            Toast.makeText(this, "已设为默认: " + account.getDisplayName(), Toast.LENGTH_SHORT).show();
                            loadAccounts();
                        } else {
                            Toast.makeText(this, "当前已是默认账号", Toast.LENGTH_SHORT).show();
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
        openEditAccount(account);
    }
    
    @Override
    public void onDeleteClick(Account account) {
        showDeleteConfirmDialog(account);
    }
    
    /**
     * 打开账号编辑页面
     */
    private void openEditAccount(Account account) {
        Intent intent = new Intent(this, AccountEditActivity.class);
        intent.putExtra(AccountEditActivity.EXTRA_ACCOUNT_ID, account.getId());
        intent.putExtra(AccountEditActivity.EXTRA_ACCOUNT_NAME, account.getName());
        intent.putExtra(AccountEditActivity.EXTRA_ACCOUNT_HOST, account.getHost());
        intent.putExtra(AccountEditActivity.EXTRA_ACCOUNT_PORT, account.getPort());
        intent.putExtra(AccountEditActivity.EXTRA_ACCOUNT_API_KEY, account.getApiKey());
        intent.putExtra(AccountEditActivity.EXTRA_ACCOUNT_COLOR_INDEX, account.getColorIndex());
        startActivityForResult(intent, REQUEST_EDIT_ACCOUNT);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            loadAccounts();
        }
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
            Intent intent = new Intent(this, AccountEditActivity.class);
            startActivityForResult(intent, REQUEST_ADD_ACCOUNT);
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
}
