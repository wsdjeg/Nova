package net.wsdjeg.nova;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天界面 Activity
 * 
 * 核心逻辑：
 * 1. 消息排序：API 返回升序 [oldest, ..., newest]
 * 2. 本地列表：保持升序，最新消息在末尾
 * 3. 显示过滤：只显示 content 不为空且 role 不是 tool 的消息
 * 
 * 消息计数说明：
 * - totalMessageCount: 服务端返回的总消息数（包含 tool 等不可显示消息）
 * - currentSince: 服务端消息索引（从1开始，基于服务端消息位置）
 * - processedServerMessageCount: 已处理的服务端消息数（基于服务端计数）
 * - adapter.getItemCount(): 显示的消息数（过滤后的，与服务端计数无关）
 * 
 * 下拉加载更多：
 * - 条件：到达顶部且 currentSince > 1（基于服务端索引）
 * - 如果加载的一页全是不可显示消息，自动继续加载下一页
 * - 直到找到可显示内容或到达第一条消息
 * 
 * Pending 消息机制：
 * - 发送消息时添加 pending 消息（临时显示）
 * - 服务端返回后，用正式消息替换 pending 消息
 * - 避免重复消息问题
 */
public class ChatActivity extends AppCompatActivity {
    
    private static final String TAG = "ChatActivity";
    private static final int REFRESH_INTERVAL_MS = 3000;
    private static final int PAGE_SIZE = 20;
    private static final int STATE_NORMAL = 0;
    private static final int STATE_SENDING = 1;
    private static final int BOTTOM_THRESHOLD = 3;
    private static final int REQUEST_SESSION_SETTINGS = 1001;
    private static final int POSITION_STABLE_DELAY = 500;
    
    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_SESSION_TITLE = "session_title";
    
    private Toolbar toolbar;
    private TextView tvSessionTitle;
    private TextView tvSessionInfo;
    private TextView tvSessionPath;
    private TextView tvLoadMore;
    private RecyclerView rvMessages;
    private EditText etMessage;
    private Button btnSend;
    private FloatingActionButton fabScrollBottom;
    private MessageAdapter adapter;
    private List<Message> messages;
    private ApiClient apiClient;
    private SettingsManager settingsManager;
    private SessionManager sessionManager;
    private AccountManager accountManager;
    
    private String currentSessionId;
    private String currentSessionTitle;
    private String currentProvider;
    private String currentModel;
    private String accountId;
    private Account currentAccount;
    
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private boolean isAutoRefreshEnabled = true;
    
    // 服务端消息计数（包含所有消息，不区分是否可显示）
    private int totalMessageCount = 0;
    private int currentSince = 0;
    private int processedServerMessageCount = 0;
    
    private int buttonState = STATE_NORMAL;
    private boolean isInProgress = false;
    private Menu chatMenu;
    
    private boolean isLoadingOlder = false;
    private boolean wasAtTopBeforeLoad = false;
    
    private long firstVisibleMessageCreated = -1;
    private int firstVisibleOffset = 0;
    
    private boolean isPositionLocked = false;
    
    // 消息指纹缓存（使用服务端时间戳 created 作为 key）
    private Map<Long, String> messageFingerprints = new HashMap<>();
    
    // Pending 消息：存储正在发送的消息内容，等待服务端确认
    private Map<String, Long> pendingMessages = new HashMap<>();
    
    private boolean userAtBottom = false;
    
    private int lastUsableHeight = 0;
    private boolean isKeyboardVisible = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        tvSessionTitle = findViewById(R.id.tv_session_title);
        tvSessionInfo = findViewById(R.id.tv_session_info);
        tvSessionPath = findViewById(R.id.tv_session_path);
        tvLoadMore = findViewById(R.id.tv_load_more);
        
        currentSessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        currentSessionTitle = getIntent().getStringExtra(EXTRA_SESSION_TITLE);
        String intentProvider = getIntent().getStringExtra("provider");
        String intentModel = getIntent().getStringExtra("model");
        String intentCwd = getIntent().getStringExtra("cwd");
        
