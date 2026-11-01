package net.wsdjeg.nova;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用量统计界面
 *
 * 基于 chat.nvim HTTP API:
 * - GET /usage-stats: 按 provider/model 查询请求与 token 统计（支持 hours/days 窗口）
 * - DELETE /usage-stats: 清空全部统计
 *
 * 功能:
 * - 时间窗口 Chip: 全部 / 最近 24 小时 / 最近 7 天 / 最近 30 天
 * - Provider / Model 下拉筛选
 * - 统计概要（记录条数 + 汇总请求与 tokens）
 * - 刷新 / 清空统计（需确认，同时清空服务端 total/hourly/daily 三层数据）
 */
public class UsageStatsActivity extends AppCompatActivity {

    /** 窗口类型 */
    private static final int WINDOW_TOTAL = 0;
    private static final int WINDOW_24H = 1;
    private static final int WINDOW_7D = 2;
    private static final int WINDOW_30D = 3;

    /** Spinner "全部" 选项的哨兵值（真实 provider/model 名不会是 "*"） */
    private static final String ALL = "*";

    private ApiClient apiClient;

    private Spinner spProvider;
    private Spinner spModel;
    private TextView tvSummary;
    private TextView tvEmpty;
    private RecyclerView rvRecords;
    private UsageStatsAdapter adapter;

    private ArrayAdapter<String> providerAdapter;
    private ArrayAdapter<String> modelAdapter;

    /** Spinner 选项列表（首项为本地化的"全部"标签） */
    private final List<String> providerNames = new ArrayList<>();
    private final List<String> modelNames = new ArrayList<>();

    /** 当前筛选状态 */
    private int currentWindow = WINDOW_TOTAL;
    private String currentProvider = ALL;
    private String currentModel = ALL;

    /** 最近一次成功拉取的完整数据（本地筛选，切换 provider/model 不重复请求） */
    private UsageStats lastStats;

