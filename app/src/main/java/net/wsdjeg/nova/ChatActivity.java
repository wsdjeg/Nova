package net.wsdjeg.nova;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天界面 Activity
 * 显示单个会话的消息列表
 * 
 * 分页加载逻辑：
 * - 初始加载：since = message_count - 20
 * - 上滚到顶部：since -= 20，加载更早的消息
 * - 直到 since <= 1 停止加载
 * 
 * 滚动位置保持优化：
 * - 加载历史消息时保持用户阅读位置
 * - 自动刷新时只在用户位于底部才自动滚动
 */
public class ChatActivity extends AppCompatActivity {
    
    private static final String TAG = "ChatActivity";
    private static final int REFRESH_INTERVAL_MS = 3000; // 3秒刷新一次
    private static final int PAGE_SIZE = 20; // 每页加载的消息数
    private static final int STATE_NORMAL = 0;
    private static final int STATE_SENDING = 1;
    private static final int BOTTOM_THRESHOLD = 3; // 距离底部3条消息以内视为"在底部"
    
    // Intent extras
    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_SESSION_TITLE = "session_title";
    
    private Toolbar toolbar;
    private TextView tvSessionTitle;
    private TextView tvSessionInfo;
    private TextView tvSessionPath;
    private RecyclerView rvMessages;
    private EditText etMessage;
    private Button btnSend;
    private MessageAdapter adapter;
    private List<Message> messages;
    private ApiClient apiClient;
    private SettingsManager settingsManager;
    private SessionManager sessionManager;
    private AccountManager accountManager;
    
    private String currentSessionId;
    private String currentSessionTitle;
    private String accountId;
    
    // 自动刷新相关
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private boolean isAutoRefreshEnabled = true;
    private int lastMessageCount = 0;
    
    // 分页加载相关
    private int totalMessageCount = 0; // 从 Session 获取的消息总数
    private int currentSince = 0;       // 当前加载的起始位置
    private boolean isLoadingMore = false; // 是否正在加载更多
    private boolean hasMoreMessages = true; // 是否还有更多历史消息
    
    // 按钮状态
    private int buttonState = STATE_NORMAL;
    
    private boolean isInProgress = false; // 从 API 获取的会话状态

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        
        // 设置 Toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        // 初始化顶部会话信息控件（三行）
        tvSessionTitle = findViewById(R.id.tv_session_title);
        tvSessionInfo = findViewById(R.id.tv_session_info);
        tvSessionPath = findViewById(R.id.tv_session_path);
        
        // 获取传入的 session ID
        currentSessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        currentSessionTitle = getIntent().getStringExtra(EXTRA_SESSION_TITLE);
        
