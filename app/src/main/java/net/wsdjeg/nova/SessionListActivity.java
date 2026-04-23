package net.wsdjeg.nova;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
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
 * 显示所有会话，支持多账号聚合
 * 点击进入聊天界面
 * 
 * 功能：
 * - 定时刷新会话列表（每5秒）
 * - 定时轮询每个会话的消息，检测新消息
 * - 计算并显示未读消息数
 * - 点击进入会话时，保存当前消息数作为已读数
 * - 多账号聚合显示
 */
public class SessionListActivity extends AppCompatActivity implements SessionAdapter.OnSessionClickListener {
    
    private static final int REFRESH_INTERVAL_MS = 5000; // 5秒刷新一次
    private static final int REQUEST_ACCOUNT_MANAGE = 1001;
    
    private RecyclerView rvSessions;
    private FloatingActionButton fabNewSession;
    private TextView textAccount;
    private View accountSelector;
    private SessionAdapter adapter;
    private List<Session> sessions;
    private SessionManager sessionManager;
    private SettingsManager settingsManager;
    private AccountManager accountManager;
    private ApiClient apiClient;
    
    // 自动刷新相关
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private boolean isFirstRefreshDone = false;
    
    // 同步状态
    private int syncTotal = 0;      // 总共需要同步的会话数
    private int syncCompleted = 0;  // 已完成同步的会话数
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_list);
        
        // 设置 Toolbar 作为 ActionBar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        sessionManager = new SessionManager(this);
        settingsManager = new SettingsManager(this);
        accountManager = AccountManager.getInstance(this);
        apiClient = new ApiClient(settingsManager);
        
        initViews();
        setupRecyclerView();
        setupAutoRefresh();
        setupAccountSelector();
        
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
        updateAccountDisplay();
        
        // 如果有激活账号，立即触发刷新并启动自动刷新
        Account activeAccount = accountManager.getActiveAccount();
        if (activeAccount != null) {
            refreshSessionsFromServer();
            startAutoRefresh();
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
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_accounts) {
            openAccountManager();
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
    
    /**
     * 设置账号选择器
     */
    private void setupAccountSelector() {
        accountSelector = findViewById(R.id.account_selector);
        textAccount = findViewById(R.id.text_account);
        
        if (accountSelector != null) {
            accountSelector.setOnClickListener(v -> {
                openAccountManager();
            });
        }
        
        updateAccountDisplay();
    }
    
    /**
     * 更新账号显示
     */
    private void updateAccountDisplay() {
        if (textAccount == null) {
            return;
        }
        
        Account activeAccount = accountManager.getActiveAccount();
        if (activeAccount != null) {
            textAccount.setText(activeAccount.getDisplayName());
        } else {
            textAccount.setText("未配置账号");
        }
    }
    
    /**
     * 打开账号管理
     */
    private void openAccountManager() {
        startActivityForResult(
            new Intent(this, AccountManagerActivity.class),
            REQUEST_ACCOUNT_MANAGE
        );
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_ACCOUNT_MANAGE) {
            // 从账号管理返回后，更新显示和刷新会话
            updateAccountDisplay();
            loadSessions();
            refreshSessionsFromServer();
        }
    }
    
    private void setupRecyclerView() {
        sessions = new ArrayList<>();
        adapter = new SessionAdapter(sessions, this);
        adapter.setAccountManager(accountManager);
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
                Account activeAccount = accountManager.getActiveAccount();
                if (activeAccount != null) {
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
     * 支持多账号聚合显示
     */
    private void loadSessions() {
        sessions.clear();
        
        Account activeAccount = accountManager.getActiveAccount();
        if (activeAccount == null) {
            // 没有激活账号，显示空列表
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "请先添加账号", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 加载当前账号的会话
        List<Session> accountSessions = sessionManager.loadSessions(activeAccount.getId());
        
        // 设置 accountId
        for (Session session : accountSessions) {
            if (session.getAccountId() == null || session.getAccountId().isEmpty()) {
                session.setAccountId(activeAccount.getId());
            }
        }
        
        sessions.addAll(accountSessions);
        
        // 按 session ID 降序排序（最新的 session 排在前面）
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
        Account activeAccount = accountManager.getActiveAccount();
        if (activeAccount == null) {
            return;
        }
        
        String accountId = activeAccount.getId();
        
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
                        serverSession.setAccountId(accountId);
                        
                        Session localSession = sessionManager.getSession(serverSession.getSessionId());
                        if (localSession == null) {
                            // 本地没有，添加新会话（保留服务器的完整信息）
                            sessionManager.addOrUpdateSession(serverSession, accountId);
                        } else {
                            // 本地已有，更新 provider, model, cwd 信息
                            localSession.setProvider(serverSession.getProvider());
                            localSession.setModel(serverSession.getModel());
                            localSession.setCwd(serverSession.getCwd());
                            sessionManager.addOrUpdateSession(localSession, accountId);
                        }
                    }
                    
                    // 同步：删除服务器没有的本地会话
                    List<Session> localSessions = sessionManager.loadSessions(accountId);
                    for (Session localSession : localSessions) {
                        if (!serverSessionMap.containsKey(localSession.getSessionId())) {
                            sessionManager.deleteSession(localSession.getSessionId());
                        }
                    }
                    
                    loadSessions();
                    
                    // 第一次刷新后，初始化所有会话的消息
                    if (!isFirstRefreshDone) {
                        isFirstRefreshDone = true;
                        String[] sessionIds = new String[serverSessions.size()];
                        for (int i = 0; i < serverSessions.size(); i++) {
                            sessionIds[i] = serverSessions.get(i).getSessionId();
                        }
                        initializeAllSessions(accountId, sessionIds);
                        startAutoRefresh();
                    } else {
                        // 后续刷新：轮询每个会话的消息，检测新消息
                        pollSessionMessages(accountId);
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
    private void initializeAllSessions(String accountId, String[] sessionIds) {
        if (sessionIds == null || sessionIds.length == 0) {
            return;
        }
        
        // 加载已初始化的会话列表
        Set<String> initializedSessions = sessionManager.loadInitializedSessions();
        
        // 重置同步计数
        syncTotal = 0;
        syncCompleted = 0;
        
        // 统计需要同步的数量
        for (String sessionId : sessionIds) {
            if (!initializedSessions.contains(sessionId)) {
                syncTotal++;
            }
        }
        
        // 显示同步提示
        if (syncTotal > 0) {
            updateSyncProgress();
        }
        
        // 遍历所有会话，获取消息列表
        for (String sessionId : sessionIds) {
            // 跳过已初始化的会话
            if (initializedSessions.contains(sessionId)) {
                continue;
            }
            
            // 获取该会话的消息列表
            final String currentSessionId = sessionId;
            apiClient.getMessages(sessionId, new ApiClient.MessagesCallback() {
                @Override
                public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                    runOnUiThread(() -> {
                        // 标记为已初始化并保存
                        sessionManager.addInitializedSession(currentSessionId);
                        syncCompleted++;
                        
                        if (chatMessages.isEmpty()) {
                            updateSyncProgress();
                            return;
                        }
                        
                        // 过滤掉 tool 消息，与 ChatActivity 保持一致
                        List<ApiClient.ChatMessage> filteredMessages = new ArrayList<>();
                        for (ApiClient.ChatMessage msg : chatMessages) {
                            if (!"tool".equals(msg.role)) {
                                filteredMessages.add(msg);
                            }
                        }
                        
                        if (filteredMessages.isEmpty()) {
                            updateSyncProgress();
                            return;
                        }
                        
                        // 获取第一条和最后一条消息
                        ApiClient.ChatMessage firstMsg = filteredMessages.get(0);
                        ApiClient.ChatMessage lastMsg = filteredMessages.get(filteredMessages.size() - 1);
                        long lastMessageTime = lastMsg.created * 1000; // 转换为毫秒
                        int messageCount = filteredMessages.size();
                        
                        // 更新会话信息
                        sessionManager.updateMessages(
                            currentSessionId,
                            firstMsg.content,
                            lastMsg.content,
                            messageCount,
                            lastMessageTime
                        );
                        
                        // 初始化时，设置已读消息数为当前消息数
                        sessionManager.saveReadMessageCount(currentSessionId, messageCount);
                        
                        // 实时更新 UI - 找到该会话并更新
                        updateSingleSession(currentSessionId);
                        
                        // 更新同步进度
                        updateSyncProgress();
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        // 即使失败也计数
                        syncCompleted++;
                        updateSyncProgress();
                        // 忽略单个会话的消息获取错误
                    });
                }
            });
        }
    }
    
    /**
     * 轮询所有会话的消息，检测新消息并计算未读数
     * 在定时刷新时调用
     */
    private void pollSessionMessages(String accountId) {
        Account activeAccount = accountManager.getActiveAccount();
        if (activeAccount == null) {
            return;
        }
        
        // 获取当前所有会话
        List<Session> currentSessions = sessionManager.loadSessions(accountId);
        if (currentSessions.isEmpty()) {
            return;
        }
        
        // 加载已初始化的会话列表，只轮询已初始化的会话
        // 避免在初始化过程中计算错误的未读数
        Set<String> initializedSessions = sessionManager.loadInitializedSessions();
        
        // 轮询每个会话的消息
        for (Session session : currentSessions) {
            final String sessionId = session.getSessionId();
            
            // 跳过未初始化的会话，避免未读数计算错误
            if (!initializedSessions.contains(sessionId)) {
                continue;
            }
            
            // 在 API 回调中重新获取 readCount，确保使用最新的值
            apiClient.getMessages(sessionId, new ApiClient.MessagesCallback() {
                @Override
                public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                    runOnUiThread(() -> {
                        if (chatMessages.isEmpty()) {
                            return;
                        }
                        
                        // 过滤掉 tool 消息，与 ChatActivity 保持一致
                        List<ApiClient.ChatMessage> filteredMessages = new ArrayList<>();
                        for (ApiClient.ChatMessage msg : chatMessages) {
                            if (!"tool".equals(msg.role)) {
                                filteredMessages.add(msg);
                            }
                        }
                        
                        if (filteredMessages.isEmpty()) {
                            return;
                        }
                        
                        // 获取第一条和最后一条消息
                        ApiClient.ChatMessage firstMsg = filteredMessages.get(0);
                        ApiClient.ChatMessage lastMsg = filteredMessages.get(filteredMessages.size() - 1);
                        long lastMessageTime = lastMsg.created * 1000;
                        int serverMessageCount = filteredMessages.size();
                        
                        // 在回调中重新获取 readCount，确保使用最新的已读消息数
                        int readCount = sessionManager.getReadMessageCount(sessionId);
                        
                        // 计算未读数：服务器消息数 - 已读消息数
                        int unreadCount = serverMessageCount - readCount;
                        if (unreadCount < 0) {
                            unreadCount = 0;
                        }
                        
                        // 更新会话信息
                        sessionManager.updateMessages(
                            sessionId,
                            firstMsg.content,
                            lastMsg.content,
                            serverMessageCount,
                            lastMessageTime
                        );
                        
                        // 更新未读数
                        Session updatedSession = sessionManager.getSession(sessionId);
                        if (updatedSession != null) {
                            updatedSession.setUnreadCount(unreadCount);
                            sessionManager.addOrUpdateSession(updatedSession, accountId);
                            
                            // 更新 UI
                            updateSingleSession(sessionId);
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    // 忽略单个会话的轮询错误
                }
            });
        }
    }
    
    /**
     * 更新单个会话的 UI
     */
    private void updateSingleSession(String sessionId) {
        // 重新从 SessionManager 加载该会话
        Session updatedSession = sessionManager.getSession(sessionId);
        if (updatedSession == null) {
            return;
        }
        
        // 在当前列表中查找并更新
        for (int i = 0; i < sessions.size(); i++) {
            if (sessions.get(i).getSessionId().equals(sessionId)) {
                sessions.set(i, updatedSession);
                adapter.notifyItemChanged(i);
                break;
            }
        }
    }
    
    /**
     * 更新同步进度提示
     */
    private void updateSyncProgress() {
        if (syncTotal == 0) {
            return;
        }
        
        if (syncCompleted < syncTotal) {
            // 正在同步
            Toast.makeText(this, 
                String.format("正在同步会话消息 (%d/%d)...", syncCompleted, syncTotal), 
                Toast.LENGTH_SHORT).show();
        } else if (syncCompleted == syncTotal) {
            // 同步完成
            Toast.makeText(this, 
                String.format("已同步 %d 个会话的消息", syncTotal), 
                Toast.LENGTH_SHORT).show();
            syncTotal = 0;
            syncCompleted = 0;
        }
    }
    
    /**
     * 创建新会话
     * 通过 API 创建新会话
     */
    private void createNewSession() {
        Account activeAccount = accountManager.getActiveAccount();
        if (activeAccount == null) {
            Toast.makeText(this, "请先添加账号", Toast.LENGTH_SHORT).show();
            openAccountManager();
            return;
        }
        
        // 显示创建中提示
        Toast.makeText(this, "正在创建新会话...", Toast.LENGTH_SHORT).show();
        
        // 通过 API 创建新会话
        apiClient.createSession(null, null, null, new ApiClient.CreateSessionCallback() {
            @Override
            public void onSuccess(Session session) {
                runOnUiThread(() -> {
                    String accountId = activeAccount.getId();
                    
                    // 保存到本地
                    session.setAccountId(accountId);
                    session.setLastMessageTime(System.currentTimeMillis());
                    sessionManager.addOrUpdateSession(session, accountId);
                    
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
     * 在进入会话时，保存当前消息数作为已读消息数
     */
    private void openChatActivity(String sessionId) {
        // 获取该会话的消息数，作为已读消息数保存
        Session session = sessionManager.getSession(sessionId);
        if (session != null) {
            int currentMessageCount = session.getMessageCount();
            sessionManager.saveReadMessageCount(sessionId, currentMessageCount);
        }
        
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
        String accountId = session.getAccountId();
        if (accountId == null || accountId.isEmpty()) {
            accountId = accountManager.getActiveAccount() != null 
                ? accountManager.getActiveAccount().getId() 
                : "";
        }
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
        Account activeAccount = accountManager.getActiveAccount();
        String accountId = session.getAccountId();
        if (accountId == null || accountId.isEmpty()) {
            accountId = activeAccount != null ? activeAccount.getId() : "";
        }
        
        if (activeAccount == null) {
            // 如果没有激活账号，只删除本地数据
            sessionManager.deleteSession(session.getSessionId());
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
