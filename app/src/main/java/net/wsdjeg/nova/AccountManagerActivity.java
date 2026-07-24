package net.wsdjeg.nova;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
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
    
    // 导出文件选择器
    private ActivityResultLauncher<String> exportLauncher;
    // 导入文件选择器
    private ActivityResultLauncher<String[]> importLauncher;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_manager);
        
        accountManager = AccountManager.getInstance(this);
        
        initLaunchers();
        initViews();
        loadAccounts();
    }
    
    /**
     * 初始化文件选择器
     */
    private void initLaunchers() {
        // 导出文件选择器
        exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) {
                    exportAccountsToFile(uri);
                }
            }
        );
        
        // 导入文件选择器
        importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    importAccountsFromFile(uri);
                }
            }
        );
    }
    
    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.title_account_manager);
        
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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.account_manager_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_export) {
            exportAccounts();
            return true;
        } else if (id == R.id.action_import) {
            importAccounts();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * 导出账号到 JSON 文件
     */
    private void exportAccounts() {
        List<Account> accounts = accountManager.getAccounts();
        if (accounts.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_accounts_to_export), Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 使用 Storage Access Framework 让用户选择保存位置
        String fileName = "nova_accounts_" + System.currentTimeMillis() + ".json";
        exportLauncher.launch(fileName);
    }
    
    /**
     * 将账号导出到指定文件
     */
    private void exportAccountsToFile(Uri uri) {
        try {
            String json = accountManager.toJson();
            
            if (json == null) {
                Toast.makeText(this, getString(R.string.export_failed_no_data), Toast.LENGTH_SHORT).show();
                return;
            }
            
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    os.write(json.getBytes("UTF-8"));
                    os.flush();
                    Toast.makeText(this, getString(R.string.export_success), Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.export_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
    
    /**
     * 从 JSON 文件导入账号
     */
    private void importAccounts() {
        // 使用 Storage Access Framework 让用户选择文件
        importLauncher.launch(new String[]{"application/json", "text/*"});
    }
    
    /**
     * 从指定文件导入账号
     */
    private void importAccountsFromFile(Uri uri) {
        try {
            StringBuilder sb = new StringBuilder();
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is != null) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        sb.append(new String(buffer, 0, len, "UTF-8"));
                    }
                }
            }
            
            String json = sb.toString();
            int importedCount = accountManager.importFromJson(json);
            
            loadAccounts();
            Toast.makeText(this, getString(R.string.import_success, importedCount), Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.import_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
    
    // ========== AccountAdapter.OnAccountClickListener ==========
    
    @Override
    public void onAccountClick(Account account) {
        // 点击账号，设为默认账号
        accountManager.setDefaultAccount(account.getId());
        loadAccounts();
        Toast.makeText(this, getString(R.string.set_as_default, account.getDisplayName()), Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onAccountLongClick(Account account) {
        // 长按账号，编辑
        Intent intent = new Intent(this, AccountEditActivity.class);
        intent.putExtra(EXTRA_ACCOUNT_ID, account.getId());
        startActivityForResult(intent, REQUEST_EDIT_ACCOUNT);
    }
    
    @Override
    public void onEditClick(Account account) {
        // 编辑按钮，编辑账号
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
