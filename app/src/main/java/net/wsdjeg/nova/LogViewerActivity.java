package net.wsdjeg.nova;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 日志查看器
 *
 * 基于 NovaLog 应用内日志引擎：
 * - 实时刷新（增量追加，不破坏长按文本选择）
 * - 关键字搜索（匹配标签/内容/堆栈，不区分大小写）
 * - V/D/I/W/E 级别筛选 Chip
 * - 单条长按局部选择复制 / 点击查看详情并整条复制
 * - 复制筛选结果 / 导出分享 .log 文件 / 一键清空
 */
public class LogViewerActivity extends AppCompatActivity implements LogAdapter.OnEntryClickListener {

    private static final long REFRESH_DEBOUNCE_MS = 150;

    private LogAdapter adapter;
    private LinearLayoutManager layoutManager;
    private TextView tvStats;
    private TextView tvEmpty;
    private RecyclerView rvLogs;

    /** 全量日志（内存缓存，与 NovaLog 缓冲区保持同步） */
    private final List<NovaLog.Entry> allEntries = new ArrayList<>();
    /** 启用的日志级别 */
    private final Set<Integer> enabledLevels = new HashSet<>();
    /** 搜索关键字 */
    private String query = "";
    /** 最后同步的序号（增量刷新用） */
    private long lastSeq = 0;
    /** 缓冲区当前最小 seq，用于检测 NovaLog.clear() 或回绕 */
    private long bufferFirstSeq = -1;

    private boolean autoFollow = true;
    private boolean atBottom = true;

    private Handler handler;
    private Runnable pendingRefresh;
    private MenuItem followItem;

