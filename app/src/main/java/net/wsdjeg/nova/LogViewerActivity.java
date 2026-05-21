package net.wsdjeg.nova;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 日志查看器 Activity
 * 用于查看 App 的 LogCat 输出，方便调试
 */
public class LogViewerActivity extends AppCompatActivity {
    
    private static final String TAG = "LogViewer";
    private TextView tvLog;
    private ScrollView scrollView;
    private ExecutorService executor;
    private Handler handler;
    private boolean autoRefresh = true;
    private StringBuilder logBuilder = new StringBuilder();
    private static final int MAX_LINES = 500;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_viewer);
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("日志查看器");
        }
        
        tvLog = findViewById(R.id.tv_log);
        scrollView = findViewById(R.id.scroll_view);
        
        executor = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());
        
        // 加载日志
        loadLogs();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.log_viewer_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        }
        if (itemId == R.id.action_refresh) {
            loadLogs();
            return true;
        }
        if (itemId == R.id.action_clear) {
            logBuilder.setLength(0);
            tvLog.setText("");
            return true;
        }
        if (itemId == R.id.action_copy) {
            copyLogToClipboard();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
    
    private void copyLogToClipboard() {
        String log = tvLog.getText().toString();
        if (log.isEmpty()) {
            Toast.makeText(this, "没有日志可复制", Toast.LENGTH_SHORT).show();
            return;
        }
        
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("日志", log);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show();
    }
    
    private void loadLogs() {
        executor.execute(() -> {
            try {
                // 使用 logcat 命令读取日志
                Process process = Runtime.getRuntime().exec(
                    "logcat -d -t " + MAX_LINES + " -v time NovaChat:V ApiClient:V MessageAdapter:V *:S"
                );
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
                );
                
                List<String> lines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
                
                // 只保留最近的日志
                if (lines.size() > MAX_LINES) {
                    lines = lines.subList(lines.size() - MAX_LINES, lines.size());
                }
                
                StringBuilder sb = new StringBuilder();
                for (String l : lines) {
                    sb.append(l).append("\n");
                }
                
                handler.post(() -> {
                    tvLog.setText(sb.toString());
                    scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
                });
                
            } catch (Exception e) {
                handler.post(() -> {
                    tvLog.setText("读取日志失败: " + e.getMessage());
                });
            }
        });
    }
}
