package net.wsdjeg.nova;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * 会话列表界面
 * 显示所有会话，点击进入聊天界面
 */
public class SessionListActivity extends AppCompatActivity implements SessionAdapter.OnSessionClickListener {
    
    private static final int REFRESH_INTERVAL_MS = 5000; // 5秒刷新一次
    
    private RecyclerView rvSessions;
    private FloatingActionButton fabNewSession;
    private SessionAdapter adapter;
    private List<Session> sessions;
    private SessionManager sessionManager;
    private SettingsManager settingsManager;
    private ApiClient apiClient;
    
    // 自动刷新相关
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private boolean isFirstRefreshDone = false;
    private Set<String> initializedSessions = new HashSet<>();
    
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
        setupAutoRefresh();
        
        // 先加载本地会话列表
        loadSessions();
        
        // 启动时刷新会话列表
        refreshSessionsFromServer();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 每次返回界面时刷新会话列表（包括从设置页面返回）
        loadSessions();
        
        // 如果设置有效，立即触发刷新并启动自动刷新
        // 这确保从设置页面保存后返回时能立即开始刷新
        if (settingsManager.hasValidSettings()) {
            refreshSessionsFromServer();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        stopAutoRefresh();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoRefresh();
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
            // 从设置页面返回后会自动调用 onResume 刷新
            startActivity(new Intent(this, SettingsActivity.class));
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
     * 设置自动刷新
     */
    private void setupAutoRefresh() {
        refreshHandler = new Handler(Looper.getMainLooper());
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (settingsManager.hasValidSettings()) {
                    refreshSessionsFromServer();
                }
                refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
            }
        };
    }
    
    /**
     * 启动自动刷新
     */
    private void startAutoRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.post(refreshRunnable);
    }
    
    /**
     * 停止自动刷新
     */
    private void stopAutoRefresh() {
        if (refreshHandler != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }
    
    /**
     * 从本地加载会话列表
     */
    private void loadSessions() {
        sessions.clear();
        sessions.addAll(sessionManager.loadSessions());
        
        // 按 session ID 降序排序（最新的 session 排在前面）
        // session ID 格式为 年月日时分秒随机数，字典序降序即为最新在前
        Collections.sort(sessions, new Comparator<Session>() {
            @Override
            public int compare(Session s1, Session s2) {
                return s2.getSessionId().compareTo(s1.getSessionId());
            }
        });
        
        adapter.notifyDataSetChanged();
        
        if (sessions.isEmpty()) {
            Toast.makeText(this, "暂无会话，点击 + 创建新会话", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 从服务器刷新会话列表
     */
    private void refreshSessionsFromServer() {
        if (!settingsManager.hasValidSettings()) {
            return;
        }
        
        apiClient.getSessions(new ApiClient.SessionsCallback() {
            @Override
            public void onSuccess(String[] sessionIds) {
                runOnUiThread(() -> {
                    // 更新本地会话列表
                    for (String sessionId : sessionIds) {
                        Session session = sessionManager.getSession(sessionId);
                        if (session == null) {
                            session = new Session(sessionId);
                            sessionManager.addOrUpdateSession(session);
                        }
                    }
                    
                    loadSessions();
                    
                    // 第一次刷新后，获取每个会话的消息列表
                    if (!isFirstRefreshDone) {
                        isFirstRefreshDone = true;
                        initializeAllSessions(sessionIds);
                        startAutoRefresh();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (!isFirstRefreshDone) {
                        // 首次刷新失败也标记为已完成，允许后续定时重试
                        isFirstRefreshDone = true;
                        startAutoRefresh();
                    }
                });
            }
        });
    }
    
    /**
     * 初始化所有会话的消息列表
     * 第一次刷新后，每个会话获取一次消息列表
     */
    private void initializeAllSessions(String[] sessionIds) {
        for (String sessionId : sessionIds) {
            if (!initializedSessions.contains(sessionId)) {
                initializedSessions.add(sessionId);
                fetchMessagesForSession(sessionId);
            }
        }
    }
    
    /**
     * 获取指定会话的消息列表并更新
     */
    private void fetchMessagesForSession(String sessionId) {
        apiClient.getMessages(sessionId, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    if (!chatMessages.isEmpty()) {
                        ApiClient.ChatMessage lastMsg = chatMessages.get(chatMessages.size() - 1);
                        // 查找第一条用户消息作为标题
                        String firstUserMessage = null;
                        for (ApiClient.ChatMessage msg : chatMessages) {
                            if ("user".equals(msg.role)) {
                                firstUserMessage = msg.content;
                                break;
                            }
                        }
                        sessionManager.updateMessages(
                            sessionId,
                            firstUserMessage,
                            lastMsg.content,
                            chatMessages.size()
                        );
                        loadSessions();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                // 忽略单个会话的消息获取错误
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
        // 清除该会话的未读数
        sessionManager.clearUnreadCount(sessionId);
        loadSessions();
        
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
                initializedSessions.remove(session.getSessionId());
                loadSessions();
                Toast.makeText(this, "已删除会话", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }
}
