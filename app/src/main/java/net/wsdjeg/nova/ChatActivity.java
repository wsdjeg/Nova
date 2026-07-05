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
    
    private static final long MIN_LOAD_INTERVAL_MS = 300;
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
    
    private int totalMessageCount = 0;
    private int currentSince = 0;
    private int processedServerMessageCount = 0;
    
    private boolean isInitialLoadComplete = false;
    
    private int buttonState = STATE_NORMAL;
    private boolean isInProgress = false;
    private Menu chatMenu;
    
    private String anchorStableKey = null;
    private int offsetToRestore = 0;
    private boolean isRestoringPosition = false;
    
    private boolean isLoadingOlder = false;
    private long lastLoadTriggerTime = 0;
    
    // [FIX] 并发请求保护：防止 fetchNewMessagesAndRestorePosition 被重复调用
    private boolean isFetchingNewMessages = false;
    
    private Set<String> messageFingerprints = new HashSet<>();
    private Map<String, Long> pendingMessages = new HashMap<>();
    private Set<String> messagesInPool = new HashSet<>();
    
    private boolean userAtBottom = true;
    private boolean isUserScrolling = false;
    private View.OnLayoutChangeListener bottomAlignWatcher = null;
    private long bottomAlignDeadline = 0L;
    private int lastKeyboardHeight = 0;
    private boolean isKeyboardVisible = false;
    private Handler keyboardHandler = new Handler(Looper.getMainLooper());
    private Runnable keyboardScrollRunnable = null;
    private int lastRecyclerViewHeight = -1;
    private long lastKeyboardScrollTime = 0;
    private int accumulatedHeightDelta = 0;

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
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        
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
            // [FIX] 用户主动回到底部，立即触发刷新追回漏掉的新消息
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
        
        initVoskRecognizer();
        
        messages.add(new Message("正在加载消息...", false));
        adapter.notifyDataSetChangedWithUpdate();
        refreshSessionStatus(() -> loadMessagesPage());
        startAutoRefresh();
    }
    
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
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (isRestoringPosition || (keyboardScrollRunnable != null)) return;
                
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                int lastVisible = lm.findLastVisibleItemPosition();
                int total = lm.getItemCount();
                
                boolean wasAtBottom = userAtBottom;
                userAtBottom = (total - 1 - lastVisible) <= BOTTOM_THRESHOLD;
                
                if (userAtBottom) {
                    isUserScrolling = false;
                    fabScrollBottom.hide();
                    if (!wasAtBottom) {
                        // [FIX] 用户从上方滚回底部，主动触发一次刷新
                        refreshMessages();
                    }
                } else {
                    isUserScrolling = true;
                    fabScrollBottom.show();
                }
                
                int firstVisible = lm.findFirstVisibleItemPosition();
                if (firstVisible <= 1 && firstVisible >= 0) {
                    triggerLoadOlder();
                }
            }
        });
    }

    
    private void setupKeyboardListener() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            
            int keyboardHeight = ime.bottom - systemBars.bottom;
            if (keyboardHeight < 0) keyboardHeight = 0;
            
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), ime.bottom);
            
            if (keyboardHeight > 50) {
                if (!isKeyboardVisible) {
                    isKeyboardVisible = true;
                    Log.d(TAG, "Keyboard opened, height=" + keyboardHeight);
                }
                scheduleKeyboardScroll(keyboardHeight);
            } else {
                if (isKeyboardVisible) {
                    isKeyboardVisible = false;
                    Log.d(TAG, "Keyboard closed");
                    if (keyboardScrollRunnable != null) {
                        keyboardHandler.removeCallbacks(keyboardScrollRunnable);
                        keyboardScrollRunnable = null;
                    }
                }
            }
            
            return WindowInsetsCompat.CONSUMED;
        });
        
        rvMessages.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int newHeight = rvMessages.getHeight();
                if (lastRecyclerViewHeight > 0 && newHeight != lastRecyclerViewHeight) {
                    if (isKeyboardVisible) {
                        scheduleKeyboardScroll(Math.abs(newHeight - lastRecyclerViewHeight));
                    }
                }
                lastRecyclerViewHeight = newHeight;
            }
        });
    }
    
    private void scheduleKeyboardScroll(int keyboardHeight) {
        if (keyboardScrollRunnable != null) {
            keyboardHandler.removeCallbacks(keyboardScrollRunnable);
        }
        
        long now = System.currentTimeMillis();
        if (now - lastKeyboardScrollTime < MIN_KEYBOARD_SCROLL_INTERVAL_MS) {
            return;
        }
        lastKeyboardScrollTime = now;
        
        keyboardScrollRunnable = () -> {
            if (userAtBottom && isKeyboardVisible) {
                rvMessages.post(() -> {
                    LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
                    if (lm != null) {
                        int lastPos = lm.findLastVisibleItemPosition();
                        int total = lm.getItemCount();
                        if (total > 0 && (total - 1 - lastPos) <= BOTTOM_THRESHOLD + 2) {
                            rvMessages.smoothScrollToPosition(total - 1);
                        }
                    }
                });
            }
        };
        keyboardHandler.postDelayed(keyboardScrollRunnable, 30);
    }

    
    private void showLoadMoreHint() {
        runOnUiThread(() -> {
            tvLoadMore.setVisibility(View.VISIBLE);
            tvLoadMore.setText("正在加载更多消息...");
        });
    }
    
    private void hideLoadMoreHint() {
        runOnUiThread(() -> tvLoadMore.setVisibility(View.GONE));
    }
    
    private void restoreDraft() {
        String draft = settingsManager.getDraft(currentSessionId);
        if (draft != null && !draft.isEmpty()) {
            etMessage.setText(draft);
            etMessage.setSelection(draft.length());
        }
    }
    
    private void saveDraft() {
        String text = etMessage.getText().toString().trim();
        settingsManager.saveDraft(currentSessionId, text);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        startAutoRefresh();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        saveDraft();
        stopAutoRefresh();
        if (isVoskListening) {
            stopVoskListening();
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_menu, menu);
        chatMenu = menu;
        updateMenuVisibility();
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_retry) {
            retrySession();
            return true;
        } else if (id == R.id.action_clear) {
            clearSession();
            return true;
        } else if (id == R.id.action_delete) {
            deleteSession();
            return true;
        } else if (id == R.id.action_settings) {
            openSessionSettings();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    
    private void updateSessionInfo(String provider, String model, String cwd) {
        currentProvider = provider;
        currentModel = model;
        
        if (cwd != null && !cwd.isEmpty()) {
            tvSessionPath.setVisibility(View.VISIBLE);
            tvSessionPath.setText(cwd);
        } else {
            tvSessionPath.setVisibility(View.GONE);
        }
        
        updateSessionInfoDisplay();
    }
    
    private void updateSessionInfoDisplay() {
        List<String> parts = new ArrayList<>();
        if (currentProvider != null && !currentProvider.isEmpty()) {
            parts.add(currentProvider);
        }
        if (currentModel != null && !currentModel.isEmpty()) {
            parts.add(currentModel);
        }
        if (parts.isEmpty()) {
            tvSessionInfo.setVisibility(View.GONE);
        } else {
            tvSessionInfo.setVisibility(View.VISIBLE);
            tvSessionInfo.setText(String.join(" | ", parts));
        }
    }
    
    private void updateSessionInfoFromResult(ApiClient.SessionResult result) {
        updateSessionInfo(result.provider, result.model, result.cwd);
        isInProgress = result.inProgress;
        if (isInProgress) {
            setButtonStateSending();
        } else {
            setButtonStateNormal();
        }
        updateMenuVisibility();
    }
    
    private void startAutoRefresh() {
        if (refreshHandler == null) {
            refreshHandler = new Handler(Looper.getMainLooper());
        }
        if (refreshRunnable == null) {
            refreshRunnable = new Runnable() {
                @Override
                public void run() {
                    refreshMessages();
                    refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
                }
            };
        }
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }
    
    private void stopAutoRefresh() {
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }

    
    private void refreshMessages() {
        if (!isInitialLoadComplete) {
            return;
        }
        
        checkLastMessageUpdate();
        
        if (isInProgress) {
            refreshSessionStatusForIncrementalRefresh();
        }
    }
    
    private void refreshSessionStatusForIncrementalRefresh() {
        // [FIX] 用户不在底部时跳过增量刷新，避免位置跳动
        if (!userAtBottom) {
            return;
        }
        
        apiClient.getSessionInfo(currentSessionId, new ApiClient.ApiCallback<ApiClient.SessionResult>() {
            @Override
            public void onSuccess(ApiClient.SessionResult result) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    
                    updateSessionInfoFromResult(result);
                    
                    int oldCount = totalMessageCount;
                    totalMessageCount = result.messages;
                    Log.d(TAG, "Incremental refresh: oldCount=" + oldCount + " newCount=" + totalMessageCount);
                    
                    if (oldCount == 0 || (result.messages > oldCount)) {
                        // [FIX] 只在用户在底部时才获取新消息
                        if (userAtBottom) {
                            fetchNewMessagesAndRestorePosition();
                        }
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Incremental refresh error: " + error);
            }
        });
    }

    
    private void fetchNewMessagesAndRestorePosition() {
        // [FIX] 并发请求保护：防止重复调用
        if (isFetchingNewMessages) {
            Log.d(TAG, "fetchNewMessagesAndRestorePosition: already in progress, skipping");
            return;
        }
        isFetchingNewMessages = true;
        
        // [FIX] 移除原先的 isUserScrolling 检查，改为直接使用 userAtBottom
        // 原先 isUserScrolling && !userAtBottom 的逻辑在用户向上滚再滚回底部时会误判
        // 现在直接以 userAtBottom 为准
        if (!userAtBottom) {
            Log.d(TAG, "fetchNewMessagesAndRestorePosition: user not at bottom, skipping");
            isFetchingNewMessages = false;
            return;
        }
        
        Log.d(TAG, "fetchNewMessagesAndRestorePosition: currentSince=" + currentSince + " totalCount=" + totalMessageCount);
        
        apiClient.getMessages(currentSince, totalMessageCount, new ApiClient.ApiCallback<List<ApiClient.ChatMessage>>() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> newMessages) {
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        isFetchingNewMessages = false;
                        return;
                    }
                    
                    Log.d(TAG, "fetchNewMessagesAndRestorePosition: got " + newMessages.size() + " messages");
                    
                    if (newMessages.isEmpty()) {
                        isFetchingNewMessages = false;
                        return;
                    }
                    
                    boolean shouldScrollToBottom = userAtBottom;
                    
                    boolean added = false;
                    int lastServerIndex = currentSince;
                    
                    for (ApiClient.ChatMessage msg : newMessages) {
                        String fingerprint = getMessageFingerprint(msg);
                        if (messageFingerprints.contains(fingerprint)) {
                            continue;
                        }
                        messageFingerprints.add(fingerprint);
                        
                        int pendingIdx = findPendingMessageIndex(msg);
                        if (pendingIdx >= 0) {
                            String key = "pending_" + msg.created;
                            messages.set(pendingIdx, createMessageFromChatMessage(msg));
                            pendingMessages.remove(key);
                            added = true;
                            Log.d(TAG, "Updated pending message at index " + pendingIdx);
                        } else {
                            messages.add(createMessageFromChatMessage(msg));
                            added = true;
                        }
                        
                        if (msg.serverIndex > lastServerIndex) {
                            lastServerIndex = msg.serverIndex;
                        }
                    }
                    
                    if (added) {
                        adapter.notifyDataSetChangedWithUpdate();
                        if (shouldScrollToBottom) {
                            rvMessages.post(() -> {
                                if (!isFinishing()) {
                                    rvMessages.scrollToPosition(messages.size() - 1);
                                }
                            });
                        }
                    }
                    
                    currentSince = lastServerIndex + 1;
                    if (currentSince > totalMessageCount) {
                        currentSince = totalMessageCount;
                    }
                    
                    isFetchingNewMessages = false;
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "fetchNewMessagesAndRestorePosition error: " + error);
                isFetchingNewMessages = false;
            }
        });
    }

    
    private int findPendingMessageIndex(ApiClient.ChatMessage msg) {
        if (msg.role == null || msg.content == null) return -1;
        if (!msg.role.equals("user")) return -1;
        
        for (Map.Entry<String, Long> entry : pendingMessages.entrySet()) {
            long elapsed = System.currentTimeMillis() - entry.getValue();
            if (elapsed < 30000) {
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Message m = messages.get(i);
                    if (m.getContent() != null && m.getContent().equals(msg.content) && m.isPending()) {
                        return i;
                    }
                }
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
            }
        }
        for (String key : toRemove) {
            pendingMessages.remove(key);
        }
    }
    
    private boolean canLoadMore() {
        return totalMessageCount > 0 && currentSince > 0 && !isLoadingOlder;
    }
    
    private void triggerLoadOlder() {
        if (!canLoadMore()) return;
        
        long now = System.currentTimeMillis();
        if (now - lastLoadTriggerTime < MIN_LOAD_INTERVAL_MS) return;
        lastLoadTriggerTime = now;
        
        loadOlderMessages();
    }
    
    private void saveScrollPosition() {
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return;
        
        int firstVisible = lm.findFirstVisibleItemPosition();
        if (firstVisible < 0 || firstVisible >= messages.size()) return;
        
        Message firstMsg = messages.get(firstVisible);
        if (firstMsg.getServerIndex() > 0) {
            anchorStableKey = String.valueOf(firstMsg.getServerIndex());
        } else {
            anchorStableKey = firstMsg.getCreated() + ":" + firstMsg.getContent().hashCode();
        }
        
        View firstChild = lm.getChildAt(0);
        if (firstChild != null) {
            offsetToRestore = firstChild.getTop();
        } else {
            offsetToRestore = 0;
        }
    }
    
    private void restoreScrollPosition() {
        if (anchorStableKey == null || messages.isEmpty()) {
            isRestoringPosition = false;
            return;
        }
        
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) {
            isRestoringPosition = false;
            return;
        }
        
        int targetPosition = -1;
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            String key;
            if (m.getServerIndex() > 0) {
                key = String.valueOf(m.getServerIndex());
            } else {
                key = m.getCreated() + ":" + m.getContent().hashCode();
            }
            if (anchorStableKey.equals(key)) {
                targetPosition = i;
                break;
            }
        }
        
        if (targetPosition >= 0) {
            final int pos = targetPosition;
            rvMessages.post(() -> {
                lm.scrollToPositionWithOffset(pos, offsetToRestore);
                rvMessages.post(() -> isRestoringPosition = false);
            });
        } else {
            isRestoringPosition = false;
        }
        
        anchorStableKey = null;
    }

    
    private void loadMessagesPage() {
        showLoadMoreHint();
        
        apiClient.getSessionInfo(currentSessionId, new ApiClient.ApiCallback<ApiClient.SessionResult>() {
            @Override
            public void onSuccess(ApiClient.SessionResult result) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    
                    updateSessionInfoFromResult(result);
                    totalMessageCount = result.messages;
                    
                    int fetchCount = Math.min(PAGE_SIZE, totalMessageCount);
                    int since = totalMessageCount - fetchCount;
                    if (since < 0) since = 0;
                    
                    currentSince = totalMessageCount;
                    
                    apiClient.getMessages(since, fetchCount, new ApiClient.ApiCallback<List<ApiClient.ChatMessage>>() {
                        @Override
                        public void onSuccess(List<ApiClient.ChatMessage> serverMessages) {
                            runOnUiThread(() -> {
                                if (isFinishing()) return;
                                
                                messages.clear();
                                messageFingerprints.clear();
                                messagesInPool.clear();
                                
                                for (ApiClient.ChatMessage msg : serverMessages) {
                                    String fingerprint = getMessageFingerprint(msg);
                                    messageFingerprints.add(fingerprint);
                                    messages.add(createMessageFromChatMessage(msg));
                                }
                                
                                adapter.notifyDataSetChangedWithUpdate();
                                hideLoadMoreHint();
                                isInitialLoadComplete = true;
                                
                                rvMessages.post(() -> {
                                    if (!isFinishing() && !messages.isEmpty()) {
                                        rvMessages.scrollToPosition(messages.size() - 1);
                                    }
                                });
                            });
                        }
                        
                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> {
                                if (isFinishing()) return;
                                hideLoadMoreHint();
                                isInitialLoadComplete = true;
                                if (!messages.isEmpty()) {
                                    messages.get(0).setContent("加载消息失败: " + error);
                                    adapter.notifyDataSetChangedWithUpdate();
                                }
                                Toast.makeText(ChatActivity.this, "加载消息失败: " + error, Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    hideLoadMoreHint();
                    isInitialLoadComplete = true;
                    if (!messages.isEmpty()) {
                        messages.get(0).setContent("获取会话信息失败: " + error);
                        adapter.notifyDataSetChangedWithUpdate();
                    }
                });
            }
        });
    }

    
    private void loadOlderMessages() {
        if (isLoadingOlder) return;
        
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return;
        
        int firstVisible = lm.findFirstVisibleItemPosition();
        if (firstVisible < 0) return;
        
        Message firstMsg = messages.get(firstVisible);
        int firstServerIdx = firstMsg.getServerIndex();
        if (firstServerIdx <= 0) return;
        
        isLoadingOlder = true;
        showLoadMoreHint();
        saveScrollPosition();
        
        int fetchCount = Math.min(PAGE_SIZE, firstServerIdx);
        int since = firstServerIdx - fetchCount;
        if (since < 0) since = 0;
        
        isRestoringPosition = true;
        
        apiClient.getMessages(since, fetchCount, new ApiClient.ApiCallback<List<ApiClient.ChatMessage>>() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> olderMessages) {
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        isLoadingOlder = false;
                        isRestoringPosition = false;
                        return;
                    }
                    
                    Log.d(TAG, "loadOlderMessages: got " + olderMessages.size() + " messages");
                    
                    List<Message> newMessages = new ArrayList<>();
                    int insertCount = 0;
                    
                    for (ApiClient.ChatMessage msg : olderMessages) {
                        String fingerprint = getMessageFingerprint(msg);
                        if (messageFingerprints.contains(fingerprint)) {
                            continue;
                        }
                        messageFingerprints.add(fingerprint);
                        newMessages.add(createMessageFromChatMessage(msg));
                        insertCount++;
                    }
                    
                    if (insertCount > 0) {
                        messages.addAll(0, newMessages);
                        adapter.notifyDataSetChangedWithUpdate();
                        restoreScrollPosition();
                    } else {
                        isRestoringPosition = false;
                    }
                    
                    hideLoadMoreHint();
                    isLoadingOlder = false;
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    hideLoadMoreHint();
                    isLoadingOlder = false;
                    isRestoringPosition = false;
                    Toast.makeText(ChatActivity.this, "加载更多消息失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    
    private void checkLastMessageUpdate() {
        // [FIX] 如果正在获取新消息，跳过本次检查，避免重复请求
        if (isFetchingNewMessages) {
            return;
        }
        
        if (messages.isEmpty()) return;
        
        Message lastMsg = messages.get(messages.size() - 1);
        String lastCreated = lastMsg.getCreated();
        
        if (lastCreated == null || lastCreated.isEmpty()) return;
        
        apiClient.getLastMessageTime(currentSessionId, new ApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String lastTime) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    
                    Log.d(TAG, "checkLastMessageUpdate: local=" + lastCreated + " server=" + lastTime);
                    
                    if (lastTime != null && !lastTime.isEmpty() && !lastTime.equals(lastCreated)) {
                        // [FIX] 只在用户在底部时才获取新消息
                        if (userAtBottom) {
                            fetchNewMessagesAndRestorePosition();
                        }
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                // ignore
            }
        });
    }
    
    private void reloadMessages() {
        isInitialLoadComplete = false;
        currentSince = 0;
        totalMessageCount = 0;
        messageFingerprints.clear();
        pendingMessages.clear();
        messagesInPool.clear();
        loadMessagesPage();
    }
    
    private void scrollToBottomSmooth() {
        if (messages.isEmpty()) return;
        rvMessages.smoothScrollToPosition(messages.size() - 1);
        installBottomAlignWatcher();
    }

    
    private void installBottomAlignWatcher() {
        uninstallBottomAlignWatcher();
        
        bottomAlignDeadline = System.currentTimeMillis() + 1500;
        
        bottomAlignWatcher = new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (System.currentTimeMillis() > bottomAlignDeadline) {
                    uninstallBottomAlignWatcher();
                    return;
                }
                if (userAtBottom) {
                    alignLastItemToBottom();
                } else {
                    uninstallBottomAlignWatcher();
                }
            }
        };
        rvMessages.addOnLayoutChangeListener(bottomAlignWatcher);
    }
    
    private void uninstallBottomAlignWatcher() {
        if (bottomAlignWatcher != null) {
            rvMessages.removeOnLayoutChangeListener(bottomAlignWatcher);
            bottomAlignWatcher = null;
        }
    }
    
    private boolean isLastItemFullyAtBottom() {
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return false;
        int lastPos = lm.findLastCompletelyVisibleItemPosition();
        int total = lm.getItemCount();
        if (total == 0) return false;
        return lastPos == total - 1;
    }
    
    private void alignLastItemToBottom() {
        if (messages.isEmpty()) return;
        rvMessages.scrollToPosition(messages.size() - 1);
    }

    
    private void refreshSessionStatus(Runnable onComplete) {
        apiClient.getSessionInfo(currentSessionId, new ApiClient.ApiCallback<ApiClient.SessionResult>() {
            @Override
            public void onSuccess(ApiClient.SessionResult result) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    
                    updateSessionInfoFromResult(result);
                    totalMessageCount = result.messages;
                    
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    
                    isInProgress = false;
                    setButtonStateNormal();
                    
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            }
        });
    }
    
    private void updateMenuVisibility() {
        if (chatMenu == null) return;
        
        MenuItem retryItem = chatMenu.findItem(R.id.action_retry);
        MenuItem clearItem = chatMenu.findItem(R.id.action_clear);
        MenuItem deleteItem = chatMenu.findItem(R.id.action_delete);
        
        retryItem.setVisible(!isInProgress);
        clearItem.setVisible(!isInProgress);
        deleteItem.setVisible(true);
    }
    
    private void openPreviewUrl(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "无法打开链接: " + url, Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openSessionSettings() {
        Intent intent = new Intent(this, SessionSettingsActivity.class);
        intent.putExtra(EXTRA_SESSION_ID, currentSessionId);
        intent.putExtra(EXTRA_SESSION_TITLE, currentSessionTitle);
        if (currentProvider != null) intent.putExtra("provider", currentProvider);
        if (currentModel != null) intent.putExtra("model", currentModel);
        startActivityForResult(intent, REQUEST_SESSION_SETTINGS);
    }

    
    private void sendMessage() {
        String content = etMessage.getText().toString().trim();
        if (content.isEmpty()) return;
        
        etMessage.setText("");
        
        final Message pendingMsg = new Message(content, true);
        pendingMsg.setPending(true);
        String pendingKey = "pending_" + System.currentTimeMillis();
        pendingMessages.put(pendingKey, System.currentTimeMillis());
        
        messages.add(pendingMsg);
        adapter.notifyDataSetChangedWithUpdate();
        userAtBottom = true;
        fabScrollBottom.hide();
        rvMessages.scrollToPosition(messages.size() - 1);
        
        isInProgress = true;
        setButtonStateSending();
        updateMenuVisibility();
        
        Log.d(TAG, "sendMessage: content=" + content);
        
        apiClient.sendMessage(content, currentAccount.getName(), new ApiClient.ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Log.d(TAG, "sendMessage onSuccess");
                runOnUiThread(() -> {
                    isInProgress = true;
                    setButtonStateSending();
                    updateMenuVisibility();
                    
                    if (userAtBottom) {
                        fetchNewMessagesAndRestorePosition();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "sendMessage onError: " + error);
                runOnUiThread(() -> {
                    isInProgress = false;
                    setButtonStateNormal();
                    updateMenuVisibility();
                    
                    int idx = messages.indexOf(pendingMsg);
                    if (idx >= 0) {
                        pendingMsg.setPending(false);
                        pendingMsg.setSendFailed(true);
                        adapter.notifyDataSetChangedWithUpdate();
                    }
                    
                    Toast.makeText(ChatActivity.this, "发送失败: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    
    private void stopSession() {
        apiClient.stopSession(new ApiClient.ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    isInProgress = false;
                    setButtonStateNormal();
                    updateMenuVisibility();
                    addSystemMessage("已停止会话");
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
    
    private void retrySession() {
        apiClient.retrySession(new ApiClient.ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    isInProgress = true;
                    setButtonStateSending();
                    updateMenuVisibility();
                    if (userAtBottom) {
                        fetchNewMessagesAndRestorePosition();
                    }
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
        buttonState = STATE_SENDING;
        isInProgress = true;
        updateButtonAppearance();
    }
    
    private void setButtonStateNormal() {
        buttonState = STATE_NORMAL;
        isInProgress = false;
        updateButtonAppearance();
    }
    
    private void updateButtonAppearance() {
        if (buttonState == STATE_SENDING) {
            btnSend.setImageResource(R.drawable.ic_stop);
            btnSend.setColorFilter(ContextCompat.getColor(this, R.color.send_stop_bg));
        } else if (buttonState == STATE_LISTENING) {
            btnSend.setImageResource(R.drawable.ic_mic);
            btnSend.setColorFilter(Color.parseColor("#FF9800"));
        } else {
            String text = etMessage.getText().toString().trim();
            if (text.isEmpty()) {
                btnSend.setImageResource(R.drawable.ic_mic);
                btnSend.setColorFilter(null);
            } else {
                btnSend.setImageResource(R.drawable.ic_send);
                btnSend.setColorFilter(ContextCompat.getColor(this, R.color.send_button_bg));
            }
        }
    }

    
    private void clearSession() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("清除会话")
            .setMessage("确定要清除所有消息吗？")
            .setPositiveButton("确定", (d, w) -> {
                apiClient.clearSession(new ApiClient.ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        runOnUiThread(() -> {
                            messages.clear();
                            messageFingerprints.clear();
                            pendingMessages.clear();
                            messagesInPool.clear();
                            totalMessageCount = 0;
                            currentSince = 0;
                            adapter.notifyDataSetChangedWithUpdate();
                            Toast.makeText(ChatActivity.this, "已清除", Toast.LENGTH_SHORT).show();
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(ChatActivity.this, "清除失败: " + error, Toast.LENGTH_SHORT).show();
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
            .setMessage("确定要删除此会话吗？")
            .setPositiveButton("确定", (d, w) -> {
                apiClient.deleteSession(new ApiClient.ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        runOnUiThread(() -> {
                            sessionManager.deleteSession(currentSessionId);
                            Toast.makeText(ChatActivity.this, "已删除", Toast.LENGTH_SHORT).show();
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
        Message sysMsg = new Message(text, false);
        sysMsg.setRole("system");
        messages.add(sysMsg);
        adapter.notifyDataSetChangedWithUpdate();
        if (userAtBottom) {
            rvMessages.scrollToPosition(messages.size() - 1);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_SESSION_SETTINGS && resultCode == RESULT_OK) {
            String newTitle = data.getStringExtra(EXTRA_SESSION_TITLE);
            String newProvider = data.getStringExtra("provider");
            String newModel = data.getStringExtra("model");
            String newCwd = data.getStringExtra("cwd");
            
            if (newTitle != null) {
                currentSessionTitle = newTitle;
                tvSessionTitle.setText(newTitle);
            }
            updateSessionInfo(newProvider, newModel, newCwd);
        }
        
        if (requestCode == REQUEST_VOICE_INPUT && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String text = voskBaseText.isEmpty() ? results.get(0) : voskBaseText + " " + results.get(0);
                etMessage.setText(text);
                etMessage.setSelection(text.length());
            }
        }
    }

    
    private void startVoiceInput() {
        if (voskRecognizer != null) {
            startVoskListening();
            return;
        }
        
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话...");
        
        try {
            startActivityForResult(intent, REQUEST_VOICE_INPUT);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "设备不支持语音输入", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (voskRecognizer != null) {
                    startVoskListening();
                }
            } else {
                Toast.makeText(this, "需要录音权限才能使用语音输入", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void initVoskRecognizer() {
        try {
            voskRecognizer = new VoskSpeechRecognizer(this);
            voskRecognizer.setListener(new VoskSpeechRecognizer.VoskListener() {
                @Override
                public void onPartialResult(String text) {
                    runOnUiThread(() -> {
                        String display = voskBaseText.isEmpty() ? text : voskBaseText + " " + text;
                        etMessage.setText(display);
                        etMessage.setSelection(display.length());
                    });
                }
                
                @Override
                public void onFinalResult(String text) {
                    runOnUiThread(() -> {
                        voskBaseText = voskBaseText.isEmpty() ? text : voskBaseText + " " + text;
                        etMessage.setText(voskBaseText);
                        etMessage.setSelection(voskBaseText.length());
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        stopListeningPulse();
                        buttonState = STATE_NORMAL;
                        updateButtonAppearance();
                        Toast.makeText(ChatActivity.this, "语音识别错误: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to init Vosk: " + e.getMessage());
            voskRecognizer = null;
        }
    }

    
    private void startVoskListening() {
        if (voskRecognizer == null) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
            } else {
                initVoskRecognizer();
                if (voskRecognizer != null) {
                    doStartVoskListening();
                }
            }
            return;
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }
        
        doStartVoskListening();
    }
    
    private void doStartVoskListening() {
        voskBaseText = etMessage.getText().toString().trim();
        if (!voskBaseText.isEmpty() && !voskBaseText.endsWith(" ")) {
            voskBaseText += " ";
        }
        voskRecognizer.startListening();
        isVoskListening = true;
        buttonState = STATE_LISTENING;
        updateButtonAppearance();
        startListeningPulse();
    }
    
    private void stopVoskListening() {
        if (voskRecognizer != null && isVoskListening) {
            voskRecognizer.stopListening();
            isVoskListening = false;
        }
        stopListeningPulse();
        buttonState = STATE_NORMAL;
        updateButtonAppearance();
    }
    
    private void startListeningPulse() {
        btnSend.post(() -> {
            pulseAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(btnSend,
                android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.2f, 1f),
                android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.2f, 1f)
            );
            pulseAnimator.setDuration(800);
            pulseAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            pulseAnimator.start();
        });
    }
    
    private void stopListeningPulse() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        btnSend.post(() -> {
            btnSend.setScaleX(1f);
            btnSend.setScaleY(1f);
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoRefresh();
        if (voskRecognizer != null) {
            voskRecognizer.destroy();
            voskRecognizer = null;
        }
        if (keyboardScrollRunnable != null) {
            keyboardHandler.removeCallbacks(keyboardScrollRunnable);
        }
    }
}