        if (currentSessionId == null || currentSessionId.isEmpty()) {
            Toast.makeText(this, "无效的会话ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        toolbar.setTitle("");
        settingsManager = new SettingsManager(this);
        sessionManager = new SessionManager(this);
        accountManager = AccountManager.getInstance(this);
        
        Session session = sessionManager.getSession(currentSessionId);
        Account sessionAccount = null;
        
        Log.d(TAG, "Initializing with sessionId: " + currentSessionId);
        if (session != null) {
            Log.d(TAG, "Session: accountId=" + session.getAccountId());
            totalMessageCount = session.getMessageCount();
            currentSince = 0;
            
            if (session.getAccountId() != null && !session.getAccountId().isEmpty()) {
                sessionAccount = accountManager.getAccountById(session.getAccountId());
            }
        }
        if (sessionAccount == null) {
            sessionAccount = accountManager.getActiveAccount();
        }
        if (sessionAccount == null) {
            Toast.makeText(this, "请先添加账号", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        currentAccount = sessionAccount;
        
        accountId = sessionAccount.getId();
        String baseUrl = sessionAccount.getUrl();
        String apiKey = sessionAccount.getApiKey();
        
        if (baseUrl == null || baseUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            Toast.makeText(this, "请先配置账号信息", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        apiClient = new ApiClient(baseUrl, apiKey);
        apiClient.setSession(currentSessionId);
        
        rvMessages = findViewById(R.id.rv_messages);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        fabScrollBottom = findViewById(R.id.fab_scroll_bottom);
        
        restoreDraft();
        
        messages = new ArrayList<>();
        adapter = new MessageAdapter(messages, this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setItemAnimator(null);
        rvMessages.setAdapter(adapter);
        
        rvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;
                
                int firstVisible = lm.findFirstVisibleItemPosition();
                int lastVisible = lm.findLastVisibleItemPosition();
                int total = adapter.getItemCount();
                
                boolean isAtTop = (firstVisible == 0 && total > 0);
                boolean isAtBottom = (total == 0) || (lastVisible >= total - BOTTOM_THRESHOLD);
                
                // 只有在位置未锁定时才处理加载和位置更新
                if (!isPositionLocked) {
                    userAtBottom = isAtBottom;
                    
                    // 更新位置记录
                    if (firstVisible >= 0 && firstVisible < messages.size()) {
                        Message firstMsg = messages.get(firstVisible);
                        firstVisibleMessageCreated = firstMsg.getTimestamp() / 1000;
                        
                        View firstChild = lm.findViewByPosition(firstVisible);
                        if (firstChild != null) {
                            firstVisibleOffset = firstChild.getTop();
                        }
                    }
                    
                    // 触发加载更多（在滚动过程中向上滚动时）
                    if (!isLoadingOlder && isAtTop && canLoadMore() && dy < 0) {
                        triggerLoadOlder();
                    }
                }
                
                if (isAtBottom) {
                    fabScrollBottom.setVisibility(View.GONE);
                } else {
                    fabScrollBottom.setVisibility(View.VISIBLE);
                }
            }
            
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (lm == null) return;
                    
                    int firstVisible = lm.findFirstVisibleItemPosition();
                    boolean isAtTop = (firstVisible == 0 && adapter.getItemCount() > 0);
                    
                    // 滚动停止时，如果在顶部且可以加载更多，则触发加载
                    if (!isLoadingOlder && isAtTop && canLoadMore()) {
                        triggerLoadOlder();
                    } else if (!isLoadingOlder) {
                        hideLoadMoreHint();
                    }
                }
            }
        });
        
        fabScrollBottom.setOnClickListener(v -> {
            userAtBottom = true;
            scrollToBottom();
        });
        
        btnSend.setOnClickListener(v -> {
            if (buttonState == STATE_SENDING) {
                stopSession();
            } else {
                sendMessage();
            }
        });
        
        tvSessionTitle.setText(currentSessionTitle != null ? currentSessionTitle : currentSessionId);
        
        updateSessionInfo(intentProvider, intentModel, intentCwd);
        
        messages.add(new Message("正在加载消息...", false));
        adapter.refreshData();
        refreshSessionStatus(() -> loadMessagesPage());
        startAutoRefresh();
        
        setupKeyboardListener();
    }
    
    private void setupKeyboardListener() {
        View rootView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            
            if (keyboardVisible && !isKeyboardVisible) {
                isKeyboardVisible = true;
                scrollToBottom();
                rvMessages.postDelayed(() -> scrollToBottom(), 100);
                rvMessages.postDelayed(() -> scrollToBottom(), 200);
            } else if (!keyboardVisible && isKeyboardVisible) {
                isKeyboardVisible = false;
            }
            
            return insets;
        });
        
        final View decorView = getWindow().getDecorView();
        decorView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                Rect r = new Rect();
                decorView.getWindowVisibleDisplayFrame(r);
                
