package net.wsdjeg.nova;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话列表界面
 * 显示所有会话，点击进入聊天界面
 */
public class SessionListActivity extends AppCompatActivity implements SessionAdapter.OnSessionClickListener {
    
    private RecyclerView rvSessions;
    private FloatingActionButton fabNewSession;
    private SessionAdapter adapter;
    private List<Session> sessions;
    private SessionManager sessionManager;
    private SettingsManager settingsManager;
    private ApiClient apiClient;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_list);
        
        // 设置 Toolbar 作为 ActionBar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        sessionManager = new SessionManager(this);
        settingsManager = new SettingsManager(this);
        apiClient = new ApiClient(settingsManager);
        
        initViews();
        setupRecyclerView();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 每次返回界面时刷新会话列表
        loadSessions();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.session_list_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_refresh) {
            loadSessionsFromServer();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void initViews() {
        rvSessions = findViewById(R.id.rv_sessions);
        fabNewSession = findViewById(R.id.fab_new_session);
        
        fabNewSession.setOnClickListener(v -> createNewSession());
    }
    
    private void setupRecyclerView() {
        sessions = new ArrayList<>();
        adapter = new SessionAdapter(sessions, this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvSessions.setLayoutManager(layoutManager);
        rvSessions.setAdapter(adapter);
    }
    
    /**
     * 从本地加载会话列表
     */
    private void loadSessions() {
        sessions.clear();
        sessions.addAll(sessionManager.loadSessions());
        adapter.notifyDataSetChanged();
        
        if (sessions.isEmpty()) {
            Toast.makeText(this, "暂无会话，点击 + 创建新会话", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 从服务器加载会话列表
     */
    private void loadSessionsFromServer() {
        if (!settingsManager.hasValidSettings()) {
            Toast.makeText(this, "请先配置 API 设置", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "正在刷新...", Toast.LENGTH_SHORT).show();
        
        apiClient.getSessions(new ApiClient.SessionsCallback() {
            @Override
            public void onSuccess(String[] sessionIds) {
                runOnUiThread(() -> {
                    // 更新本地会话列表
                    for (String sessionId : sessionIds) {
                        Session session = new Session(sessionId);
                        sessionManager.addOrUpdateSession(session);
                    }
                    
                    loadSessions();
                    Toast.makeText(SessionListActivity.this, 
                        "已加载 " + sessionIds.length + " 个会话", 
                        Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> 
                    Toast.makeText(SessionListActivity.this, 
                        "加载失败: " + error, 
                        Toast.LENGTH_SHORT).show()
                );
            }
        });
    }
    
    /**
     * 创建新会话
     */
    private void createNewSession() {
        if (!settingsManager.hasValidSettings()) {
            Toast.makeText(this, "请先配置 API 设置", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        
        // 生成新的 session ID
        String newSessionId = java.util.UUID.randomUUID().toString();
        
        // 保存为新会话
        Session newSession = new Session(newSessionId);
        newSession.setLastMessageTime(System.currentTimeMillis());
        sessionManager.addOrUpdateSession(newSession);
        
        // 设置为当前会话
        sessionManager.saveCurrentSession(newSessionId);
        
        // 可选：创建后直接打开聊天界面
        openChatActivity(newSessionId);
    }
    
    /**
     * 打开聊天界面
     */
    private void openChatActivity(String sessionId) {
        sessionManager.saveCurrentSession(sessionId);
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("session_id", sessionId);
        startActivity(intent);
    }
    
    // ========== SessionAdapter.OnSessionClickListener 接口实现 ==========
    
    @Override
    public void onSessionClick(Session session) {
        // 点击会话，进入聊天界面
        openChatActivity(session.getSessionId());
    }
    
    @Override
    public void onSessionLongClick(Session session) {
        // 长按会话，显示删除确认对话框
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("删除会话")
            .setMessage("确定要删除会话 " + session.getTitle() + " 吗？\n\n此操作不可恢复。")
            .setPositiveButton("删除", (dialog, which) -> {
                sessionManager.deleteSession(session.getSessionId());
                loadSessions();
                Toast.makeText(this, "已删除会话", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }
}
