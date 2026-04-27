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
 * 核心逻辑：
 * 1. 消息排序：API 返回消息按 created 时间升序（从旧到新）[oldest, ..., newest]
 * 2. 本地列表：保持升序 [oldest, ..., newest]，最新消息在末尾
 * 3. 显示过滤：只显示 content 不为空且 role 不是 tool 的消息
 * 
 * 增量刷新：
 * - 使用 processedServerMessageCount 追踪服务器消息总数
 * - 因为本地 messages.size() != 服务器 message_count（服务器包含 tool 消息）
 * - sinceIndex = processedServerMessageCount + 1
 */
public class ChatActivity extends AppCompatActivity {
    
    private static final String TAG = "ChatActivity";
    private static final int REFRESH_INTERVAL_MS = 3000;
    private static final int PAGE_SIZE = 20;
    private static final int STATE_NORMAL = 0;
    private static final int STATE_SENDING = 1;
    private static final int BOTTOM_THRESHOLD = 3;
    
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
    
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private boolean isAutoRefreshEnabled = true;
    private int lastMessageCount = 0;
    
    private int totalMessageCount = 0;
    private boolean isLoadingMore = false;
    private boolean hasMoreMessages = true;
    private int buttonState = STATE_NORMAL;
    private boolean isInProgress = false;
    private Menu chatMenu;
    
    // 消息指纹缓存（用于检测变化）
    private Map<Long, String> messageFingerprints = new HashMap<>();
    private long lastMessageCreatedTimestamp = -1;
    private String lastMessageContent = null;
    private int lastCheckedContentLength = -1;
    
    // 追踪服务器消息总数（用于增量刷新，因为本地消息数 != 服务器消息数）
    private int processedServerMessageCount = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        tvSessionTitle = findViewById(R.id.tv_session_title);
        tvSessionInfo = findViewById(R.id.tv_session_info);
        tvSessionPath = findViewById(R.id.tv_session_path);
        
        currentSessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        currentSessionTitle = getIntent().getStringExtra(EXTRA_SESSION_TITLE);
        
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
        }
        
        if (session != null && session.getAccountId() != null && !session.getAccountId().isEmpty()) {
            sessionAccount = accountManager.getAccountById(session.getAccountId());
        }
        if (sessionAccount == null) {
            sessionAccount = accountManager.getActiveAccount();
        }
        if (sessionAccount == null) {
            Toast.makeText(this, "请先添加账号", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
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
        
        messages = new ArrayList<>();
        adapter = new MessageAdapter(messages, this);
        rvMessages.setAdapter(adapter);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        
        // 下拉加载更早消息
        rvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm != null && lm.findFirstVisibleItemPosition() == 0 && !isLoadingMore && hasMoreMessages) {
                    loadOlderMessages();
                }
            }
        });
        
        btnSend.setOnClickListener(v -> sendMessage());
        
        tvSessionTitle.setText(currentSessionTitle != null ? currentSessionTitle : currentSessionId);
        
        updateSessionInfo();
        
        // 初始化加载
        messages.add(new Message("正在加载消息...", false));
        adapter.notifyDataSetChanged();
        
        refreshSessionStatus(() -> loadMessagesPage());
        
        startAutoRefresh();
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
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_menu, menu);
        chatMenu = menu;
        updateMenuVisibility(menu);
        return true;
    }
    
    private void updateMenuVisibility(Menu menu) {
        MenuItem stopItem = menu.findItem(R.id.action_stop);
        MenuItem retryItem = menu.findItem(R.id.action_retry);
        if (stopItem != null) {
            stopItem.setVisible(isInProgress);
        }
        if (retryItem != null) {
            retryItem.setVisible(!isInProgress && messages != null && !messages.isEmpty());
        }
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
        } else if (id == R.id.action_stop) {
            stopSession();
            return true;
        } else if (id == R.id.action_retry) {
            retrySession();
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void updateSessionInfo() {
        Session session = sessionManager.getSession(currentSessionId);
        if (session != null) {
            tvSessionInfo.setText("Provider: " + session.getProvider() + " | Model: " + session.getModel());
            tvSessionPath.setText(session.getCwd() != null ? session.getCwd() : "");
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
                if (isAutoRefreshEnabled) {
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
        if (apiClient == null || currentSessionId == null) return;
        refreshSessionStatusForIncrementalRefresh();
    }
    
    /**
     * 增量刷新：检查服务器消息数量变化
     */
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
                            
                            if (isInProgress) {
                                setButtonStateSending();
                            } else {
                                if (wasInProgress) {
                                    setButtonStateNormal();
                                    fetchNewMessagesFromIndex(processedServerMessageCount + 1);
                                } else {
                                    setButtonStateNormal();
                                }
                            }
                            
                            if (serverCount > processedServerMessageCount) {
                                Log.d(TAG, "New messages: server=" + serverCount + ", processed=" + processedServerMessageCount);
                                fetchNewMessagesFromIndex(processedServerMessageCount + 1);
                            } else if (serverCount < processedServerMessageCount) {
                                Log.d(TAG, "Messages decreased, reloading");
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
     * 从指定索引获取新消息
     * API 返回升序，正向遍历添加到末尾
     */
    private void fetchNewMessagesFromIndex(int sinceIndex) {
        boolean wasAtBottom = isUserAtBottom();
        
        Log.d(TAG, "Fetching messages from index: since=" + sinceIndex);
        
        apiClient.getNewMessages(currentSessionId, sinceIndex, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    if (chatMessages.isEmpty()) return;
                    
                    List<ApiClient.ChatMessage> filtered = new ArrayList<>();
                    for (ApiClient.ChatMessage msg : chatMessages) {
                        if (msg.content != null && !msg.content.isEmpty() && !"tool".equals(msg.role)) {
                            filtered.add(msg);
                        }
                    }
                    
                    if (filtered.isEmpty()) {
                        processedServerMessageCount += chatMessages.size();
                        return;
                    }
                    
                    int insertStart = messages.size();
                    for (ApiClient.ChatMessage msg : filtered) {
                        if (!messageFingerprints.containsKey(msg.created)) {
                            messages.add(new Message(msg.content, "user".equals(msg.role), msg.created * 1000L));
                            messageFingerprints.put(msg.created, msg.content);
                        }
                    }
                    
                    processedServerMessageCount += chatMessages.size();
                    
                    lastMessageCount = messages.size();
                    updateLastMessageTracking();
                    adapter.notifyItemRangeInserted(insertStart, filtered.size());
                    
                    Log.d(TAG, "Incremental: fetched " + chatMessages.size() + " server, " + filtered.size() + " displayed, processed=" + processedServerMessageCount);
                    
                    if (wasAtBottom) rvMessages.scrollToPosition(messages.size() - 1);
                    
                    if (!messages.isEmpty()) {
                        sessionManager.updateMessages(currentSessionId,
                            messages.get(0).getContent(),
                            messages.get(messages.size()-1).getContent(),
                            lastMessageCount,
                            messages.get(messages.size()-1).getTimestamp());
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
     * 初始加载消息
     */
    private void loadMessagesPage() {
        if (apiClient == null || currentSessionId == null) return;
        
        if (totalMessageCount <= 0) {
            if (messages.isEmpty()) addMessage("暂无消息", false);
            processedServerMessageCount = 0;
            return;
        }
        
        Session session = sessionManager.getSession(currentSessionId);
        int firstMessageIndex = session != null ? session.getFirstMessageIndex() : 0;
        int since;
        
        if (firstMessageIndex == 0) {
            if (totalMessageCount <= PAGE_SIZE) {
                since = 1;
                hasMoreMessages = false;
            } else {
                since = totalMessageCount - PAGE_SIZE + 1;
                hasMoreMessages = true;
            }
            sessionManager.updateFirstMessageIndex(currentSessionId, since);
            Log.d(TAG, "Initialized firstMessageIndex: " + since);
        } else {
            since = firstMessageIndex;
            hasMoreMessages = (since > 1);
        }
        
        Log.d(TAG, "Loading: since=" + since + ", total=" + totalMessageCount);
        isLoadingMore = true;
        
        apiClient.getNewMessages(currentSessionId, since, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    isLoadingMore = false;
                    
                    if (messages.size() == 1 && messages.get(0).getContent().equals("正在加载消息...")) {
                        messages.clear();
                    }
                    
                    if (chatMessages.isEmpty()) {
                        if (messages.isEmpty()) addMessage("暂无消息", false);
                        processedServerMessageCount = 0;
                        return;
                    }
                    
                    List<ApiClient.ChatMessage> filtered = new ArrayList<>();
                    for (ApiClient.ChatMessage msg : chatMessages) {
                        if (msg.content != null && !msg.content.isEmpty() && !"tool".equals(msg.role)) {
                            filtered.add(msg);
                        }
                    }
                    
                    for (ApiClient.ChatMessage msg : filtered) {
                        boolean isUser = "user".equals(msg.role);
                        messages.add(new Message(msg.content, isUser, msg.created * 1000L));
                        messageFingerprints.put(msg.created, msg.content);
                    }
                    
                    processedServerMessageCount = totalMessageCount;
                    
                    updateLastMessageTracking();
                    lastMessageCount = messages.size();
                    
                    Log.d(TAG, "Loaded: local=" + messages.size() + ", serverTotal=" + totalMessageCount + ", processed=" + processedServerMessageCount);
                    
                    if (!messages.isEmpty()) {
                        sessionManager.updateMessages(currentSessionId,
                            messages.get(0).getContent(),
                            messages.get(messages.size()-1).getContent(),
                            lastMessageCount,
                            messages.get(messages.size()-1).getTimestamp());
                    }
                    
                    adapter.notifyDataSetChanged();
                    rvMessages.scrollToPosition(messages.size() - 1);
                    
                    Session current = sessionManager.getSession(currentSessionId);
                    if (current != null && current.getFirstMessageIndex() == 1) {
                        hasMoreMessages = false;
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    isLoadingMore = false;
                    if (messages.isEmpty()) addMessage("加载失败: " + error, false);
                    Toast.makeText(ChatActivity.this, "加载失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * 加载更早的消息
     */
    private void loadOlderMessages() {
        if (isLoadingMore || !hasMoreMessages) return;
        
        Session session = sessionManager.getSession(currentSessionId);
        int currentFirstIndex = session != null ? session.getFirstMessageIndex() : 0;
        
        if (currentFirstIndex <= 1) {
            hasMoreMessages = false;
            Toast.makeText(this, "已到第一条消息", Toast.LENGTH_SHORT).show();
            return;
        }
        
        int newSince = Math.max(currentFirstIndex - PAGE_SIZE, 1);
        if (newSince >= currentFirstIndex) {
            hasMoreMessages = false;
            return;
        }
        
        isLoadingMore = true;
        final int insertPosition = 0;
        
        apiClient.getNewMessages(currentSessionId, newSince, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    isLoadingMore = false;
                    
                    if (chatMessages.isEmpty()) {
                        hasMoreMessages = false;
                        return;
                    }
                    
                    List<ApiClient.ChatMessage> filtered = new ArrayList<>();
                    for (ApiClient.ChatMessage msg : chatMessages) {
                        if (msg.content != null && !msg.content.isEmpty() && !"tool".equals(msg.role)) {
                            filtered.add(msg);
                        }
                    }
                    
                    // 反向插入到开头
                    for (int i = filtered.size() - 1; i >= 0; i--) {
                        ApiClient.ChatMessage msg = filtered.get(i);
                        messages.add(0, new Message(msg.content, "user".equals(msg.role), msg.created * 1000L));
                        messageFingerprints.put(msg.created, msg.content);
                    }
                    
                    sessionManager.updateFirstMessageIndex(currentSessionId, newSince);
                    hasMoreMessages = (newSince > 1);
                    
                    adapter.notifyDataSetChanged();
                    rvMessages.scrollToPosition(filtered.size());
                    
                    Log.d(TAG, "Loaded older: " + filtered.size() + " messages, newSince=" + newSince);
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    isLoadingMore = false;
                    Toast.makeText(ChatActivity.this, "加载失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * 检查最后一条消息的更新（流式输出时）
     */
    private void checkLastMessageUpdate() {
        if (processedServerMessageCount <= 0) return;
        if (lastMessageContent == null) {
            updateLastMessageTracking();
            return;
        }
        
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
                    
                    String serverContent = latestFiltered.content;
                    boolean changed = !serverContent.equals(lastMessageContent);
                    
                    if (changed && messages.size() > 0) {
                        int lastIdx = messages.size() - 1;
                        messages.set(lastIdx, new Message(serverContent, "user".equals(latestFiltered.role), latestFiltered.created * 1000L));
                        messageFingerprints.put(latestFiltered.created, serverContent);
                        lastMessageContent = serverContent;
                        adapter.notifyItemChanged(lastIdx);
                        Log.d(TAG, "Stream updated: len=" + serverContent.length());
                        if (isUserAtBottom()) rvMessages.scrollToPosition(messages.size() - 1);
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
        messages.clear();
        messageFingerprints.clear();
        processedServerMessageCount = 0;
        sessionManager.updateFirstMessageIndex(currentSessionId, 0);
        hasMoreMessages = true;
        loadMessagesPage();
    }
    
    private boolean isUserAtBottom() {
        if (rvMessages == null || messages == null || messages.isEmpty()) return true;
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return true;
        return (messages.size() - lm.findLastVisibleItemPosition()) <= BOTTOM_THRESHOLD;
    }
    
    private void updateLastMessageTracking() {
        if (messages != null && !messages.isEmpty()) {
            Message last = messages.get(messages.size() - 1);
            lastMessageCreatedTimestamp = last.getTimestamp() / 1000;
            lastMessageContent = last.getContent();
        }
    }
    
    private void refreshSessionStatus(Runnable onComplete) {
        apiClient.getSessions(accountId, new ApiClient.SessionsCallback() {
            @Override
            public void onSuccess(List<Session> sessions) {
                runOnUiThread(() -> {
                    for (Session session : sessions) {
                        if (session.getSessionId().equals(currentSessionId)) {
                            isInProgress = session.isInProgress();
                            totalMessageCount = session.getMessageCount();
                            
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
    
    private void addMessage(String content, boolean isUser) {
        if (messages == null) messages = new ArrayList<>();
        if (adapter == null) {
            adapter = new MessageAdapter(messages, this);
            if (rvMessages != null) rvMessages.setAdapter(adapter);
        }
        messages.add(new Message(content, isUser));
        adapter.notifyItemInserted(messages.size() - 1);
        if (rvMessages != null) rvMessages.scrollToPosition(messages.size() - 1);
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
        setButtonStateSending();
        
        addMessage(content, true);
        
        apiClient.sendMessage(currentSessionId, content, new ApiClient.MessageCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Message sent successfully");
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "发送失败: " + error, Toast.LENGTH_SHORT).show();
                    setButtonStateNormal();
                });
            }
        });
    }
    
    private void setButtonStateNormal() {
        buttonState = STATE_NORMAL;
        btnSend.setText("发送");
        btnSend.setEnabled(true);
        btnSend.setBackgroundColor(Color.parseColor("#4CAF50"));
        isInProgress = false;
        if (chatMenu != null) {
            updateMenuVisibility(chatMenu);
        }
    }
    
    private void setButtonStateSending() {
        buttonState = STATE_SENDING;
        btnSend.setText("发送中...");
        btnSend.setEnabled(false);
        btnSend.setBackgroundColor(Color.parseColor("#9E9E9E"));
        isInProgress = true;
        if (chatMenu != null) {
            updateMenuVisibility(chatMenu);
        }
    }
    
    private void clearSession() {
        messages.clear();
        messageFingerprints.clear();
        processedServerMessageCount = 0;
        adapter.notifyDataSetChanged();
        addMessage("会话已清空", false);
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
    
    private void stopSession() {
        if (apiClient == null) return;
        apiClient.stopSession(currentSessionId, new ApiClient.StopCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "已停止", Toast.LENGTH_SHORT).show();
                    setButtonStateNormal();
                    isInProgress = false;
                    refreshMessages();
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
        if (apiClient == null) return;
        setButtonStateSending();
        apiClient.retrySession(currentSessionId, new ApiClient.MessageCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "正在重试...", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "重试失败: " + error, Toast.LENGTH_SHORT).show();
                    setButtonStateNormal();
                });
            }
        });
    }
}
