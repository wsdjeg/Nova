package net.wsdjeg.nova;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 聊天界面 Activity
 * 
 * 核心逻辑：
 * 1. 消息排序：API 返回升序 [oldest, ..., newest]
 * 2. 本地列表：保持升序，最新消息在末尾
 * 3. 显示过滤：只显示 content 不为空且 role 不是 tool 的消息
 * 4. 错误消息：带有 error 字段的消息以红色背景特殊显示
 * 5. 工具调用：assistant 消息中的 tool_calls 拆分显示
 * 6. 工具结果：role=tool 的消息单独显示（带工具名称和状态）
 * 
 * 消息计数说明：
 * - totalMessageCount: 服务端返回的总消息数（包含 tool 等不可显示消息）
 * - currentSince: 服务端消息索引（从1开始，基于服务端消息位置）
 * - processedServerMessageCount: 已处理的服务端消息数（基于服务端计数）
 * - adapter.getItemCount(): 显示的消息数（过滤后的，与服务端计数无关）
 * 
 * 下拉加载更多 - 位置恢复机制（StableKey 版本）：
 * - 使用 MessageAdapter.computeStableKey 生成的全局唯一键作为锚点
 * - Key 来源：
 *   - tool_call: tc:<tool_call.id>     (LLM 生成的全局唯一 ID)
 *   - tool_msg:  tr:<tool_call_id>     (引用对应 toolCall)
 *   - 普通消息:   msg:<serverIndex>     (服务端 1-indexed 索引)
 * - 不再使用 created 时间戳，因为同一 assistant 消息拆分为多个 visibleItem
 *   时共享同一 created，会导致定位错误。
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
    private static final int STATE_LISTENING = 2;
    private static final int BOTTOM_THRESHOLD = 3;
    private static final int REQUEST_SESSION_SETTINGS = 1001;
    private static final int REQUEST_VOICE_INPUT = 1002;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1003;
    
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
    private ImageButton btnSend;
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
    
    // 位置恢复：使用 StableKey 作为锚点
    private String anchorStableKey = null;
    private int offsetToRestore = 0;
    private boolean isRestoringPosition = false;
    
    // 加载更早消息的状态标志
    private boolean isLoadingOlder = false;
    private long lastLoadTriggerTime = 0;
    
    // 消息指纹缓存（格式：created:role:toolcallid:content）
    private Set<String> messageFingerprints = new HashSet<>();

    // Pending 消息：存储正在发送的消息内容，等待服务端确认（用于UI显示）
    private Map<String, Long> pendingMessages = new HashMap<>();
    
    // Bug 2 修复：消息池等待集合
    private Set<String> messagesInPool = new HashSet<>();
    
    private boolean userAtBottom = true;
    private boolean isUserScrolling = false;
    private View.OnLayoutChangeListener bottomAlignWatcher = null;
    private long bottomAlignDeadline = 0L;
    // 键盘状态追踪
    private int lastKeyboardHeight = 0;
    private boolean isKeyboardVisible = false;
    private Handler keyboardHandler = new Handler(Looper.getMainLooper());
    private Runnable keyboardScrollRunnable = null;
    private int lastRecyclerViewHeight = -1;
    private long lastKeyboardScrollTime = 0;
    private int accumulatedHeightDelta = 0;

    // Vosk 离线语音识别
    private VoskSpeechRecognizer voskRecognizer;
    private boolean isVoskListening = false;
    private String voskBaseText = "";
    private android.animation.ObjectAnimator pulseAnimator;

    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // 启用返回按钮
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        
        // 设置返回箭头颜色为白色
        Drawable navigationIcon = toolbar.getNavigationIcon();
        if (navigationIcon != null) {
            navigationIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.white), PorterDuff.Mode.SRC_IN);
            toolbar.setNavigationIcon(navigationIcon);
        }
        
        tvSessionTitle = findViewById(R.id.tv_session_title);
        tvSessionInfo = findViewById(R.id.tv_session_info);
        tvSessionPath = findViewById(R.id.tv_session_path);
        tvLoadMore = findViewById(R.id.tv_load_more);
        
        currentSessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        currentSessionTitle = getIntent().getStringExtra(EXTRA_SESSION_TITLE);
        String intentProvider = getIntent().getStringExtra("provider");
        String intentModel = getIntent().getStringExtra("model");
        String intentCwd = getIntent().getStringExtra("cwd");
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
        rvMessages.setItemAnimator(null);
        rvMessages.setAdapter(adapter);
        
        setupScrollListener();
        setupKeyboardListener();
        
        fabScrollBottom.setOnClickListener(v -> {
            userAtBottom = true;
            scrollToBottomSmooth();
            // [FIX] 用户回到底部后立即刷新，追回漏掉的新消息
            refreshMessages();
        });
        
        btnSend.setOnClickListener(v -> {
            if (buttonState == STATE_SENDING) {
                stopSession();
            } else if (buttonState == STATE_LISTENING) {
                stopVoskListening();
            } else {
                String content = etMessage.getText().toString().trim();
                if (content.isEmpty()) {
                    startVoiceInput();
                } else {
                    sendMessage();
                }
            }
        });
        
        etMessage.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(android.text.Editable s) {
                updateButtonAppearance();
            }
        });
        
        tvSessionTitle.setText(currentSessionTitle != null ? currentSessionTitle : currentSessionId);
        updateSessionInfo(intentProvider, intentModel, intentCwd);
        
        // 初始化 Vosk 离线语音识别
        initVoskRecognizer();
        
        messages.add(new Message("正在加载消息...", false));
        adapter.notifyDataSetChangedWithUpdate();
        refreshSessionStatus(() -> loadMessagesPage());
        startAutoRefresh();
    }
    
    /**
     * 从 ChatMessage 创建 Message 对象
     */
    private Message createMessageFromChatMessage(ApiClient.ChatMessage msg, int serverIndex) {
        Message message;
        
        if (msg.error != null && !msg.error.isEmpty()) {
            message = new Message(msg.error, msg.created);
        } else {
            message = new Message(msg.content, msg.role, msg.created);
            
            if (msg.toolCalls != null && !msg.toolCalls.isEmpty()) {
                message.setToolCalls(msg.toolCalls);
                Log.d(TAG, "createMessageFromChatMessage: set toolCalls=" + msg.toolCalls.size() + " for role=" + msg.role);
            }
            
            if (msg.toolCallState != null) {
                message.setToolName(msg.toolCallState.name);
                message.setToolError(msg.toolCallState.error);
            }
            
            if (msg.toolCallId != null && !msg.toolCallId.isEmpty()) {
                message.setToolCallId(msg.toolCallId);
            }
        }
        
        if (serverIndex > 0) {
            message.setServerIndex(serverIndex);
        }
        
        return message;
    }
    
    private Message createMessageFromChatMessage(ApiClient.ChatMessage msg) {
        return createMessageFromChatMessage(msg, -1);
    }
    
    private String getMessageFingerprint(ApiClient.ChatMessage msg) {
        String toolCallId = "";
        if (msg.toolCalls != null && !msg.toolCalls.isEmpty()) {
            toolCallId = msg.toolCalls.get(0).id != null ? msg.toolCalls.get(0).id : "";
        }
        String content = "";
        if (msg.error != null && !msg.error.isEmpty()) {
            content = "error:" + msg.error;
        } else if (msg.content != null) {
            content = msg.content;
        }
        return msg.created + ":" + (msg.role != null ? msg.role : "") + ":" + toolCallId + ":" + content;
    }

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

                fabScrollBottom.setVisibility(isAtBottom ? View.GONE : View.VISIBLE);
                
                int scrollState = recyclerView.getScrollState();
                boolean userDriven = (scrollState == RecyclerView.SCROLL_STATE_DRAGGING
                        || scrollState == RecyclerView.SCROLL_STATE_SETTLING);
                
                if (userDriven && !isRestoringPosition) {
                    userAtBottom = isAtBottom;
                }
                
                if (firstVisible >= 0 && !isRestoringPosition && userDriven) {
                    String key = adapter.getStableKeyAt(firstVisible);
                    if (key != null) {
                        anchorStableKey = key;
                        View firstChild = lm.findViewByPosition(firstVisible);
                        if (firstChild != null) {
                            offsetToRestore = firstChild.getTop();
                        }
                    }
                }

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
                
                isUserScrolling = (newState == RecyclerView.SCROLL_STATE_DRAGGING
                        || newState == RecyclerView.SCROLL_STATE_SETTLING);
                
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (lm == null) return;
                    
                    int firstVisible = lm.findFirstVisibleItemPosition();
                    int total = adapter.getItemCount();
                    boolean isAtTop = (firstVisible == 0 && total > 0);
                    
                    if (isAtTop && canLoadMore() && !isLoadingOlder) {
                        long now = System.currentTimeMillis();
                        if (now - lastLoadTriggerTime >= MIN_LOAD_INTERVAL_MS) {
                            lastLoadTriggerTime = now;
                            triggerLoadOlder();
                        }
                    }
                    
                    // [FIX] 用户滚回底部后立即刷新，追回漏掉的新消息
                    if (userAtBottom) {
                        refreshMessages();
                    }
                }
            }
        });
    }
    
    private void setupKeyboardListener() {
        View rootView = findViewById(android.R.id.content);
        
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
        if (id == android.R.id.home) {
            finish();
            return true;
        }

        if (id == R.id.action_retry) {
            retrySession();
            return true;
        } else if (id == R.id.action_refresh) {
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
    
    private void updateSessionInfoFromResult(String provider, String model, String cwd, String title) {
        if (provider != null && !provider.isEmpty()) {
            currentProvider = provider;
        }
        if (model != null && !model.isEmpty()) {
            currentModel = model;
        }
        if (title != null && !title.isEmpty()) {
            currentSessionTitle = title;
            tvSessionTitle.setText(title);
        }
        
        if (currentProvider != null && currentModel != null) {
            tvSessionInfo.setText(currentProvider + " | " + currentModel);
        }
        if (cwd != null) {
            tvSessionPath.setText("cwd: " + cwd);
        }
        
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
            if (title != null && !title.isEmpty()) {
                session.setTitle(title);
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
                if (!isAutoRefreshEnabled || isLoadingOlder) {
                    refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
                    return;
                }
                if (isUserScrolling && !userAtBottom) {
                    Log.d(TAG, "skip refresh: user is scrolling (not at bottom)");
                    refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
                    return;
                }
                refreshMessages();
                refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
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
        apiClient.getSession(currentSessionId, accountId, new ApiClient.SessionCallback() {
            @Override
            public void onSuccess(Session session) {
                runOnUiThread(() -> {
                    boolean wasInProgress = isInProgress;
                    isInProgress = session.isInProgress();
                    
                    int serverCount = session.getMessageCount();
                    
                    if (isInProgress) {
                        setButtonStateSending();
                    } else {
                        if (wasInProgress || messagesInPool.size() > 0) {
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
                });
            }
            
            @Override
            public void onError(String error) {
                Log.d(TAG, "Session status refresh failed: " + error);
            }
        });
    }
    
    private void fetchNewMessagesAndRestorePosition() {
        final int sinceIndex = processedServerMessageCount + 1;
        
        Log.d(TAG, "Fetching messages from server index: since=" + sinceIndex);
        
        apiClient.getNewMessages(currentSessionId, sinceIndex, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    if (chatMessages.isEmpty()) return;
                    
                    Log.d(TAG, "=== FETCH: received " + chatMessages.size() + " messages ===");
                    
                    boolean addedNew = false;
                    for (int i = 0; i < chatMessages.size(); i++) {
                        ApiClient.ChatMessage msg = chatMessages.get(i);
                        int serverIndex = sinceIndex + i;
                        
                        boolean hasToolCalls = msg.toolCalls != null && !msg.toolCalls.isEmpty();
                        boolean isTool = "tool".equals(msg.role);

                        StringBuilder tcInfo = new StringBuilder();
                        if (hasToolCalls) {
                            tcInfo.append("tool_calls[").append(msg.toolCalls.size()).append("]:");
                            for (ApiClient.ToolCall tc : msg.toolCalls) {
                                tcInfo.append(tc.function.name).append(",");
                            }
                        }
                        Log.d(TAG, "  MSG[" + i + "] svrIdx=" + serverIndex + ", role=" + msg.role + 
                              ", hasToolCalls=" + hasToolCalls + 
                              ", isTool=" + isTool + 
                              ", toolCallState=" + (msg.toolCallState != null ? msg.toolCallState.name : "null") +
                              ", toolCallId=" + (msg.toolCallId != null ? msg.toolCallId : "null") +
                              ", contentLen=" + (msg.content == null ? "null" : msg.content.length()) +
                              (tcInfo.length() > 0 ? ", " + tcInfo.toString() : ""));
                        
                        if (messageFingerprints.contains(getMessageFingerprint(msg))) {
                            Log.d(TAG, "  → SKIP: already exists");
                            continue;
                        }

                        if (msg.error == null && "user".equals(msg.role) && msg.content != null) {
                            int pendingIndex = findPendingMessageIndex(msg.content);
                            if (pendingIndex >= 0) {
                                Log.d(TAG, "  → REPLACE pending at " + pendingIndex);
                                messages.set(pendingIndex, createMessageFromChatMessage(msg, serverIndex));
                                pendingMessages.remove(msg.content);
                                messageFingerprints.add(getMessageFingerprint(msg));
                                continue;
                            }
                        }
                        
                        messages.add(createMessageFromChatMessage(msg, serverIndex));
                        String fingerprint = getMessageFingerprint(msg);
                        messageFingerprints.add(fingerprint);
                        Log.d(TAG, "  → ADD: fingerprint=" + fingerprint);
                        addedNew = true;
                    }
                    
                    processedServerMessageCount += chatMessages.size();
                    
                    cleanupPendingMessages();
                    
                    final boolean wasAtBottom = userAtBottom;
                    
                    adapter.notifyDataSetChangedWithUpdate();
                    
                    if (addedNew) {
                        if (wasAtBottom) {
                            scrollToBottomSmooth();
                        } else {
                            restoreScrollPosition();
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

    private int findPendingMessageIndex(String content) {
        if (content == null) return -1;
        
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
    
    private boolean canLoadMore() {
        return isInitialLoadComplete && currentSince > 1 && totalMessageCount > 0;
    }
    
    private void triggerLoadOlder() {
        if (isLoadingOlder || !canLoadMore()) return;
        
        isLoadingOlder = true;
        showLoadMoreHint("⏳ 加载中...");
        stopAutoRefresh();
        
        saveScrollPosition();
        
        loadOlderMessages();
    }
    
    private void saveScrollPosition() {
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return;
        
        int firstVisiblePosition = lm.findFirstVisibleItemPosition();
        if (firstVisiblePosition >= 0) {
            String key = adapter.getStableKeyAt(firstVisiblePosition);
            if (key != null) {
                anchorStableKey = key;
            }
            View firstChild = lm.findViewByPosition(firstVisiblePosition);
            if (firstChild != null) {
                offsetToRestore = firstChild.getTop();
            }
            Log.d(TAG, "Saved position: anchorKey=" + anchorStableKey + ", offset=" + offsetToRestore);
        }
    }
    
    private void restoreScrollPosition() {
        if (anchorStableKey == null) return;
        
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return;
        
        final String savedAnchor = anchorStableKey;
        final int savedOffset = offsetToRestore;
        
        Log.d(TAG, "Restore position: anchorKey=" + savedAnchor + ", offset=" + savedOffset);
        
        isRestoringPosition = true;
        rvMessages.post(() -> {
            try {
                int pos = adapter.findVisiblePositionByKey(savedAnchor);
                if (pos >= 0 && pos < adapter.getItemCount()) {
                    lm.scrollToPositionWithOffset(pos, savedOffset);
                    Log.d(TAG, "Restored to position " + pos + " with offset " + savedOffset);
                } else {
                    Log.d(TAG, "Restore skipped: anchor not found in visible items");
                }
            } finally {
                rvMessages.post(() -> isRestoringPosition = false);
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
            adapter.notifyDataSetChangedWithUpdate();
            hideLoadMoreHint();
            isInitialLoadComplete = true;
            return;
        }
        
        final int since = Math.max(1, totalMessageCount - PAGE_SIZE + 1);
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
                        adapter.notifyDataSetChangedWithUpdate();
                        hideLoadMoreHint();
                        isInitialLoadComplete = true;
                        return;
                    }
                    
                    for (int i = 0; i < chatMessages.size(); i++) {
                        ApiClient.ChatMessage msg = chatMessages.get(i);
                        int serverIndex = since + i;
                        messages.add(createMessageFromChatMessage(msg, serverIndex));
                        messageFingerprints.add(getMessageFingerprint(msg));
                    }
                    
                    processedServerMessageCount = totalMessageCount;
                    sessionManager.updateFirstMessageIndex(currentSessionId, currentSince);
                    adapter.notifyDataSetChangedWithUpdate();
                    
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
                    adapter.notifyDataSetChangedWithUpdate();
                    hideLoadMoreHint();
                    isInitialLoadComplete = true;
                });
            }
        });
    }
    
    private void loadOlderMessages() {
        if (!canLoadMore()) {
            isLoadingOlder = false;
            hideLoadMoreHint();
            Toast.makeText(this, "已到第一条消息", Toast.LENGTH_SHORT).show();
            return;
        }
        
        final int newSince = Math.max(1, currentSince - PAGE_SIZE);
        
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
                        int serverIndex = newSince + i;
                        if (!messageFingerprints.contains(getMessageFingerprint(msg))) {
                            Message message = createMessageFromChatMessage(msg, serverIndex);
                            messages.add(0, message);
                            messageFingerprints.add(getMessageFingerprint(msg));
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
                    
                    adapter.notifyDataSetChangedWithUpdate();
                    
                    final String savedAnchor = anchorStableKey;
                    final int savedOffset = offsetToRestore;
                    final int loadedVisible = newVisibleCount;
                    
                    rvMessages.post(() -> {
                        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
                        if (lm != null) {
                            int newPosition = (savedAnchor != null)
                                    ? adapter.findVisiblePositionByKey(savedAnchor)
                                    : -1;
                            
                            Log.d(TAG, "Position restore: anchorKey=" + savedAnchor + 
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
        
        final int sinceIndex = processedServerMessageCount + 1;
        
        apiClient.getNewMessages(currentSessionId, sinceIndex, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    if (!userAtBottom) return;
                    if (chatMessages.isEmpty()) return;

                    ApiClient.ChatMessage latestFiltered = null;
                    int latestSvrIdx = -1;
                    for (int i = chatMessages.size() - 1; i >= 0; i--) {
                        ApiClient.ChatMessage msg = chatMessages.get(i);
                        if (msg.error != null && !msg.error.isEmpty()) {
                            latestFiltered = msg;
                            latestSvrIdx = sinceIndex + i;
                            break;
                        }
                        if (msg.content != null && !msg.content.isEmpty() && !"tool".equals(msg.role)) {
                            latestFiltered = msg;
                            latestSvrIdx = sinceIndex + i;
                            break;
                        }
                    }
                    
                    if (latestFiltered == null) return;
                    
                    if (messages.size() > 0) {
                        int lastIdx = messages.size() - 1;
                        Message lastMsg = messages.get(lastIdx);
                        
                        String newContent = latestFiltered.error != null ? latestFiltered.error : latestFiltered.content;
                        String oldContent = lastMsg.isError() ? lastMsg.getError() : lastMsg.getContent();
                        
                        if (newContent != null && !newContent.equals(oldContent)) {
                            messages.set(lastIdx, createMessageFromChatMessage(latestFiltered, latestSvrIdx));
                            messageFingerprints.add(getMessageFingerprint(latestFiltered));
                            adapter.notifyDataSetChangedWithUpdate();
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
    
    private void scrollToBottomSmooth() {
        if (rvMessages == null || adapter == null) return;
        int itemCount = adapter.getItemCount();
        if (itemCount == 0) return;

        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return;

        int lastPosition = itemCount - 1;

        lm.scrollToPositionWithOffset(lastPosition, 0);

        rvMessages.post(() -> alignLastItemToBottom());

        installBottomAlignWatcher(600L);
    }

    private void installBottomAlignWatcher(long durationMs) {
        if (rvMessages == null) return;

        long now = System.currentTimeMillis();
        bottomAlignDeadline = Math.max(bottomAlignDeadline, now + durationMs);

        if (bottomAlignWatcher != null) {
            return;
        }

        bottomAlignWatcher = new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b,
                                       int ol, int ot, int or_, int ob) {
                if (System.currentTimeMillis() > bottomAlignDeadline) {
                    uninstallBottomAlignWatcher();
                    return;
                }
                if (isUserScrolling) {
                    uninstallBottomAlignWatcher();
                    return;
                }
                if (!userAtBottom) {
                    uninstallBottomAlignWatcher();
                    return;
                }
                int newH = b - t;
                int oldH = ob - ot;
                if (newH != oldH || !isLastItemFullyAtBottom()) {
                    alignLastItemToBottom();
                }
            }
        };
        rvMessages.addOnLayoutChangeListener(bottomAlignWatcher);

        rvMessages.postDelayed(this::uninstallBottomAlignWatcher, durationMs + 50);
    }

    private void uninstallBottomAlignWatcher() {
        if (bottomAlignWatcher != null && rvMessages != null) {
            rvMessages.removeOnLayoutChangeListener(bottomAlignWatcher);
        }
        bottomAlignWatcher = null;
    }

    private boolean isLastItemFullyAtBottom() {
        if (rvMessages == null || adapter == null) return true;
        int itemCount = adapter.getItemCount();
        if (itemCount == 0) return true;
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return true;
        int pos = itemCount - 1;
        View lastChild = lm.findViewByPosition(pos);
        if (lastChild == null) return false;
        int recyclerBottom = rvMessages.getHeight() - rvMessages.getPaddingBottom();
        return Math.abs(lastChild.getBottom() - recyclerBottom) <= 1;
    }

    private void alignLastItemToBottom() {
        if (rvMessages == null || adapter == null) return;
        int itemCount = adapter.getItemCount();
        if (itemCount == 0) return;

        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return;

        int pos = itemCount - 1;
        View lastChild = lm.findViewByPosition(pos);

        if (lastChild != null) {
            int recyclerHeight = rvMessages.getHeight() - rvMessages.getPaddingTop() - rvMessages.getPaddingBottom();
            int itemHeight = lastChild.getHeight();
            int offset = recyclerHeight - itemHeight;
            lm.scrollToPositionWithOffset(pos, offset);
        } else {
            lm.scrollToPositionWithOffset(pos, 0);
            rvMessages.post(() -> alignLastItemToBottom());
        }
    }

    private void refreshSessionStatus() {
        refreshSessionStatus(null);
    }
    
    private void refreshSessionStatus(Runnable onComplete) {
        apiClient.getSession(currentSessionId, accountId, new ApiClient.SessionCallback() {
            @Override
            public void onSuccess(Session serverSession) {
                runOnUiThread(() -> {
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
                        String serverTitle = serverSession.getTitle();
                        
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
                        if (serverTitle != null && !serverTitle.isEmpty()
                            && !serverTitle.equals(localSession.getTitle())) {
                            localSession.setTitle(serverTitle);
                            currentSessionTitle = serverTitle;
                            tvSessionTitle.setText(serverTitle);
                            updated = true;
                        }
                        
                        if (updated) {
                            sessionManager.updateSession(localSession);
                            tvSessionInfo.setText(currentProvider + " | " + currentModel);
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
            previewItem.setVisible(true);
        }
    }
    
    private void openPreviewUrl() {
        if (currentSessionId == null) return;
        if (currentAccount == null) {
            Toast.makeText(this, "账号信息无效", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = currentAccount.getUrl();
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "请先配置服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }
        String previewUrl = url + "/session?id=" + currentSessionId;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(previewUrl));
        startActivity(intent);
    }
    
    private void openSessionSettings() {
        Intent intent = new Intent(this, SessionSettingsActivity.class);
        intent.putExtra(SessionSettingsActivity.EXTRA_SESSION_ID, currentSessionId);
        intent.putExtra(SessionSettingsActivity.EXTRA_ACCOUNT_ID, accountId);
        intent.putExtra(SessionSettingsActivity.EXTRA_PROVIDER, currentProvider);
        intent.putExtra(SessionSettingsActivity.EXTRA_MODEL, currentModel);
        Session session = sessionManager.getSession(currentSessionId);
        if (session != null) {
            intent.putExtra(SessionSettingsActivity.EXTRA_CWD, session.getCwd());
            intent.putExtra(SessionSettingsActivity.EXTRA_TITLE, session.getTitle());
        }
        startActivityForResult(intent, REQUEST_SESSION_SETTINGS);
    }
    
    private void sendMessage() {
        String content = etMessage.getText().toString().trim();
        if (content.isEmpty()) return;
        etMessage.setText("");
        
        Message pendingMsg = Message.createPending(content, true);
        messages.add(pendingMsg);
        pendingMessages.put(content, System.currentTimeMillis());
        adapter.notifyDataSetChangedWithUpdate();
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
                    adapter.notifyDataSetChangedWithUpdate();
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
    
    private void retrySession() {
        if (isInProgress) {
            Toast.makeText(this, "会话正在进行中，请先停止", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "正在重试...", Toast.LENGTH_SHORT).show();
        
        apiClient.retrySession(currentSessionId, new ApiClient.RetryCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    setButtonStateSending();
                    isInProgress = true;
                    Toast.makeText(ChatActivity.this, "已发起重试", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "重试失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void setButtonStateSending() {
        if (isVoskListening) return;
        buttonState = STATE_SENDING;
        updateButtonAppearance();
    }
    
    private void setButtonStateNormal() {
        if (isVoskListening) return;
        buttonState = STATE_NORMAL;
        updateButtonAppearance();
    }
    
    private void updateButtonAppearance() {
        if (buttonState == STATE_SENDING) {
            btnSend.setImageResource(R.drawable.ic_stop);
            btnSend.setBackgroundResource(R.drawable.btn_stop_bg);
        } else if (buttonState == STATE_LISTENING) {
            btnSend.setImageResource(R.drawable.ic_voice_wave);
            btnSend.setBackgroundResource(R.drawable.listening_button_bg);
        } else {
            String content = etMessage.getText().toString().trim();
            if (!content.isEmpty()) {
                btnSend.setImageResource(R.drawable.ic_send);
                btnSend.setBackgroundResource(R.drawable.send_button_bg);
            } else {
                btnSend.setImageResource(R.drawable.ic_mic);
                btnSend.setBackgroundResource(R.drawable.mic_button_bg);
            }
        }
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
                            adapter.notifyDataSetChangedWithUpdate();
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
        adapter.notifyDataSetChangedWithUpdate();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SESSION_SETTINGS && resultCode == RESULT_OK && data != null) {
            String newProvider = data.getStringExtra(SessionSettingsActivity.RESULT_PROVIDER);
            String newModel = data.getStringExtra(SessionSettingsActivity.RESULT_MODEL);
            String newCwd = data.getStringExtra(SessionSettingsActivity.RESULT_CWD);
            String newTitle = data.getStringExtra(SessionSettingsActivity.RESULT_TITLE);
            
            updateSessionInfoFromResult(newProvider, newModel, newCwd, newTitle);
        } else if (requestCode == REQUEST_VOICE_INPUT && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String text = results.get(0);
                String current = etMessage.getText().toString().trim();
                if (!current.isEmpty()) {
                    etMessage.setText(current + " " + text);
                } else {
                    etMessage.setText(text);
                }
                etMessage.setSelection(etMessage.length());
                etMessage.requestFocus();
            }
        }
    }
    /**
     * 优先使用 Vosk 离线识别，不可用时回退到 Android 系统语音识别
     */
    private void startVoiceInput() {
        // 1. 检查 RECORD_AUDIO 运行时权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }

        // 2. 优先尝试 Vosk 离线识别
        if (voskRecognizer != null && voskRecognizer.isModelReady()) {
            startVoskListening();
            return;
        }

        // 2.5 Vosk 模型状态检查
        if (voskRecognizer != null && !voskRecognizer.isModelReady()) {
            if (voskRecognizer.hasModelError()) {
                // 模型加载已失败，显示具体原因
                String errMsg = voskRecognizer.getModelError();
                Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show();
                voskRecognizer.clearModelError();
                Log.w(TAG, "Vosk model error shown to user: " + errMsg);
            } else {
                // 模型还在后台加载中
                Toast.makeText(this, "语音模型加载中，请稍候...", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // 3. 回退到 Android 系统语音识别
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "说话...");
        try {
            startActivityForResult(intent, REQUEST_VOICE_INPUT);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "未安装语音识别引擎，且离线模型未就绪", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "语音识别启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceInput();
            } else {
                Toast.makeText(this, "需要麦克风权限才能使用语音输入", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initVoskRecognizer() {
        try {
            voskRecognizer = new VoskSpeechRecognizer(this);
            voskRecognizer.setListener(new VoskSpeechRecognizer.RecognitionListener() {
                @Override
                public void onModelReady() {
                    Log.i(TAG, "Vosk model ready");
                }

                @Override
                public void onModelError(String error) {
                    Log.w(TAG, "Vosk model error: " + error);
                    if (voskRecognizer != null) {
                        voskRecognizer.setModelError(error);
                    }
                }

                @Override
                public void onFinalResult(String text) {
                    if (text != null && !text.trim().isEmpty()) {
                        runOnUiThread(() -> {
                            voskBaseText += text.trim();
                            etMessage.setText(voskBaseText);
                            etMessage.setSelection(etMessage.length());
                        });
                    }
                }

                @Override
                public void onPartialResult(String text) {
                    if (text != null && !text.trim().isEmpty()) {
                        runOnUiThread(() -> {
                            etMessage.setText(voskBaseText + text.trim());
                            etMessage.setSelection(etMessage.length());
                        });
                    }
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        isVoskListening = false;
                        voskBaseText = "";
                        buttonState = STATE_NORMAL;
                        updateButtonAppearance();
                        stopListeningPulse();
                        Toast.makeText(ChatActivity.this, "识别失败: " + error, Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onTimeout() {
                    runOnUiThread(() -> {
                        isVoskListening = false;
                        voskBaseText = "";
                        buttonState = STATE_NORMAL;
                        updateButtonAppearance();
                        stopListeningPulse();
                        String currentText = etMessage.getText().toString().trim();
                        if (!currentText.isEmpty()) {
                            Toast.makeText(ChatActivity.this, "语音识别结束", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
            // 异步加载模型
            voskRecognizer.initModel();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create VoskSpeechRecognizer", e);
            voskRecognizer = null;
        }
    }

    private void startVoskListening() {
        if (voskRecognizer == null || isVoskListening) return;
        if (!voskRecognizer.isModelReady()) {
            Toast.makeText(this, "语音模型正在加载中...", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            voskBaseText = etMessage.getText().toString().trim();
            if (!voskBaseText.isEmpty()) {
                voskBaseText += " ";
            }
            voskRecognizer.startListening();
            isVoskListening = true;
            buttonState = STATE_LISTENING;
            updateButtonAppearance();
            startListeningPulse();
        } catch (Exception e) {
            isVoskListening = false;
            Toast.makeText(this, "启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    private void stopVoskListening() {
        if (voskRecognizer == null || !isVoskListening) return;
        try {
            voskRecognizer.stopListening();
        } catch (Exception e) {
            // ignore
        } finally {
            isVoskListening = false;
            voskBaseText = "";
            if (isInProgress) {
                buttonState = STATE_SENDING;
            } else {
                buttonState = STATE_NORMAL;
            }
            updateButtonAppearance();
            stopListeningPulse();
        }
    }

    private void startListeningPulse() {
        stopListeningPulse();
        pulseAnimator = android.animation.ObjectAnimator.ofFloat(btnSend, "alpha", 1f, 0.4f);
        pulseAnimator.setDuration(600);
        pulseAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        pulseAnimator.start();
    }

    private void stopListeningPulse() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        if (btnSend != null) {
            btnSend.setAlpha(1f);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopListeningPulse();
        stopVoskListening();
        if (voskRecognizer != null) {
            voskRecognizer.destroy();
            voskRecognizer = null;
        }
    }
}
