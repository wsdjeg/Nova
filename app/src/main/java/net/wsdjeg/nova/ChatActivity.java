package net.wsdjeg.nova;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.wsdjeg.nova.MarkwonHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 聊天界面 Activity
 * 
 * 功能：
 * 1. 消息列表显示（支持 Markdown 渲染）
 * 2. 消息发送和接收
 * 3. 自动刷新新消息
 * 4. 下拉加载更早消息
 * 5. 工具调用：assistant 消息中的 tool_calls 拆分显示
 */
public class ChatActivity extends AppCompatActivity {
    private static final String TAG = "ChatActivity";
    private static final int BOTTOM_THRESHOLD = 3;
    private static final int DEFAULT_LIMIT = 50;
    private static final long AUTO_REFRESH_INTERVAL = 3000; // 3秒

    private RecyclerView rvMessages;
    private EditText etMessage;
    private ImageButton btnSend;
    private MessageAdapter adapter;
    private List<Message> messages;
    private ApiClient apiClient;
    private String currentSessionId;
    private String currentSessionTitle;
    private String intentProvider;
    private String intentModel;
    private String intentCwd;
    
    // 消息指纹缓存（用于检测消息变化）
    // Key: created 时间戳, Value: 消息内容指纹
    private Map<Long, String> messageFingerprints = new HashMap<>();
    
    // 待确认消息缓存（用于替换 pending 消息）
    private Map<String, Long> pendingMessages = new HashMap<>();
    
    // 消息池（用于保存已加载的消息 created 值）
    private Set<Long> messagesInPool = new HashSet<>();
    
    private int currentSince = 0;
    private int totalMessageCount = 0;
    private int processedServerMessageCount = 0;
    private boolean isInitialLoadComplete = false;
    private boolean isLoadingOlder = false;
    private boolean userAtBottom = true;
    private long anchorMessageCreated = -1;
    private int offsetToRestore = 0;
    private String pendingUserMessage = null;
    
    private SessionManager sessionManager;
    private SettingsManager settingsManager;
    
    // 自动刷新
    private Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private Runnable autoRefreshRunnable;
    private boolean isAutoRefreshRunning = false;
    
    // UI 元素
    private TextView tvSessionTitle;
    private TextView tvProvider;
    private TextView tvModel;
    private LinearLayout layoutLoadMore;
    private TextView tvLoadMore;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabScrollBottom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        
        // 初始化管理器
        settingsManager = new SettingsManager(this);
        sessionManager = new SessionManager(this);
        
        // 初始化 API 客户端
        apiClient = new ApiClient(settingsManager);
        
        // 获取 Intent 参数
        Intent intent = getIntent();
        currentSessionId = intent.getStringExtra("session_id");
        currentSessionTitle = intent.getStringExtra("session_title");
        intentProvider = intent.getStringExtra("provider");
        intentModel = intent.getStringExtra("model");
        intentCwd = intent.getStringExtra("cwd");
        
        // 初始化消息列表
        messages = new ArrayList<>();
        adapter = new MessageAdapter(messages, this);
        
        // 初始化视图
        initViews();
        
        // 加载消息
        if (currentSessionId != null && !currentSessionId.isEmpty()) {
            // 设置当前会话 ID 到 API 客户端
            apiClient.setSession(currentSessionId);
            loadMessagesPage();
        } else {
            messages.add(new Message("请先在设置中配置 Session ID", false));
            adapter.notifyDataSetChangedWithUpdate();
        }
        
