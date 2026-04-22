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
        } else if (id == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
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
            public void onSuccess(List<Session> serverSessions) {
                runOnUiThread(() -> {
                    // 将服务器会话转为 Map，方便查找
                    java.util.Map<String, Session> serverSessionMap = new java.util.HashMap<>();
                    for (Session session : serverSessions) {
                        serverSessionMap.put(session.getSessionId(), session);
                    }
                    
                    // 同步：添加或更新本地会话
                    for (Session serverSession : serverSessions) {
                        Session localSession = sessionManager.getSession(serverSession.getSessionId());
                        if (localSession == null) {
                            // 本地没有，添加新会话（保留服务器的完整信息）
                            sessionManager.addOrUpdateSession(serverSession);
                        } else {
                            // 本地已有，更新 provider, model, cwd 信息
                            localSession.setProvider(serverSession.getProvider());
                            localSession.setModel(serverSession.getModel());
                            localSession.setCwd(serverSession.getCwd());
                            sessionManager.addOrUpdateSession(localSession);
                        }
                    }
                    
                    // 同步：删除服务器没有的本地会话
                    List<Session> localSessions = sessionManager.loadSessions();
                    for (Session localSession : localSessions) {
                        if (!serverSessionMap.containsKey(localSession.getSessionId())) {
                            sessionManager.deleteSession(localSession.getSessionId());
                            initializedSessions.remove(localSession.getSessionId());
                        }
                    }
                    
                    loadSessions();
                    
                    // 第一次刷新后，获取每个会话的消息列表
                    if (!isFirstRefreshDone) {
                        isFirstRefreshDone = true;
                        String[] sessionIds = new String[serverSessions.size()];
                        for (int i = 0; i < serverSessions.size(); i++) {
                            sessionIds[i] = serverSessions.get(i).getSessionId();
                        }
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
     * 初始化所有会话的消息信息
     * 获取每个会话的消息列表，提取最后一条消息作为预览
     */
    private void initializeAllSessions(String[] sessionIds) {
        for (String sessionId : sessionIds) {
            if (initializedSessions.contains(sessionId)) {
                continue;
            }
            
            apiClient.getMessages(sessionId, new ApiClient.MessagesCallback() {
                @Override
                public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                    runOnUiThread(() -> {
                        initializedSessions.add(sessionId);
                        
                        if (chatMessages.isEmpty()) {
                            return;
                        }
                        
                        // 获取最后一条消息
                        ApiClient.ChatMessage lastMsg = chatMessages.get(chatMessages.size() - 1);
                        long lastMessageTime = lastMsg.created * 1000; // 转换为毫秒
                        
                        // 更新会话信息
                        Session session = sessionManager.getSession(sessionId);
                        if (session != null) {
                            session.updateSessionInfo(
                                lastMsg.content,
                                chatMessages.size(),
                                lastMessageTime
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
    }
    
    /**
     * 创建新会话
     * 通过 API 创建新会话
     */
    private void createNewSession() {
        if (!settingsManager.hasValidSettings()) {
            Toast.makeText(this, "请先配置 API 设置", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        
        // 显示创建中提示
        Toast.makeText(this, "正在创建新会话...", Toast.LENGTH_SHORT).show();
        
        // 通过 API 创建新会话
        apiClient.createSession(null, null, null, new ApiClient.CreateSessionCallback() {
            @Override
            public void onSuccess(Session session) {
                runOnUiThread(() -> {
                    // 保存到本地
                    session.setLastMessageTime(System.currentTimeMillis());
                    sessionManager.addOrUpdateSession(session);
                    
                    // 设置为当前会话
                    sessionManager.saveCurrentSession(session.getSessionId());
                    
                    // 刷新列表
                    loadSessions();
                    
                    // 直接打开聊天界面
                    openChatActivity(session.getSessionId());
                    
                    Toast.makeText(SessionListActivity.this, 
                        "已创建新会话: " + session.getSessionId(), 
                        Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(SessionListActivity.this, 
                        "创建会话失败: " + error, 
                        Toast.LENGTH_SHORT).show();
                });
            }
        });
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
                deleteSession(session);
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 删除会话（通过 API）
     */
    private void deleteSession(Session session) {
        if (!settingsManager.hasValidSettings()) {
            // 如果没有配置 API，只删除本地数据
            sessionManager.deleteSession(session.getSessionId());
            initializedSessions.remove(session.getSessionId());
            loadSessions();
            Toast.makeText(this, "已删除本地会话", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 显示删除中提示
        Toast.makeText(this, "正在删除会话...", Toast.LENGTH_SHORT).show();
        
        // 调用 API 删除会话
        apiClient.deleteSession(session.getSessionId(), new ApiClient.DeleteSessionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    // 删除成功，同时删除本地数据
                    sessionManager.deleteSession(session.getSessionId());
                    initializedSessions.remove(session.getSessionId());
                    loadSessions();
                    Toast.makeText(SessionListActivity.this, 
                        "已删除会话", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    // 特殊处理：如果是 404，说明会话在服务器上不存在，直接删除本地数据
                    if (error.contains("404") || error.contains("Not Found")) {
                        sessionManager.deleteSession(session.getSessionId());
                        initializedSessions.remove(session.getSessionId());
                        loadSessions();
                        Toast.makeText(SessionListActivity.this, 
                            "已删除本地会话（服务器上不存在）", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(SessionListActivity.this, 
                            "删除会话失败: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}
