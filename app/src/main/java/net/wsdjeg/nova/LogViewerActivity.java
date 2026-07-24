package net.wsdjeg.nova;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Log viewer Activity
 * Used to view the app's LogCat output for debugging
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
            getSupportActionBar().setTitle(R.string.title_log_viewer);
        }
        
        tvLog = findViewById(R.id.tv_log);
        scrollView = findViewById(R.id.scroll_view);
        
        executor = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());
        
        // Load logs
        loadLogs();
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == R.id.action_refresh) {
            loadLogs();
            return true;
        }
        if (item.getItemId() == R.id.action_clear) {
            logBuilder.setLength(0);
            tvLog.setText("");
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
    
    private void loadLogs() {
        executor.execute(() -> {
            try {
                // Read logs using logcat command
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
                
                // Keep only recent logs
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
                    tvLog.setText(getString(R.string.read_log_failed, e.getMessage()));
                });
            }
        });
    }
}

