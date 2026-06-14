package net.wsdjeg.nova;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
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
    
    // 位置恢复：使用 StableKey 作为锚点
    // 原因：
    //   1. messages 列表包含不可见消息（tool 类型），索引不等于可见位置
    //   2. created 不唯一（assistant 消息拆分为多个 visibleItem 时共享 created）
    //   3. StableKey 基于 tool_call.id / serverIndex，全局唯一稳定
    private String anchorStableKey = null;    // 锚点 visibleItem 的 stableKey
    private int offsetToRestore = 0;          // 锚点的视觉偏移
    // 程序化滚动期间禁止 onScrolled 更新 anchor，避免锚点漂移
    private boolean isRestoringPosition = false;
    
    // 加载更早消息的状态标志
    private boolean isLoadingOlder = false;
    private long lastLoadTriggerTime = 0;
    
    // 消息指纹缓存（格式：created:role:toolcallid:content）
    private Set<String> messageFingerprints = new HashSet<>();

    
    // Pending 消息：存储正在发送的消息内容，等待服务端确认（用于UI显示）
    private Map<String, Long> pendingMessages = new HashMap<>();
    
    // Bug 2 修复：消息池等待集合
    // 存储已成功推送到服务端消息池但还没被AI处理的消息
    // 因为消息池最多5秒才处理，期间需要保持"停止"按钮状态
    private Set<String> messagesInPool = new HashSet<>();
    
    private boolean userAtBottom = true;
    // 用户是否正在主动滚动（DRAGGING 或 SETTLING）
    // 在此期间应跳过定时刷新，避免破坏滚动惯性 / 闪烁
    private boolean isUserScrolling = false;
    // 贴底校正监听器（在最后一项布局尺寸变化时触发，配合 Markdown 异步测量）
    private View.OnLayoutChangeListener bottomAlignWatcher = null;
    private long bottomAlignDeadline = 0L;
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
        adapter.notifyDataSetChangedWithUpdate();
        refreshSessionStatus(() -> loadMessagesPage());
        startAutoRefresh();
    }
    
    /**
     * 从 ChatMessage 创建 Message 对象
     * 正确处理 error 字段、tool_calls、tool_call_state 和 serverIndex
     *
     * @param msg API 返回的消息对象
     * @param serverIndex 该消息在服务端的 1-indexed 索引（用于稳定 key）
     */
    private Message createMessageFromChatMessage(ApiClient.ChatMessage msg, int serverIndex) {
        Message message;
        
        // 如果有 error 字段，创建错误消息
        if (msg.error != null && !msg.error.isEmpty()) {
            message = new Message(msg.error, msg.created);
        } else {
            // 创建基础消息
            message = new Message(msg.content, msg.role, msg.created);
            
            // 设置 tool_calls（assistant 消息中的工具调用请求）
            if (msg.toolCalls != null && !msg.toolCalls.isEmpty()) {
                message.setToolCalls(msg.toolCalls);
                Log.d(TAG, "createMessageFromChatMessage: set toolCalls=" + msg.toolCalls.size() + " for role=" + msg.role);
            }
            
            // 设置 tool_call_state（tool 消息中的工具状态）
            if (msg.toolCallState != null) {
                message.setToolName(msg.toolCallState.name);
                message.setToolError(msg.toolCallState.error);
            }
            
            // 设置 tool_call_id（tool 消息引用 ToolCall 的 ID）
            if (msg.toolCallId != null && !msg.toolCallId.isEmpty()) {
                message.setToolCallId(msg.toolCallId);
            }
        }
        
        // 设置服务端索引（用于 stableKey 计算）
        if (serverIndex > 0) {
            message.setServerIndex(serverIndex);
        }
        
        return message;
    }
    
    /**
     * 兼容旧调用：不传 serverIndex 时设为 -1（仅用于 pending 等本地消息）
     */
    private Message createMessageFromChatMessage(ApiClient.ChatMessage msg) {
        return createMessageFromChatMessage(msg, -1);
    }
    
    /**
     * 获取消息的唯一标识（用于指纹缓存）
    /**
     * 生成消息指纹（格式：created:role:toolcallid:content）
     * 用于消息去重，避免仅靠 created 时间戳导致误判
     */
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
                
                // 更新位置记录（用于刷新后恢复）
                // 关键：只在用户主动滚动时更新 anchor，
                // 程序化滚动（scrollToPositionWithOffset / smoothScrollToPosition）
                // 或 notifyDataSetChanged 引起的布局抖动期间不更新，
                // 避免锚点被覆盖导致刷新后跳到错误位置。
                if (firstVisible >= 0 && !isRestoringPosition) {
                    int scrollState = recyclerView.getScrollState();
                    boolean userDriven = (scrollState == RecyclerView.SCROLL_STATE_DRAGGING
                            || scrollState == RecyclerView.SCROLL_STATE_SETTLING);
                    if (userDriven) {
                        String key = adapter.getStableKeyAt(firstVisible);
                        if (key != null) {
                            anchorStableKey = key;
                            View firstChild = lm.findViewByPosition(firstVisible);
                            if (firstChild != null) {
                                offsetToRestore = firstChild.getTop();
                            }
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
                
                // 维护 isUserScrolling 状态：仅 DRAGGING/SETTLING 视为用户正在滚动
                isUserScrolling = (newState == RecyclerView.SCROLL_STATE_DRAGGING
                        || newState == RecyclerView.SCROLL_STATE_SETTLING);
                
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
        // 处理返回按钮点击
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
    
    /**
     * 从 SessionSettingsActivity 返回的结果更新会话信息
     */
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
                    // 仍然继续排队下一次刷新
                    refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
                    return;
                }
                // 用户正在主动滚动且不在底部时，跳过本次刷新
                // 避免拖动惯性中途被 dispatchUpdates 打断、或刷新触发的高度变化造成画面抖动
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
                    
                    // 按钮状态逻辑：
                    // 1. 服务端 in_progress=true → 显示停止按钮
                    // 2. 服务端 in_progress=false → 清除 messagesInPool，显示发送按钮
                    if (isInProgress) {
                        setButtonStateSending();
                    } else {
                        // 服务端已完成处理，清除消息池并恢复按钮
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
    /**
     * 获取新消息并保持位置
     */
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
                        // 该消息在服务端的 1-indexed 索引
                        int serverIndex = sinceIndex + i;
                        
                        // 详细日志
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

                        
                        // 只对普通用户消息检查 pending 替换（错误消息不需要）
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
                    
                    adapter.notifyDataSetChangedWithUpdate();
                    
                    if (addedNew) {
                        if (userAtBottom) {
                            scrollToBottomSmooth();
                        } else {
                            // 用户不在底部时，恢复之前保存的位置
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
                toRemove.add(entry.getKey());  // 使用 getKey() 方法
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
     * 保存当前滚动位置（使用 stableKey 作为锚点）
     */
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
    
    /**
     * 恢复滚动位置
     * 
     * 关键修复：
     * 1. 快照 anchorStableKey 和 offsetToRestore，防止 post 期间被
     *    onScrolled（notifyDataSetChanged 引起的布局抖动）覆盖。
     * 2. 设置 isRestoringPosition 标志，恢复期间忽略 onScrolled 回调，
     *    避免 scrollToPositionWithOffset 自身触发的 onScrolled 反向修改 anchor。
     * 3. 使用 stableKey 而非 created：
     *    - tool_call.id 全局唯一稳定
     *    - serverIndex 索引稳定
     *    - 避免同一 created 多个 visibleItem 时定位错误
     */
    private void restoreScrollPosition() {
        if (anchorStableKey == null) return;
        
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return;
        
        // 快照（防止异步执行期间被覆盖）
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
                // 等下一帧让 layout 完成，再解除保护
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
                    
                    // 倒序遍历但保留正确的 serverIndex 计算
                    // chatMessages[i] 的 serverIndex = newSince + i
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
                    if (chatMessages.isEmpty()) return;
                    
                    // 找到最新的可显示消息（非 tool，有 content 或 error）
                    ApiClient.ChatMessage latestFiltered = null;
                    int latestSvrIdx = -1;
                    for (int i = chatMessages.size() - 1; i >= 0; i--) {
                        ApiClient.ChatMessage msg = chatMessages.get(i);
                        // 错误消息也可以是最新消息
                        if (msg.error != null && !msg.error.isEmpty()) {
                            latestFiltered = msg;
                            latestSvrIdx = sinceIndex + i;
                            break;
                        }
                        // 或者有 content 且不是 tool
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
                        
                        // 检查内容是否变化
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
    
    /**
     * 平滑滚动到底部 —— 贴底稳定算法
     *
     * 痛点:
     *   Markdown 富文本（Markwon）在 setText 后会异步测量布局，导致最后一项的高度
     *   在 onBindViewHolder 之后还会发生变化。固定 100ms 兜底常常追不上变化，
     *   出现"贴不到底"或"反复跳"的现象。
     *
     * 算法:
     *   1. 立即让最后一项底部贴 RecyclerView 底部 (alignLastItemToBottom)
     *   2. 在 RecyclerView 上注册 OnLayoutChangeListener，监听 600ms 内任何布局变化:
     *      - 子 View 高度由于 Markdown 异步测量发生改变 → 触发布局
     *      - RecyclerView 自身因键盘 / 父容器变化触发布局
     *      只要不在底部就重新校正一次，直到 deadline 自动注销
     *   3. 用户在此期间任何主动滚动会撤销监听，避免与用户操作冲突
     *
     * 这种"事件驱动 + 时间窗口"的策略，比单点 postDelayed(100ms) 稳得多。
     */
    private void scrollToBottomSmooth() {
        if (rvMessages == null || adapter == null) return;
        int itemCount = adapter.getItemCount();
        if (itemCount == 0) return;

        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return;

        int lastPosition = itemCount - 1;

        // 第一步：立即跳到最后一项顶部（视图占位）
        lm.scrollToPositionWithOffset(lastPosition, 0);

        // 第二步：下一帧根据实测高度精确贴底
        rvMessages.post(() -> alignLastItemToBottom());

        // 第三步：注册持续校正监听器，覆盖 Markdown 异步测量窗口（600ms）
        installBottomAlignWatcher(600L);
    }

    /**
     * 安装贴底校正监听器，在指定时间窗口内持续校正
     * 重复调用会自动延长 deadline 并复用同一监听器
     */
    private void installBottomAlignWatcher(long durationMs) {
        if (rvMessages == null) return;

        long now = System.currentTimeMillis();
        bottomAlignDeadline = Math.max(bottomAlignDeadline, now + durationMs);

        if (bottomAlignWatcher != null) {
            // 已经安装，仅延长 deadline
            return;
        }

        bottomAlignWatcher = new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b,
                                       int ol, int ot, int or_, int ob) {
                // 超时自动注销
                if (System.currentTimeMillis() > bottomAlignDeadline) {
                    uninstallBottomAlignWatcher();
                    return;
                }
                // 用户在主动滚动，立即让位
                if (isUserScrolling) {
                    uninstallBottomAlignWatcher();
                    return;
                }
                // 用户已离开底部（说明用户上滑了），不再强制贴底
                if (!userAtBottom) {
                    uninstallBottomAlignWatcher();
                    return;
                }
                // 高度真的变了再校正，避免无意义工作
                int newH = b - t;
                int oldH = ob - ot;
                if (newH != oldH || !isLastItemFullyAtBottom()) {
                    alignLastItemToBottom();
                }
            }
        };
        rvMessages.addOnLayoutChangeListener(bottomAlignWatcher);

        // 兜底：到达 deadline 一定要注销，避免内存泄漏
        rvMessages.postDelayed(this::uninstallBottomAlignWatcher, durationMs + 50);
    }

    private void uninstallBottomAlignWatcher() {
        if (bottomAlignWatcher != null && rvMessages != null) {
            rvMessages.removeOnLayoutChangeListener(bottomAlignWatcher);
        }
        bottomAlignWatcher = null;
    }

    /**
     * 检查最后一项是否已经"完全贴底"（用于决定是否需要再次校正）
     */
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
        // 允许 1px 误差
        return Math.abs(lastChild.getBottom() - recyclerBottom) <= 1;
    }

    /**
     * 让最后一项的底部与 RecyclerView 的底部对齐
     */
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
            // offset 为负表示最后一项太长（超出屏幕），此时让其底部贴合
            lm.scrollToPositionWithOffset(pos, offset);
        } else {
            // 子 View 还未创建，先跳转到位置，下一帧再校正
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
        // 预览按钮现在可用，保持可见
        MenuItem previewItem = menu.findItem(R.id.action_preview);
        if (previewItem != null) {
            previewItem.setVisible(true);
        }
    }
    
    private void openPreviewUrl() {
        if (currentSessionId == null) return;
        // 使用当前账号的 URL
        if (currentAccount == null) {
            Toast.makeText(this, "账号信息无效", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = currentAccount.getUrl();
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "请先配置服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }
        // API: GET /session?id={session_id} - HTML preview
        String previewUrl = url + "/session?id=" + currentSessionId;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(previewUrl));
        startActivity(intent);
    }
    
    private void openSessionSettings() {
        Intent intent = new Intent(this, SessionSettingsActivity.class);
        intent.putExtra(SessionSettingsActivity.EXTRA_SESSION_ID, currentSessionId);
        intent.putExtra(SessionSettingsActivity.EXTRA_ACCOUNT_ID, accountId);
        // 传递当前的 provider/model/cwd/title 作为初始值
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
    
    /**
     * 重试当前会话的最后一次 AI 回复
     * 调用 POST /session/:id/retry API
     */
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
            // 从 SessionSettingsActivity 返回的结果更新显示
            String newProvider = data.getStringExtra(SessionSettingsActivity.RESULT_PROVIDER);
            String newModel = data.getStringExtra(SessionSettingsActivity.RESULT_MODEL);
            String newCwd = data.getStringExtra(SessionSettingsActivity.RESULT_CWD);
            String newTitle = data.getStringExtra(SessionSettingsActivity.RESULT_TITLE);
            
            updateSessionInfoFromResult(newProvider, newModel, newCwd, newTitle);
        }
    }
}

