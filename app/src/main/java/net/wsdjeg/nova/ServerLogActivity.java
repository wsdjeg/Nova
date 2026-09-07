package net.wsdjeg.nova;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.google.android.material.chip.ChipGroup;

import java.util.List;

/**
 * 服务端运行时日志查看器
 *
 * 基于 chat.nvim HTTP API:
 * - GET /logs: 获取运行时日志（支持 level 级别过滤）
 * - DELETE /logs: 清空服务端日志
 *
 * 功能:
 * - 级别筛选 Chip: 全部 / 错误 / 警告及以上 / 信息及以上 / 调试及以上
 * - 自动刷新（默认开启，3 秒轮询，可在菜单中开关）
 * - 点击日志行查看完整内容并复制
 * - 复制全部日志 / 清空服务端日志（需确认）
 */
public class ServerLogActivity extends AppCompatActivity implements ServerLogAdapter.OnLogClickListener {

    private static final long AUTO_REFRESH_INTERVAL_MS = 3000;

    private ApiClient apiClient;

    private ServerLogAdapter adapter;
    private LinearLayoutManager layoutManager;
    private TextView tvStats;
    private TextView tvEmpty;
    private RecyclerView rvLogs;

    /** 级别过滤参数: null = 不过滤, 可选 "error" / "warn" / "info" / "debug" */
    private String levelFilter = null;

    private boolean autoRefresh = true;
    private boolean refreshing = false;