        // 启动自动刷新
        startAutoRefresh();
    }
    
    private void initViews() {
        // 设置 Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("");
        
        // 会话信息
        tvSessionTitle = findViewById(R.id.tv_session_title);
        tvProvider = findViewById(R.id.tv_provider);
        tvModel = findViewById(R.id.tv_model);
        
        // 消息列表
        rvMessages = findViewById(R.id.rv_messages);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(adapter);
        
        // 输入区域
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        
        // 加载更多提示
        layoutLoadMore = findViewById(R.id.layout_load_more);
        tvLoadMore = findViewById(R.id.tv_load_more);
        
        // 滚动到底部 FAB
        fabScrollBottom = findViewById(R.id.fab_scroll_bottom);
        
        // 设置发送按钮点击事件
        btnSend.setOnClickListener(v -> sendMessage());
        
        // 设置输入框回车发送
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND ||
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_DOWN)) {
                sendMessage();
                return true;
            }
            return false;
        });
        
        tvSessionTitle.setText(currentSessionTitle != null ? currentSessionTitle : currentSessionId);
        updateSessionInfo(intentProvider, intentModel, intentCwd);
        refreshSessionStatus(() -> loadMessagesPage());
        startAutoRefresh();
    }
    
    /**
     * 从 ChatMessage 创建 Message 对象
     * 正确处理 error 字段、tool_calls 和 tool_call_state
     */
    private Message createMessageFromChatMessage(ApiClient.ChatMessage msg) {
        // 如果有 error 字段，创建错误消息
        if (msg.error != null && !msg.error.isEmpty()) {
            return new Message(msg.error, msg.created);
        }
        
        // 创建基础消息
        Message message = new Message(msg.content, msg.role, msg.created);
        
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
        
        return message;
    }
    
    /**
     * 获取消息的唯一标识（用于指纹缓存）
     * 错误消息使用 error 内容
     * 有 tool_calls 的消息使用 tool_calls 的第一个 ID
     * 普通消息使用 content
     */
    private String getMessageIdentifier(ApiClient.ChatMessage msg) {
        // 错误消息使用 error 内容
        if (msg.error != null && !msg.error.isEmpty()) {
            return "error:" + msg.error;
        }
        
        // 有 tool_calls 的消息使用第一个 tool_call 的 ID
        if (msg.toolCalls != null && !msg.toolCalls.isEmpty()) {
            return "tool_call:" + msg.toolCalls.get(0).id;
        }
        
        // 普通消息使用 content
        return msg.content != null ? "content:" + msg.content : "empty";
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
                // 只要滚屏就记录位置，确保刷新后能恢复
                if (firstVisible >= 0) {
                    Object item = adapter.getVisibleItemAt(firstVisible);
                    if (item instanceof Message) {
                        anchorMessageCreated = ((Message) item).getCreated();
                    } else if (item instanceof MessageAdapter.ToolCallItem) {
                        anchorMessageCreated = ((MessageAdapter.ToolCallItem) item).getCreated();
                    }
                }
                
                // 触发加载更早消息
                if (!isLoadingOlder && firstVisible == 0 && dy < 0) {
                    triggerLoadOlder();
                }
            }
        });
    }
    
    /**
     * 更新会话信息显示
     */
    private void updateSessionInfo(String provider, String model, String cwd) {
        if (provider != null && !provider.isEmpty()) {
            tvProvider.setText(provider);
            tvProvider.setVisibility(View.VISIBLE);
        } else {
            tvProvider.setVisibility(View.GONE);
        }
        
        if (model != null && !model.isEmpty()) {
            tvModel.setText(model);
            tvModel.setVisibility(View.VISIBLE);
        } else {
            tvModel.setVisibility(View.GONE);
        }
    }
    
    /**
     * 发送消息
     */
    private void sendMessage() {
        String content = etMessage.getText().toString().trim();
        if (content.isEmpty()) return;
        
        // 清空输入框
        etMessage.setText("");
        
        // 添加用户消息到列表
        Message userMessage = new Message(content, true);
        messages.add(userMessage);
        adapter.notifyDataSetChangedWithUpdate();
        
        // 记录待确认消息
        pendingMessages.put(content, System.currentTimeMillis());
        pendingUserMessage = content;
        
        // 滚动到底部
        scrollToBottom();
        
        // 发送到服务器
        sendMessageToServer(content);
    }
    
    /**
     * 发送消息到服务器
     */
    private void sendMessageToServer(String content) {
        if (apiClient == null || currentSessionId == null) {
            showError("未配置会话");
            return;
        }
        
        apiClient.sendMessage(content, new ApiClient.MessageCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    // 移除待确认状态
                    pendingMessages.remove(content);
                    // 刷新消息列表
                    refreshSessionStatus(() -> fetchNewMessagesAndRestorePosition());
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    pendingMessages.remove(content);
                    showError(error);
                });
            }
        });
    }
    
    /**
     * 显示错误消息
     */
    private void showError(String error) {
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 滚动到底部
     */
    private void scrollToBottom() {
        if (rvMessages == null || adapter == null) return;
        int itemCount = adapter.getItemCount();
        if (itemCount == 0) return;
        
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return;
        
        lm.scrollToPosition(itemCount - 1);
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
    }
    
    /**
     * 启动自动刷新
     */
    private void startAutoRefresh() {
        if (isAutoRefreshRunning) return;
        
        isAutoRefreshRunning = true;
        autoRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isFinishing() && currentSessionId != null) {
                    refreshSessionStatus(() -> {
                        if (userAtBottom) {
                            checkLastMessageUpdate();
                        }
                    });
                }
                autoRefreshHandler.postDelayed(this, AUTO_REFRESH_INTERVAL);
            }
        };
        autoRefreshHandler.post(autoRefreshRunnable);
    }
    
    /**
     * 停止自动刷新
     */
    private void stopAutoRefresh() {
        isAutoRefreshRunning = false;
        if (autoRefreshRunnable != null) {
            autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        }
    }
    
    /**
     * 刷新会话状态
     */
    private void refreshSessionStatus(Runnable onComplete) {
        if (apiClient == null || currentSessionId == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        
        apiClient.getSessionStatus(currentSessionId, new ApiClient.StatusCallback() {
            @Override
            public void onSuccess(int serverCount, boolean isInProgress) {
                runOnUiThread(() -> {
                    totalMessageCount = serverCount;
                    if (onComplete != null) onComplete.run();
                });
            }
            
            @Override
            public void onError(String error) {
                if (onComplete != null) onComplete.run();
            }
        });
    }
    
    /**
     * 检查最后一条消息的更新
     */
    private void checkLastMessageUpdate() {
        int sinceIndex = processedServerMessageCount + 1;
        
        apiClient.getNewMessages(currentSessionId, sinceIndex, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    if (chatMessages.isEmpty()) return;
                    
                    // 处理所有新消息
                    boolean addedNew = false;
                    for (ApiClient.ChatMessage msg : chatMessages) {
                        String identifier = getMessageIdentifier(msg);
                        if (messageFingerprints.containsKey(msg.created) && 
                            identifier.equals(messageFingerprints.get(msg.created))) {
                            continue; // 已存在相同消息
                        }
                        
                        messages.add(createMessageFromChatMessage(msg));
                        messageFingerprints.put(msg.created, identifier);
                        addedNew = true;
                    }
                    
                    if (addedNew) {
                        processedServerMessageCount += chatMessages.size();
                        adapter.notifyDataSetChangedWithUpdate();
                        if (userAtBottom) {
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
                        String identifier = getMessageIdentifier(msg);
                        if (messageFingerprints.containsKey(msg.created)) {
                            Log.d(TAG, "Message already exists, skipping: created=" + msg.created);
                            continue;
                        }
                        
                        // 只对普通用户消息检查 pending 替换（错误消息不需要）
                        if (msg.error == null && "user".equals(msg.role) && msg.content != null) {
                            int pendingIndex = findPendingMessageIndex(msg.content);
                            if (pendingIndex >= 0) {
                                Log.d(TAG, "Replacing pending message at index " + pendingIndex);
                                messages.set(pendingIndex, createMessageFromChatMessage(msg));
                                pendingMessages.remove(msg.content);
                                messageFingerprints.put(msg.created, identifier);
                                continue;
                            }
                        }
                        
                        messages.add(createMessageFromChatMessage(msg));
                        messageFingerprints.put(msg.created, identifier);
                        addedNew = true;
                    }
                    
                    processedServerMessageCount += chatMessages.size();
                    
                    cleanupPendingMessages();
                    
                    adapter.notifyDataSetChangedWithUpdate();
                    
                    if (addedNew) {
                        if (userAtBottom) {
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
    
    /**
     * 清理超时的 pending 消息
     */
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
            Object item = adapter.getVisibleItemAt(firstVisiblePosition);
            if (item instanceof Message) {
                anchorMessageCreated = ((Message) item).getCreated();
            } else if (item instanceof MessageAdapter.ToolCallItem) {
                anchorMessageCreated = ((MessageAdapter.ToolCallItem) item).getCreated();
            }
            View firstChild = lm.findViewByPosition(firstVisiblePosition);
            if (firstChild != null) {
                offsetToRestore = firstChild.getTop();
            }
            Log.d(TAG, "Saved position: anchorCreated=" + anchorMessageCreated + ", offset=" + offsetToRestore);
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
            adapter.notifyDataSetChangedWithUpdate();
            hideLoadMoreHint();
            isInitialLoadComplete = true;
            return;
        }
        
        apiClient.getMessagesWithOptions(currentSessionId, 0, DEFAULT_LIMIT, true, new ApiClient.MessagesCallback() {
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
                    
                    // 添加消息
                    for (ApiClient.ChatMessage msg : chatMessages) {
                        messages.add(createMessageFromChatMessage(msg));
                        messageFingerprints.put(msg.created, getMessageIdentifier(msg));
                        messagesInPool.add(msg.created);
                    }
                    
                    processedServerMessageCount = chatMessages.size();
                    
                    // 设置初始 since 值
                    if (!chatMessages.isEmpty()) {
                        currentSince = chatMessages.get(0).created;
                    }
                    
                    adapter.notifyDataSetChangedWithUpdate();
                    scrollToBottom();
                    hideLoadMoreHint();
                    isInitialLoadComplete = true;
                    
                    // 设置滚动监听器
                    setupScrollListener();
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
                    processedServerMessageCount = 0;
                    currentSince = 0;
                    adapter.notifyDataSetChangedWithUpdate();
                    hideLoadMoreHint();
                    isInitialLoadComplete = true;
                });
            }
        });
    }
    
    /**
     * 添加系统消息
     */
    private void addSystemMessage(String text) {
        Message systemMessage = new Message(text, false);
        messages.add(systemMessage);
    }
    
    /**
     * 加载更早的消息
     */
    private void loadOlderMessages() {
        if (apiClient == null || currentSessionId == null) return;
        
        int since = currentSince - 1;
        if (since < 1) {
            isLoadingOlder = false;
            hideLoadMoreHint();
            Toast.makeText(this, "已到第一条消息", Toast.LENGTH_SHORT).show();
            startAutoRefresh();
            return;
        }
        
        apiClient.getMessagesWithOptions(currentSessionId, since, DEFAULT_LIMIT, false, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    if (chatMessages.isEmpty()) {
                        currentSince = 0;
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
                        String identifier = getMessageIdentifier(msg);
                        if (!messageFingerprints.containsKey(msg.created)) {
                            Message message = createMessageFromChatMessage(msg);
                            messages.add(0, message);
                            messageFingerprints.put(msg.created, identifier);
                            newTotalCount++;
                            
                            if (message.shouldDisplay()) {
                                newVisibleCount++;
                            }
                        }
                    }
                    
                    Log.d(TAG, "Loaded messages: total=" + newTotalCount + ", visible=" + newVisibleCount);
                    
                    if (newTotalCount == 0) {
                        currentSince = since;
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
                    
                    // 更新 currentSince 为最早消息的 created
                    currentSince = chatMessages.get(0).created;
                    sessionManager.updateFirstMessageIndex(currentSessionId, currentSince);
                    
                    adapter.notifyDataSetChangedWithUpdate();
                    
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
                                Log.d(TAG, "Restored to position " + newPosition + " with offset " + savedOffset);
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
                            }, 2000);
                        } else {
                            hideLoadMoreHint();
                            Toast.makeText(ChatActivity.this, "已到第一条消息", Toast.LENGTH_SHORT).show();
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
                    showError("加载失败: " + error);
                    startAutoRefresh();
                });
            }
        });
    }
    
    private void showLoadMoreHint(String text) {
        if (layoutLoadMore != null) {
            layoutLoadMore.setVisibility(View.VISIBLE);
            if (tvLoadMore != null) {
                tvLoadMore.setText(text);
            }
        }
    }
    
    private void hideLoadMoreHint() {
        if (layoutLoadMore != null) {
            layoutLoadMore.setVisibility(View.GONE);
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SessionSettingsActivity.class);
            intent.putExtra("session_id", currentSessionId);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_clear) {
            showClearConfirmation();
            return true;
        } else if (id == R.id.action_scroll_bottom) {
            scrollToBottom();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private void showClearConfirmation() {
        new AlertDialog.Builder(this)
            .setTitle("清空消息")
            .setMessage("确定要清空当前会话的所有消息吗？")
            .setPositiveButton("清空", (dialog, which) -> clearMessages())
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void clearMessages() {
        messages.clear();
        messageFingerprints.clear();
        pendingMessages.clear();
        messagesInPool.clear();
        addSystemMessage("消息已清空");
        adapter.notifyDataSetChangedWithUpdate();
        
        // 清空服务器消息
        if (apiClient != null && currentSessionId != null) {
            apiClient.clearMessages(currentSessionId, new ApiClient.SimpleCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        processedServerMessageCount = 0;
                        currentSince = 0;
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> showError("清空失败: " + error));
                }
            });
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        startAutoRefresh();
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
}
