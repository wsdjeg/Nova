package net.wsdjeg.nova;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
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
 * 下拉加载更多：
 * - 条件：到达顶部且 currentSince > 1
 * - 暂停定时刷新，加载完成后恢复
 * 
 * 位置保持：
 * - 记录第一条可见消息的 created 时间戳
 * - 刷新后根据时间戳恢复位置
 * - 用户在底部时跟随新消息（仅 session.in_progress 时）
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
    private String accountId;
    private Account currentAccount;  // 当前账号（用于预览等功能）
    
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private boolean isAutoRefreshEnabled = true;
    
    private int totalMessageCount = 0;
    private int currentSince = 0;  // 当前加载位置
    
    private int buttonState = STATE_NORMAL;
    private boolean isInProgress = false;
    private Menu chatMenu;
    
    // 下拉加载状态
    private boolean isLoadingOlder = false;
    private boolean wasAtTopBeforeLoad = false;
    
    // 位置保持：记录第一条可见消息的 created 时间戳
    private long firstVisibleMessageCreated = -1;
    private int firstVisibleOffset = 0;  // 消息顶部距离 RecyclerView 顶部的偏移
    
    // 消息指纹缓存
    private Map<Long, String> messageFingerprints = new HashMap<>();
    private int processedServerMessageCount = 0;
    
    // 是否用户主动滚动到底部（跟随模式）
    private boolean userAtBottom = false;
    
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
            currentSince = session.getFirstMessageIndex();
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
        
        // 保存当前账号（用于预览等功能）
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
        
        messages = new ArrayList<>();
        adapter = new MessageAdapter(messages, this);
        rvMessages.setAdapter(adapter);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setItemAnimator(null);
        
        // 滚动监听：下拉加载 + 位置记录 + 滚动到底部按钮
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
                
                // 记录用户是否在底部
                userAtBottom = isAtBottom;
                
                // 记录第一条可见消息的位置（用于刷新后恢复）
                if (firstVisible >= 0 && firstVisible < messages.size()) {
                    Message firstMsg = messages.get(firstVisible);
                    firstVisibleMessageCreated = firstMsg.getTimestamp() / 1000;
                    
                    View firstChild = lm.findViewByPosition(firstVisible);
                    if (firstChild != null) {
                        firstVisibleOffset = firstChild.getTop();
                    }
                }
                
                // 下拉加载逻辑
                if (!isLoadingOlder && isAtTop && canLoadMore()) {
                    // 到顶部且有更多消息，显示提示
                    showLoadMoreHint("下拉加载更多");
                    
                    // 检测过度滚动（继续下拉）
                    if (dy < 0) {
                        // 触发加载
                        triggerLoadOlder();
                    }
                } else if (!isLoadingOlder && !isAtTop) {
                    hideLoadMoreHint();
                }
                
                // 滚动到底部按钮
                if (isAtBottom) {
                    fabScrollBottom.setVisibility(View.GONE);
                } else {
                    fabScrollBottom.setVisibility(View.VISIBLE);
                }
            }
            
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                
                // 滚动停止时检查是否在顶部
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (lm == null) return;
                    
                    int firstVisible = lm.findFirstVisibleItemPosition();
                    boolean isAtTop = (firstVisible == 0 && adapter.getItemCount() > 0);
                    
                    if (!isLoadingOlder) {
                        if (isAtTop && canLoadMore()) {
                            showLoadMoreHint("下拉加载更多");
                        } else {
                            hideLoadMoreHint();
                        }
                    }
                }
            }
        });
        
        // 点击滚动到底部
        fabScrollBottom.setOnClickListener(v -> scrollToBottom());
        
        // 发送按钮
        btnSend.setOnClickListener(v -> {
            if (buttonState == STATE_SENDING) {
                stopSession();
            } else {
                sendMessage();
            }
        });
        
        tvSessionTitle.setText(currentSessionTitle != null ? currentSessionTitle : currentSessionId);
        updateSessionInfo();
        
        // 初始化加载
        messages.add(new Message("正在加载消息...", false));
        adapter.refreshData();
        
        refreshSessionStatus(() -> loadMessagesPage());
        startAutoRefresh();
    }
    
    /**
     * 是否可以加载更多
     */
    private boolean canLoadMore() {
        return currentSince > 1 && totalMessageCount > 0;
    }
    
    /**
     * 触发加载更早消息
     */
    private void triggerLoadOlder() {
        if (isLoadingOlder || !canLoadMore()) return;
        
        isLoadingOlder = true;
        showLoadMoreHint("正在加载...");
        
        // 暂停定时刷新
        stopAutoRefresh();
        
        // 记录加载前的位置
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm != null) {
            int firstVisible = lm.findFirstVisibleItemPosition();
            wasAtTopBeforeLoad = (firstVisible == 0);
            
            // 记录第一条可显示消息的 created
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
    
    /**
     * 显示下拉加载提示
     */
    private void showLoadMoreHint(String text) {
        if (tvLoadMore != null) {
            tvLoadMore.setVisibility(View.VISIBLE);
            tvLoadMore.setText(text);
            if (text.contains("正在加载")) {
                tvLoadMore.setBackgroundColor(Color.parseColor("#FFBB33"));
            } else {
                tvLoadMore.setBackgroundColor(Color.parseColor("#2196F3"));
            }
        }
    }
    
    /**
     * 隐藏下拉加载提示
     */
    private void hideLoadMoreHint() {
        if (tvLoadMore != null) {
            tvLoadMore.setVisibility(View.GONE);
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
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void updateSessionInfo() {
        Session session = sessionManager.getSession(currentSessionId);
        if (session != null) {
            tvSessionInfo.setText(session.getProvider() + " | " + session.getModel());
            tvSessionPath.setText("cwd: " + (session.getCwd() != null ? session.getCwd() : ""));
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
    
    /**
     * 定时刷新：保持位置或跟随新消息
     */
    private void refreshMessages() {
        if (apiClient == null || currentSessionId == null || isLoadingOlder) return;
        refreshSessionStatusForIncrementalRefresh();
    }
    
    /**
     * 增量刷新
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
                                    // AI 回复完成，获取新消息
                                    fetchNewMessagesAndRestorePosition();
                                } else {
                                    setButtonStateNormal();
                                }
                            }
                            
                            if (serverCount > processedServerMessageCount) {
                                Log.d(TAG, "New messages: server=" + serverCount + ", processed=" + processedServerMessageCount);
                                fetchNewMessagesAndRestorePosition();
                            } else if (serverCount < processedServerMessageCount) {
                                Log.d(TAG, "Messages decreased, reloading");
                                reloadMessages();
                            } else if (isInProgress) {
                                // 正在生成，检查最后消息更新
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
        
        Log.d(TAG, "Fetching messages from index: since=" + sinceIndex + ", userAtBottom=" + userAtBottom);
        
        apiClient.getNewMessages(currentSessionId, sinceIndex, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    if (chatMessages.isEmpty()) return;
                    
                    // 添加新消息
                    for (ApiClient.ChatMessage msg : chatMessages) {
                        if (!messageFingerprints.containsKey(msg.created)) {
                            messages.add(new Message(msg.content, msg.role, msg.created));
                            messageFingerprints.put(msg.created, msg.content != null ? msg.content : "");
                        }
                    }
                    
                    processedServerMessageCount += chatMessages.size();
                    adapter.refreshData();
                    
                    Log.d(TAG, "Incremental: fetched " + chatMessages.size() + ", userAtBottom=" + userAtBottom);
                    
                    // 位置恢复逻辑
                    if (userAtBottom) {
                        // 用户在底部，跟随新消息滚动到底部
                        scrollToBottom();
                    } else {
                        // 用户不在底部，保持原位置
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
    
    /**
     * 根据时间戳恢复位置
     */
    private void restorePositionByTimestamp() {
        if (firstVisibleMessageCreated <= 0) return;
        
        LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (lm == null) return;
        
        // 查找时间戳对应的新位置
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getTimestamp() / 1000 == firstVisibleMessageCreated) {
                // 恢复到这个位置，保持相同的 offset
                lm.scrollToPositionWithOffset(i, firstVisibleOffset);
                Log.d(TAG, "Restored position: index=" + i + ", offset=" + firstVisibleOffset);
                return;
            }
        }
        
        // 没找到，可能是被过滤的消息，尝试找最接近的
        for (int i = 0; i < messages.size(); i++) {
            long created = messages.get(i).getTimestamp() / 1000;
            if (created >= firstVisibleMessageCreated) {
                lm.scrollToPositionWithOffset(i, firstVisibleOffset);
                Log.d(TAG, "Restored position (fallback): index=" + i);
                return;
            }
        }
    }
    
    /**
     * 初始加载
     */
    private void loadMessagesPage() {
        if (apiClient == null || currentSessionId == null) return;
        
        if (totalMessageCount <= 0) {
            messages.clear();
            messageFingerprints.clear();
            addMessage("暂无消息", false);
            processedServerMessageCount = 0;
            currentSince = 0;
            adapter.refreshData();
            hideLoadMoreHint();
            return;
        }
        
        int since = Math.max(1, totalMessageCount - PAGE_SIZE + 1);
        currentSince = since;
        
        Log.d(TAG, "Loading: since=" + since + ", total=" + totalMessageCount);
        
        apiClient.getNewMessages(currentSessionId, since, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    messages.clear();
                    messageFingerprints.clear();
                    
                    if (chatMessages.isEmpty()) {
                        addMessage("暂无消息", false);
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
                    
                    processedServerMessageCount = totalMessageCount;
                    sessionManager.updateFirstMessageIndex(currentSessionId, currentSince);
                    adapter.refreshData();
                    
                    Log.d(TAG, "Loaded: local=" + messages.size() + ", currentSince=" + currentSince);
                    
                    // 初始加载滚动到底部
                    scrollToBottom();
                    
                    // 延迟确保滚动完成
                    rvMessages.postDelayed(() -> scrollToBottom(), 100);
                    rvMessages.postDelayed(() -> scrollToBottom(), 300);
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    messages.clear();
                    messageFingerprints.clear();
                    addMessage("加载失败: " + error, false);
                    currentSince = 0;
                    adapter.refreshData();
                    hideLoadMoreHint();
                });
            }
        });
    }
    
    /**
     * 加载更早消息
     */
    private void loadOlderMessages() {
        if (!canLoadMore()) {
            isLoadingOlder = false;
            hideLoadMoreHint();
            Toast.makeText(this, "已到第一条消息", Toast.LENGTH_SHORT).show();
            return;
        }
        
        int newSince = Math.max(1, currentSince - PAGE_SIZE);
        if (newSince >= currentSince) {
            isLoadingOlder = false;
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
                        hideLoadMoreHint();
                        Toast.makeText(ChatActivity.this, "已到第一条消息", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    // 记录加载前第一条可显示消息的 created
                    long targetCreated = firstVisibleMessageCreated;
                    
                    // 添加消息到开头
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
                            
                            if (msg.content != null && !msg.content.isEmpty() && !"tool".equals(msg.role)) {
                                newDisplayableCount++;
                            }
                        }
                    }
                    
                    currentSince = newSince;
                    sessionManager.updateFirstMessageIndex(currentSessionId, newSince);
                    adapter.refreshData();
                    
                    // 恢复位置：找到原来的第一条消息的新位置
                    restorePositionByTimestamp();
                    
                    Log.d(TAG, "Loaded older: newSince=" + newSince + ", restored position");
                    
                    // 显示结果
                    if (canLoadMore()) {
                        showLoadMoreHint("已加载 " + newDisplayableCount + " 条");
                        rvMessages.postDelayed(() -> {
                            LinearLayoutManager lm = (LinearLayoutManager) rvMessages.getLayoutManager();
                            if (lm != null) {
                                int firstVisible = lm.findFirstVisibleItemPosition();
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
                    
                    // 恢复定时刷新
                    startAutoRefresh();
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
    
    /**
     * 检查最后消息更新（流式回复）
     */
    private void checkLastMessageUpdate() {
        if (!userAtBottom) return;  // 用户不在底部，不检查
        
        int sinceIndex = processedServerMessageCount + 1;
        
        apiClient.getNewMessages(currentSessionId, sinceIndex, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    if (chatMessages.isEmpty()) return;
                    
                    // 找最后一条可显示消息
                    ApiClient.ChatMessage latestFiltered = null;
                    for (int i = chatMessages.size() - 1; i >= 0; i--) {
                        ApiClient.ChatMessage msg = chatMessages.get(i);
                        if (msg.content != null && !msg.content.isEmpty() && !"tool".equals(msg.role)) {
                            latestFiltered = msg;
                            break;
                        }
                    }
                    
                    if (latestFiltered == null) return;
                    
                    // 更新最后消息
                    if (messages.size() > 0) {
                        int lastIdx = messages.size() - 1;
                        String lastContent = messages.get(lastIdx).getContent();
                        
                        if (!latestFiltered.content.equals(lastContent)) {
                            messages.set(lastIdx, new Message(latestFiltered.content, "user".equals(latestFiltered.role), latestFiltered.created * 1000L));
                            messageFingerprints.put(latestFiltered.created, latestFiltered.content);
                            adapter.refreshData();
                            
                            // 用户在底部，跟随滚动
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
        // 记录当前位置
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
        processedServerMessageCount = 0;
        currentSince = 0;
        sessionManager.updateFirstMessageIndex(currentSessionId, 0);
        hideLoadMoreHint();
        loadMessagesPage();
    }
    
    /**
     * 滚动到底部（最后一个 item 显示，但不强制置顶）
     */
    private void scrollToBottom() {
        if (rvMessages == null || adapter == null) return;
        int itemCount = adapter.getItemCount();
        if (itemCount > 0) {
            rvMessages.scrollToPosition(itemCount - 1);
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
        String role = isUser ? "user" : "assistant";
        messages.add(new Message(content, role, System.currentTimeMillis()));
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
        addMessage(content, true);
        userAtBottom = true;  // 发送后标记用户在底部
        
        apiClient.sendMessage(currentSessionId, content, new ApiClient.MessageCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Message sent successfully");
                refreshSessionStatus();
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "发送失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
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
                    processedServerMessageCount = 0;
                    currentSince = 0;
                    adapter.refreshData();
                    addMessage("会话已清空", false);
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
    
    /**
     * 打开预览 URL
     * 在浏览器中打开当前会话的预览页面
     */
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
