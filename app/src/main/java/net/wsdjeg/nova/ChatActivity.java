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
 * 分页加载：
 * - firstMessageIndex: 当前已加载的最旧消息索引（从 1 开始）
 * - 首次加载最新的 PAGE_SIZE 条消息
 * - 下拉加载更早的消息
 * 
 * 增量刷新：
 * - 比较 localCount 与 serverCount
 * - 只获取新增的消息添加到末尾
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
    
    // 消息指纹缓存（用于检测变化）
    private Map<Long, String> messageFingerprints = new HashMap<>();
    private long lastMessageCreatedTimestamp = -1;
    private String lastMessageContent = null;
    private int lastCheckedContentLength = -1;
    
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
            Toast.makeText(this, "账号配置不完整", Toast.LENGTH_SHORT).show();
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
    
    private void loadInitialMessages() {
        Session session = sessionManager.getSession(currentSessionId);
        
        if (session == null || session.getMessageCount() <= 0) {
            addMessage("正在加载消息...", false);
            refreshSessionStatus(() -> {
                if (messages.size() == 1 && messages.get(0).getContent().equals("正在加载消息...")) {
                    messages.clear();
                }
                
                Session updatedSession = sessionManager.getSession(currentSessionId);
                if (updatedSession != null) {
                    totalMessageCount = updatedSession.getMessageCount();
                }
                
                if (totalMessageCount <= 0) {
                    addMessage("暂无消息", false);
                    return;
                }
                
                loadMessagesPage();
            });
        } else {
            totalMessageCount = session.getMessageCount();
            loadMessagesPage();
        }
    }
    
    /**
     * 加载一页消息
     * API 返回升序（从旧到新），直接添加到列表末尾
     */
    private void loadMessagesPage() {
        if (apiClient == null || currentSessionId == null) return;
        
        if (totalMessageCount <= 0) {
            if (messages.isEmpty()) addMessage("暂无消息", false);
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
        
        apiClient.getMessagesWithOptions(currentSessionId, since, PAGE_SIZE, false, 
            new ApiClient.MessagesCallback() {
                @Override
                public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                    runOnUiThread(() -> {
                        isLoadingMore = false;
                        
                        if (messages.size() == 1 && messages.get(0).getContent().equals("正在加载消息...")) {
                            messages.clear();
                        }
                        
                        if (chatMessages.isEmpty()) {
                            if (messages.isEmpty()) addMessage("暂无消息", false);
                            return;
                        }
                        
                        // 过滤：只显示有 content 且不是 tool 的消息
                        List<ApiClient.ChatMessage> filtered = new ArrayList<>();
                        for (ApiClient.ChatMessage msg : chatMessages) {
                            if (msg.content != null && !msg.content.isEmpty() && !"tool".equals(msg.role)) {
                                filtered.add(msg);
                            }
                        }
                        
                        // API 返回升序 [oldest...newest]，直接添加到末尾
                        for (ApiClient.ChatMessage msg : filtered) {
                            boolean isUser = "user".equals(msg.role);
                            messages.add(new Message(msg.content, isUser, msg.created * 1000L));
                            messageFingerprints.put(msg.created, msg.content);
                        }
                        
                        updateLastMessageTracking();
                        lastMessageCount = messages.size();
                        
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
     * API 返回升序，插入到列表开头
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
        
        final int since = newSince;
        final int insertCount = currentFirstIndex - newSince;
        
        Log.d(TAG, "Loading older: since=" + since + ", count=" + insertCount);
        
        LinearLayoutManager layoutManager = (LinearLayoutManager) rvMessages.getLayoutManager();
        int oldPos = layoutManager.findFirstVisibleItemPosition();
        View firstView = layoutManager.getChildAt(0);
        int oldTop = firstView != null ? firstView.getTop() : 0;
        
        addMessageAtTop("加载中...");
        isLoadingMore = true;
        
        apiClient.getMessagesWithOptions(currentSessionId, since, insertCount, false,
            new ApiClient.MessagesCallback() {
                @Override
                public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                    runOnUiThread(() -> {
                        isLoadingMore = false;
                        
                        if (!messages.isEmpty() && messages.get(0).getContent().equals("加载中...")) {
                            messages.remove(0);
                            adapter.notifyItemRemoved(0);
                        }
                        
                        if (chatMessages.isEmpty()) {
                            hasMoreMessages = false;
                            return;
                        }
                        
                        // 过滤
                        List<ApiClient.ChatMessage> filtered = new ArrayList<>();
                        for (ApiClient.ChatMessage msg : chatMessages) {
                            if (msg.content != null && !msg.content.isEmpty() && !"tool".equals(msg.role)) {
                                filtered.add(msg);
                            }
                        }
                        
                        if (filtered.isEmpty()) return;
                        
                        // API 返回升序 [oldest...newest]，反向遍历插入到开头
                        int added = 0;
                        for (int i = filtered.size() - 1; i >= 0; i--) {
                            ApiClient.ChatMessage msg = filtered.get(i);
                            messages.add(added, new Message(msg.content, "user".equals(msg.role), msg.created * 1000L));
                            messageFingerprints.put(msg.created, msg.content);
                            added++;
                        }
                        
                        lastMessageCount = messages.size();
                        adapter.notifyItemRangeInserted(0, added);
                        
                        int newPos = oldPos + added;
                        layoutManager.scrollToPositionWithOffset(newPos, oldTop);
                        
                        sessionManager.updateFirstMessageIndex(currentSessionId, since);
                        if (since == 1) hasMoreMessages = false;
                        
                        Log.d(TAG, "Inserted " + added + " older messages");
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
                        Toast.makeText(ChatActivity.this, "加载失败: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
    }
    
    private void addMessageAtTop(String content) {
        if (messages == null) messages = new ArrayList<>();
        messages.add(0, new Message(content, false, System.currentTimeMillis()));
        if (adapter != null) adapter.notifyItemInserted(0);
    }
    
    private void loadSessionInfo() {
        Session session = sessionManager.getSession(currentSessionId);
        if (session != null) {
            updateSessionInfo(session);
            totalMessageCount = session.getMessageCount();
        } else {
            tvSessionTitle.setText("新会话");
            tvSessionInfo.setText("unknown | unknown");
            tvSessionPath.setText("CWD: unknown");
        }
    }
    
    private void updateSessionInfo(Session session) {
        tvSessionTitle.setText(session.getTitle() != null && !session.getTitle().isEmpty() ? session.getTitle() : "新会话");
        tvSessionInfo.setText((session.getProvider() != null ? session.getProvider() : "unknown") + " | " + (session.getModel() != null ? session.getModel() : "unknown"));
        tvSessionPath.setText("CWD: " + (session.getCwd() != null ? session.getCwd() : "unknown"));
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadSessionInfo();
        if (isAutoRefreshEnabled && apiClient != null) startAutoRefresh();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        stopAutoRefresh();
        if (currentSessionId != null && messages != null) {
            sessionManager.saveReadMessageCount(currentSessionId, messages.size());
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
        int id = item.getItemId();
        if (id == android.R.id.home) { onBackPressed(); return true; }
        if (id == R.id.action_settings) { openSessionSettings(); return true; }
        if (id == R.id.action_refresh) { refreshMessages(); Toast.makeText(this, "已刷新", Toast.LENGTH_SHORT).show(); return true; }
        if (id == R.id.action_preview) { openPreviewInBrowser(); return true; }
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
        Account active = accountManager.getActiveAccount();
        if (active == null) { Toast.makeText(this, "请先添加账号", Toast.LENGTH_SHORT).show(); return; }
        startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(active.getUrl() + "/session?id=" + currentSessionId)));
    }
    
    private void initViews() {
        rvMessages = findViewById(R.id.rv_messages);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        btnSend.setOnClickListener(v -> onSendButtonClick());
        setButtonStateNormal();
    }
    
    private void onSendButtonClick() {
        if (buttonState == STATE_SENDING) stopGeneration();
        else sendMessage();
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
        
        rvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm != null && lm.findFirstVisibleItemPosition() == 0 && dy < 0 && !isLoadingMore && hasMoreMessages) {
                    loadOlderMessages();
                }
            }
        });
    }
    
    private void setupAutoRefresh() {
        refreshHandler = new Handler(Looper.getMainLooper());
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAutoRefreshEnabled && apiClient != null) refreshMessages();
                refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
            }
        };
    }
    
    private void startAutoRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.post(refreshRunnable);
    }
    
    private void stopAutoRefresh() {
        if (refreshHandler != null) refreshHandler.removeCallbacks(refreshRunnable);
    }
    
    private void refreshMessages() {
        if (apiClient == null || currentSessionId == null) return;
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
                            int localCount = messages.size();
                            
                            if (isInProgress) setButtonStateSending();
                            else {
                                if (wasInProgress) {
                                    setButtonStateNormal();
                                    fetchLatestMessages();
                                } else setButtonStateNormal();
                            }
                            
                            if (serverCount > localCount) {
                                Log.d(TAG, "New messages: server=" + serverCount + ", local=" + localCount);
                                fetchIncrementalMessages(localCount, serverCount);
                            } else if (serverCount < localCount) {
                                Log.d(TAG, "Messages decreased, reloading");
                                reloadMessages();
                            } else if (isInProgress) checkLastMessageUpdate();
                            
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
     * 获取增量消息
     * API 返回升序，正向遍历添加到末尾
     */
    private void fetchIncrementalMessages(int localCount, int serverCount) {
        int newCount = serverCount - localCount;
        boolean wasAtBottom = isUserAtBottom();
        int sinceIndex = localCount + 1;
        
        Log.d(TAG, "Fetching incremental: since=" + sinceIndex + ", count=" + newCount);
        
        apiClient.getMessagesWithOptions(currentSessionId, sinceIndex, newCount, false, 
            new ApiClient.MessagesCallback() {
                @Override
                public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                    runOnUiThread(() -> {
                        if (chatMessages.isEmpty()) return;
                        
                        // 过滤
                        List<ApiClient.ChatMessage> filtered = new ArrayList<>();
                        for (ApiClient.ChatMessage msg : chatMessages) {
                            if (msg.content != null && !msg.content.isEmpty() && !"tool".equals(msg.role)) {
                                filtered.add(msg);
                            }
                        }
                        
                        if (filtered.isEmpty()) return;
                        
                        // API 返回升序 [oldest...newest]，正向遍历添加到末尾
                        int insertStart = messages.size();
                        for (ApiClient.ChatMessage msg : filtered) {
                            messages.add(new Message(msg.content, "user".equals(msg.role), msg.created * 1000L));
                            messageFingerprints.put(msg.created, msg.content);
                        }
                        
                        lastMessageCount = messages.size();
                        updateLastMessageTracking();
                        adapter.notifyItemRangeInserted(insertStart, filtered.size());
                        
                        Log.d(TAG, "Incremental refresh: inserted " + filtered.size());
                        
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
    
    private void fetchLatestMessages() {
        boolean wasAtBottom = isUserAtBottom();
        
        apiClient.getLastMessage(currentSessionId, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    if (chatMessages.isEmpty()) return;
                    
                    ApiClient.ChatMessage lastMsg = chatMessages.get(0);
                    if ("tool".equals(lastMsg.role) || lastMsg.content == null || lastMsg.content.isEmpty()) return;
                    
                    long serverCreated = lastMsg.created;
                    String serverContent = lastMsg.content;
                    
                    boolean isNew = (serverCreated != lastMessageCreatedTimestamp);
                    boolean changed = (lastMessageContent == null || !serverContent.equals(lastMessageContent));
                    
                    if (isNew) {
                        messages.add(new Message(serverContent, "user".equals(lastMsg.role), serverCreated * 1000L));
                        messageFingerprints.put(serverCreated, serverContent);
                        lastMessageCount = messages.size();
                        updateLastMessageTracking();
                        adapter.notifyItemInserted(messages.size() - 1);
                        if (wasAtBottom) rvMessages.scrollToPosition(messages.size() - 1);
                        Log.d(TAG, "New message added");
                    } else if (changed) {
                        int lastIdx = messages.size() - 1;
                        messages.set(lastIdx, new Message(serverContent, "user".equals(lastMsg.role), serverCreated * 1000L));
                        messageFingerprints.put(serverCreated, serverContent);
                        lastMessageContent = serverContent;
                        adapter.notifyItemChanged(lastIdx);
                        Log.d(TAG, "Last message updated: len=" + serverContent.length());
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.d(TAG, "Latest fetch failed: " + error);
            }
        });
    }
    
    private void checkLastMessageUpdate() {
        if (messages.isEmpty()) return;
        if (lastMessageContent == null) {
            updateLastMessageTracking();
            return;
        }
        
        apiClient.getLastMessage(currentSessionId, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    if (chatMessages.isEmpty()) return;
                    
                    ApiClient.ChatMessage lastMsg = chatMessages.get(0);
                    if ("tool".equals(lastMsg.role) || lastMsg.content == null) return;
                    
                    String serverContent = lastMsg.content;
                    boolean changed = !serverContent.equals(lastMessageContent);
                    
                    if (changed) {
                        int lastIdx = messages.size() - 1;
                        messages.set(lastIdx, new Message(serverContent, "user".equals(lastMsg.role), lastMsg.created * 1000L));
                        messageFingerprints.put(lastMsg.created, serverContent);
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
            Log.d(TAG, "Tracking: timestamp=" + lastMessageCreatedTimestamp + ", len=" + lastMessageContent.length());
        }
    }
    
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
                            
                            if (isInProgress) setButtonStateSending();
                            else {
                                if (wasInProgress) fetchLatestMessages();
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
        
        long timestamp = System.currentTimeMillis();
        messages.add(new Message(content, isUser, timestamp));
        messageFingerprints.put(timestamp / 1000, content);
        updateLastMessageTracking();
        
        if (adapter != null) adapter.notifyItemInserted(messages.size() - 1);
        if (rvMessages != null) rvMessages.scrollToPosition(messages.size() - 1);
    }
    
    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) { Toast.makeText(this, "请输入消息", Toast.LENGTH_SHORT).show(); return; }
        
        addMessage(text, true);
        etMessage.setText("");
        sendChatMessage(text);
    }
    
    private void sendChatMessage(String content) {
        if (apiClient == null) { addMessage("请先配置账号", false); return; }
        if (currentSessionId == null || currentSessionId.isEmpty()) { addMessage("无效的会话ID", false); return; }
        
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
        if (apiClient == null || currentSessionId == null) return;
        
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
                    if (error.contains("not found") || error.contains("不存在")) {
                        new androidx.appcompat.app.AlertDialog.Builder(ChatActivity.this)
                            .setTitle("会话不存在")
                            .setMessage("该会话在服务器上不存在，是否返回会话列表？")
                            .setPositiveButton("返回列表", (d, w) -> finish())
                            .setNegativeButton("取消", (d, w) -> setButtonStateNormal())
                            .show();
                    } else {
                        Toast.makeText(ChatActivity.this, "停止失败: " + error, Toast.LENGTH_SHORT).show();
                        if (isInProgress) setButtonStateSending();
                        else setButtonStateNormal();
                    }
                });
            }
        });
    }
    
    public void retryLastMessage() {
        if (apiClient == null || currentSessionId == null) return;
        
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
