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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * 下拉加载更多 - 位置恢复机制：
 * - 使用消息 created 时间戳作为锚点（而非索引）
 * - 保存：记录第一条可见消息的 created 时间戳和视觉偏移
 * - 恢复：在新数据中找到该消息的可见位置，精确恢复
 * - 原因：messages 列表包含不可见消息（tool 类型），索引不等于可见位置
 * 
 * Pending 消息机制：
 * - 发送消息时添加 pending 消息（临时显示）
 * - 服务端返回后，用正式消息替换 pending 消息
 * - 避免重复消息问题
 * 
 * 键盘处理机制：
 * - 键盘弹出/关闭时，RecyclerView 高度变化
 * - 使用 scrollBy 补偿，保持消息相对于输入框的位置不变
 * - 使用防抖机制避免 GlobalLayoutListener 多次触发导致的重复滚动
 */
public class ChatActivity extends AppCompatActivity {
    
    private static final String TAG = "ChatActivity";
    private static final int REFRESH_INTERVAL_MS = 3000;
    private static final int PAGE_SIZE = 50;
    private static final int STATE_NORMAL = 0;
    private static final int STATE_SENDING = 1;
    private static final int BOTTOM_THRESHOLD = 3;
    private static final int REQUEST_SESSION_SETTINGS = 1001;
    
    // 加载更多防抖：最小触发间隔
    private static final long MIN_LOAD_INTERVAL_MS = 300;
    
    // 键盘防抖：最小触发间隔
    private static final long MIN_KEYBOARD_SCROLL_INTERVAL_MS = 50;
    
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
    
    // 首次加载完成标志：确保在首次加载完成前不显示"下拉加载更多"提示
    private boolean isInitialLoadComplete = false;
    
    private int buttonState = STATE_NORMAL;
    private boolean isInProgress = false;
    private Menu chatMenu;
    
    // 位置恢复：使用消息时间戳作为锚点（而非索引）
    // 原因：messages 列表包含不可见消息（tool 类型），索引不等于可见位置
    private long anchorMessageCreated = -1;  // 锚点消息的服务端时间戳
    private int offsetToRestore = 0;          // 锚点消息的视觉偏移
    
    // 加载更早消息的状态标志
    private boolean isLoadingOlder = false;
    private long lastLoadTriggerTime = 0;
    
    // 消息指纹缓存（使用服务端时间戳 created 作为 key）
    private Map<Long, String> messageFingerprints = new HashMap<>();
    
    // Pending 消息：存储正在发送的消息内容，等待服务端确认（用于UI显示）
    private Map<String, Long> pendingMessages = new HashMap<>();
    
    // Bug 2 修复：消息池等待集合
    // 存储已成功推送到服务端消息池但还没被AI处理的消息
    // 因为消息池最多5秒才处理，期间需要保持"停止"按钮状态
    private Set<String> messagesInPool = new HashSet<>();
    