                int screenHeight = decorView.getHeight();
                int usableHeight = r.bottom - r.top;
                int keyboardHeight = screenHeight - usableHeight;
                
                boolean keyboardNowVisible = keyboardHeight > screenHeight * 0.15;
                
                if (keyboardNowVisible != isKeyboardVisible) {
                    isKeyboardVisible = keyboardNowVisible;
                    
                    if (keyboardNowVisible) {
                        scrollToBottom();
                        rvMessages.postDelayed(() -> scrollToBottom(), 100);
                        rvMessages.postDelayed(() -> scrollToBottom(), 200);
                    }
                }
                
                lastUsableHeight = usableHeight;
            }
        });
        
        etMessage.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                rvMessages.postDelayed(() -> scrollToBottom(), 300);
            }
        });
    }
    
    /**
     * 是否可以加载更多消息
     * 基于服务端消息索引判断，不依赖客户端显示的消息数
     */
    private boolean canLoadMore() {
        return currentSince > 1 && totalMessageCount > 0;
    }
    
    private void triggerLoadOlder() {
        if (isLoadingOlder || !canLoadMore()) return;
        
        isLoadingOlder = true;
        isPositionLocked = true;
        userAtBottom = false;
        showLoadMoreHint("正在加载...");
        
        stopAutoRefresh();
        
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm != null) {
            int firstVisible = lm.findFirstVisibleItemPosition();
            wasAtTopBeforeLoad = (firstVisible == 0);
            
            if (firstVisible >= 0 && firstVisible < messages.size()) {
                firstVisibleMessageCreated = messages.get(firstVisible).getTimestamp() / 1000;
                View firstChild = lm.findViewByPosition(firstVisible);
                if (firstChild != null) {
                    firstVisibleOffset = firstChild.getTop();
                }
            }
        }
        
        loadOlderMessages();
    }
    
    private void showLoadMoreHint(String text) {
        if (tvLoadMore != null) {
            tvLoadMore.setVisibility(View.VISIBLE);
            tvLoadMore.setText(text);
            if (text.contains("正在加载") || text.contains("继续加载")) {
                tvLoadMore.setBackgroundColor(Color.parseColor("#FFBB33"));
            } else {
                tvLoadMore.setBackgroundColor(Color.parseColor("#2196F3"));
            }
        }
    }
    
    private void hideLoadMoreHint() {
        if (tvLoadMore != null) {
            tvLoadMore.setVisibility(View.GONE);
        }
    }
    
    private void restoreDraft() {
        if (sessionManager == null || currentSessionId == null || etMessage == null) return;
        
        String draft = sessionManager.getDraft(currentSessionId);
        if (draft != null && !draft.isEmpty()) {
            etMessage.setText(draft);
            etMessage.setSelection(draft.length());
        }
    }
    
    private void saveDraft() {
        if (sessionManager == null || currentSessionId == null || etMessage == null) return;
        
        String content = etMessage.getText().toString().trim();
        if (content.isEmpty()) {
            sessionManager.clearDraft(currentSessionId);
        } else {
            sessionManager.saveDraft(currentSessionId, content);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        isAutoRefreshEnabled = true;
        startAutoRefresh();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        isAutoRefreshEnabled = false;
        stopAutoRefresh();
        saveDraft();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_menu, menu);
        chatMenu = menu;
        updateMenuVisibility(menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            reloadMessages();
            return true;
        } else if (id == R.id.action_clear_session) {
            clearSession();
            return true;
        } else if (id == R.id.action_delete_session) {
            deleteSession();
            return true;
        } else if (id == R.id.action_preview) {
            openPreviewUrl();
            return true;
        } else if (id == R.id.action_settings) {
            openSessionSettings();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void updateSessionInfo(String intentProvider, String intentModel, String intentCwd) {
        if (intentProvider != null && !intentProvider.isEmpty() && 
            intentModel != null && !intentModel.isEmpty()) {
            currentProvider = intentProvider;
            currentModel = intentModel;
            tvSessionInfo.setText(intentProvider + " | " + intentModel);
            tvSessionPath.setText("cwd: " + (intentCwd != null ? intentCwd : ""));
            return;
        }
        
        Session session = sessionManager.getSession(currentSessionId);
        if (session != null) {
            currentProvider = session.getProvider();
            currentModel = session.getModel();
            tvSessionInfo.setText(currentProvider + " | " + currentModel);
            tvSessionPath.setText("cwd: " + (session.getCwd() != null ? session.getCwd() : ""));
        }
    }
    
    private void updateSessionInfoDisplay() {
        if (currentProvider != null && currentModel != null) {
            tvSessionInfo.setText(currentProvider + " | " + currentModel);
        } else {
            Session session = sessionManager.getSession(currentSessionId);
            if (session != null) {
                currentProvider = session.getProvider();
                currentModel = session.getModel();
                tvSessionInfo.setText(currentProvider + " | " + currentModel);
                tvSessionPath.setText("cwd: " + (session.getCwd() != null ? session.getCwd() : ""));
            }
        }
    }
    
    private void startAutoRefresh() {
        if (refreshHandler == null) {
            refreshHandler = new Handler(Looper.getMainLooper());
        }
        stopAutoRefresh();
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAutoRefreshEnabled && !isLoadingOlder && !isPositionLocked) {
                    refreshMessages();
                    refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
                }
            }
        };
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }
    
    private void stopAutoRefresh() {
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }
    
    private void refreshMessages() {
        if (apiClient == null || currentSessionId == null || isLoadingOlder || isPositionLocked) return;
        refreshSessionStatusForIncrementalRefresh();
    }
    
    private void refreshSessionStatusForIncrementalRefresh() {
        apiClient.getSessions(accountId, new ApiClient.SessionsCallback() {
            @Override
            public void onSuccess(List<Session> sessions) {
                runOnUiThread(() -> {
                    for (Session session : sessions) {
                        if (session.getSessionId().equals(currentSessionId)) {
                            boolean wasInProgress = isInProgress;
                            isInProgress = session.isInProgress();
                            
                            // 服务端消息计数（包含所有消息类型）
                            int serverCount = session.getMessageCount();
                            
                            if (isInProgress) {
                                setButtonStateSending();
                            } else {
                                if (wasInProgress) {
                                    setButtonStateNormal();
                                    fetchNewMessagesAndRestorePosition();
                                } else {
                                    setButtonStateNormal();
                                }
                            }
                            
                            // 基于服务端计数判断是否需要获取新消息
                            if (serverCount > processedServerMessageCount) {
                                fetchNewMessagesAndRestorePosition();
                            } else if (serverCount < processedServerMessageCount) {
                                reloadMessages();
                            } else if (isInProgress) {
                                checkLastMessageUpdate();
                            }
                            
                            totalMessageCount = serverCount;
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
     * 获取新消息并保持位置
     * 计数基于服务端返回的消息数，不依赖显示的消息数
     */
    private void fetchNewMessagesAndRestorePosition() {
        if (isPositionLocked) {
            Log.d(TAG, "Position locked, skipping fetch");
            return;
        }
        
        // 基于已处理的服务端消息数计算起始索引
        int sinceIndex = processedServerMessageCount + 1;
        
        Log.d(TAG, "Fetching messages from server index: since=" + sinceIndex);
        
        apiClient.getNewMessages(currentSessionId, sinceIndex, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    if (chatMessages.isEmpty()) return;
                    
                    for (ApiClient.ChatMessage msg : chatMessages) {
                        if (messageFingerprints.containsKey(msg.created)) {
                            Log.d(TAG, "Message already exists, skipping: created=" + msg.created);
                            continue;
                        }
                        
                        if ("user".equals(msg.role) && msg.content != null) {
                            int pendingIndex = findPendingMessageIndex(msg.content);
                            if (pendingIndex >= 0) {
                                Log.d(TAG, "Replacing pending message at index " + pendingIndex);
                                messages.set(pendingIndex, new Message(msg.content, msg.role, msg.created));
                                pendingMessages.remove(msg.content);
                                messageFingerprints.put(msg.created, msg.content);
                                continue;
                            }
                        }
                        
                        messages.add(new Message(msg.content, msg.role, msg.created));
                        messageFingerprints.put(msg.created, msg.content != null ? msg.content : "");
                    }
                    
                    // 增加已处理的服务端消息计数（基于服务端返回的消息数）
                    processedServerMessageCount += chatMessages.size();
                    
                    cleanupPendingMessages();
                    
                    adapter.refreshData();
                    
                    if (userAtBottom && !isPositionLocked) {
                        scrollToBottom();
                    } else {
                        restorePositionByTimestamp();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.d(TAG, "Incremental fetch failed: " + error);
            }
        });
    }
    
    private int findPendingMessageIndex(String content) {
        if (content == null || !pendingMessages.containsKey(content)) {
            return -1;
        }
        
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (msg.isPending() && content.equals(msg.getContent())) {
                return i;
            }
        }
        return -1;
    }
    
    private void cleanupPendingMessages() {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();
        
        for (Map.Entry<String, Long> entry : pendingMessages.entrySet()) {
            if (now - entry.getValue() > 30000) {
                toRemove.add(entry.getKey());
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Message msg = messages.get(i);
                    if (msg.isPending() && entry.getKey().equals(msg.getContent())) {
                        messages.remove(i);
                        Log.d(TAG, "Removed timeout pending message");
                        break;
                    }
                }
            }
        }
        
        for (String key : toRemove) {
            pendingMessages.remove(key);
        }
    }
    
    private void restorePositionByTimestamp() {
        if (firstVisibleMessageCreated <= 0) return;
        
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return;
        
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getTimestamp() / 1000 == firstVisibleMessageCreated) {
                lm.scrollToPositionWithOffset(i, firstVisibleOffset);
                return;
            }
        }
        
        for (int i = 0; i < messages.size(); i++) {
            long created = messages.get(i).getTimestamp() / 1000;
            if (created >= firstVisibleMessageCreated) {
                lm.scrollToPositionWithOffset(i, firstVisibleOffset);
                return;
            }
        }
    }
    
    private void loadMessagesPage() {
        if (apiClient == null || currentSessionId == null) return;
        
        if (totalMessageCount <= 0) {
            messages.clear();
            messageFingerprints.clear();
            pendingMessages.clear();
            addSystemMessage("暂无消息");
            processedServerMessageCount = 0;
            currentSince = 0;
            adapter.refreshData();
            hideLoadMoreHint();
            return;
        }
        
        // 基于服务端消息计数计算起始索引
        int since = Math.max(1, totalMessageCount - PAGE_SIZE + 1);
        currentSince = since;
        
        apiClient.getNewMessages(currentSessionId, since, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    messages.clear();
                    messageFingerprints.clear();
                    pendingMessages.clear();
                    
                    if (chatMessages.isEmpty()) {
                        addSystemMessage("暂无消息");
                        processedServerMessageCount = 0;
                        currentSince = 0;
                        adapter.refreshData();
                        hideLoadMoreHint();
                        return;
                    }
                    
                    for (ApiClient.ChatMessage msg : chatMessages) {
                        messages.add(new Message(msg.content, msg.role, msg.created));
                        messageFingerprints.put(msg.created, msg.content != null ? msg.content : "");
                    }
                    
                    // 已处理的服务端消息数等于服务端返回的总数
                    processedServerMessageCount = totalMessageCount;
                    sessionManager.updateFirstMessageIndex(currentSessionId, currentSince);
                    adapter.refreshData();
                    
                    userAtBottom = true;
                    isPositionLocked = false;
                    scrollToBottom();
                    
                    rvMessages.postDelayed(() -> scrollToBottom(), 100);
                    rvMessages.postDelayed(() -> scrollToBottom(), 300);
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    messages.clear();
                    messageFingerprints.clear();
                    pendingMessages.clear();
                    addSystemMessage("加载失败: " + error);
                    currentSince = 0;
                    adapter.refreshData();
                    hideLoadMoreHint();
                    isPositionLocked = false;
                });
            }
        });
    }
    
    /**
     * 加载更早的消息
     * 
     * 计数说明：
     * - currentSince: 服务端消息索引（从1开始）
     * - totalMessageCount: 服务端返回的总消息数
     * - newDisplayableCount: 本次加载中可显示的消息数（过滤后）
     * 
     * 如果本次加载没有可显示的消息，自动继续加载下一页，
     * 直到找到可显示内容或到达第一条消息
     */
    private void loadOlderMessages() {
        if (!canLoadMore()) {
            isLoadingOlder = false;
            isPositionLocked = false;
            hideLoadMoreHint();
            Toast.makeText(this, "已到第一条消息", Toast.LENGTH_SHORT).show();
            return;
        }
        
        int newSince = Math.max(1, currentSince - PAGE_SIZE);
        if (newSince >= currentSince) {
            isLoadingOlder = false;
            isPositionLocked = false;
            currentSince = 1;
            hideLoadMoreHint();
            Toast.makeText(this, "已到第一条消息", Toast.LENGTH_SHORT).show();
            return;
        }
        
        apiClient.getNewMessages(currentSessionId, newSince, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    isLoadingOlder = false;
                    
                    if (chatMessages.isEmpty()) {
                        currentSince = 1;
                        isPositionLocked = false;
                        hideLoadMoreHint();
                        Toast.makeText(ChatActivity.this, "已到第一条消息", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    long targetCreated = firstVisibleMessageCreated;
                    int targetOffset = firstVisibleOffset;
                    
                    int newDisplayableCount = 0;
                    for (int i = chatMessages.size() - 1; i >= 0; i--) {
                        ApiClient.ChatMessage msg = chatMessages.get(i);
                        if (!messageFingerprints.containsKey(msg.created)) {
                            Message message = new Message(
                                msg.content != null ? msg.content : "",
                                msg.role,
                                msg.created
                            );
                            messages.add(0, message);
                            messageFingerprints.put(msg.created, msg.content);
                            
                            // 统计可显示的消息数（用于提示用户）
                            if (msg.content != null && !msg.content.isEmpty() && !"tool".equals(msg.role)) {
                                newDisplayableCount++;
                            }
                        }
                    }
                    
                    // 更新服务端消息索引
                    currentSince = newSince;
                    sessionManager.updateFirstMessageIndex(currentSessionId, newSince);
                    adapter.refreshData();
                    
                    LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
                    if (lm != null) {
                        for (int i = 0; i < messages.size(); i++) {
                            if (messages.get(i).getTimestamp() / 1000 == targetCreated) {
                                lm.scrollToPositionWithOffset(i, targetOffset);
                                break;
                            }
                        }
                    }
                    
                    // 如果本次加载没有可显示消息，且还有更多消息可加载，自动继续加载
                    if (newDisplayableCount == 0 && canLoadMore()) {
                        showLoadMoreHint("继续加载...");
                        rvMessages.postDelayed(() -> {
                            if (!isLoadingOlder && canLoadMore()) {
                                isLoadingOlder = true;
                                loadOlderMessages();
                            }
                        }, 200);
                        return;
                    }
                    
                    if (canLoadMore()) {
                        showLoadMoreHint("已加载 " + newDisplayableCount + " 条");
                        rvMessages.postDelayed(() -> {
                            LinearLayoutManager lm2 = (LinearLayoutManager) rvMessages.getLayoutManager();
                            if (lm2 != null) {
                                int firstVisible = lm2.findFirstVisibleItemPosition();
                                if (firstVisible == 0 && canLoadMore()) {
                                    showLoadMoreHint("下拉加载更多");
                                } else {
                                    hideLoadMoreHint();
                                }
                            }
                        }, 1500);
                    } else {
                        showLoadMoreHint("已到第一条消息");
                        rvMessages.postDelayed(() -> hideLoadMoreHint(), 1500);
                    }
                    
                    rvMessages.postDelayed(() -> {
                        isPositionLocked = false;
                        startAutoRefresh();
                    }, POSITION_STABLE_DELAY);
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    isLoadingOlder = false;
                    isPositionLocked = false;
                    hideLoadMoreHint();
                    Toast.makeText(ChatActivity.this, "加载失败: " + error, Toast.LENGTH_SHORT).show();
                    startAutoRefresh();
                });
            }
        });
    }
    
    private void checkLastMessageUpdate() {
        if (!userAtBottom || isPositionLocked) return;
        
        int sinceIndex = processedServerMessageCount + 1;
        
        apiClient.getNewMessages(currentSessionId, sinceIndex, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    if (chatMessages.isEmpty()) return;
                    
                    ApiClient.ChatMessage latestFiltered = null;
                    for (int i = chatMessages.size() - 1; i >= 0; i--) {
                        ApiClient.ChatMessage msg = chatMessages.get(i);
                        if (msg.content != null && !msg.content.isEmpty() && !"tool".equals(msg.role)) {
                            latestFiltered = msg;
                            break;
                        }
                    }
                    
                    if (latestFiltered == null) return;
                    
                    if (messages.size() > 0) {
                        int lastIdx = messages.size() - 1;
                        String lastContent = messages.get(lastIdx).getContent();
                        
                        if (!latestFiltered.content.equals(lastContent)) {
                            messages.set(lastIdx, new Message(latestFiltered.content, "user".equals(latestFiltered.role), latestFiltered.created * 1000L));
                            messageFingerprints.put(latestFiltered.created, latestFiltered.content);
                            adapter.refreshData();
                            scrollToBottom();
                        }
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.d(TAG, "Check update failed: " + error);
            }
        });
    }
    
    private void reloadMessages() {
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm != null) {
            int firstVisible = lm.findFirstVisibleItemPosition();
            if (firstVisible >= 0 && firstVisible < messages.size()) {
                firstVisibleMessageCreated = messages.get(firstVisible).getTimestamp() / 1000;
                View firstChild = lm.findViewByPosition(firstVisible);
                if (firstChild != null) {
                    firstVisibleOffset = firstChild.getTop();
                }
            }
        }
        
        messages.clear();
        messageFingerprints.clear();
        pendingMessages.clear();
        processedServerMessageCount = 0;
        currentSince = 0;
        sessionManager.updateFirstMessageIndex(currentSessionId, 0);
        hideLoadMoreHint();
        loadMessagesPage();
    }
    
    private void scrollToBottom() {
        if (isPositionLocked) return;
        
        if (rvMessages == null || adapter == null) return;
        int itemCount = adapter.getItemCount();
        if (itemCount > 0) {
            LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
            if (lm != null) {
                lm.scrollToPositionWithOffset(itemCount - 1, Integer.MIN_VALUE);
                rvMessages.postDelayed(() -> {
                    if (!isPositionLocked && adapter.getItemCount() > 0) {
                        lm.scrollToPositionWithOffset(adapter.getItemCount() - 1, Integer.MIN_VALUE);
                    }
                }, 100);
            }
        }
    }
    
    private void refreshSessionStatus() {
        refreshSessionStatus(null);
    }
    
    private void refreshSessionStatus(Runnable onComplete) {
        apiClient.getSessions(accountId, new ApiClient.SessionsCallback() {
            @Override
            public void onSuccess(List<Session> sessions) {
                runOnUiThread(() -> {
                    for (Session serverSession : sessions) {
                        if (serverSession.getSessionId().equals(currentSessionId)) {
                            isInProgress = serverSession.isInProgress();
                            totalMessageCount = serverSession.getMessageCount();
                            
                            Session localSession = sessionManager.getSession(currentSessionId);
                            if (localSession != null) {
                                boolean updated = false;
                                String serverProvider = serverSession.getProvider();
                                String serverModel = serverSession.getModel();
                                
                                if (serverProvider != null && !serverProvider.isEmpty() 
                                    && !serverProvider.equals(localSession.getProvider())) {
                                    localSession.setProvider(serverProvider);
                                    currentProvider = serverProvider;
                                    updated = true;
                                }
                                if (serverModel != null && !serverModel.isEmpty() 
                                    && !serverModel.equals(localSession.getModel())) {
                                    localSession.setModel(serverModel);
                                    currentModel = serverModel;
                                    updated = true;
                                }
                                if (updated) {
                                    sessionManager.addOrUpdateSession(localSession);
                                    updateSessionInfoDisplay();
                                }
                            }
                            
                            if (isInProgress) {
                                setButtonStateSending();
                            } else {
                                setButtonStateNormal();
                            }
                            break;
                        }
                    }
                    if (onComplete != null) onComplete.run();
                });
            }
            
            @Override
            public void onError(String error) {
                if (onComplete != null) runOnUiThread(onComplete);
            }
        });
    }
    
    private void addSystemMessage(String content) {
        if (messages == null) messages = new ArrayList<>();
        messages.add(new Message(content, "assistant", System.currentTimeMillis() / 1000));
        adapter.refreshData();
        scrollToBottom();
    }
    
    private void sendMessage() {
        String content = etMessage.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入消息", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (apiClient == null) {
            Toast.makeText(this, "API未初始化", Toast.LENGTH_SHORT).show();
            return;
        }
        
        etMessage.setText("");
        sessionManager.clearDraft(currentSessionId);
        
        addPendingMessage(content);
        userAtBottom = true;
        
        apiClient.sendMessage(currentSessionId, content, new ApiClient.MessageCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Message sent successfully");
                refreshSessionStatus();
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    removePendingMessage(content);
                    Toast.makeText(ChatActivity.this, "发送失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void addPendingMessage(String content) {
        if (messages == null) messages = new ArrayList<>();
        
        Message pendingMsg = Message.createPending(content, true);
        messages.add(pendingMsg);
        
        pendingMessages.put(content, System.currentTimeMillis());
        
        adapter.refreshData();
        scrollToBottom();
        
        Log.d(TAG, "Added pending message: " + content.length() + " chars");
    }
    
    private void removePendingMessage(String content) {
        pendingMessages.remove(content);
        
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg.isPending() && content.equals(msg.getContent())) {
                messages.remove(i);
                adapter.refreshData();
                break;
            }
        }
    }
    
    private void setButtonStateNormal() {
        buttonState = STATE_NORMAL;
        btnSend.setText("发送");
        btnSend.setEnabled(true);
        btnSend.setBackgroundColor(Color.parseColor("#2196F3"));
        isInProgress = false;
        if (chatMenu != null) {
            updateMenuVisibility(chatMenu);
        }
    }
    
    private void setButtonStateSending() {
        buttonState = STATE_SENDING;
        btnSend.setText("停止");
        btnSend.setEnabled(true);
        btnSend.setBackgroundColor(Color.parseColor("#F44336"));
        isInProgress = true;
        if (chatMenu != null) {
            updateMenuVisibility(chatMenu);
        }
    }
    
    private void clearSession() {
        if (apiClient == null) return;
        apiClient.clearSession(currentSessionId, new ApiClient.ClearCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    messages.clear();
                    messageFingerprints.clear();
                    pendingMessages.clear();
                    processedServerMessageCount = 0;
                    currentSince = 0;
                    adapter.refreshData();
                    addSystemMessage("会话已清空");
                    Toast.makeText(ChatActivity.this, "会话已清空", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "清空失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void deleteSession() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("删除会话")
            .setMessage("确定要删除此会话吗？")
            .setPositiveButton("删除", (dialog, which) -> {
                apiClient.deleteSession(currentSessionId, new ApiClient.DeleteSessionCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            sessionManager.deleteSession(currentSessionId);
                            Toast.makeText(ChatActivity.this, "会话已删除", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(ChatActivity.this, "删除失败: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void updateMenuVisibility(Menu menu) {
        if (menu == null) return;
        
        MenuItem refresh = menu.findItem(R.id.action_refresh);
        MenuItem clear = menu.findItem(R.id.action_clear_session);
        MenuItem delete = menu.findItem(R.id.action_delete_session);
        MenuItem preview = menu.findItem(R.id.action_preview);
        
        boolean enabled = !isInProgress;
        
        if (refresh != null) refresh.setEnabled(enabled);
        if (clear != null) clear.setEnabled(enabled);
        if (delete != null) delete.setEnabled(enabled);
        if (preview != null) preview.setEnabled(true);
    }
    
    private void openPreviewUrl() {
        if (currentAccount == null || currentSessionId == null) {
            Toast.makeText(this, "无法打开预览", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String baseUrl = currentAccount.getUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            Toast.makeText(this, "服务器地址未配置", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String previewUrl = baseUrl + "/session?id=" + currentSessionId;
        
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(previewUrl));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开浏览器: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openSessionSettings() {
        if (currentSessionId == null) {
            Toast.makeText(this, "会话ID无效", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Session session = sessionManager.getSession(currentSessionId);
        Intent intent = new Intent(this, SessionSettingsActivity.class);
        intent.putExtra(SessionSettingsActivity.EXTRA_SESSION_ID, currentSessionId);
        intent.putExtra(SessionSettingsActivity.EXTRA_ACCOUNT_ID, accountId);
        
        if (session != null) {
            intent.putExtra(SessionSettingsActivity.EXTRA_PROVIDER, session.getProvider());
            intent.putExtra(SessionSettingsActivity.EXTRA_MODEL, session.getModel());
            intent.putExtra(SessionSettingsActivity.EXTRA_CWD, session.getCwd());
        }
        
        startActivityForResult(intent, REQUEST_SESSION_SETTINGS);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_SESSION_SETTINGS && resultCode == RESULT_OK && data != null) {
            String newProvider = data.getStringExtra(SessionSettingsActivity.RESULT_PROVIDER);
            String newModel = data.getStringExtra(SessionSettingsActivity.RESULT_MODEL);
            
            if (newProvider != null && newModel != null) {
                currentProvider = newProvider;
                currentModel = newModel;
                
                Session session = sessionManager.getSession(currentSessionId);
                if (session != null) {
                    session.setProvider(newProvider);
                    session.setModel(newModel);
                    sessionManager.updateSession(session);
                }
            }
            
            updateSessionInfoDisplay();
            Toast.makeText(this, "会话设置已更新", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void stopSession() {
        if (apiClient == null) return;
        apiClient.stopSession(currentSessionId, new ApiClient.StopCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "已停止", Toast.LENGTH_SHORT).show();
                    refreshSessionStatus();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "停止失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