        if (currentSessionId == null || currentSessionId.isEmpty()) {
            Toast.makeText(this, "无效的会话ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // 不显示标题文字，改为显示会话信息
        toolbar.setTitle("");
        settingsManager = new SettingsManager(this);
        sessionManager = new SessionManager(this);
        accountManager = AccountManager.getInstance(this);
        
        // 根据 session 所属账号获取正确的 ApiClient
        Session session = sessionManager.getSession(currentSessionId);
        Account sessionAccount = null;
        
        // 调试日志
        Log.d(TAG, "Initializing ChatActivity with sessionId: " + currentSessionId);
        if (session != null) {
            Log.d(TAG, "Session found: accountId=" + session.getAccountId() + ", cwd=" + session.getCwd());
            totalMessageCount = session.getMessageCount();
            Log.d(TAG, "Total message count from session: " + totalMessageCount);
        } else {
            Log.w(TAG, "Session NOT found locally!");
        }
        
        if (session != null && session.getAccountId() != null && !session.getAccountId().isEmpty()) {
            sessionAccount = accountManager.getAccountById(session.getAccountId());
            Log.d(TAG, "Using session's account: " + session.getAccountId());
        }
        
        if (sessionAccount == null) {
            sessionAccount = accountManager.getActiveAccount();
            Log.d(TAG, "Using active account instead");
        }
        
        if (sessionAccount == null) {
            Toast.makeText(this, "请先添加账号", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        accountId = sessionAccount.getId();
        Log.d(TAG, "Final account: " + accountId + ", URL: " + sessionAccount.getUrl());
        
        String baseUrl = sessionAccount.getUrl();
        String apiKey = sessionAccount.getApiKey();
        
        if (baseUrl == null || baseUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            Toast.makeText(this, "账号配置不完整，请检查 URL 和 API Key", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        apiClient = new ApiClient(baseUrl, apiKey);
        settingsManager.setSession(currentSessionId);
        sessionManager.saveCurrentSession(currentSessionId);
        
        loadSessionInfo();
        initViews();
        setupRecyclerView();
        setupAutoRefresh();
        loadInitialMessages();
    }
    
    /**
     * 加载初始消息
     */
    private void loadInitialMessages() {
        if (totalMessageCount <= 0) {
            addMessage("正在加载消息...", false);
            refreshSessionStatus(() -> {
                if (messages.size() == 1 && messages.get(0).getContent().equals("正在加载消息...")) {
                    messages.clear();
                }
                
                Session session = sessionManager.getSession(currentSessionId);
                if (session != null) {
                    totalMessageCount = session.getMessageCount();
                    Log.d(TAG, "Updated total message count: " + totalMessageCount);
                }
                
                if (totalMessageCount <= 0) {
                    addMessage("暂无消息", false);
                    return;
                }
                
                loadMessagesPage();
            });
        } else {
            loadMessagesPage();
        }
    }
    
    /**
     * 加载一页消息
     */
    private void loadMessagesPage() {
        if (apiClient == null || currentSessionId == null) {
            return;
        }
        
        if (totalMessageCount <= 0) {
            if (messages.isEmpty()) {
                addMessage("暂无消息", false);
            }
            return;
        }
        
        if (currentSince == 0) {
            if (totalMessageCount <= PAGE_SIZE) {
                currentSince = 1;
                hasMoreMessages = false;
            } else {
                currentSince = totalMessageCount - PAGE_SIZE + 1;
                hasMoreMessages = true;
            }
        }
        
        Log.d(TAG, "Loading messages: since=" + currentSince + ", total=" + totalMessageCount);
        
        isLoadingMore = true;
        
        apiClient.getMessagesWithOptions(currentSessionId, currentSince, PAGE_SIZE, false, 
            new ApiClient.MessagesCallback() {
                @Override
                public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                    runOnUiThread(() -> {
                        isLoadingMore = false;
                        
                        if (messages.size() == 1 && messages.get(0).getContent().equals("正在加载消息...")) {
                            messages.clear();
                        }
                        
                        if (chatMessages.isEmpty()) {
                            if (messages.isEmpty()) {
                                addMessage("暂无消息", false);
                            }
                            return;
                        }
                        
                        // 过滤掉 tool 消息
                        List<ApiClient.ChatMessage> filteredMessages = new ArrayList<>();
                        for (ApiClient.ChatMessage msg : chatMessages) {
                            if (!"tool".equals(msg.role)) {
                                filteredMessages.add(msg);
                            }
                        }
                        
                        // 反向添加（从旧到新）
                        for (int i = filteredMessages.size() - 1; i >= 0; i--) {
                            ApiClient.ChatMessage msg = filteredMessages.get(i);
                            boolean isUser = "user".equals(msg.role);
                            long timestamp = msg.created * 1000L;
                            messages.add(0, new Message(msg.content, isUser, timestamp));
                        }
                        
                        lastMessageCount = messages.size();
                        
                        if (!messages.isEmpty()) {
                            Message lastMsg = messages.get(messages.size() - 1);
                            Message firstMsg = messages.get(0);
                            sessionManager.updateMessages(
                                currentSessionId,
                                firstMsg.getContent(),
                                lastMsg.getContent(),
                                lastMessageCount,
                                lastMsg.getTimestamp()
                            );
                            tvSessionTitle.setText(firstMsg.getContent().split("\n")[0]);
                        }
                        
                        adapter.notifyDataSetChanged();
                        rvMessages.scrollToPosition(messages.size() - 1);
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        isLoadingMore = false;
                        if (messages.isEmpty()) {
                            messages.clear();
                            addMessage("加载失败: " + error, false);
                        }
                        Toast.makeText(ChatActivity.this, "加载失败: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
    }
    
    /**
     * 加载更早的消息
     * 上滚到顶部时调用
     * 优化：保持用户阅读位置不跳动
     */
    private void loadOlderMessages() {
        if (isLoadingMore || !hasMoreMessages) {
            return;
        }
        
        // 计算新的起始位置
        int newSince = currentSince - PAGE_SIZE;
        if (newSince <= 1) {
            newSince = 1;
            hasMoreMessages = false;
        }
        
        if (newSince >= currentSince) {
            hasMoreMessages = false;
            return;
        }
        
        // === 关键优化：记录当前滚动位置 ===
        LinearLayoutManager layoutManager = (LinearLayoutManager) rvMessages.getLayoutManager();
        int oldFirstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
        View firstVisibleView = layoutManager.getChildAt(0);
        int oldTop = firstVisibleView != null ? firstVisibleView.getTop() : 0;
        Log.d(TAG, "Preserving scroll: position=" + oldFirstVisiblePosition + ", top=" + oldTop);
        
        currentSince = newSince;
        Log.d(TAG, "Loading older messages: since=" + currentSince);
        
        isLoadingMore = true;
        // 显示加载提示
        messages.add(0, new Message("加载中...", false, System.currentTimeMillis()));
        adapter.notifyItemInserted(0);
        
        apiClient.getMessagesWithOptions(currentSessionId, currentSince, PAGE_SIZE, false,
            new ApiClient.MessagesCallback() {
                @Override
                public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                    runOnUiThread(() -> {
                        isLoadingMore = false;
                        
                        // 移除加载提示
                        if (!messages.isEmpty() && messages.get(0).getContent().equals("加载中...")) {
                            messages.remove(0);
                            adapter.notifyItemRemoved(0);
                        }
                        
                        if (chatMessages.isEmpty()) {
                            hasMoreMessages = false;
                            return;
                        }
                        
                        // 过滤掉 tool 消息
                        List<ApiClient.ChatMessage> filteredMessages = new ArrayList<>();
                        for (ApiClient.ChatMessage msg : chatMessages) {
                            if (!"tool".equals(msg.role)) {
                                filteredMessages.add(msg);
                            }
                        }
                        
                        if (filteredMessages.isEmpty()) {
                            return;
                        }
                        
                        // 插入到列表开头
                        int insertCount = 0;
                        for (int i = filteredMessages.size() - 1; i >= 0; i--) {
                            ApiClient.ChatMessage msg = filteredMessages.get(i);
                            boolean isUser = "user".equals(msg.role);
                            long timestamp = msg.created * 1000L;
                            messages.add(insertCount, new Message(msg.content, isUser, timestamp));
                            insertCount++;
                        }
                        
                        lastMessageCount = messages.size();
                        
                        // === 关键优化：恢复滚动位置 ===
                        adapter.notifyItemRangeInserted(0, insertCount);
                        
                        // 计算新的位置：原位置 + 插入的消息数 + 加载提示移除的1个位置
                        int newPosition = oldFirstVisiblePosition + insertCount;
                        layoutManager.scrollToPositionWithOffset(newPosition, oldTop);
                        Log.d(TAG, "Restored scroll: newPosition=" + newPosition + ", offset=" + oldTop);
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        isLoadingMore = false;
                        
                        if (!messages.isEmpty() && messages.get(0).getContent().equals("加载中...")) {
                            messages.remove(0);
                            adapter.notifyItemRemoved(0);
                        }
                        
                        Toast.makeText(ChatActivity.this, "加载历史消息失败: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
    }
    
    /**
     * 加载会话信息并显示在顶部
     */
    private void loadSessionInfo() {
        Session session = sessionManager.getSession(currentSessionId);
        
        if (session != null) {
            updateSessionInfo(session);
            totalMessageCount = session.getMessageCount();
        } else {
            apiClient.getSessions(accountId, new ApiClient.SessionsCallback() {
                @Override
                public void onSuccess(List<Session> sessions) {
                    runOnUiThread(() -> {
                        for (Session s : sessions) {
                            if (s.getSessionId().equals(currentSessionId)) {
                                sessionManager.updateSession(s);
                                updateSessionInfo(s);
                                totalMessageCount = s.getMessageCount();
                                break;
                            }
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        tvSessionTitle.setText("未知会话");
                        tvSessionInfo.setText("unknown | unknown");
                        tvSessionPath.setText("CWD: unknown");
                    });
                }
            });
        }
    }
    
    /**
     * 更新顶部会话信息显示（三行）
     */
    private void updateSessionInfo(Session session) {
        String title = session.getTitle();
        String provider = session.getProvider();
        String model = session.getModel();
        String cwd = session.getCwd();
        
        tvSessionTitle.setText(title != null && !title.isEmpty() ? title : "新会话");
        
        String infoLine = (provider != null ? provider : "unknown") 
                        + " | " 
                        + (model != null ? model : "unknown");
        tvSessionInfo.setText(infoLine);
        
        String pathLine = "CWD: " + (cwd != null ? cwd : "unknown");
        tvSessionPath.setText(pathLine);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadSessionInfo();
        if (isAutoRefreshEnabled && apiClient != null) {
            startAutoRefresh();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        stopAutoRefresh();
        
        if (currentSessionId != null && messages != null) {
            int currentMessageCount = messages.size();
            sessionManager.saveReadMessageCount(currentSessionId, currentMessageCount);
            sessionManager.clearUnreadCount(currentSessionId);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoRefresh();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (itemId == R.id.action_settings) {
            openSessionSettings();
            return true;
        } else if (itemId == R.id.action_refresh) {
            refreshMessages();
            Toast.makeText(this, "已刷新", Toast.LENGTH_SHORT).show();
            return true;
        } else if (itemId == R.id.action_preview) {
            openPreviewInBrowser();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private void openSessionSettings() {
        Session session = sessionManager.getSession(currentSessionId);
        
        Intent intent = new Intent(this, SessionSettingsActivity.class);
        intent.putExtra(SessionSettingsActivity.EXTRA_SESSION_ID, currentSessionId);
        
        if (session != null) {
            intent.putExtra(SessionSettingsActivity.EXTRA_ACCOUNT_ID, session.getAccountId());
            intent.putExtra(SessionSettingsActivity.EXTRA_PROVIDER, session.getProvider());
            intent.putExtra(SessionSettingsActivity.EXTRA_MODEL, session.getModel());
            intent.putExtra(SessionSettingsActivity.EXTRA_CWD, session.getCwd());
        }
        
        startActivity(intent);
    }
    
    private void openPreviewInBrowser() {
        Account activeAccount = accountManager.getActiveAccount();
        if (activeAccount == null) {
            Toast.makeText(this, "请先添加账号", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String url = activeAccount.getUrl() + "/session?id=" + currentSessionId;
        
        Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
        startActivity(intent);
    }
    
    private void initViews() {
        rvMessages = findViewById(R.id.rv_messages);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        btnSend.setOnClickListener(v -> onSendButtonClick());
        
        setButtonStateNormal();
    }
    
    private void onSendButtonClick() {
        if (buttonState == STATE_SENDING) {
            stopGeneration();
        } else {
            sendMessage();
        }
    }
    
    private void setButtonStateSending() {
        buttonState = STATE_SENDING;
        btnSend.setText("停止");
        btnSend.setBackgroundColor(Color.parseColor("#F44336"));
        btnSend.setEnabled(true);
    }
    
    private void setButtonStateNormal() {
        buttonState = STATE_NORMAL;
        btnSend.setText("发送");
        btnSend.setBackgroundColor(Color.parseColor("#2196F3"));
        btnSend.setEnabled(true);
    }
    
    private void setupRecyclerView() {
        messages = new ArrayList<>();
        adapter = new MessageAdapter(messages, this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(adapter);
        
        // 添加滚动监听器，检测上滚到顶部
        rvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null) {
                    int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
                    
                    if (firstVisiblePosition == 0 && dy < 0 && !isLoadingMore && hasMoreMessages) {
                        Log.d(TAG, "Scrolled to top, loading older messages...");
                        loadOlderMessages();
                    }
                }
            }
        });
    }
    
    private void setupAutoRefresh() {
        refreshHandler = new Handler(Looper.getMainLooper());
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAutoRefreshEnabled && apiClient != null) {
                    refreshMessages();
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
     * 刷新消息和会话状态
     */
    private void refreshMessages() {
        if (apiClient == null || currentSessionId == null) {
            return;
        }
        
        refreshSessionStatus(null);
        
        if (lastMessageCount > 0) {
            refreshNewMessages();
        }
    }
    
    /**
     * 检测用户是否在列表底部
     * 用于判断是否需要自动滚动到最新消息
     */
    private boolean isUserAtBottom() {
        if (rvMessages == null || messages == null || messages.isEmpty()) {
            return true;
        }
        
        LinearLayoutManager layoutManager = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (layoutManager == null) {
            return true;
        }
        
        int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();
        int totalItems = messages.size();
        
        // 如果最后一个可见项在距离底部 BOTTOM_THRESHOLD 条消息以内，视为"在底部"
        return lastVisiblePosition >= totalItems - 1 - BOTTOM_THRESHOLD;
    }
    
    /**
     * 增量刷新：只获取新消息
     * 优化：
     * 1. 检测用户是否在底部，只有底部时才自动滚动
     * 2. 使用 notifyItemRangeInserted 精确更新
     */
    private void refreshNewMessages() {
        // 记录当前用户是否在底部
        final boolean wasAtBottom = isUserAtBottom();
        
        apiClient.getNewMessages(currentSessionId, lastMessageCount, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    if (chatMessages.isEmpty()) {
                        return;
                    }
                    
                    // 过滤掉 tool 消息
                    List<ApiClient.ChatMessage> newMessages = new ArrayList<>();
                    for (ApiClient.ChatMessage msg : chatMessages) {
                        if (!"tool".equals(msg.role)) {
                            newMessages.add(msg);
                        }
                    }
                    
                    if (newMessages.isEmpty()) {
                        return;
                    }
                    
                    // 记录插入前的消息数量
                    int insertStartIndex = messages.size();
                    int insertCount = 0;
                    
                    for (ApiClient.ChatMessage msg : newMessages) {
                        boolean isUser = "user".equals(msg.role);
                        long timestamp = msg.created * 1000L;
                        messages.add(new Message(msg.content, isUser, timestamp));
                        insertCount++;
                    }
                    
                    lastMessageCount = messages.size();
                    
                    // 使用精确更新替代 notifyDataSetChanged
                    adapter.notifyItemRangeInserted(insertStartIndex, insertCount);
                    
                    // 只有用户之前在底部时，才自动滚动到最新消息
                    if (wasAtBottom) {
                        rvMessages.scrollToPosition(messages.size() - 1);
                    }
                    
                    ApiClient.ChatMessage lastMsg = newMessages.get(newMessages.size() - 1);
                    Session currentSession = sessionManager.getSession(currentSessionId);
                    sessionManager.updateMessages(
                        currentSessionId,
                        currentSession != null ? currentSession.getFirstMessage() : "",
                        lastMsg.content,
                        lastMessageCount,
                        lastMsg.created * 1000L
                    );
                });
            }

            @Override
            public void onError(String error) {
                Log.d(TAG, "Incremental refresh failed: " + error);
            }
        });
    }
    
    /**
     * 刷新会话状态（检查是否正在生成）
     */
    private void refreshSessionStatus(Runnable onComplete) {
        apiClient.getSessions(accountId, new ApiClient.SessionsCallback() {
            @Override
            public void onSuccess(List<Session> sessions) {
                runOnUiThread(() -> {
                    for (Session session : sessions) {
                        if (session.getSessionId().equals(currentSessionId)) {
                            boolean wasInProgress = isInProgress;
                            isInProgress = session.isInProgress();
                            
                            totalMessageCount = session.getMessageCount();
                            
                            if (isInProgress) {
                                setButtonStateSending();
                            } else {
                                if (wasInProgress) {
                                    refreshNewMessages();
                                }
                                setButtonStateNormal();
                            }
                            break;
                        }
                    }
                    
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (onComplete != null) {
                    runOnUiThread(onComplete);
                }
            }
        });
    }
    
    private void addMessage(String content, boolean isUser) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        if (adapter == null) {
            adapter = new MessageAdapter(messages, this);
            if (rvMessages != null) {
                rvMessages.setAdapter(adapter);
            }
        }
        
        long timestamp = System.currentTimeMillis();
        messages.add(new Message(content, isUser, timestamp));
        
        if (adapter != null) {
            adapter.notifyItemInserted(messages.size() - 1);
        }
        if (rvMessages != null) {
            rvMessages.scrollToPosition(messages.size() - 1);
        }
    }
    
    private void sendMessage() {
        String messageText = etMessage.getText().toString().trim();
        
        if (messageText.isEmpty()) {
            Toast.makeText(this, "请输入消息", Toast.LENGTH_SHORT).show();
            return;
        }
        
        addMessage(messageText, true);
        etMessage.setText("");
        
        sendChatMessage(messageText);
    }

    private void sendChatMessage(String content) {
        if (apiClient == null) {
            addMessage("请先配置账号", false);
            return;
        }
        
        if (currentSessionId == null || currentSessionId.isEmpty()) {
            addMessage("无效的会话ID", false);
            return;
        }
        
        setButtonStateSending();
        isInProgress = true;
        sessionManager.setSessionInProgress(currentSessionId, true);
        
        apiClient.sendMessage(currentSessionId, content, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                runOnUiThread(() -> {
                    sessionManager.setSessionInProgress(currentSessionId, false);
                    isInProgress = false;
                    setButtonStateNormal();
                    refreshMessages();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    sessionManager.setSessionInProgress(currentSessionId, false);
                    isInProgress = false;
                    setButtonStateNormal();
                    addMessage("错误: " + error, false);
                    Toast.makeText(ChatActivity.this, "请求失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void stopGeneration() {
        if (apiClient == null || currentSessionId == null) {
            return;
        }
        
        Session session = sessionManager.getSession(currentSessionId);
        final String debugInfo;
        if (session != null) {
            debugInfo = "Session ID: " + currentSessionId + "\nAccount ID: " + session.getAccountId();
        } else {
            debugInfo = "Session ID: " + currentSessionId + "\nSession not found locally!";
        }
        Log.d("ChatActivity", "Stopping session: " + debugInfo);
        
        btnSend.setEnabled(false);
        btnSend.setText("停止中...");
        apiClient.stopSession(currentSessionId, new ApiClient.StopCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "已停止生成", Toast.LENGTH_SHORT).show();
                    isInProgress = false;
                    setButtonStateNormal();
                    refreshNewMessages();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.e("ChatActivity", "Stop failed: " + error + "\n" + debugInfo);
                    
                    if (error.contains("not found") || error.contains("不存在")) {
                        new androidx.appcompat.app.AlertDialog.Builder(ChatActivity.this)
                            .setTitle("会话不存在")
                            .setMessage("该会话在服务器上不存在，可能已被删除或切换了账号。\n是否返回会话列表？")
                            .setPositiveButton("返回列表", (d, w) -> finish())
                            .setNegativeButton("取消", (d, w) -> setButtonStateNormal())
                            .show();
                    } else {
                        Toast.makeText(ChatActivity.this, "停止失败: " + error, Toast.LENGTH_SHORT).show();
                        if (isInProgress) {
                            setButtonStateSending();
                        } else {
                            setButtonStateNormal();
                        }
                    }
                });
            }
        });
    }
    
    public void retryLastMessage() {
        if (apiClient == null || currentSessionId == null) {
            return;
        }
        
        setButtonStateSending();
        isInProgress = true;
        
        apiClient.retrySession(currentSessionId, new ApiClient.RetryCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "已重新发送", Toast.LENGTH_SHORT).show();
                    refreshNewMessages();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    isInProgress = false;
                    setButtonStateNormal();
                    Toast.makeText(ChatActivity.this, "重试失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
