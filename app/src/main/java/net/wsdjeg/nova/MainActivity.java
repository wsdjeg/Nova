package net.wsdjeg.nova;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    
    private static final int REFRESH_INTERVAL_MS = 5000; // 5秒刷新一次
    
    private RecyclerView rvMessages;
    private EditText etMessage;
    private Button btnSend;
    private MessageAdapter adapter;
    private List<Message> messages;
    private ApiClient apiClient;
    private SettingsManager settingsManager;
    
    // 自动刷新相关
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private boolean isAutoRefreshEnabled = true;
    private int lastMessageCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        settingsManager = new SettingsManager(this);
        apiClient = new ApiClient(settingsManager);
        
        initViews();
        setupRecyclerView();
        setupAutoRefresh();
        
        // Check if settings are configured
        if (settingsManager.hasValidSettings()) {
            addMessage("欢迎回来！正在加载消息...", false);
            // 立即加载一次消息
            refreshMessages();
        } else {
            addMessage("欢迎！点击右上角「Settings」配置 API。\n\n可用命令：\n• /sessions - 列出所有会话\n• /switch <session-id> - 切换会话\n• /refresh - 手动刷新\n• /pause - 暂停自动刷新\n• /resume - 恢复自动刷新\n• /clear - 清空消息\n• /help - 显示帮助", false);
            isAutoRefreshEnabled = false;
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Reload settings when returning from settings activity
        settingsManager = new SettingsManager(this);
        apiClient = new ApiClient(settingsManager);
        
        // 如果配置有效，恢复自动刷新
        if (settingsManager.hasValidSettings() && isAutoRefreshEnabled) {
            startAutoRefresh();
        }
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initViews() {
        rvMessages = findViewById(R.id.rv_messages);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        
        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void setupRecyclerView() {
        messages = new ArrayList<>();
        adapter = new MessageAdapter(messages);
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
                if (isAutoRefreshEnabled && settingsManager.hasValidSettings()) {
                    refreshMessages();
                }
                // 继续下一次刷新
                refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
            }
        };
    }
    
    private void startAutoRefresh() {
        // 移除之前的回调，避免重复
        refreshHandler.removeCallbacks(refreshRunnable);
        // 立即执行一次
        refreshHandler.post(refreshRunnable);
    }
    
    private void stopAutoRefresh() {
        if (refreshHandler != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }
    
    /**
     * 刷新消息列表 - 自动获取并更新界面
     */
    private void refreshMessages() {
        if (!settingsManager.hasValidSettings()) {
            return;
        }
        
        String session = settingsManager.getSession();
        if (session == null || session.isEmpty()) {
            return;
        }
        
        apiClient.getMessages(session, new ApiClient.MessagesCallback() {
            @Override
            public void onSuccess(List<ApiClient.ChatMessage> chatMessages) {
                runOnUiThread(() -> {
                    // 只有消息数量变化时才更新界面
                    if (chatMessages.size() != lastMessageCount) {
                        updateMessagesList(chatMessages);
                        lastMessageCount = chatMessages.size();
                    }
                });
            }

            @Override
            public void onError(String error) {
                // 静默失败，不干扰用户
                // 只有首次加载失败时才提示
                if (messages.size() <= 1) {
                    runOnUiThread(() -> 
                        addMessage("无法加载消息: " + error + "\n\n请检查网络连接和API配置。", false));
                }
            }
        });
    }
    
    /**
     * 更新消息列表界面
     */
    private void updateMessagesList(List<ApiClient.ChatMessage> chatMessages) {
        // 保存当前滚动位置
        int scrollPosition = ((LinearLayoutManager) rvMessages.getLayoutManager())
                .findFirstVisibleItemPosition();
        boolean shouldScrollToBottom = scrollPosition >= messages.size() - 2;
        
        // 清空并重新填充
        messages.clear();
        
        for (ApiClient.ChatMessage msg : chatMessages) {
            // role: "user" 或 "assistant"
            // 显示格式: [角色]: 内容
            String displayText = String.format("[%s]:\n%s", 
                    msg.role, msg.content);
            boolean isUser = "user".equals(msg.role);
            
            messages.add(new Message(displayText, isUser));
        }
        
        // 通知适配器更新
        adapter.notifyDataSetChanged();
        
        // 如果之前在底部，自动滚动到底部
        if (shouldScrollToBottom && messages.size() > 0) {
            rvMessages.smoothScrollToPosition(messages.size() - 1);
        }
    }

    private void sendMessage() {
        String messageText = etMessage.getText().toString().trim();
        
        if (messageText.isEmpty()) {
            Toast.makeText(this, "请输入消息", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Add user message to display
        addMessage("[user]:\n" + messageText, true);
        etMessage.setText("");
        
        // Check if it's a command
        if (messageText.startsWith("/")) {
            handleCommand(messageText);
        } else {
            // Normal message - send to API
            sendChatMessage(messageText);
        }
    }

    private void handleCommand(String command) {
        String[] parts = command.split("\\s+");
        String cmd = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        
        switch (cmd) {
            case "/sessions":
                handleSessionsCommand();
                break;
                
            case "/switch":
                if (args.length == 0) {
                    addMessage("用法: /switch <session-id>\n示例: /switch 2024-01-15-10-30-00", false);
                } else {
                    handleSwitchSession(args[0]);
                }
                break;
                
            case "/refresh":
                refreshMessages();
                addMessage("手动刷新中...", false);
                break;
                
            case "/pause":
                isAutoRefreshEnabled = false;
                stopAutoRefresh();
                addMessage("已暂停自动刷新\n输入 /resume 恢复", false);
                break;
                
            case "/resume":
                isAutoRefreshEnabled = true;
                startAutoRefresh();
                addMessage("已恢复自动刷新（每5秒）", false);
                break;
                
            case "/help":
                showHelp();
                break;
                
            case "/clear":
                clearMessages();
                break;
                
            case "/settings":
                showCurrentSettings();
                break;
                
            default:
                addMessage("未知命令: " + cmd + "\n输入 /help 查看可用命令", false);
                break;
        }
    }
    
    private void handleSwitchSession(String sessionId) {
        settingsManager.setSession(sessionId);
        lastMessageCount = 0; // 重置计数，强制刷新
        addMessage("已切换到会话: " + sessionId + "\n正在加载消息...", false);
        
        // 立即刷新
        refreshMessages();
        
        // 恢复自动刷新
        if (!isAutoRefreshEnabled) {
            isAutoRefreshEnabled = true;
            startAutoRefresh();
        }
    }

    private void handleSessionsCommand() {
        if (!checkApiSettings()) {
            addMessage("请先配置 API 设置（URL 和 API Key）", false);
            return;
        }
        
        btnSend.setEnabled(false);
        addMessage("正在获取会话列表...", false);
        
        apiClient.getSessions(new ApiClient.SessionsCallback() {
            @Override
            public void onSuccess(String[] sessions) {
                runOnUiThread(() -> {
                    if (sessions.length == 0) {
                        addMessage("当前没有活动的会话", false);
                    } else {
                        StringBuilder sb = new StringBuilder();
                        sb.append("活动会话 (共 ").append(sessions.length).append(" 个):\n\n");
                        for (int i = 0; i < sessions.length; i++) {
                            sb.append("• ").append(sessions[i]);
                            if (sessions[i].equals(settingsManager.getSession())) {
                                sb.append(" ✓ (当前)");
                            }
                            sb.append("\n");
                        }
                        sb.append("\n使用 /switch <session-id> 切换会话");
                        addMessage(sb.toString(), false);
                    }
                    btnSend.setEnabled(true);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    addMessage("获取会话失败: " + error, false);
                    btnSend.setEnabled(true);
                });
            }
        });
    }

    private void showHelp() {
        String helpText = "可用命令:\n\n" +
                         "• /sessions - 列出所有活动会话\n" +
                         "• /switch <id> - 切换到指定会话\n" +
                         "• /refresh - 手动刷新消息\n" +
                         "• /pause - 暂停自动刷新\n" +
                         "• /resume - 恢复自动刷新\n" +
                         "• /settings - 显示当前配置\n" +
                         "• /clear - 清空消息列表\n" +
                         "• /help - 显示此帮助信息\n\n" +
                         "提示:\n" +
                         "• 界面每5秒自动刷新\n" +
                         "• 普通消息会发送到当前 Session";
        addMessage(helpText, false);
    }

    private void clearMessages() {
        messages.clear();
        lastMessageCount = 0;
        adapter.notifyDataSetChanged();
        addMessage("消息已清空", false);
    }

    private void showCurrentSettings() {
        String url = settingsManager.getUrl();
        String port = settingsManager.getPort();
        String apiKey = settingsManager.getApiKey();
        String session = settingsManager.getSession();
        
        // Mask API key for security
        String maskedKey = apiKey.isEmpty() ? "(未设置)" : 
                          apiKey.length() > 8 ? apiKey.substring(0, 8) + "..." : "***";
        
        StringBuilder sb = new StringBuilder();
        sb.append("当前配置:\n\n");
        sb.append("URL: ").append(url.isEmpty() ? "(未设置)" : url).append("\n");
        sb.append("端口: ").append(port.isEmpty() ? "(默认)" : port).append("\n");
        sb.append("API Key: ").append(maskedKey).append("\n");
        sb.append("Session: ").append(session.isEmpty() ? "(未设置)" : session).append("\n\n");
        sb.append("完整 URL: ").append(settingsManager.getFullUrl()).append("\n\n");
        sb.append("自动刷新: ").append(isAutoRefreshEnabled ? "开启" : "关闭");
        
        addMessage(sb.toString(), false);
    }

    private void sendChatMessage(String content) {
        if (!checkApiSettings()) {
            addMessage("请先配置 API 设置", false);
            return;
        }
        
        String session = settingsManager.getSession();
        if (session == null || session.isEmpty()) {
            addMessage("请配置 Session ID（在设置中或使用 /switch 命令）", false);
            return;
        }
        
        btnSend.setEnabled(false);
        
        apiClient.sendMessage(content, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                runOnUiThread(() -> {
                    // 发送成功后立即刷新消息列表
                    refreshMessages();
                    btnSend.setEnabled(true);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    addMessage("错误: " + error, false);
                    btnSend.setEnabled(true);
                    Toast.makeText(MainActivity.this, "请求失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private boolean checkApiSettings() {
        String url = settingsManager.getFullUrl();
        String apiKey = settingsManager.getApiKey();
        return !url.isEmpty() && !apiKey.isEmpty();
    }

    private void addMessage(String text, boolean isUser) {
        messages.add(new Message(text, isUser));
        adapter.notifyItemInserted(messages.size() - 1);
        rvMessages.smoothScrollToPosition(messages.size() - 1);
    }
}