    private boolean suppressProviderListener = false;
    private boolean suppressModelListener = false;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usage_stats);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_usage_stats);
        }

        AccountManager accountManager = AccountManager.getInstance(this);
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

        initViews();
        refresh();
    }

    private void initViews() {
        spProvider = findViewById(R.id.sp_usage_provider);
        spModel = findViewById(R.id.sp_usage_model);
        tvSummary = findViewById(R.id.tv_usage_summary);
        tvEmpty = findViewById(R.id.tv_usage_empty);
        rvRecords = findViewById(R.id.rv_usage_records);

        adapter = new UsageStatsAdapter();
        rvRecords.setLayoutManager(new LinearLayoutManager(this));
        rvRecords.setAdapter(adapter);

        // Provider Spinner
        providerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, providerNames);
        providerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProvider.setAdapter(providerAdapter);

        // Model Spinner
        modelAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, modelNames);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spModel.setAdapter(modelAdapter);

        // Provider 选择监听
        spProvider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressProviderListener || position < 0 || position >= providerNames.size()) {
                    return;
                }
                String selected = keyFromLabel(providerNames.get(position));
                if (ALL.equals(selected)) {
                    currentProvider = ALL;
                    updateModelSpinner(lastStats, null);
                } else {
                    currentProvider = selected;
                    updateModelSpinner(lastStats, selected);
                }
                currentModel = ALL;
                applyFilter();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Model 选择监听
        spModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressModelListener || position < 0 || position >= modelNames.size()) {
                    return;
                }
                currentModel = keyFromLabel(modelNames.get(position));
                applyFilter();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // 时间窗口 Chip
        ChipGroup chipGroup = findViewById(R.id.cg_usage_windows);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            int window = windowFromChipId(checkedIds.get(0));
            if (window != currentWindow) {
                currentWindow = window;
                refresh();
            }
        });
    }

    // ------------------------------------------------------------------
    // 数据加载
    // ------------------------------------------------------------------

    /** 按当前时间窗口从服务端拉取数据 */
    private void refresh() {
        if (apiClient == null || loading) {
            return;
        }
        loading = true;
        tvSummary.setText(R.string.loading);

        int hours = 0;
        int days = 0;
        switch (currentWindow) {
            case WINDOW_24H:
                hours = 24;
                break;
            case WINDOW_7D:
                days = 7;
                break;
            case WINDOW_30D:
                days = 30;
                break;
            case WINDOW_TOTAL:
            default:
                break;
        }

        apiClient.getUsageStats(hours, days, new ApiClient.UsageStatsCallback() {
            @Override
            public void onSuccess(UsageStats stats) {
                runOnUiThread(() -> {
                    loading = false;
                    lastStats = stats;
                    rebuildSpinners();
                    applyFilter();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    loading = false;
                    tvSummary.setText(getString(R.string.load_failed, error));
                });
            }
        });
    }

    /** 数据加载后重建 Provider Spinner 选项（"全部" + 所有 provider），并重置筛选 */
    private void rebuildSpinners() {
        currentProvider = ALL;
        currentModel = ALL;

        suppressProviderListener = true;
        providerNames.clear();
        providerNames.add(getString(R.string.usage_filter_all));
        if (lastStats != null) {
            providerNames.addAll(lastStats.getStats().keySet());
        }
        providerAdapter.notifyDataSetChanged();
        spProvider.setSelection(0);
        suppressProviderListener = false;

        updateModelSpinner(lastStats, null);
    }

    /**
     * 根据 provider 筛选重建 Model Spinner
     * @param provider null 表示"全部"（聚合所有 provider 的 model 去重）
     */
    private void updateModelSpinner(UsageStats stats, String provider) {
        suppressModelListener = true;
        modelNames.clear();
        modelNames.add(getString(R.string.usage_filter_all));
        if (stats != null) {
            Map<String, Boolean> seen = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, UsageRecord>> entry : stats.getStats().entrySet()) {
                if (provider != null && !provider.equals(entry.getKey())) {
                    continue;
                }
                for (String model : entry.getValue().keySet()) {
                    seen.put(model, Boolean.TRUE);
                }
            }
            modelNames.addAll(seen.keySet());
        }
        modelAdapter.notifyDataSetChanged();
        spModel.setSelection(0);
        suppressModelListener = false;
    }

    // ------------------------------------------------------------------
    // 本地筛选与展示
    // ------------------------------------------------------------------

    /** 按当前 provider/model 筛选并刷新列表与概要 */
    private void applyFilter() {
        List<UsageStatsAdapter.Entry> entries = new ArrayList<>();
        long totalRequests = 0;
        long totalTokens = 0;

        if (lastStats != null) {
            for (Map.Entry<String, Map<String, UsageRecord>> providerEntry
                    : lastStats.getStats().entrySet()) {
                String provider = providerEntry.getKey();
                if (!ALL.equals(currentProvider) && !currentProvider.equals(provider)) {
                    continue;
                }
                for (Map.Entry<String, UsageRecord> modelEntry
                        : providerEntry.getValue().entrySet()) {
                    String model = modelEntry.getKey();
                    if (!ALL.equals(currentModel) && !currentModel.equals(model)) {
                        continue;
                    }
                    UsageRecord record = modelEntry.getValue();
                    // 未筛选 provider 时显示 "provider/model"，已筛选时只显示 model
                    String title = ALL.equals(currentProvider)
                            ? provider + " / " + model : model;
                    entries.add(new UsageStatsAdapter.Entry(title, provider, model, record));
                    totalRequests += record.getRequests();
                    totalTokens += record.getTotalTokens();
                }
            }
        }

        adapter.setData(entries);
        tvEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);

        String windowLabel = windowLabel(currentWindow);
        if (entries.isEmpty()) {
            tvSummary.setText(getString(R.string.usage_summary_empty, windowLabel));
        } else {
            tvSummary.setText(getString(R.string.usage_summary_format,
                    windowLabel,
                    entries.size(),
                    Session.formatTokens(totalRequests),
                    Session.formatTokens(totalTokens)));
        }
    }

    // ------------------------------------------------------------------
    // 清空统计
    // ------------------------------------------------------------------

    private void confirmClear() {
        if (apiClient == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.usage_clear_title)
                .setMessage(R.string.usage_clear_message)
                .setPositiveButton(R.string.clear, (dialog, which) -> clearUsageStats())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void clearUsageStats() {
        if (apiClient == null) {
            return;
        }
        apiClient.clearUsageStats(new ApiClient.ClearUsageStatsCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(UsageStatsActivity.this,
                            R.string.usage_cleared, Toast.LENGTH_SHORT).show();
                    refresh();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() ->
                        Toast.makeText(UsageStatsActivity.this,
                                getString(R.string.usage_clear_failed, error),
                                Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ------------------------------------------------------------------
    // 菜单
    // ------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.usage_stats_menu, menu);
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
        if (id == R.id.action_clear) {
            confirmClear();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    /** Spinner 标签 -> 真实键："全部"标签映射为 "*"，其余原样返回 */
    private String keyFromLabel(String label) {
        if (label == null || label.equals(getString(R.string.usage_filter_all))) {
            return ALL;
        }
        return label;
    }

    /** Chip ID -> 时间窗口 */
    private int windowFromChipId(int chipId) {
        if (chipId == R.id.chip_usage_24h) {
            return WINDOW_24H;
        }
        if (chipId == R.id.chip_usage_7d) {
            return WINDOW_7D;
        }
        if (chipId == R.id.chip_usage_30d) {
            return WINDOW_30D;
        }
        return WINDOW_TOTAL;
    }

    /** 时间窗口 -> 概要栏显示文案 */
    private String windowLabel(int window) {
        switch (window) {
            case WINDOW_24H:
                return getString(R.string.usage_window_24h);
            case WINDOW_7D:
                return getString(R.string.usage_window_7d);
            case WINDOW_30D:
                return getString(R.string.usage_window_30d);
            case WINDOW_TOTAL:
            default:
                return getString(R.string.usage_window_total);
        }
    }
}