    private boolean userAtBottom = true;
    // 键盘状态追踪
    private int lastKeyboardHeight = 0;
    private boolean isKeyboardVisible = false;
    private Handler keyboardHandler = new Handler(Looper.getMainLooper());
    private Runnable keyboardScrollRunnable = null;
    private int lastRecyclerViewHeight = -1;  // 记录 RecyclerView 高度变化
    private long lastKeyboardScrollTime = 0;  // 键盘滚动防抖时间戳
    private int accumulatedHeightDelta = 0;   // 累积的高度变化（用于防抖）
    
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
        // 从 intent 获取 in_progress 状态（会话列表传递的最新状态）
        boolean intentInProgress = getIntent().getBooleanExtra("in_progress", false);
        
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
        // 优先使用 intent 传递的 in_progress 状态
        if (intentInProgress) {
            isInProgress = true;
            Log.d(TAG, "Using intent in_progress: true");
        } else if (session != null) {
            Log.d(TAG, "Session: accountId=" + session.getAccountId());
            totalMessageCount = session.getMessageCount();
            currentSince = 0;
            isInProgress = session.isInProgress();
            Log.d(TAG, "Using session in_progress: " + isInProgress);
            
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
        
        // 根据会话状态初始化按钮
        if (isInProgress) {
            setButtonStateSending();
        } else {
            setButtonStateNormal();
        }
        
        restoreDraft();
        
        messages = new ArrayList<>();
        adapter = new MessageAdapter(messages, this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvMessages.setLayoutManager(layoutManager);
        // 关闭 ItemAnimator 避免加载更多时的闪烁
        rvMessages.setItemAnimator(null);
        rvMessages.setAdapter(adapter);
        
        setupScrollListener();
        setupKeyboardListener();
        
        fabScrollBottom.setOnClickListener(v -> {
            userAtBottom = true;
            scrollToBottomSmooth();
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
    }
    
    /**
     * 设置滚动监听器 - 优化的下拉加载
     */
    private void setupScrollListener() {
        rvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;
                
                int firstVisible = lm.findFirstVisibleItemPosition();
                int lastVisible = lm.findLastVisibleItemPosition();
                int total = adapter.getItemCount();
                
                boolean isAtBottom = (total == 0) || (lastVisible >= total - BOTTOM_THRESHOLD);
                
                // 更新用户是否在底部的状态
                userAtBottom = isAtBottom;
                
                // 更新 FAB 可见性
                fabScrollBottom.setVisibility(isAtBottom ? View.GONE : View.VISIBLE);
                
                // 更新位置记录（用于加载更多后恢复）
                if (firstVisible >= 0 && !isLoadingOlder) {
                    Message anchorMsg = adapter.getVisibleMessageAt(firstVisible);
                    if (anchorMsg != null) {
                        anchorMessageCreated = anchorMsg.getCreated();
                        View firstChild = lm.findViewByPosition(firstVisible);
                        if (firstChild != null) {
                            offsetToRestore = firstChild.getTop();
                        }
                    }
                }
                
                // 显示/隐藏加载提示
                boolean isAtTop = (firstVisible == 0 && total > 0);
                if (!isLoadingOlder) {
                    if (isAtTop && canLoadMore()) {
                        showLoadMoreHint("↑ 下拉加载更多");
                    } else {
                        hideLoadMoreHint();
                    }
                }
            }
            
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                
                // 只在滚动停止时检查是否需要加载更多
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (lm == null) return;
                    
                    int firstVisible = lm.findFirstVisibleItemPosition();
                    int total = adapter.getItemCount();
                    boolean isAtTop = (firstVisible == 0 && total > 0);
                    
                    // 到达顶部且可以加载更多时触发
                    if (isAtTop && canLoadMore() && !isLoadingOlder) {
                        // 防抖：避免快速滑动时多次触发
                        long now = System.currentTimeMillis();
                        if (now - lastLoadTriggerTime >= MIN_LOAD_INTERVAL_MS) {
                            lastLoadTriggerTime = now;
                            triggerLoadOlder();
                        }
                    }
                }
            }
        });
    }
    
    /**
     * 设置键盘监听器 - 优化的键盘响应
     */
    private void setupKeyboardListener() {
        View rootView = findViewById(android.R.id.content);
        
        // 监听 RecyclerView 高度变化，使用防抖机制
        rvMessages.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int currentHeight = rvMessages.getHeight();
                
                if (lastRecyclerViewHeight > 0 && currentHeight != lastRecyclerViewHeight) {
                    int heightDelta = currentHeight - lastRecyclerViewHeight;
                    long now = System.currentTimeMillis();
                    
                    accumulatedHeightDelta += heightDelta;
                    
                    if (now - lastKeyboardScrollTime >= MIN_KEYBOARD_SCROLL_INTERVAL_MS) {
                        if (accumulatedHeightDelta < 0) {
                            rvMessages.scrollBy(0, -accumulatedHeightDelta);
                            Log.d(TAG, "Keyboard show scroll: accumulatedDelta=" + accumulatedHeightDelta);
                        }
                        
                        accumulatedHeightDelta = 0;
                        lastKeyboardScrollTime = now;
                    }
                    
                    lastRecyclerViewHeight = currentHeight;
                } else if (lastRecyclerViewHeight < 0) {
                    lastRecyclerViewHeight = currentHeight;
                }
            }
        });
        
        // WindowInsets 监听器用于检测键盘状态变化
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            int keyboardHeight = imeInsets.bottom;
            
            boolean keyboardNowVisible = keyboardHeight > 100;
            
            if (keyboardNowVisible != isKeyboardVisible) {
                isKeyboardVisible = keyboardNowVisible;
                lastKeyboardHeight = keyboardHeight;
                
                if (keyboardNowVisible) {
                    scheduleKeyboardScroll();
                }
            } else if (keyboardNowVisible && keyboardHeight != lastKeyboardHeight) {
                lastKeyboardHeight = keyboardHeight;
                scheduleKeyboardScroll();
            }
            
            return insets;
        });
        
        etMessage.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus && isKeyboardVisible) {
                scheduleKeyboardScroll();
            }
        });
    }
    
    /**
     * 调度键盘滚动任务 - 防抖机制
     */
    private void scheduleKeyboardScroll() {
        if (keyboardScrollRunnable != null) {
            keyboardHandler.removeCallbacks(keyboardScrollRunnable);
        }
        
        keyboardScrollRunnable = () -> {
            if (userAtBottom) {
                scrollToBottomSmooth();
            }
        };
        
        keyboardHandler.postDelayed(keyboardScrollRunnable, 150);
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
    
    /**
     * 从 SessionSettingsActivity 返回的结果更新会话信息
     */
    private void updateSessionInfoFromResult(String provider, String model, String cwd) {
        if (provider != null && !provider.isEmpty()) {
            currentProvider = provider;
        }
        if (model != null && !model.isEmpty()) {
            currentModel = model;
        }
        
        // 更新显示
        if (currentProvider != null && currentModel != null) {
            tvSessionInfo.setText(currentProvider + " | " + currentModel);
        }
        if (cwd != null) {
            tvSessionPath.setText("cwd: " + cwd);
        }
        
        // 更新本地 SessionManager
        Session session = sessionManager.getSession(currentSessionId);
        if (session != null) {
            if (provider != null && !provider.isEmpty()) {
                session.setProvider(provider);
            }
            if (model != null && !model.isEmpty()) {
                session.setModel(model);
            }
            if (cwd != null) {
                session.setCwd(cwd);
            }
            sessionManager.updateSession(session);
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
                if (isAutoRefreshEnabled && !isLoadingOlder) {
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
        if (apiClient == null || currentSessionId == null || isLoadingOlder) return;
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
                            
                            int serverCount = session.getMessageCount();
                            
                            boolean hasMessagesInPool = messagesInPool.size() > 0;
                            
                            if (isInProgress || hasMessagesInPool) {
                                setButtonStateSending();
                            } else {
                                if (wasInProgress) {
                                    messagesInPool.clear();
                                    setButtonStateNormal();
                                    fetchNewMessagesAndRestorePosition();
                                } else {
                                    setButtonStateNormal();
                                }
                            }
                            
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
     */
    private void fetchNewMessagesAndRestorePosition() {
        int sinceIndex = processedServerMessageCount + 1;
        
        Log.d(TAG, "Fetching messages from server index: since=" + sinceIndex);
        
        apiClient.getNewMessages(currentSessionId, sinceIndex, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    if (chatMessages.isEmpty()) return;
                    
                    boolean addedNew = false;
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
                        addedNew = true;
                    }
                    
                    processedServerMessageCount += chatMessages.size();
                    
                    cleanupPendingMessages();
                    
                    adapter.refreshData();
                    
                    if (addedNew) {
                        if (userAtBottom) {
                            scrollToBottomSmooth();
                        }
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
     * 查找 pending 消息的位置
     */
    private int findPendingMessageIndex(String content) {
        if (content == null) {
            return -1;
        }
        
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg.isPending() && msg.isUser() && content.equals(msg.getContent())) {
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
    
    /**
     * 是否可以加载更多消息
     */
    private boolean canLoadMore() {
        return isInitialLoadComplete && currentSince > 1 && totalMessageCount > 0;
    }
    
    /**
     * 触发加载更早的消息
     */
    private void triggerLoadOlder() {
        if (isLoadingOlder || !canLoadMore()) return;
        
        isLoadingOlder = true;
        showLoadMoreHint("⏳ 加载中...");
        stopAutoRefresh();
        
        saveScrollPosition();
        
        loadOlderMessages();
    }
    
    /**
     * 保存当前滚动位置
     */
    private void saveScrollPosition() {
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return;
        
        int firstVisiblePosition = lm.findFirstVisibleItemPosition();
        if (firstVisiblePosition >= 0) {
            Message anchorMsg = adapter.getVisibleMessageAt(firstVisiblePosition);
            if (anchorMsg != null) {
                anchorMessageCreated = anchorMsg.getCreated();
                View firstChild = lm.findViewByPosition(firstVisiblePosition);
                if (firstChild != null) {
                    offsetToRestore = firstChild.getTop();
                }
                Log.d(TAG, "Saved position: anchorCreated=" + anchorMessageCreated + ", offset=" + offsetToRestore);
            }
        }
    }
    
    /**
     * 恢复滚动位置
     */
    private void restoreScrollPosition() {
        if (anchorMessageCreated < 0) return;
        
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return;
        
        int newPosition = adapter.findVisiblePositionByCreated(anchorMessageCreated);
        
        Log.d(TAG, "Restore position: anchorCreated=" + anchorMessageCreated + ", newPosition=" + newPosition);
        
        rvMessages.post(() -> {
            int pos = adapter.findVisiblePositionByCreated(anchorMessageCreated);
            if (pos >= 0 && pos < adapter.getItemCount()) {
                lm.scrollToPositionWithOffset(pos, offsetToRestore);
                Log.d(TAG, "Restored to position " + pos + " with offset " + offsetToRestore);
            }
        });
    }
    
    private void loadMessagesPage() {
        if (apiClient == null || currentSessionId == null) return;
        
        if (totalMessageCount <= 0) {
            messages.clear();
            messageFingerprints.clear();
            pendingMessages.clear();
            messagesInPool.clear();
            addSystemMessage("暂无消息");
            processedServerMessageCount = 0;
            currentSince = 0;
            adapter.refreshData();
            hideLoadMoreHint();
            isInitialLoadComplete = true;
            return;
        }
        
        int since = Math.max(1, totalMessageCount - PAGE_SIZE + 1);
        currentSince = since;
        apiClient.getNewMessages(currentSessionId, since, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    messages.clear();
                    messageFingerprints.clear();
                    pendingMessages.clear();
                    messagesInPool.clear();
                    
                    if (chatMessages.isEmpty()) {
                        addSystemMessage("暂无消息");
                        processedServerMessageCount = 0;
                        currentSince = 0;
                        adapter.refreshData();
                        hideLoadMoreHint();
                        isInitialLoadComplete = true;
                        return;
                    }
                    
                    for (ApiClient.ChatMessage msg : chatMessages) {
                        messages.add(new Message(msg.content, msg.role, msg.created));
                        messageFingerprints.put(msg.created, msg.content != null ? msg.content : "");
                    }
                    
                    processedServerMessageCount = totalMessageCount;
                    sessionManager.updateFirstMessageIndex(currentSessionId, currentSince);
                    adapter.refreshData();
                    
                    isInitialLoadComplete = true;
                    hideLoadMoreHint();
                    
                    userAtBottom = true;
                    scrollToBottomSmooth();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    messages.clear();
                    messageFingerprints.clear();
                    pendingMessages.clear();
                    messagesInPool.clear();
                    addSystemMessage("加载失败: " + error);
                    currentSince = 0;
                    adapter.refreshData();
                    hideLoadMoreHint();
                    isInitialLoadComplete = true;
                });
            }
        });
    }
    
    /**
     * 加载更早的消息
     */
    private void loadOlderMessages() {
        if (!canLoadMore()) {
            isLoadingOlder = false;
            hideLoadMoreHint();
            Toast.makeText(this, "已到第一条消息", Toast.LENGTH_SHORT).show();
            return;
        }
        
        int newSince = Math.max(1, currentSince - PAGE_SIZE);
        
        apiClient.getNewMessages(currentSessionId, newSince, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    if (chatMessages.isEmpty()) {
                        currentSince = 1;
                        isLoadingOlder = false;
                        hideLoadMoreHint();
                        Toast.makeText(ChatActivity.this, "已到第一条消息", Toast.LENGTH_SHORT).show();
                        startAutoRefresh();
                        return;
                    }
                    
                    int newTotalCount = 0;
                    int newVisibleCount = 0;
                    
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
                            newTotalCount++;
                            
                            if (message.shouldDisplay()) {
                                newVisibleCount++;
                            }
                        }
                    }
                    
                    Log.d(TAG, "Loaded messages: total=" + newTotalCount + ", visible=" + newVisibleCount);
                    
                    if (newTotalCount == 0) {
                        currentSince = newSince;
                        isLoadingOlder = false;
                        if (canLoadMore()) {
                            showLoadMoreHint("⏳ 继续加载...");
                            rvMessages.postDelayed(() -> triggerLoadOlder(), 200);
                        } else {
                            hideLoadMoreHint();
                            Toast.makeText(ChatActivity.this, "已到第一条消息", Toast.LENGTH_SHORT).show();
                            startAutoRefresh();
                        }
                        return;
                    }
                    
                    currentSince = newSince;
                    sessionManager.updateFirstMessageIndex(currentSessionId, newSince);
                    
                    adapter.refreshData();
                    
                    final long savedAnchor = anchorMessageCreated;
                    final int savedOffset = offsetToRestore;
                    final int loadedVisible = newVisibleCount;
                    
                    rvMessages.post(() -> {
                        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
                        if (lm != null) {
                            int newPosition = adapter.findVisiblePositionByCreated(savedAnchor);
                            
                            Log.d(TAG, "Position restore: anchor=" + savedAnchor + 
                                  ", oldVisiblePos=" + newPosition + 
                                  ", newVisibleAdded=" + loadedVisible);
                            
                            if (newPosition >= 0 && newPosition < adapter.getItemCount()) {
                                lm.scrollToPositionWithOffset(newPosition, savedOffset);
                                Log.d(TAG, "Restored to visible position " + newPosition + " with offset " + savedOffset);
                            } else {
                                Log.d(TAG, "Anchor message not found in visibleMessages");
                            }
                        }
                        
                        isLoadingOlder = false;
                        
                        if (canLoadMore()) {
                            showLoadMoreHint("✓ 已加载 " + loadedVisible + " 条可见消息");
                            rvMessages.postDelayed(() -> {
                                LinearLayoutManager lm2 = (LinearLayoutManager) rvMessages.getLayoutManager();
                                if (lm2 != null && lm2.findFirstVisibleItemPosition() == 0 && canLoadMore()) {
                                    showLoadMoreHint("↑ 下拉加载更多");
                                } else {
                                    hideLoadMoreHint();
                                }
                            }, 1500);
                        } else {
                            showLoadMoreHint("已到第一条消息");
                            rvMessages.postDelayed(() -> hideLoadMoreHint(), 1500);
                        }
                        
                        startAutoRefresh();
                    });
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    isLoadingOlder = false;
                    hideLoadMoreHint();
                    Toast.makeText(ChatActivity.this, "加载失败: " + error, Toast.LENGTH_SHORT).show();
                    startAutoRefresh();
                });
            }
        });
    }
    
    private void checkLastMessageUpdate() {
        if (!userAtBottom) return;
        
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
                            scrollToBottomSmooth();
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
        isInitialLoadComplete = false;
        
        saveScrollPosition();
        
        messages.clear();
        messageFingerprints.clear();
        pendingMessages.clear();
        messagesInPool.clear();
        processedServerMessageCount = 0;
        currentSince = 0;
        sessionManager.updateFirstMessageIndex(currentSessionId, 0);
        hideLoadMoreHint();
        loadMessagesPage();
    }
    
    /**
     * 平滑滚动到底部
     */
    private void scrollToBottomSmooth() {
        if (rvMessages == null || adapter == null) return;
        int itemCount = adapter.getItemCount();
        if (itemCount == 0) return;
        
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return;
        
        int lastPosition = itemCount - 1;
        
        lm.scrollToPosition(lastPosition);
        
        rvMessages.post(() -> {
            if (adapter.getItemCount() == 0) return;
            
            int pos = adapter.getItemCount() - 1;
            View lastChild = lm.findViewByPosition(pos);
            
            if (lastChild != null) {
                int recyclerHeight = rvMessages.getHeight();
                int itemHeight = lastChild.getHeight();
                int offset = recyclerHeight - itemHeight;
                
                lm.scrollToPositionWithOffset(pos, offset);
            } else {
                lm.scrollToPosition(pos);
            }
        });
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
                            
                            if (isInProgress) {
                                setButtonStateSending();
                            } else {
                                setButtonStateNormal();
                            }
                            
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
                                    sessionManager.updateSession(localSession);
                                    tvSessionInfo.setText(currentProvider + " | " + currentModel);
                                }
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
                runOnUiThread(() -> {
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            }
        });
    }
    
    private void updateMenuVisibility(Menu menu) {
        if (menu == null) return;
        MenuItem previewItem = menu.findItem(R.id.action_preview);
        if (previewItem != null) {
            previewItem.setVisible(false);
        }
    }
    
    private void openPreviewUrl() {
        if (currentSessionId == null) return;
        String url = settingsManager.getFullUrl();
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "请先配置服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }
        String previewUrl = url + "/preview/" + currentSessionId;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(previewUrl));
        startActivity(intent);
    }
    
    private void openSessionSettings() {
        Intent intent = new Intent(this, SessionSettingsActivity.class);
        intent.putExtra(SessionSettingsActivity.EXTRA_SESSION_ID, currentSessionId);
        intent.putExtra(SessionSettingsActivity.EXTRA_ACCOUNT_ID, accountId);
        startActivityForResult(intent, REQUEST_SESSION_SETTINGS);
    }
    
    private void sendMessage() {
        String content = etMessage.getText().toString().trim();
        if (content.isEmpty()) return;
        etMessage.setText("");
        
        Message pendingMsg = Message.createPending(content, true);
        messages.add(pendingMsg);
        pendingMessages.put(content, System.currentTimeMillis());
        adapter.refreshData();
        scrollToBottomSmooth();
        
        setButtonStateSending();
        isInProgress = true;
        
        apiClient.sendMessage(currentSessionId, content, new ApiClient.MessageCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    messagesInPool.add(content);
                    fetchNewMessagesAndRestorePosition();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    pendingMessages.remove(content);
                    messagesInPool.remove(content);
                    for (int i = messages.size() - 1; i >= 0; i--) {
                        Message msg = messages.get(i);
                        if (msg.isPending() && content.equals(msg.getContent())) {
                            messages.remove(i);
                            break;
                        }
                    }
                    adapter.refreshData();
                    setButtonStateNormal();
                    isInProgress = false;
                    Toast.makeText(ChatActivity.this, "发送失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void stopSession() {
        messagesInPool.clear();
        
        apiClient.stopSession(currentSessionId, new ApiClient.StopCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    setButtonStateNormal();
                    isInProgress = false;
                    refreshMessages();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    setButtonStateNormal();
                    isInProgress = false;
                    Toast.makeText(ChatActivity.this, "停止失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void setButtonStateSending() {
        buttonState = STATE_SENDING;
        btnSend.setText("停止");
        btnSend.setBackgroundResource(R.drawable.btn_stop_bg);
        btnSend.setBackgroundTintList(null);
    }
    
    private void setButtonStateNormal() {
        buttonState = STATE_NORMAL;
        btnSend.setText("发送");
        btnSend.setBackgroundResource(R.drawable.send_button_bg);
        btnSend.setBackgroundTintList(null);
    }
    
    private void clearSession() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("清空会话")
            .setMessage("确定要清空当前会话的所有消息吗？")
            .setPositiveButton("清空", (dialog, which) -> {
                apiClient.clearSession(currentSessionId, new ApiClient.ClearCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            messages.clear();
                            messageFingerprints.clear();
                            pendingMessages.clear();
                            messagesInPool.clear();
                            processedServerMessageCount = 0;
                            currentSince = 0;
                            totalMessageCount = 0;
                            adapter.refreshData();
                            addSystemMessage("会话已清空");
                            sessionManager.updateFirstMessageIndex(currentSessionId, 0);
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
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void deleteSession() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("删除会话")
            .setMessage("确定要删除当前会话吗？")
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
    
    private void addSystemMessage(String text) {
        messages.add(new Message(text, false));
        adapter.refreshData();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SESSION_SETTINGS && resultCode == RESULT_OK && data != null) {
            // 从 SessionSettingsActivity 返回的结果更新显示
            String newProvider = data.getStringExtra(SessionSettingsActivity.RESULT_PROVIDER);
            String newModel = data.getStringExtra(SessionSettingsActivity.RESULT_MODEL);
            String newCwd = data.getStringExtra(SessionSettingsActivity.RESULT_CWD);
            
            updateSessionInfoFromResult(newProvider, newModel, newCwd);
        }
    }
}
