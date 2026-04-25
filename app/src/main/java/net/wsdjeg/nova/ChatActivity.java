package net.wsdjeg.nova;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.util.List;

/**
 * 聊天界面 Activity
 * 显示单个会话的消息列表
 */
public class ChatActivity extends AppCompatActivity {
    
    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_SESSION_TITLE = "session_title";
    
    private static final int REFRESH_INTERVAL_MS = 3000; // 3秒刷新一次
    private static final int STATE_NORMAL = 0;
    private static final int STATE_SENDING = 1;
    
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
    
    // 自动刷新相关
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private boolean isAutoRefreshEnabled = true;
    private int lastMessageCount = 0;
    
    // 按钮状态
    private int buttonState = STATE_NORMAL;
    private boolean isInProgress = false; // 从 API 获取的会话状态

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
        
        // 获取当前激活账号
        Account activeAccount = accountManager.getActiveAccount();
        if (activeAccount == null) {
            Toast.makeText(this, "请先添加账号", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        accountId = activeAccount.getId();
        
        // 使用当前账号的 URL 和 API Key 创建 ApiClient
        String baseUrl = activeAccount.getUrl();
        String apiKey = activeAccount.getApiKey();
        
        if (baseUrl == null || baseUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            Toast.makeText(this, "账号配置不完整，请检查 URL 和 API Key", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        apiClient = new ApiClient(baseUrl, apiKey);
        
        // 保存当前 session
        settingsManager.setSession(currentSessionId);
        sessionManager.saveCurrentSession(currentSessionId);
        
        // 加载会话信息
        loadSessionInfo();
        
        initViews();
        setupRecyclerView();
        setupAutoRefresh();
        
        // 加载消息
        addMessage("正在加载消息...", false);
        refreshMessages();
    }
    
    /**
     * 加载会话信息并显示在顶部
     */
    private void loadSessionInfo() {
        // 从 SessionManager 获取会话信息
        Session session = sessionManager.getSession(currentSessionId);
        
        if (session != null) {
            updateSessionInfo(session);
        } else {
            // 从 API 获取会话详情
            apiClient.getSessions(new ApiClient.SessionsCallback() {
                @Override
                public void onSuccess(List<Session> sessions) {
                    runOnUiThread(() -> {
                        for (Session s : sessions) {
                            if (s.getSessionId().equals(currentSessionId)) {
                                sessionManager.updateSession(s);
                                updateSessionInfo(s);
                                break;
                            }
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    // 如果无法获取，显示默认信息
                    runOnUiThread(() -> {
                        tvSessionTitle.setText("未知会话");
                        tvSessionInfo.setText("unknown | unknown");
                        tvSessionPath.setText("CWD: unknown");
                    });
                }
            });
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
        
        // 第一行：会话标题（粗体、白色）
        tvSessionTitle.setText(title != null && !title.isEmpty() ? title : "新会话");
        
        // 第二行：provider | model（不要前面的标签）
        String infoLine = (provider != null ? provider : "unknown") 
                        + " | " 
                        + (model != null ? model : "unknown");
        tvSessionInfo.setText(infoLine);
        
        // 第三行：CWD: session.cwd
        String pathLine = "CWD: " + (cwd != null ? cwd : "unknown");
        tvSessionPath.setText(pathLine);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (isAutoRefreshEnabled && apiClient != null) {
            startAutoRefresh();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        stopAutoRefresh();
        
        // 离开聊天界面时，保存当前消息数作为已读数
        // 这样返回会话列表时，该会话不会显示未读数
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
            // 返回按钮
            onBackPressed();
            return true;
        } else if (itemId == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (itemId == R.id.action_refresh) {
            refreshMessages();
            Toast.makeText(this, "已刷新", Toast.LENGTH_SHORT).show();
            return true;
        } else if (itemId == R.id.action_preview) {
            // 预览：使用浏览器打开当前会话
            openPreviewInBrowser();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * 使用浏览器打开预览链接
     */
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
        
        // 初始化按钮状态
        setButtonStateNormal();
    }
    
    /**
     * 发送按钮点击处理
     * 根据当前状态决定是发送消息还是停止生成
     */
    private void onSendButtonClick() {
        if (buttonState == STATE_SENDING) {
            // 当前正在发送，点击停止
            stopGeneration();
        } else {
            // 正常状态，发送消息
            sendMessage();
        }
    }
    
    /**
     * 设置按钮为"停止"状态（发送中）
     */
    private void setButtonStateSending() {
        buttonState = STATE_SENDING;
        btnSend.setText("⏹ 停止");
        btnSend.setBackgroundColor(Color.parseColor("#F44336")); // 红色
        btnSend.setEnabled(true);
    }
    
    /**
     * 设置按钮为"发送"状态（正常）
     */
    private void setButtonStateNormal() {
        buttonState = STATE_NORMAL;
        btnSend.setText("发送");
        btnSend.setBackgroundColor(Color.parseColor("#2196F3")); // 蓝色
        btnSend.setEnabled(true);
    }
    
    private void setupRecyclerView() {
        messages = new ArrayList<>();
        adapter = new MessageAdapter(messages, this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(adapter);
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
     */
    private void refreshMessages() {
        if (apiClient == null || currentSessionId == null) {
            return;
        }
        
        // 同时获取消息和会话状态
        refreshSessionStatus();
        
        apiClient.getMessages(currentSessionId, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    if (chatMessages.size() != lastMessageCount) {
                        updateMessagesList(chatMessages);
                        lastMessageCount = chatMessages.size();
                        
                        // 更新 SessionManager 中的消息信息
                        if (!chatMessages.isEmpty()) {
                            ApiClient.ChatMessage lastMsg = chatMessages.get(chatMessages.size() - 1);
                            // 查找第一条用户消息作为标题
                            String firstUserMessage = null;
                            for (ApiClient.ChatMessage msg : chatMessages) {
                                if ("user".equals(msg.role)) {
                                    firstUserMessage = msg.content;
                                    break;
                                }
                            }
                            sessionManager.updateMessages(
                                currentSessionId,
                                firstUserMessage,  // 第一条用户消息作为标题
                                lastMsg.content,
                                chatMessages.size(),
                                lastMsg.created * 1000L  // lastMessageTime in ms
                            );
                            
                            // 更新顶部标题
                            if (firstUserMessage != null) {
                                tvSessionTitle.setText(firstUserMessage);
                            }
                        }
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (messages.size() <= 1) {
                    runOnUiThread(() -> 
                        addMessage("无法加载消息: " + error + "\n\n请检查网络连接和API配置。", false));
                }
            }
        });
    }
    
    /**
     * 刷新会话状态（检查是否正在生成）
     */
    private void refreshSessionStatus() {
        apiClient.getSessions(new ApiClient.SessionsCallback() {
            @Override
            public void onSuccess(List<Session> sessions) {
                runOnUiThread(() -> {
                    for (Session session : sessions) {
                        if (session.getSessionId().equals(currentSessionId)) {
                            boolean wasInProgress = isInProgress;
                            isInProgress = session.isInProgress();
                            
                            // 更新按钮状态
                            if (isInProgress) {
                                setButtonStateSending();
                            } else {
                                // 从进行中变为非进行中，说明响应完成或被停止
                                if (wasInProgress) {
                                    // 刷新消息以获取最新响应
                                    lastMessageCount = 0; // 强制刷新
                                    refreshMessages();
                                }
                                setButtonStateNormal();
                            }
                            break;
                        }
                    }
                });
            }

            @Override
            public void onError(String error) {
                // 忽略错误，保持当前状态
            }
        });
    }
    
    private void updateMessagesList(List<ApiClient.ChatMessage> chatMessages) {
        int scrollPosition = ((LinearLayoutManager) rvMessages.getLayoutManager())
                .findFirstVisibleItemPosition();
        boolean shouldScrollToBottom = scrollPosition >= messages.size() - 2;
        
        messages.clear();
        
        for (ApiClient.ChatMessage msg : chatMessages) {
            // 过滤掉 role == "tool" 的消息
            if ("tool".equals(msg.role)) {
                continue;
            }
            boolean isUser = "user".equals(msg.role);
            // Lua os.time() 返回的是秒，Java 需要毫秒
            long timestamp = msg.created * 1000L;
            messages.add(new Message(msg.content, isUser, timestamp));
        }
        
        adapter.notifyDataSetChanged();
        
        if (shouldScrollToBottom && messages.size() > 0) {
            // 直接跳转到底部，避免长时间滚动动画
            rvMessages.scrollToPosition(messages.size() - 1);
        }
    }
    
    /**
     * 添加消息到列表
     * @param content 消息内容
     * @param isUser 是否为用户消息
     */
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
        messages.add(new Message(content, isUser, timestamp));
        
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
        
        // 设置按钮为"停止"状态
        setButtonStateSending();
        isInProgress = true;
        
        // 设置会话为进行中状态
        sessionManager.setSessionInProgress(currentSessionId, true);
        
        // 使用带 sessionId 参数的 sendMessage 方法
        apiClient.sendMessage(currentSessionId, content, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                runOnUiThread(() -> {
                    // 设置会话为非进行中状态
                    sessionManager.setSessionInProgress(currentSessionId, false);
                    isInProgress = false;
                    setButtonStateNormal();
                    refreshMessages();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    // 设置会话为非进行中状态
                    sessionManager.setSessionInProgress(currentSessionId, false);
                    isInProgress = false;
                    setButtonStateNormal();
                    addMessage("错误: " + error, false);
                    Toast.makeText(ChatActivity.this, "请求失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
        rvMessages.smoothScrollToPosition(messages.size() - 1);
    }
    
    /**
     * 停止生成
     */
    private void stopGeneration() {
        if (apiClient == null || currentSessionId == null) {
            return;
        }
        
        btnSend.setEnabled(false);
        btnSend.setText("停止中...");
        
        apiClient.stopSession(currentSessionId, new ApiClient.StopCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "已停止生成", Toast.LENGTH_SHORT).show();
                    isInProgress = false;
                    setButtonStateNormal();
                    // 刷新消息
                    lastMessageCount = 0;
                    refreshMessages();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "停止失败: " + error, Toast.LENGTH_SHORT).show();
                    // 恢复按钮状态
                    if (isInProgress) {
                        setButtonStateSending();
                    } else {
                        setButtonStateNormal();
                    }
                });
            }
        });
    }
    
    /**
     * 重试最后一条消息
     */
    public void retryLastMessage() {
        if (apiClient == null || currentSessionId == null) {
            return;
        }
        
        // 设置按钮为"停止"状态
        setButtonStateSending();
        isInProgress = true;
        
        apiClient.retrySession(currentSessionId, new ApiClient.RetryCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "已重新发送", Toast.LENGTH_SHORT).show();
                    // 刷新消息
                    lastMessageCount = 0;
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