    /** NovaLog 监听器：任意线程回调，post 到主线程做防抖刷新 */
    private final NovaLog.Listener logListener = entry -> {
        if (handler != null) {
            handler.post(this::scheduleRefresh);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_viewer);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_log_viewer);
        }

        handler = new Handler(Looper.getMainLooper());

        tvStats = findViewById(R.id.tv_log_stats);
        tvEmpty = findViewById(R.id.tv_log_empty);
        rvLogs = findViewById(R.id.rv_logs);

        adapter = new LogAdapter(this);
        layoutManager = new LinearLayoutManager(this);
        rvLogs.setLayoutManager(layoutManager);
        rvLogs.setAdapter(adapter);

        rvLogs.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                int last = layoutManager.findLastVisibleItemPosition();
                int count = adapter.getItemCount();
                atBottom = count == 0 || last >= count - 2;
                // 用户滚离底部：暂停跟随；滚回底部：恢复跟随
                if (atBottom != autoFollow) {
                    autoFollow = atBottom;
                    updateFollowMenu();
                }
            }
        });

        // 搜索
        TextView etSearch = findViewById(R.id.et_log_search);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.US);
                applyFilter();
            }
        });

        // 级别筛选 Chip：先读取 XML 初始 checked 状态，再挂监听
        readChipLevels();
        ChipGroup chipGroup = findViewById(R.id.cg_log_levels);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            enabledLevels.clear();
            for (int id : checkedIds) {
                int level = chipLevel(id);
                if (level > 0) {
                    enabledLevels.add(level);
                }
            }
            applyFilter();
        });

        NovaLog.register(logListener);
        fullRefresh();
    }

    /** 从 XML 初始 checked 状态读取默认级别集合 */
    private void readChipLevels() {
        enabledLevels.clear();
        ChipGroup chipGroup = findViewById(R.id.cg_log_levels);
        for (int id : chipGroup.getCheckedChipIds()) {
            int level = chipLevel(id);
            if (level > 0) {
                enabledLevels.add(level);
            }
        }
    }

    private int chipLevel(int chipId) {
        if (chipId == R.id.chip_v) {
            return NovaLog.VERBOSE;
        }
        if (chipId == R.id.chip_d) {
            return NovaLog.DEBUG;
        }
        if (chipId == R.id.chip_i) {
            return NovaLog.INFO;
        }
        if (chipId == R.id.chip_w) {
            return NovaLog.WARN;
        }
        if (chipId == R.id.chip_e) {
            return NovaLog.ERROR;
        }
        return 0;
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.log_viewer_menu, menu);
        followItem = menu.findItem(R.id.action_follow);
        followItem.setChecked(autoFollow);
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
            fullRefresh();
            return true;
        }
        if (id == R.id.action_follow) {
            autoFollow = !autoFollow;
            item.setChecked(autoFollow);
            if (autoFollow) {
                scrollToBottom();
            }
            return true;
        }
        if (id == R.id.action_copy_filtered) {
            copyToClipboard(NovaLog.dump(adapter.getItems()));
            return true;
        }
        if (id == R.id.action_export) {
            exportLogs();
            return true;
        }
        if (id == R.id.action_clear) {
            NovaLog.clear();
            fullRefresh();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NovaLog.unregister(logListener);
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            pendingRefresh = null;
        }
    }

    // ------------------------------------------------------------------
    // 刷新逻辑（防抖 + 增量追加）
    // ------------------------------------------------------------------

    private void scheduleRefresh() {
        if (pendingRefresh != null) {
            return;
        }
        pendingRefresh = () -> {
            pendingRefresh = null;
            incrementalRefresh();
        };
        handler.postDelayed(pendingRefresh, REFRESH_DEBOUNCE_MS);
    }

    /** 全量重建（首次进入 / 手动刷新 / 清空后） */
    private void fullRefresh() {
        List<NovaLog.Entry> snap = NovaLog.snapshot();
        allEntries.clear();
        allEntries.addAll(snap);
        if (snap.isEmpty()) {
            lastSeq = 0;
            bufferFirstSeq = -1;
        } else {
            lastSeq = snap.get(snap.size() - 1).seq;
            bufferFirstSeq = snap.get(0).seq;
        }
        applyFilter();
    }

    /**
     * 增量刷新：仅追加新日志，避免整表刷新破坏长按选择状态；
     * 若检测到缓冲区被清空或回绕，则退化为全量重建。
     */
    private void incrementalRefresh() {
        List<NovaLog.Entry> snap = NovaLog.snapshot();
        if (snap.isEmpty()) {
            if (!allEntries.isEmpty()) {
                fullRefresh();
            } else {
                updateStats();
            }
            return;
        }

        long firstSeq = snap.get(0).seq;
        if (bufferFirstSeq != -1 && firstSeq != bufferFirstSeq) {
            // 缓冲区被 clear 或发生回绕
            fullRefresh();
            return;
        }

        for (NovaLog.Entry e : snap) {
            if (e.seq > lastSeq) {
                allEntries.add(e);
                lastSeq = e.seq;
                if (matchesFilter(e)) {
                    adapter.append(e);
                }
            }
        }
        updateStats();
        maybeScrollToBottom();
    }

    // ------------------------------------------------------------------
    // 筛选
    // ------------------------------------------------------------------

    /** 按当前级别 + 关键字过滤全量数据并刷新列表 */
    private void applyFilter() {
        List<NovaLog.Entry> visible = new ArrayList<>(allEntries.size());
        for (NovaLog.Entry e : allEntries) {
            if (matchesFilter(e)) {
                visible.add(e);
            }
        }
        adapter.setData(visible);
        updateStats();
        maybeScrollToBottom();
    }

    private boolean matchesFilter(NovaLog.Entry e) {
        if (!enabledLevels.contains(e.level)) {
            return false;
        }
        if (query.isEmpty()) {
            return true;
        }
        return (e.tag != null && e.tag.toLowerCase(Locale.US).contains(query))
                || (e.message != null && e.message.toLowerCase(Locale.US).contains(query))
                || (e.stack != null && e.stack.toLowerCase(Locale.US).contains(query));
    }

    private void updateStats() {
        int visible = adapter.getItemCount();
        tvStats.setText(getString(R.string.log_stats_format, allEntries.size(), visible));
        tvEmpty.setVisibility(visible == 0 ? View.VISIBLE : View.GONE);
    }

    private void maybeScrollToBottom() {
        if (autoFollow && adapter.getItemCount() > 0) {
            scrollToBottom();
        }
    }

    private void scrollToBottom() {
        int count = adapter.getItemCount();
        if (count > 0) {
            rvLogs.scrollToPosition(count - 1);
        }
    }

    private void updateFollowMenu() {
        if (followItem != null) {
            followItem.setChecked(autoFollow);
        }
    }

    // ------------------------------------------------------------------
    // 详情 / 复制 / 导出
    // ------------------------------------------------------------------

    @Override
    public void onEntryClick(NovaLog.Entry entry) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_log_entry, null);
        TextView tvHeader = view.findViewById(R.id.tv_log_header);
        TextView tvBody = view.findViewById(R.id.tv_log_body);
        tvHeader.setText(NovaLog.formatShort(entry.time) + " "
                + NovaLog.levelLetter(entry.level) + "/" + entry.tag);
        tvBody.setText(entry.stack == null || entry.stack.isEmpty()
                ? entry.message
                : entry.message + "\n" + entry.stack);
        tvBody.setTextIsSelectable(true);

        new AlertDialog.Builder(this)
                .setTitle(R.string.log_detail_title)
                .setView(view)
                .setPositiveButton(R.string.log_copy_entry, (dialog, which) ->
                        copyToClipboard(NovaLog.formatFull(entry)))
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
            cm.setPrimaryClip(ClipData.newPlainText("NovaLog", text));
        }
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
    }

    /** 将当前筛选结果写入缓存目录并调起系统分享 */
    private void exportLogs() {
        try {
            String text = NovaLog.dump(adapter.getItems());
            if (text.isEmpty()) {
                Toast.makeText(this, R.string.log_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            File dir = new File(getCacheDir(), "logs");
            if (!dir.exists() && !dir.mkdirs()) {
                Toast.makeText(this, getString(R.string.log_export_failed, "mkdir"), Toast.LENGTH_SHORT).show();
                return;
            }
            String name = "nova-log-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                    .format(new Date()) + ".log";
            File file = new File(dir, name);
            try (FileWriter writer = new FileWriter(file)) {
                writer.append(text);
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.log_share_via)));
            Toast.makeText(this, getString(R.string.log_export_success, name), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.log_export_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }
}

