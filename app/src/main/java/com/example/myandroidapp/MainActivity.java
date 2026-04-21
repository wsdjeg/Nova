package com.example.myandroidapp;

import android.content.Intent;
import android.os.Bundle;
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
    
    private RecyclerView rvMessages;
    private EditText etMessage;
    private Button btnSend;
    private MessageAdapter adapter;
    private List<Message> messages;
    private ApiClient apiClient;
    private SettingsManager settingsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        settingsManager = new SettingsManager(this);
        apiClient = new ApiClient(settingsManager);
        
        initViews();
        setupRecyclerView();
        
        // Check if settings are configured
        if (settingsManager.hasValidSettings()) {
            addMessage("欢迎回来！输入 /help 查看可用命令。", false);
        } else {
            addMessage("欢迎！点击右上角「Settings」配置 API。\n\n可用命令：\n• /sessions - 列出所有会话\n• /session <id> - 查看会话预览\n• /help - 显示帮助", false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload settings when returning from settings activity
        settingsManager = new SettingsManager(this);
        apiClient = new ApiClient(settingsManager);
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

    private void sendMessage() {
        String messageText = etMessage.getText().toString().trim();
        
        if (messageText.isEmpty()) {
            Toast.makeText(this, "请输入消息", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Add user message to display
        addMessage(messageText, true);
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
                
            case "/session":
                if (args.length == 0) {
                    addMessage("用法: /session <session-id>\n示例: /session 2024-01-15-10-30-00", false);
                } else {
                    handleSessionPreviewCommand(args[0]);
                }
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

    private void handleSessionPreviewCommand(String sessionId) {
        if (!checkApiSettings()) {
            addMessage("请先配置 API 设置（URL 和 API Key）", false);
            return;
        }
        
        btnSend.setEnabled(false);
        addMessage("正在获取会话预览...", false);
        
        apiClient.getSessionPreview(sessionId, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String html) {
                runOnUiThread(() -> {
                    // HTML preview is too long for chat display
                    // Show a summary message instead
                    addMessage("会话 " + sessionId + " 预览已获取\n\n" +
                               "(HTML 内容较大，建议在浏览器中查看)", false);
                    btnSend.setEnabled(true);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    addMessage("获取预览失败: " + error, false);
                    btnSend.setEnabled(true);
                });
            }
        });
    }

    private void showHelp() {
        String helpText = "可用命令:\n\n" +
                         "• /sessions - 列出所有活动会话\n" +
                         "• /session <id> - 查看指定会话预览\n" +
                         "• /settings - 显示当前配置\n" +
                         "• /clear - 清空消息列表\n" +
                         "• /help - 显示此帮助信息\n\n" +
                         "提示:\n" +
                         "• 普通消息会发送到配置的 Session\n" +
                         "• Session ID 格式: YYYY-MM-DD-HH-MM-SS";
        addMessage(helpText, false);
    }

    private void clearMessages() {
        messages.clear();
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
        sb.append("完整 URL: ").append(settingsManager.getFullUrl());
        
        addMessage(sb.toString(), false);
    }

    private void sendChatMessage(String content) {
        if (!checkApiSettings()) {
            addMessage("请先配置 API 设置", false);
            return;
        }
        
        String session = settingsManager.getSession();
        if (session == null || session.isEmpty()) {
            addMessage("请配置 Session ID（在设置中或使用命令）", false);
            return;
        }
        
        btnSend.setEnabled(false);
        
        apiClient.sendMessage(content, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response) {
                runOnUiThread(() -> {
                    addMessage(response, false);
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
