package net.wsdjeg.nova;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.wsdjeg.nova.api.TrashApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 回收站界面
 *
 * 对接 chat.nvim HTTP Server 的三个端点：
 * - GET    /trash                  列出回收站条目（裸数组）
 * - POST   /trash/{target}/restore  恢复会话，返回 {"id": "..."}
 * - DELETE /trash/{target}          永久清除快照，返回 204
 *
 * 与会话列表一致，回收站也是**跨账号聚合视图**：每台服务器各自维护
 * 一份回收站，这里并行拉取所有配置完整的账号后合并展示，条目上标注
 * 账号标签；恢复 / 永久删除时按条目的 accountId 回到对应服务器执行。
 *
 * 寻址使用回收站文件名而不是会话 ID：
 * 同一会话被删除多次会留下多份快照（id.json、id~1.json …），
 * 它们的 id 完全相同，只有文件名能唯一确定要恢复 / 删除哪一份。
 * 见 {@link TrashEntry#getTarget()}。
 */
public class TrashActivity extends AppCompatActivity implements TrashAdapter.OnTrashActionListener {

    /** 旋转屏幕后恢复上次滚动位置不必处理：列表数据每次都重新拉取，回到顶部可接受 */

    private AccountManager accountManager;
    private SettingsManager settingsManager;

    private TrashAdapter adapter;
    private RecyclerView rvTrash;
    private TextView tvStats;
    private TextView tvEmpty;

    /** 是否正在向服务器拉取列表 */
    private boolean loading = false;
    /** 是否有恢复 / 删除操作尚未完成（避免重复点击） */
    private boolean operating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trash);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_trash);
        }

        accountManager = AccountManager.getInstance(this);
        settingsManager = new SettingsManager(this);

        if (!accountManager.hasAccounts()) {
            Toast.makeText(this, R.string.trash_no_account, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从聊天页返回时服务端回收站可能已经变化（例如又删了一个会话），
        // 重新拉取一次保证列表与服务器一致。
        if (!loading && adapter != null) {
            refresh();
        }
    }

    private void initViews() {
        rvTrash = findViewById(R.id.rv_trash);
        tvStats = findViewById(R.id.tv_trash_stats);
        tvEmpty = findViewById(R.id.tv_trash_empty);

        adapter = new TrashAdapter(this);
        adapter.setAccountManager(accountManager);
        adapter.setSettingsManager(settingsManager);

        rvTrash.setLayoutManager(new LinearLayoutManager(this));
        rvTrash.setAdapter(adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.trash_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        if (id == R.id.action_refresh) {
            refresh();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------
    // 数据加载（跨账号聚合）
    // ------------------------------------------------------------------

    /**
     * 配置完整的账号（URL 与 API Key 均非空），只有这些账号才能访问回收站
     */
    private List<Account> usableAccounts() {
        List<Account> accounts = new ArrayList<>();
        for (Account account : accountManager.getAccounts()) {
            if (account.getUrl() == null || account.getUrl().isEmpty()) {
                continue;
            }
            if (account.getApiKey() == null || account.getApiKey().isEmpty()) {
                continue;
            }
            accounts.add(account);
        }
        return accounts;
    }

    /**
     * 并行拉取所有账号的回收站，全部返回后合并展示。
     *
     * 单个账号失败不会清空其它账号的数据：把失败信息收集起来，
     * 成功的数据照常显示，统计栏追加失败提示 —— 一台服务器连不上
     * 不应该让另外几台的回收站也看不见。
     */
    private void refresh() {
        if (loading) {
            return;
        }

        List<Account> accounts = usableAccounts();
        if (accounts.isEmpty()) {
            Toast.makeText(this, R.string.account_config_incomplete, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loading = true;
        tvStats.setText(R.string.trash_loading);
        tvEmpty.setVisibility(View.GONE);

        final List<TrashEntry> collected = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final AtomicInteger remaining = new AtomicInteger(accounts.size());

        for (Account account : accounts) {
            final String accountId = account.getId();
            final String accountName = account.getDisplayName();
            ApiClient client = new ApiClient(account.getUrl(), account.getApiKey());

            client.listTrash(new ApiClient.TrashCallback() {
                @Override
                public void onSuccess(List<TrashEntry> entries) {
                    synchronized (collected) {
                        for (TrashEntry entry : entries) {
                            // 标注归属，界面显示账号标签，操作时按此回到对应服务器
                            entry.setAccountId(accountId);
                            collected.add(entry);
                        }
                    }
                    if (remaining.decrementAndGet() == 0) {
                        applyData(collected, errors);
                    }
                }

                @Override
                public void onError(String error) {
                    synchronized (errors) {
                        errors.add(accountName + ": " + error);
                    }
                    if (remaining.decrementAndGet() == 0) {
                        applyData(collected, errors);
                    }
                }
            });
        }
    }

    /**
     * 合并结果排序后刷新 UI（回调来自网络线程，统一切回主线程）
     *
     * @param entries 聚合后的条目（可能来自多个账号）
     * @param errors  拉取失败的账号描述
     */
    private void applyData(List<TrashEntry> entries, List<String> errors) {
        runOnUiThread(() -> {
            loading = false;

            // 最近删除的排在前面；删除时间缺失（0）的条目自然沉底
            Collections.sort(entries, (a, b) ->
                    Long.compare(b.getDeletedAtMillis(), a.getDeletedAtMillis()));

            adapter.setData(entries);

            int count = entries.size();
            tvEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);

            if (errors.isEmpty()) {
                tvStats.setText(getString(R.string.trash_count_format, count));
            } else {
                String message = count == 0
                        ? getString(R.string.trash_load_failed, joinErrors(errors))
                        : getString(R.string.trash_count_format, count) + " · "
                          + getString(R.string.trash_load_partial, joinErrors(errors));
                tvStats.setText(message);
            }
        });
    }

    /** 多条失败信息合成一行 */
    private String joinErrors(List<String> errors) {
        synchronized (errors) {
            if (errors.size() == 1) {
                return errors.get(0);
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < errors.size(); i++) {
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append(errors.get(i));
            }
            return sb.toString();
        }
    }

    // ------------------------------------------------------------------
    // 条目操作
    // ------------------------------------------------------------------

    /**
     * 根据条目的 accountId 找到对应账号；账号已被删除时返回 null
     */
    private Account accountFor(TrashEntry entry) {
        String accountId = entry.getAccountId();
        if (!accountId.isEmpty()) {
            Account account = accountManager.getAccount(accountId);
            if (account != null) {
                return account;
            }
        }
        return null;
    }

    @Override
    public void onEntryClick(TrashEntry entry) {
        if (entry == null) {
            return;
        }
        Account account = accountFor(entry);
        String accountName = account != null
                ? account.getDisplayName()
                : getString(R.string.account_not_exist);

        String deletedTime = TimeUtils.formatTime(entry.getDeletedAtMillis());
        if (deletedTime.isEmpty()) {
            deletedTime = getString(R.string.trash_deleted_never);
        }

        String preview = entry.getDisplayPreview();
        if (preview.isEmpty()) {
            preview = getString(R.string.trash_detail_preview_empty);
        }

        String detail = getString(R.string.trash_detail_file, entry.getTarget()) + "\n"
                + getString(R.string.trash_detail_session_id, entry.getId()) + "\n"
                + getString(R.string.trash_detail_deleted_at, deletedTime) + "\n"
                + getString(R.string.trash_detail_messages, entry.getMessageCount()) + "\n"
                + getString(R.string.trash_detail_account, accountName) + "\n\n"
                + getString(R.string.trash_detail_preview, preview);

        new AlertDialog.Builder(this)
                .setTitle(entry.getDisplayTitle())
                .setMessage(detail)
                .setPositiveButton(R.string.trash_restore, (dialog, which) -> restore(entry))
                .setNeutralButton(R.string.trash_purge, (dialog, which) -> confirmPurge(entry))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    @Override
    public void onRestore(TrashEntry entry) {
        restore(entry);
    }

    /**
     * 恢复快照
     *
     * 服务端只把会话写回缓存并载入内存，随后会出现在 GET /sessions；
     * 它不会打开任何编辑器窗口，也不需要客户端再补一次会话创建。
     */
    private void restore(TrashEntry entry) {
        if (operating) {
            return;
        }
        Account account = accountFor(entry);
        if (account == null) {
            Toast.makeText(this, R.string.account_not_exist, Toast.LENGTH_SHORT).show();
            return;
        }

        operating = true;
        Toast.makeText(this, R.string.trash_restoring, Toast.LENGTH_SHORT).show();

        new ApiClient(account.getUrl(), account.getApiKey())
                .restoreTrash(entry.getTarget(), new ApiClient.TrashRestoreCallback() {
                    @Override
                    public void onSuccess(String sessionId) {
                        runOnUiThread(() -> {
                            operating = false;
                            String message = (sessionId != null && !sessionId.isEmpty())
                                    ? getString(R.string.trash_restored, sessionId)
                                    : getString(R.string.trash_restored_unknown_id);
                            Toast.makeText(TrashActivity.this, message, Toast.LENGTH_SHORT).show();
                            // 该条目已离开回收站，重新拉取列表
                            refresh();
                        });
                    }

                    @Override
                    public void onError(int httpCode, String error) {
                        runOnUiThread(() -> {
                            operating = false;
                            Toast.makeText(TrashActivity.this,
                                    describeRestoreError(httpCode, error),
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    @Override
    public void onPurge(TrashEntry entry) {
        confirmPurge(entry);
    }

    /**
     * 永久删除前二次确认：快照删掉之后无法再恢复
     */
    private void confirmPurge(TrashEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.trash_purge_title)
                .setMessage(getString(R.string.trash_purge_message, entry.getDisplayTitle()))
                .setPositiveButton(R.string.trash_purge, (dialog, which) -> purge(entry))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void purge(TrashEntry entry) {
        if (operating) {
            return;
        }
        Account account = accountFor(entry);
        if (account == null) {
            Toast.makeText(this, R.string.account_not_exist, Toast.LENGTH_SHORT).show();
            return;
        }

        operating = true;
        Toast.makeText(this, R.string.trash_purging, Toast.LENGTH_SHORT).show();

        new ApiClient(account.getUrl(), account.getApiKey())
                .purgeTrash(entry.getTarget(), new ApiClient.TrashPurgeCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            operating = false;
                            Toast.makeText(TrashActivity.this,
                                    R.string.trash_purged, Toast.LENGTH_SHORT).show();
                            refresh();
                        });
                    }

                    @Override
                    public void onError(int httpCode, String error) {
                        runOnUiThread(() -> {
                            operating = false;
                            Toast.makeText(TrashActivity.this,
                                    describePurgeError(httpCode, error),
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    // ------------------------------------------------------------------
    // 错误文案映射
    // ------------------------------------------------------------------

    /**
     * 恢复失败文案
     *
     * 服务端按错误语义映射状态码：
     * 404 回收站里没这条，409 同名会话已存在（不覆盖），
     * 400 文件存在但内容不可用。其余状态码（含网络异常）透传服务端文本。
     */
    private String describeRestoreError(int httpCode, String error) {
        if (httpCode == 404) {
            return getString(R.string.trash_restore_not_found);
        }
        if (httpCode == 409) {
            return getString(R.string.trash_restore_conflict);
        }
        if (httpCode == 400) {
            return getString(R.string.trash_restore_unusable);
        }
        return getString(R.string.trash_restore_failed, fallbackErrorText(httpCode, error));
    }

    /**
     * 永久删除失败文案
     */
    private String describePurgeError(int httpCode, String error) {
        if (httpCode == 404) {
            return getString(R.string.trash_purge_not_found);
        }
        return getString(R.string.trash_purge_failed, fallbackErrorText(httpCode, error));
    }

    /**
     * 服务端没有给出 error 文本时，用状态码兜底，避免出现「失败: null」
     */
    private String fallbackErrorText(int httpCode, String error) {
        if (error != null && !error.trim().isEmpty()) {
            return error.trim();
        }
        if (httpCode == TrashApi.NETWORK_ERROR) {
            return "Network error";
        }
        return "HTTP " + httpCode;
    }
}

