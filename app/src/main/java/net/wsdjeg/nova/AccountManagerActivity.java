package net.wsdjeg.nova;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/**
 * 账号管理页面
 * 显示所有账号列表，支持添加、编辑、删除账号
 * 添加功能通过右下角 FAB 按钮实现
 */
public class AccountManagerActivity extends AppCompatActivity implements AccountAdapter.OnAccountClickListener {
    
    public static final String EXTRA_ACCOUNT_ID = "account_id";
    private static final int REQUEST_ADD_ACCOUNT = 1;
    private static final int REQUEST_EDIT_ACCOUNT = 2;
    
    private RecyclerView recyclerView;
    private AccountAdapter adapter;
    private LinearLayout emptyView;
    private AccountManager accountManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_manager);
        
        accountManager = AccountManager.getInstance(this);
        
        initViews();
        loadAccounts();
    }
    
    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("账号管理");
        
        recyclerView = findViewById(R.id.recycler_accounts);
        emptyView = findViewById(R.id.empty_view);
        
        adapter = new AccountAdapter(this);
        adapter.setOnAccountClickListener(this);
        adapter.setAccounts(new ArrayList<>());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        
        // FAB 添加按钮
        findViewById(R.id.fab_add_account).setOnClickListener(v -> {
            Intent intent = new Intent(this, AccountEditActivity.class);
            startActivityForResult(intent, REQUEST_ADD_ACCOUNT);
        });
    }
    
    private void loadAccounts() {
        List<Account> accounts = accountManager.getAccounts();
        adapter.setAccounts(accounts);
        
        if (accounts.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadAccounts();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            loadAccounts();
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    // ========== AccountAdapter.OnAccountClickListener ==========
    
    @Override
    public void onAccountClick(Account account) {
        // 点击账号，编辑
        Intent intent = new Intent(this, AccountEditActivity.class);
        intent.putExtra(EXTRA_ACCOUNT_ID, account.getId());
        startActivityForResult(intent, REQUEST_EDIT_ACCOUNT);
    }
    
    @Override
    public void onAccountLongClick(Account account) {
        // 长按账号，设置为默认
        accountManager.setDefaultAccount(account.getId());
        loadAccounts();
    }
    
    @Override
    public void onEditClick(Account account) {
        // 编辑账号
        Intent intent = new Intent(this, AccountEditActivity.class);
        intent.putExtra(EXTRA_ACCOUNT_ID, account.getId());
        startActivityForResult(intent, REQUEST_EDIT_ACCOUNT);
    }
    
    @Override
    public void onDeleteClick(Account account) {
        // 删除账号
        accountManager.deleteAccount(account.getId());
        loadAccounts();
    }
}
