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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天界面 Activity
 * 显示单个会话的消息列表
 * 
 * 分页加载逻辑：
 * - 初始加载：since = message_count - 20
 * - 上滚到顶部：since -= 20，加载更早的消息
 * - 直到 since <= 1 停止加载
 * 
 * 增量刷新优化：
 * - 使用消息指纹（created + content）检测变化
 * - 先获取会话状态（消息总数），再决定是否需要获取新消息
 * - 只在消息真正变化时才更新 UI
 * - 支持流式消息的内容更新检测
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
    
    // 会话相关
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
    
    // === 增量刷新优化：消息指纹缓存 ===
    // Key: created 时间戳（秒）, Value: 消息内容
    private Map<Long, String> messageFingerprints = new HashMap<>();
    // 最后一条消息的 created 时间戳（秒），用于快速判断是否有新消息
    private long lastMessageCreatedTimestamp = -1;  // 初始化为 -1，表示未设置
    // 最后一条消息的内容，用于检测流式更新
    private String lastMessageContent = null;  // 初始化为 null，表示未设置
    // 最后一次检查时的消息内容长度，用于避免频繁更新
    private int lastCheckedContentLength = -1;
    // 用于跟踪是否刚刚完成生成（只触发一次检查）
    private boolean justFinishedGeneration = false;
    
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
                        
                        // API 返回消息按 created 时间排序，最新在前 [newest, older, oldest]
                        // 正向遍历并添加到开头，得到从旧到新的顺序 [oldest, older, newest]
                        for (int i = 0; i < filteredMessages.size(); i++) {
                            ApiClient.ChatMessage msg = filteredMessages.get(i);
                            boolean isUser = "user".equals(msg.role);
                            long timestamp = msg.created * 1000L;
                            messages.add(0, new Message(msg.content, isUser, timestamp));
                            
                            // 更新消息指纹缓存
                            messageFingerprints.put(msg.created, msg.content);
                        }
                        
                        // 更新最后一条消息的追踪信息
                        updateLastMessageTracking();
                        
                        lastMessageCount = messages.size();
                        
                        // 更新本地会话信息（用于会话列表显示）
                        // 注意：不更新会话标题，标题只从 /sessions API 获取
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
     */
    private void loadOlderMessages() {
        if (isLoadingMore || !hasMoreMessages) {
            return;
        }
        
        // 计算新的 since 值，使用临时变量以满足 lambda 的 effectively final 要求
        int candidateSince = currentSince - PAGE_SIZE;
        if (candidateSince <= 1) {
            candidateSince = 1;
        }
        
        // 如果计算值没有向前移动，停止加载
        if (candidateSince >= currentSince) {
            hasMoreMessages = false;
            return;
        }
        
        // 根据最终值更新 hasMoreMessages
        if (candidateSince == 1) {
            hasMoreMessages = false;
        }
        
        // 创建 effectively final 变量供 lambda 使用
        final int newSince = candidateSince;
        
        // === 关键优化：记录当前滚动位置 ===
        LinearLayoutManager layoutManager = (LinearLayoutManager) rvMessages.getLayoutManager();
        int oldFirstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
        View firstVisibleView = layoutManager.getChildAt(0);
        int oldTop = firstVisibleView != null ? firstVisibleView.getTop() : 0;
        Log.d(TAG, "Preserving scroll: position=" + oldFirstVisiblePosition + ", top=" + oldTop);
        
        // 先显示加载提示
        addMessageAtTop("加载中...");
        
        apiClient.getMessagesWithOptions(currentSessionId, newSince, PAGE_SIZE, false,
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
                            
                            // 更新消息指纹缓存
                            messageFingerprints.put(msg.created, msg.content);
                            insertCount++;
                        }
                        
                        lastMessageCount = messages.size();
                        
                        // === 关键优化：恢复滚动位置 ===
                        adapter.notifyItemRangeInserted(0, insertCount);
                        
                        // 计算新的位置：原位置 + 插入的消息数 + 加载提示移除的1个位置
                        int newPosition = oldFirstVisiblePosition + insertCount;
                        layoutManager.scrollToPositionWithOffset(newPosition, oldTop);
                        Log.d(TAG, "Restored scroll: newPosition=" + newPosition + ", offset=" + oldTop);
                        
                        // 更新 since
                        currentSince = newSince;
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
     * 在列表顶部添加消息
     */
    private void addMessageAtTop(String content) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(0, new Message(content, false, System.currentTimeMillis()));
        if (adapter != null) {
            adapter.notifyItemInserted(0);
        }
    }
    
    /**
     * 加载会话信息并显示在顶部
     * 注意：标题不在此处更新，只在 SessionListActivity 获取会话列表时更新
     */
    private void loadSessionInfo() {
        Session session = sessionManager.getSession(currentSessionId);
        
        if (session != null) {
            updateSessionInfo(session);
            totalMessageCount = session.getMessageCount();
        } else {
            // 本地没有 session 信息，显示默认值
            // 不调用 getSessions API，标题只在会话列表刷新时更新
            tvSessionTitle.setText("新会话");
            tvSessionInfo.setText("unknown | unknown");
            tvSessionPath.setText("CWD: unknown");
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
     * 
     * 优化：先获取会话状态（包含消息总数），再决定是否需要获取新消息
     */
    private void refreshMessages() {
        if (apiClient == null || currentSessionId == null) {
            return;
        }
        
        // 先获取会话状态，包括消息总数
        refreshSessionStatusForIncrementalRefresh();
    }
    
    /**
     * 专为增量刷新设计的会话状态获取
     * 比较服务端消息总数与本地数量，决定是否需要获取新消息
     */
    private void refreshSessionStatusForIncrementalRefresh() {
        apiClient.getSessions(accountId, new ApiClient.SessionsCallback() {
            @Override
            public void onSuccess(List<Session> sessions) {
                runOnUiThread(() -> {
                    for (Session session : sessions) {
                        if (session.getSessionId().equals(currentSessionId)) {
                            // 保存之前的状态
                            boolean wasInProgress = isInProgress;
                            // 更新当前状态
                            isInProgress = session.isInProgress();
                            
                            // 获取服务端消息总数
                            int serverMessageCount = session.getMessageCount();
                            int localMessageCount = messages.size();
                            
                            // 更新按钮状态
                            if (isInProgress) {
                                setButtonStateSending();
                            } else {
                                // 从生成状态结束
                                if (wasInProgress) {
                                    setButtonStateNormal();
                                    // 刚刚结束生成，获取最新消息
                                    fetchLatestMessages();
                                } else {
                                    setButtonStateNormal();
                                }
                            }
                            
                            // === 核心优化：检测消息数量变化 ===
                            if (serverMessageCount > localMessageCount) {
                                // 有新消息，获取增量部分
                                Log.d(TAG, "Incremental refresh: serverCount=" + serverMessageCount 
                                    + ", localCount=" + localMessageCount + ", fetching new messages");
                                fetchIncrementalMessages(localMessageCount, serverMessageCount);
                            } else if (serverMessageCount < localMessageCount) {
                                // 服务端消息减少（异常情况，如会话被清理），需要重新加载
                                Log.d(TAG, "Message count decreased, reloading: serverCount=" + serverMessageCount 
                                    + ", localCount=" + localMessageCount);
                                reloadMessages();
                            } else {
                                // 数量相同
                                // 只有正在生成时才检查最后一条消息的更新（流式消息）
                                if (isInProgress) {
                                    checkLastMessageUpdate();
                                }
                            }
                            
                            totalMessageCount = serverMessageCount;
                            break;
                        }
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.d(TAG, "Session status refresh failed: " + error);
            }
        });
    }
    
    /**
     * 获取增量消息
     * 
     * @param localCount 本地消息数量
     * @param serverCount 服务端消息总数
     */
    private void fetchIncrementalMessages(int localCount, int serverCount) {
        // 计算需要获取的消息数量
        int newCount = serverCount - localCount;
        
        final boolean wasAtBottom = isUserAtBottom();
        
        // 获取最新 newCount 条消息
        // API 的 since 参数是从第几条开始（索引从 1 开始）
        // 我们需要获取索引 (localCount + 1) 到 serverCount 的消息
        int sinceIndex = localCount + 1;
        
        Log.d(TAG, "Fetching incremental messages: since=" + sinceIndex + ", expected=" + newCount + " new messages");
        
        apiClient.getMessagesWithOptions(currentSessionId, sinceIndex, newCount, false, 
            new ApiClient.MessagesCallback() {
                @Override
                public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                    runOnUiThread(() -> {
                        if (chatMessages.isEmpty()) {
                            Log.d(TAG, "Incremental fetch returned empty");
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
                        
                        // === 关键：反向添加（从旧到新）===
                        // API 返回的消息是按 created 时间排序，最新在前
                        // 我们需要从旧到新添加到列表末尾
                        int insertStartIndex = messages.size();
                        int insertCount = 0;
                        
                        for (int i = filteredMessages.size() - 1; i >= 0; i--) {
                            ApiClient.ChatMessage msg = filteredMessages.get(i);
                            boolean isUser = "user".equals(msg.role);
                            long timestamp = msg.created * 1000L;
                            messages.add(new Message(msg.content, isUser, timestamp));
                            insertCount++;
                            
                            // 更新指纹缓存
                            messageFingerprints.put(msg.created, msg.content);
                        }
                        
                        lastMessageCount = messages.size();
                        updateLastMessageTracking();
                        
                        // 精确更新 UI
                        adapter.notifyItemRangeInserted(insertStartIndex, insertCount);
                        
                        Log.d(TAG, "Incremental refresh complete: inserted " + insertCount + " messages");
                        
                        // 只有用户之前在底部时，才自动滚动到最新消息
                        if (wasAtBottom) {
                            rvMessages.scrollToPosition(messages.size() - 1);
                        }
                        
                        // 更新会话信息
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
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    Log.d(TAG, "Incremental fetch failed: " + error);
                }
            });
    }
    
    /**
     * 获取最新一条消息（用于检测流式消息更新）
     */
    private void fetchLatestMessages() {
        final boolean wasAtBottom = isUserAtBottom();
        
        apiClient.getLastMessage(currentSessionId, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    if (chatMessages.isEmpty()) {
                        return;
                    }
                    
                    ApiClient.ChatMessage lastServerMsg = chatMessages.get(0);
                    if ("tool".equals(lastServerMsg.role)) {
                        return;
                    }
                    
                    // 检查是否是新消息或内容有更新
                    long serverCreated = lastServerMsg.created;
                    String serverContent = lastServerMsg.content;
                    
                    boolean isNewMessage = (serverCreated != lastMessageCreatedTimestamp);
                    boolean contentChanged = (!serverContent.equals(lastMessageContent));
                    
                    if (isNewMessage) {
                        // 是一条新消息，添加到末尾
                        boolean isUser = "user".equals(lastServerMsg.role);
                        long timestamp = lastServerMsg.created * 1000L;
                        messages.add(new Message(lastServerMsg.content, isUser, timestamp));
                        
                        messageFingerprints.put(lastServerMsg.created, lastServerMsg.content);
                        lastMessageCount = messages.size();
                        updateLastMessageTracking();
                        
                        adapter.notifyItemInserted(messages.size() - 1);
                        
                        if (wasAtBottom) {
                            rvMessages.scrollToPosition(messages.size() - 1);
                        }
                        
                        Log.d(TAG, "New message added from latest fetch");
                    } else if (contentChanged) {
                        // 最后一条消息内容有更新（流式消息）
                        int lastIndex = messages.size() - 1;
                        boolean isUser = "user".equals(lastServerMsg.role);
                        messages.set(lastIndex, new Message(lastServerMsg.content, isUser, lastServerMsg.created * 1000L));
                        
                        messageFingerprints.put(lastServerMsg.created, lastServerMsg.content);
                        lastMessageContent = lastServerMsg.content;
                        
                        adapter.notifyItemChanged(lastIndex);
                        
                        Log.d(TAG, "Last message content updated: len=" + lastServerMsg.content.length());
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.d(TAG, "Latest message fetch failed: " + error);
            }
        });
    }
    
    /**
     * 检查最后一条消息是否有内容更新
     * 用于流式消息生成过程中
     */
    private void checkLastMessageUpdate() {
        if (messages.isEmpty()) {
            return;
        }
        
        // 如果还没有设置 lastMessageContent，先初始化它
        if (lastMessageContent == null) {
            updateLastMessageTracking();
            Log.d(TAG, "Initialized last message tracking in checkLastMessageUpdate");
            return;
        }
        
        apiClient.getLastMessage(currentSessionId, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    if (chatMessages.isEmpty()) {
                        return;
                    }
                    
                    ApiClient.ChatMessage lastServerMsg = chatMessages.get(0);
                    if ("tool".equals(lastServerMsg.role)) {
                        return;
                    }
                    
                    // 检查内容是否变化
                    String serverContent = lastServerMsg.content;
                    
                    // 快速检查：如果内容长度没变，且之前已经检查过，跳过
                    // 但流式消息通常会增长，所以只检查长度增加的情况
                    int serverLen = serverContent.length();
                    
                    // 使用更精确的比较：内容和长度都要检查
                    boolean contentChanged = !serverContent.equals(lastMessageContent);
                    
                    if (contentChanged) {
                        // 内容有变化，更新最后一条消息
                        int lastIndex = messages.size() - 1;
                        boolean isUser = "user".equals(lastServerMsg.role);
                        messages.set(lastIndex, new Message(lastServerMsg.content, isUser, lastServerMsg.created * 1000L));
                        
                        messageFingerprints.put(lastServerMsg.created, lastServerMsg.content);
                        lastMessageContent = lastServerMsg.content;
                        lastCheckedContentLength = serverLen;
                        
                        adapter.notifyItemChanged(lastIndex);
                        
                        Log.d(TAG, "Stream message updated: contentLen=" + serverLen + ", delta=" + (serverLen - lastCheckedContentLength));
                        
                        // 如果用户在底部，滚动到最新
                        if (isUserAtBottom()) {
                            rvMessages.scrollToPosition(messages.size() - 1);
                        }
                    } else {
                        // 内容没变，更新检查长度
                        lastCheckedContentLength = serverLen;
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.d(TAG, "Check last message update failed: " + error);
            }
        });
    }
    
    /**
     * 重新加载所有消息
     * 用于异常情况（如消息数量减少）
     */
    private void reloadMessages() {
        messages.clear();
        messageFingerprints.clear();
        currentSince = 0;
        hasMoreMessages = true;
        loadMessagesPage();
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
        return (totalItems - lastVisiblePosition) <= BOTTOM_THRESHOLD;
    }
    
    /**
     * 更新最后一条消息的追踪信息
     * 用于增量刷新时快速判断是否有变化
     */
    private void updateLastMessageTracking() {
        if (messages != null && !messages.isEmpty()) {
            Message lastMsg = messages.get(messages.size() - 1);
            // Message 的 timestamp 是毫秒，需要转换为秒
            lastMessageCreatedTimestamp = lastMsg.getTimestamp() / 1000;
            lastMessageContent = lastMsg.getContent();
            Log.d(TAG, "Updated last message tracking: timestamp=" + lastMessageCreatedTimestamp 
                + ", contentLen=" + lastMessageContent.length());
        }
    }
    
    /**
     * 刷新会话状态（检查是否正在生成）
     * 用于手动刷新或其他需要获取状态的场景
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
                                    fetchLatestMessages();
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
        Message msg = new Message(content, isUser, timestamp);
        messages.add(msg);
        
        // 更新指纹缓存
        messageFingerprints.put(timestamp / 1000, content);
        updateLastMessageTracking();
        
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
                    refreshMessages();
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
                    refreshMessages();
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