    private Handler handler;
    private Runnable autoRefreshRunnable;
    private MenuItem autoRefreshItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_log);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_server_log);
        }

        AccountManager accountManager = AccountManager.getInstance(this);

        // 使用当前账号（或默认账号）连接服务端
        Account account = accountManager.getCurrentAccount();
        if (account == null) {
            account = accountManager.getDefaultAccount();
        }
        if (account == null
                || account.getUrl() == null || account.getUrl().isEmpty()
                || account.getApiKey() == null || account.getApiKey().isEmpty()) {
            Toast.makeText(this, R.string.please_add_account, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        apiClient = new ApiClient(account.getUrl(), account.getApiKey());

        tvStats = findViewById(R.id.tv_server_log_stats);
        tvEmpty = findViewById(R.id.tv_server_log_empty);
        rvLogs = findViewById(R.id.rv_server_logs);

        adapter = new ServerLogAdapter(this);
        layoutManager = new LinearLayoutManager(this);
        rvLogs.setLayoutManager(layoutManager);
        rvLogs.setAdapter(adapter);

        // 级别筛选 Chip（单选）
        ChipGroup chipGroup = findViewById(R.id.cg_server_log_levels);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            levelFilter = levelFromChipId(checkedIds.isEmpty() ? 0 : checkedIds.get(0));
            refreshLogs(false);
        });

        handler = new Handler(Looper.getMainLooper());
        autoRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                refreshLogs(true);
                if (autoRefresh) {
                    handler.postDelayed(this, AUTO_REFRESH_INTERVAL_MS);
                }
            }
        };

        refreshLogs(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLogs(true);
        startAutoRefresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoRefresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.server_log_menu, menu);
        autoRefreshItem = menu.findItem(R.id.action_auto_refresh);
        autoRefreshItem.setChecked(autoRefresh);
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
            refreshLogs(false);
            return true;
        }
        if (id == R.id.action_auto_refresh) {
            autoRefresh = !autoRefresh;
            item.setChecked(autoRefresh);
            if (autoRefresh) {
                startAutoRefresh();
            } else {
                stopAutoRefresh();
            }
            return true;
        }
        if (id == R.id.action_copy_all) {
            copyToClipboard(adapter.getAllText());
            return true;
        }
        if (id == R.id.action_clear) {
            confirmClearLogs();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------
    // 数据加载
    // ------------------------------------------------------------------

    /**
     * 刷新日志
     *
     * @param silent true 时不显示加载提示（用于自动轮询）
     */
    private void refreshLogs(boolean silent) {
        if (apiClient == null || refreshing) {
            return;
        }
        refreshing = true;
        if (!silent) {
            tvStats.setText(R.string.loading);
        }
        apiClient.getLogs(levelFilter, null, 0, new ApiClient.LogsCallback() {
            @Override
            public void onSuccess(List<String> logs) {
                runOnUiThread(() -> {
                    refreshing = false;
                    boolean wasAtBottom = isAtBottom();
                    boolean changed = adapter.setData(logs);
                    updateStats(null);
                    if (changed && wasAtBottom && adapter.getItemCount() > 0) {
                        rvLogs.scrollToPosition(adapter.getItemCount() - 1);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    refreshing = false;
                    updateStats(error);
                });
            }
        });
    }

    /** 更新统计栏: 正常显示条数, 出错时显示错误信息 */
    private void updateStats(String error) {
        if (error != null) {
            tvStats.setText(getString(R.string.read_log_failed, error));
        } else {
            tvStats.setText(getString(R.string.server_log_stats_format, adapter.getItemCount()));
        }
        tvEmpty.setVisibility(
                adapter.getItemCount() == 0 && error == null ? View.VISIBLE : View.GONE);
    }

    /** 列表是否已滚动到底部（自动刷新时保持跟随） */
    private boolean isAtBottom() {
        int last = layoutManager.findLastVisibleItemPosition();
        int count = adapter.getItemCount();
        return count == 0 || last >= count - 2;
    }

    // ------------------------------------------------------------------
    // 自动刷新
    // ------------------------------------------------------------------

    private void startAutoRefresh() {
        if (handler == null || autoRefreshRunnable == null) {
            return;
        }
        handler.removeCallbacks(autoRefreshRunnable);
        if (autoRefresh) {
            handler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL_MS);
        }
    }

    private void stopAutoRefresh() {
        if (handler != null && autoRefreshRunnable != null) {
            handler.removeCallbacks(autoRefreshRunnable);
        }
    }

    // ------------------------------------------------------------------
    // 清空服务端日志
    // ------------------------------------------------------------------

    private void confirmClearLogs() {
        if (apiClient == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.server_log_clear_title)
                .setMessage(R.string.server_log_clear_message)
                .setPositiveButton(R.string.clear, (dialog, which) -> clearServerLogs())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void clearServerLogs() {
        if (apiClient == null) {
            return;
        }
        apiClient.clearLogs(new ApiClient.ClearLogsCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(ServerLogActivity.this,
                            R.string.server_log_cleared, Toast.LENGTH_SHORT).show();
                    refreshLogs(true);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() ->
                        Toast.makeText(ServerLogActivity.this,
                                getString(R.string.server_log_clear_failed, error),
                                Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ------------------------------------------------------------------
    // 详情 / 复制
    // ------------------------------------------------------------------

    @Override
    public void onLogClick(String line) {
        TextView tv = new TextView(this);
        tv.setText(line);
        tv.setTextIsSelectable(true);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(12);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad / 2, pad, 0);

        new AlertDialog.Builder(this)
                .setTitle(R.string.log_detail_title)
                .setView(tv)
                .setPositiveButton(R.string.log_copy_entry,
                        (dialog, which) -> copyToClipboard(line))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void copyToClipboard(String text) {
        if (text == null || text.isEmpty()) {
            Toast.makeText(this, R.string.log_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("NovaServerLog", text));
        }
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
    }

    /** Chip ID -> API level 参数 */
    private String levelFromChipId(int chipId) {
        if (chipId == R.id.chip_server_filter_error) {
            return "error";
        }
        if (chipId == R.id.chip_server_filter_warn) {
            return "warn";
        }
        if (chipId == R.id.chip_server_filter_info) {
            return "info";
        }
        if (chipId == R.id.chip_server_filter_debug) {
            return "debug";
        }
        return null; // 全部
    }
}

