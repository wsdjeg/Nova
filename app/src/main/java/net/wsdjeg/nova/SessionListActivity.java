package net.wsdjeg.nova;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
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
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

/**
 * 会话列表界面
 * 显示所有会话，支持多账号
 * 点击进入聊天界面
 */
public class SessionListActivity extends AppCompatActivity implements SessionAdapter.OnSessionClickListener {
    
    private static final int REFRESH_INTERVAL_MS = 5000; // 5秒刷新一次
    private static final int REQUEST_ACCOUNT_MANAGE = 1001;
    
    private RecyclerView rvSessions;
    private FloatingActionButton fabNewSession;
    private SessionAdapter adapter;
    private List<Session> sessions;
    private SessionManager sessionManager;
    private SettingsManager settingsManager;
    private AccountManager accountManager;
    
    // 自动刷新相关
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private boolean isFirstRefreshDone = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_list);
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        sessionManager = new SessionManager(this);
        settingsManager = new SettingsManager(this);
        accountManager = AccountManager.getInstance(this);
        
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
        loadSessions();
        
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
            startActivityForResult(
                new Intent(this, AccountManagerActivity.class),
                REQUEST_ACCOUNT_MANAGE
            );
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
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_ACCOUNT_MANAGE) {
            // 从账号管理返回后，更新显示和刷新会话
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
    
    private void startAutoRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.post(refreshRunnable);
    }
    
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
        
        Account activeAccount = accountManager.getActiveAccount();
        if (activeAccount == null) {
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "请先添加账号", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 加载当前账号的会话
        List<Session> accountSessions = sessionManager.loadSessions(activeAccount.getId());
        
        for (Session session : accountSessions) {
            if (session.getAccountId() == null || session.getAccountId().isEmpty()) {
                session.setAccountId(activeAccount.getId());
            }
        }
        
        sessions.addAll(accountSessions);
        
        // 按 session ID 降序排序
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
     * 每个会话的 provider 和 model 来自服务器返回的数据
     */
    private void refreshSessionsFromServer() {
        Account activeAccount = accountManager.getActiveAccount();
        if (activeAccount == null) {
            return;
        }
        
        // 使用当前账号的 URL 和 API Key 创建 ApiClient
        String baseUrl = activeAccount.getUrl();
        String apiKey = activeAccount.getApiKey();
        
        if (baseUrl == null || baseUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            Toast.makeText(this, "账号配置不完整，请检查 URL 和 API Key", Toast.LENGTH_SHORT).show();
            return;
        }
        
        ApiClient accountApiClient = new ApiClient(baseUrl, apiKey);
        String accountId = activeAccount.getId();
        
        accountApiClient.getSessions(accountId, new ApiClient.SessionsCallback() {
            @Override
            public void onSuccess(List<Session> serverSessions) {
                runOnUiThread(() -> {
                    Map<String, Session> serverSessionMap = new HashMap<>();
                    for (Session session : serverSessions) {
                        serverSessionMap.put(session.getSessionId(), session);
                    }
                    
                    // 同步：添加或更新本地会话
                    for (Session serverSession : serverSessions) {
                        serverSession.setAccountId(accountId);
                        
                        Session localSession = sessionManager.getSession(serverSession.getSessionId());
                        if (localSession == null) {
                            // 新会话，直接添加（包含服务器返回的 provider/model）
                            sessionManager.addOrUpdateSession(serverSession, accountId);
                        } else {
                            // 已存在的会话，更新服务器返回的字段（包括 provider/model）
                            localSession.setProvider(serverSession.getProvider());
                            localSession.setModel(serverSession.getModel());
                            localSession.setCwd(serverSession.getCwd());
                            localSession.setInProgress(serverSession.isInProgress());
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
                    
                    if (!isFirstRefreshDone) {
                        isFirstRefreshDone = true;
                        String[] sessionIds = new String[serverSessions.size()];
                        for (int i = 0; i < serverSessions.size(); i++) {
                            sessionIds[i] = serverSessions.get(i).getSessionId();
                        }
                        initializeAllSessions(accountId, sessionIds, accountApiClient);
                        startAutoRefresh();
                    } else {
                        pollSessionMessages(accountId, accountApiClient);
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(SessionListActivity.this, 
                        "获取会话列表失败: " + error, Toast.LENGTH_SHORT).show();
                    if (!isFirstRefreshDone) {
                        isFirstRefreshDone = true;
                        startAutoRefresh();
                    }
                });
            }
        });
    }
    
    /**
     * 初始化所有会话（不再获取消息列表）
     * 消息列表只在打开会话时获取
     */
    private void initializeAllSessions(String accountId, String[] sessionIds, ApiClient accountApiClient) {
        // 不再获取消息列表，只完成初始化
        if (sessionIds == null || sessionIds.length == 0) {
            return;
        }
        
        // 标记所有会话为已初始化
        for (String sessionId : sessionIds) {
            sessionManager.addInitializedSession(sessionId);
        }
    }
    /**
     * 轮询会话状态（不再获取消息列表）
     * 消息列表只在打开会话时获取
     */
    private void pollSessionMessages(String accountId, ApiClient accountApiClient) {
        // 不再轮询消息，消息列表只在打开会话时获取
    }
    
    private void updateSingleSession(String sessionId) {
        Session updatedSession = sessionManager.getSession(sessionId);
        if (updatedSession == null) {
            return;
        }
        
        for (int i = 0; i < sessions.size(); i++) {
            if (sessions.get(i).getSessionId().equals(sessionId)) {
                sessions.set(i, updatedSession);
                adapter.notifyItemChanged(i);
                break;
            }
        }
    }
    
    /**
     * 创建新会话
     * 使用设置中的默认 provider 和 model，但会话创建后由服务器返回实际的 provider/model
     */
    private void createNewSession() {
        Account activeAccount = accountManager.getActiveAccount();
        if (activeAccount == null) {
            Toast.makeText(this, "请先添加账号", Toast.LENGTH_SHORT).show();
            startActivityForResult(
                new Intent(this, AccountManagerActivity.class),
                REQUEST_ACCOUNT_MANAGE
            );
            return;
        }
        
        String baseUrl = activeAccount.getUrl();
        String apiKey = activeAccount.getApiKey();
        
        if (baseUrl == null || baseUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            Toast.makeText(this, "账号配置不完整，请检查 URL 和 API Key", Toast.LENGTH_SHORT).show();
            return;
        }
        
        ApiClient accountApiClient = new ApiClient(baseUrl, apiKey);
        
        // 从设置获取默认的 provider 和 model（仅用于创建请求）
        String defaultProvider = settingsManager.getDefaultProvider();
        String defaultModel = settingsManager.getDefaultModel();
        
        Toast.makeText(this, "正在创建新会话...", Toast.LENGTH_SHORT).show();
        
        accountApiClient.createSession(null, defaultProvider, defaultModel, activeAccount.getId(), new ApiClient.CreateSessionCallback() {
            @Override
            public void onSuccess(Session session) {
                runOnUiThread(() -> {
                    String accountId = activeAccount.getId();
                    
                    session.setAccountId(accountId);
                    session.setLastMessageTime(System.currentTimeMillis());
                    // session 的 provider/model 由服务器返回，已在 ApiClient 中设置
                    
                    sessionManager.addOrUpdateSession(session, accountId);
                    sessionManager.saveCurrentSession(session.getSessionId());
                    sessionManager.addInitializedSession(session.getSessionId());
                    
                    loadSessions();
                    
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
    
    private void openChatActivity(String sessionId) {
        Session session = sessionManager.getSession(sessionId);
        if (session != null) {
            int currentMessageCount = session.getMessageCount();
            sessionManager.saveReadMessageCount(sessionId, currentMessageCount);
        }
        
        sessionManager.clearUnreadCount(sessionId);
        loadSessions();
        
        sessionManager.saveCurrentSession(sessionId);
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("session_id", sessionId);
        startActivity(intent);
    }
    
    @Override
    public void onSessionClick(Session session) {
        openChatActivity(session.getSessionId());
    }
    
    @Override
    public void onSessionLongClick(Session session) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("删除会话")
            .setMessage("确定要删除会话 " + session.getTitle() + " 吗？\n\n此操作不可恢复。")
            .setPositiveButton("删除", (dialog, which) -> {
                deleteSession(session);
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void deleteSession(Session session) {
        Account activeAccount = accountManager.getActiveAccount();
        
        if (activeAccount == null) {
            sessionManager.deleteSession(session.getSessionId());
            loadSessions();
            Toast.makeText(this, "已删除本地会话", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String baseUrl = activeAccount.getUrl();
        String apiKey = activeAccount.getApiKey();
        
        if (baseUrl == null || baseUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            // 账号配置不完整，只删除本地数据
            sessionManager.deleteSession(session.getSessionId());
            loadSessions();
            Toast.makeText(this, "已删除本地会话", Toast.LENGTH_SHORT).show();
            return;
        }
        
        ApiClient accountApiClient = new ApiClient(baseUrl, apiKey);
        
        Toast.makeText(this, "正在删除会话...", Toast.LENGTH_SHORT).show();
        
        accountApiClient.deleteSession(session.getSessionId(), new ApiClient.DeleteSessionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    sessionManager.deleteSession(session.getSessionId());
                    loadSessions();
                    Toast.makeText(SessionListActivity.this, 
                        "已删除会话", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
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
