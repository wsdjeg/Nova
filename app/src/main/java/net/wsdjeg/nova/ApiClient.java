package net.wsdjeg.nova;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * API 客户端
 * 支持多账号：可以指定 baseUrl 和 apiKey，或使用 SettingsManager 的默认设置
 */
public class ApiClient {
    private static final String TAG = "ApiClient";
    
    private final SettingsManager settingsManager;
    private final String overrideBaseUrl;
    private final String overrideApiKey;
    private String sessionId;
    
    public interface ApiCallback {
        void onSuccess(String response);
        void onError(String error);
    }
    
    public interface MessageCallback {
        void onSuccess();
        void onError(String error);
    }
    
    public interface SessionsCallback {
        void onSuccess(List<Session> sessions);
        void onError(String error);
    }
    
    public interface MessagesCallback {
        void onSuccess(List<ChatMessage> messages);
        void onError(String error);
    }
    
    public interface CreateSessionCallback {
        void onSuccess(Session session);
        void onError(String error);
    }
    
    public interface DeleteSessionCallback {
        void onSuccess();
        void onError(String error);
    }
    
    public interface StopCallback {
        void onSuccess();
        void onError(String error);
    }
    
    public interface RetryCallback {
        void onSuccess();
        void onError(String error);
    }
    
    public interface UpdateSessionCallback {
        void onSuccess();
        void onError(String error);
    }
    
    public interface ClearCallback {
        void onSuccess();
        void onError(String error);
    }
    
    public interface ProvidersCallback {
        void onSuccess(List<Provider> providers);
        void onError(String error);
    }
    
    /**
     * 获取单个会话的回调接口
     * 用于 GET /sessions/:id API
     */
    public interface SessionCallback {
        void onSuccess(Session session);
        void onError(String error);
    }
    
    public static class Provider {
        public String name;
        public List<String> models;
        
        public Provider(String name, List<String> models) {
            this.name = name;
            this.models = models;
        }
    }
    
    /**
     * 工具调用参数
     */
    public static class ToolCallFunction {
        public String name;
        public String arguments; // JSON 字符串
        
        public ToolCallFunction(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }
    }
    
    /**
     * 工具调用
     */
    public static class ToolCall {
        public String id;
        public String type;
        public ToolCallFunction function;
        
        public ToolCall(String id, String type, ToolCallFunction function) {
            this.id = id;
            this.type = type;
            this.function = function;
        }
    }
    
    /**
     * 工具调用状态（用于工具结果消息）
     */
    public static class ToolCallState {
        public String name;
        public String error;
        
        public ToolCallState(String name, String error) {
            this.name = name;
            this.error = error;
        }
    }
    
    /**
     * 聊天消息数据模型
     * 包含 role、content、error、created、tool_calls 和 tool_call_state 字段
     * 
     * 消息类型：
     * - 正常消息：有 role 和 content
     * - 错误消息：有 error 字段（无 role 或 role 为空）
     * - 工具调用消息：有 tool_calls（role=assistant）
     * - 工具结果消息：role=tool，有 tool_call_state 和 content
     */
    public static class ChatMessage {
        public String role;
        public String content;
        public String error;    // 错误消息字段
        public long created;
        public List<ToolCall> toolCalls;  // 工具调用（AI 调用工具时）
        public ToolCallState toolCallState;  // 工具状态（工具结果消息）
        
        public ChatMessage(String role, String content, long created) {
            this.role = role;
            this.content = content;
            this.error = null;
            this.created = created;
            this.toolCalls = null;
            this.toolCallState = null;
        }
        
        /**
         * 创建错误消息
         */
        public ChatMessage(String error, long created) {
            this.role = null;
            this.content = null;
            this.error = error;
            this.created = created;
            this.toolCalls = null;
            this.toolCallState = null;
        }
        
        /**
         * 创建带工具调用的消息
         */
        public ChatMessage(String role, String content, long created, List<ToolCall> toolCalls) {
            this.role = role;
            this.content = content;
            this.error = null;
            this.created = created;
            this.toolCalls = toolCalls;
            this.toolCallState = null;
        }
        
        /**
         * 创建工具结果消息
         */
        public static ChatMessage createToolResult(String content, long created, ToolCallState state) {
            ChatMessage msg = new ChatMessage("tool", content, created);
            msg.toolCallState = state;
            return msg;
        }
        
        /**
         * 是否是错误消息
         */
        public boolean isError() {
            return error != null && !error.isEmpty();
        }
        
        /**
         * 是否有工具调用
         */
        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
        
        /**
         * 是否是工具结果消息
         */
        public boolean isToolResult() {
            return "tool".equals(role) && toolCallState != null;
        }
        
        /**
         * 是否是工具错误消息
         */
        public boolean isToolError() {
            return "tool".equals(role) && toolCallState != null 
                && toolCallState.error != null && !toolCallState.error.isEmpty();
        }
        
        /**
         * 是否有可显示的内容（content 或 error）
         */
        public boolean hasDisplayableContent() {
            return (content != null && !content.isEmpty()) || isError();
        }
    }
    
    public ApiClient(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
        this.overrideBaseUrl = null;
        this.overrideApiKey = null;
    }
    
    public ApiClient(String baseUrl, String apiKey) {
        this.settingsManager = null;
        this.overrideBaseUrl = baseUrl;
        this.overrideApiKey = apiKey;
    }
    
    public void setSession(String sessionId) {
        this.sessionId = sessionId;
    }
    
    private String getBaseUrl() {
        if (overrideBaseUrl != null) {
            return overrideBaseUrl;
        }
        if (settingsManager != null) {
            return settingsManager.getFullUrl();
        }
        return "";
    }
    
    private String getApiKey() {
        if (overrideApiKey != null) {
            return overrideApiKey;
        }
        if (settingsManager != null) {
            return settingsManager.getApiKey();
        }
        return "";
    }
    
    private String getSession() {
        if (settingsManager != null) {
            return settingsManager.getSession();
        }
        return "";
    }
    
    public boolean hasValidSettings() {
        return !getBaseUrl().isEmpty() && !getApiKey().isEmpty();
    }
    
    public void sendMessage(String sessionId, String content, ApiCallback callback) {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (baseUrl.isEmpty()) {
            callback.onError("Please configure API URL in settings");
            return;
        }
        
        if (apiKey.isEmpty()) {
            callback.onError("Please configure API Key in settings");
            return;
        }
        
        if (sessionId == null || sessionId.isEmpty()) {
            callback.onError("Session ID is required");
            return;
        }
        
        new Thread(() -> {
            HttpURLConnection conn = null;
            BufferedReader br = null;
            try {
                URL url = new URL(baseUrl + "/");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setUseCaches(false);

                JSONObject requestBody = new JSONObject();
                requestBody.put("session", sessionId);
                requestBody.put("content", content);
                
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                
                if (responseCode == 204) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess("Message sent successfully"));
                } else if (responseCode == 200 || responseCode == 201) {
                    br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    
                    String result = response.toString();
                    try {
                        JSONObject jsonResponse = new JSONObject(result);
                        result = jsonResponse.optString("response", result);
                    } catch (Exception e) {
                        // Ignore JSON parsing errors
                    }
                    
                    final String finalResult = result;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess(finalResult));
                } else {
                    String errorMessage;
                    if (responseCode == 401) {
                        errorMessage = "Unauthorized: Invalid API Key";
                    } else if (responseCode == 400) {
                        errorMessage = "Bad Request: Invalid message format";
                    } else if (responseCode == 404) {
                        errorMessage = "Not Found: Wrong endpoint or session not found";
                    } else {
                        errorMessage = "Error: " + responseCode;
                    }
                    
                    final String errorMsg = errorMessage;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError(errorMsg));
                }
            } catch (Exception e) {
                Log.e(TAG, "sendMessage failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            } finally {
                if (br != null) {
                    try { br.close(); } catch (Exception ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
    
    public void sendMessage(String content, ApiCallback callback) {
        String session = getSession();
        sendMessage(session, content, callback);
    }
    
    public void sendMessage(String sessionId, String content, MessageCallback callback) {
        sendMessage(sessionId, content, new ApiCallback() {
            @Override
            public void onSuccess(String response) {
                callback.onSuccess();
            }
            
            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }
    
    public void getSessions(String accountId, SessionsCallback callback) {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        new Thread(() -> {
            HttpURLConnection conn = null;
            BufferedReader br = null;
            try {
                URL url = new URL(baseUrl + "/sessions");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setUseCaches(false);
                
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    
                    JSONArray jsonArray = new JSONArray(response.toString());
                    List<Session> sessions = new ArrayList<>();
                    
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject sessionObj = jsonArray.getJSONObject(i);
                        String id = sessionObj.optString("id", "");
                        String title = sessionObj.optString("title", "");
                        String cwd = sessionObj.optString("cwd", "");
                        String provider = sessionObj.optString("provider", "");
                        String model = sessionObj.optString("model", "");
                        boolean inProgress = sessionObj.optBoolean("in_progress", false);
                        // 服务器返回的字段名是 "pin"，不是 "pinned"
                        boolean pinned = sessionObj.optBoolean("pin", false);
                        int messageCount = sessionObj.optInt("message_count", 0);
                        long lastMessageTime = System.currentTimeMillis();
                        String lastMessageContent = "";
                        String lastMessageRole = "";
                        
                        JSONObject lastMsgObj = sessionObj.optJSONObject("last_message");
                        if (lastMsgObj != null) {
                            lastMessageContent = lastMsgObj.optString("content", "");
                            lastMessageRole = lastMsgObj.optString("role", "");
                            lastMessageTime = lastMsgObj.optLong("created", System.currentTimeMillis()) * 1000;
                            Log.d(TAG, "Session " + id + " last_message: content=" + lastMessageContent + ", role=" + lastMessageRole);
                        } else {
                            Log.w(TAG, "Session " + id + " last_message is NULL!");
                        }
                        
                        if (!id.isEmpty()) {
                            Session session = new Session(id);
                            session.setAccountId(accountId);
                            session.setTitle(title);
                            session.setCwd(cwd);
                            session.setProvider(provider);
                            session.setModel(model);
                            session.setInProgress(inProgress);
                            session.setPinned(pinned);
                            session.setLastMessage(lastMessageContent);
                            session.setLastMessageRole(lastMessageRole);
                            session.setMessageCount(messageCount);
                            session.setLastMessageTime(lastMessageTime);
                            Log.d(TAG, "Session " + id + " preview: " + session.getPreview() + ", pinned: " + pinned);
                            sessions.add(session);
                        }
                    }
                    
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess(sessions));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "getSessions failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            } finally {
                if (br != null) {
                    try { br.close(); } catch (Exception ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
    
    /**
     * 获取单个会话详情
     * API 端点: GET /sessions/:id
     * 响应格式: { "id": "xxx", "title": "...", "cwd": "...", "provider": "...", "model": "...", "in_progress": false, "pin": false, "message_count": 5, "last_message": {...} }
     */
    public void getSession(String sessionId, String accountId, SessionCallback callback) {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        if (sessionId == null || sessionId.isEmpty()) {
            callback.onError("Session ID is required");
            return;
        }
        
        new Thread(() -> {
            HttpURLConnection conn = null;
            BufferedReader br = null;
            try {
                URL url = new URL(baseUrl + "/sessions/" + sessionId);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setUseCaches(false);
                
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    
                    JSONObject sessionObj = new JSONObject(response.toString());
                    String id = sessionObj.optString("id", "");
                    String title = sessionObj.optString("title", "");
                    String cwd = sessionObj.optString("cwd", "");
                    String provider = sessionObj.optString("provider", "");
                    String model = sessionObj.optString("model", "");
                    boolean inProgress = sessionObj.optBoolean("in_progress", false);
                    // 服务器返回的字段名是 "pin"，不是 "pinned"
                    boolean pinned = sessionObj.optBoolean("pin", false);
                    int messageCount = sessionObj.optInt("message_count", 0);
                    long lastMessageTime = System.currentTimeMillis();
                    String lastMessageContent = "";
                    String lastMessageRole = "";
                    
                    JSONObject lastMsgObj = sessionObj.optJSONObject("last_message");
                    if (lastMsgObj != null) {
                        lastMessageContent = lastMsgObj.optString("content", "");
                        lastMessageRole = lastMsgObj.optString("role", "");
                        lastMessageTime = lastMsgObj.optLong("created", System.currentTimeMillis()) * 1000;
                    }
                    
                    if (!id.isEmpty()) {
                        Session session = new Session(id);
                        session.setAccountId(accountId);
                        session.setTitle(title);
                        session.setCwd(cwd);
                        session.setProvider(provider);
                        session.setModel(model);
                        session.setInProgress(inProgress);
                        session.setPinned(pinned);
                        session.setLastMessage(lastMessageContent);
                        session.setLastMessageRole(lastMessageRole);
                        session.setMessageCount(messageCount);
                        session.setLastMessageTime(lastMessageTime);
                        
                        new Handler(Looper.getMainLooper()).post(() -> 
                            callback.onSuccess(session));
                    } else {
                        new Handler(Looper.getMainLooper()).post(() -> 
                            callback.onError("Invalid session response"));
                    }
                } else if (responseCode == 404) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Session not found"));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "getSession failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            } finally {
                if (br != null) {
                    try { br.close(); } catch (Exception ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
    
    public void getProviders(ProvidersCallback callback) {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        new Thread(() -> {
            HttpURLConnection conn = null;
            BufferedReader br = null;
            try {
                URL url = new URL(baseUrl + "/providers");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setUseCaches(false);
                
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    
                    JSONArray jsonArray = new JSONArray(response.toString());
                    List<Provider> providers = new ArrayList<>();
                    
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject providerObj = jsonArray.getJSONObject(i);
                        String name = providerObj.optString("name", "");
                        JSONArray modelsArray = providerObj.optJSONArray("models");
                        
                        if (!name.isEmpty()) {
                            List<String> models = new ArrayList<>();
                            if (modelsArray != null) {
                                for (int j = 0; j < modelsArray.length(); j++) {
                                    models.add(modelsArray.getString(j));
                                }
                            }
                            providers.add(new Provider(name, models));
                        }
                    }
                    
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess(providers));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "getProviders failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            } finally {
                if (br != null) {
                    try { br.close(); } catch (Exception ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
    
    /**
     * 创建新会话
     * API 端点: POST /session/new
     * 响应格式: { "id": "xxx", "cwd": "...", "provider": "...", "model": "...", "pin": false }
     */
    public void createSession(String cwd, String provider, String model, String accountId, CreateSessionCallback callback) {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        new Thread(() -> {
            HttpURLConnection conn = null;
            BufferedReader br = null;
            try {
                URL url = new URL(baseUrl + "/session/new");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setUseCaches(false);
                
                JSONObject requestBody = new JSONObject();
                if (cwd != null && !cwd.isEmpty()) {
                    requestBody.put("cwd", cwd);
                }
                if (provider != null && !provider.isEmpty()) {
                    requestBody.put("provider", provider);
                }
                if (model != null && !model.isEmpty()) {
                    requestBody.put("model", model);
                }
                
                OutputStream os = conn.getOutputStream();
                os.write(requestBody.toString().getBytes("UTF-8"));
                os.flush();
                os.close();
                
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    String sessionId = jsonResponse.optString("id", "");
                    
                    if (sessionId.isEmpty()) {
                        new Handler(Looper.getMainLooper()).post(() -> 
                            callback.onError("No session_id in response"));
                        return;
                    }
                    
                    String responseCwd = jsonResponse.optString("cwd", cwd != null ? cwd : "");
                    String responseProvider = jsonResponse.optString("provider", provider != null ? provider : "");
                    String responseModel = jsonResponse.optString("model", model != null ? model : "");
                    String responseTitle = jsonResponse.optString("title", "");
                    // 服务器返回的字段名是 "pin"，不是 "pinned"
                    boolean responsePinned = jsonResponse.optBoolean("pin", false);
                    int messageCount = jsonResponse.optInt("message_count", 0);
                    boolean inProgress = jsonResponse.optBoolean("in_progress", false);
                    
                    Session session = new Session(sessionId);
                    session.setAccountId(accountId);
                    session.setTitle(responseTitle);
                    session.setCwd(responseCwd);
                    session.setProvider(responseProvider);
                    session.setModel(responseModel);
                    session.setPinned(responsePinned);
                    session.setMessageCount(messageCount);
                    session.setInProgress(inProgress);
                    
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess(session));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else if (responseCode == 400) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Bad Request: Invalid parameters"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "createSession failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            } finally {
                if (br != null) {
                    try { br.close(); } catch (Exception ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
    
    public void createSession(String cwd, String provider, String model, CreateSessionCallback callback) {
        createSession(cwd, provider, model, null, callback);
    }
    
    public void deleteSession(String sessionId, DeleteSessionCallback callback) {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        if (sessionId == null || sessionId.isEmpty()) {
            callback.onError("Session ID is required");
            return;
        }
        
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(baseUrl + "/session/" + sessionId);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("DELETE");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setUseCaches(false);

                int responseCode = conn.getResponseCode();
                
                if (responseCode == 204) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess());
                } else if (responseCode == 404) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Session not found"));
                } else if (responseCode == 409) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Session is in progress, cannot delete"));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "deleteSession failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
    
    /**
     * 设置会话的工作目录
     * API 端点: PUT /session/:id/cwd
     */
    public void setSessionCwd(String sessionId, String cwd, UpdateSessionCallback callback) {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        if (sessionId == null || sessionId.isEmpty()) {
            callback.onError("Session ID is required");
            return;
        }
        
        if (cwd == null || cwd.isEmpty()) {
            callback.onError("CWD is required");
            return;
        }
        
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(baseUrl + "/session/" + sessionId + "/cwd");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setUseCaches(false);

                JSONObject requestBody = new JSONObject();
                requestBody.put("cwd", cwd);
                
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                
                if (responseCode == 204 || responseCode == 200) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess());
                } else if (responseCode == 404) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Session not found"));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else if (responseCode == 400) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Bad Request: Invalid cwd"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "setSessionCwd failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
    
    /**
     * 设置会话的置顶状态
     * API 端点: PUT /session/:id/pin
     * 请求格式: { "pin": true/false }
     */
    public void setSessionPinned(String sessionId, boolean pinned, UpdateSessionCallback callback) {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        if (sessionId == null || sessionId.isEmpty()) {
            callback.onError("Session ID is required");
            return;
        }
        
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                // 使用正确的端点: /session/:id/pin
                URL url = new URL(baseUrl + "/session/" + sessionId + "/pin");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setUseCaches(false);

                // 使用正确的参数名: pin
                JSONObject requestBody = new JSONObject();
                requestBody.put("pin", pinned);
                
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                
                if (responseCode == 204 || responseCode == 200) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess());
                } else if (responseCode == 404) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Session not found"));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else if (responseCode == 400) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Bad Request: Invalid pin value"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "setSessionPinned failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
    
    public void updateSession(String sessionId, String provider, String model, UpdateSessionCallback callback) {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        if (sessionId == null || sessionId.isEmpty()) {
            callback.onError("Session ID is required");
            return;
        }
        
        new Thread(() -> {
            AtomicBoolean allSuccess = new AtomicBoolean(true);
            AtomicReference<String> errorMsg = new AtomicReference<>("");
            
            if (provider != null && !provider.isEmpty()) {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(baseUrl + "/session/" + sessionId + "/provider");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("PUT");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("X-API-Key", apiKey);
                    conn.setRequestProperty("Connection", "close");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setUseCaches(false);

                    JSONObject requestBody = new JSONObject();
                    requestBody.put("provider", provider);
                    
                    try (OutputStream os = conn.getOutputStream()) {
                        byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }

                    int responseCode = conn.getResponseCode();
                    
                    if (responseCode != 204 && responseCode != 200) {
                        allSuccess.set(false);
                        if (responseCode == 404) {
                            errorMsg.set("Session not found");
                        } else if (responseCode == 401) {
                            errorMsg.set("Unauthorized: Invalid API Key");
                        } else if (responseCode == 400) {
                            errorMsg.set("Invalid provider");
                        } else {
                            errorMsg.set("Provider update failed: " + responseCode);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Update provider failed", e);
                    allSuccess.set(false);
                    errorMsg.set("Network error: " + e.getMessage());
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
            
            if (allSuccess.get() && model != null && !model.isEmpty()) {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(baseUrl + "/session/" + sessionId + "/model");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("PUT");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("X-API-Key", apiKey);
                    conn.setRequestProperty("Connection", "close");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setUseCaches(false);

                    JSONObject requestBody = new JSONObject();
                    requestBody.put("model", model);
                    
                    try (OutputStream os = conn.getOutputStream()) {
                        byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }

                    int responseCode = conn.getResponseCode();
                    
                    if (responseCode != 204 && responseCode != 200) {
                        allSuccess.set(false);
                        if (responseCode == 404) {
                            errorMsg.set("Session not found");
                        } else if (responseCode == 401) {
                            errorMsg.set("Unauthorized: Invalid API Key");
                        } else if (responseCode == 400) {
                            errorMsg.set("Invalid model");
                        } else {
                            errorMsg.set("Model update failed: " + responseCode);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Update model failed", e);
                    allSuccess.set(false);
                    errorMsg.set("Network error: " + e.getMessage());
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
            
            if (allSuccess.get()) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess());
            } else {
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(errorMsg.get()));
            }
        }).start();
    }
    
    /**
     * 解析消息 JSON 对象
     * 支持正常消息（role + content）、错误消息（error）、工具调用（tool_calls）、工具结果（role=tool）
     */
    private ChatMessage parseMessage(JSONObject msg) {
        String role = msg.optString("role", "");
        String content = msg.optString("content", "");
        String error = msg.optString("error", "");
        long created = msg.optLong("created", System.currentTimeMillis() / 1000);
        
        // 解析 tool_calls
        List<ToolCall> toolCalls = null;
        JSONArray toolCallsArray = msg.optJSONArray("tool_calls");
        if (toolCallsArray != null && toolCallsArray.length() > 0) {
            toolCalls = new ArrayList<>();
            Log.d(TAG, "parseMessage: found tool_calls array, length=" + toolCallsArray.length() + ", role=" + role);
            for (int i = 0; i < toolCallsArray.length(); i++) {
                try {
                    JSONObject tc = toolCallsArray.getJSONObject(i);
                    String id = tc.optString("id", "");
                    String type = tc.optString("type", "function");
                    
                    JSONObject funcObj = tc.optJSONObject("function");
                    if (funcObj != null) {
                        String name = funcObj.optString("name", "");
                        String args = funcObj.optString("arguments", "");
                        toolCalls.add(new ToolCall(id, type, new ToolCallFunction(name, args)));
                        Log.d(TAG, "parseMessage: parsed tool_call[" + i + "] id=" + id + ", name=" + name);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to parse tool_call: " + e.getMessage());
                }
            }
        }
        
        // 解析 tool_call_state (用于 role=tool 的消息)
        ToolCallState toolCallState = null;
        JSONObject stateObj = msg.optJSONObject("tool_call_state");
        if (stateObj != null) {
            String name = stateObj.optString("name", "");
            String stateError = stateObj.optString("error", "");
            toolCallState = new ToolCallState(name, stateError);
        }
        
        // 如果有 error 字段，创建错误消息
        if (!error.isEmpty()) {
            return new ChatMessage(error, created);
        }
        
        // 如果是工具结果消息
        if ("tool".equals(role) && toolCallState != null) {
            return ChatMessage.createToolResult(content, created, toolCallState);
        }
        
        // 如果有工具调用
        if (toolCalls != null && !toolCalls.isEmpty()) {
            Log.d(TAG, "parseMessage: creating ChatMessage with toolCalls, role=" + role + ", content=" + (content.isEmpty() ? "(empty)" : content.substring(0, Math.min(50, content.length()))));
            return new ChatMessage(role, content, created, toolCalls);
        }
        
        // 否则创建正常消息
        if (!content.isEmpty()) {
            return new ChatMessage(role, content, created);
        }
        
        // 无可显示内容，返回 null
        Log.d(TAG, "parseMessage: returning null for role=" + role + ", content empty=" + content.isEmpty() + ", toolCalls=" + (toolCalls == null ? "null" : toolCalls.size()));
        return null;
    }
    
    public void getMessages(String sessionId, MessagesCallback callback) {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = getSession();
            if (sessionId.isEmpty()) {
                callback.onError("Please configure Session ID");
                return;
            }
        }
        
        final String finalSessionId = sessionId;
        
        new Thread(() -> {
            HttpURLConnection conn = null;
            BufferedReader br = null;
            try {
                URL url = new URL(baseUrl + "/messages?session=" + finalSessionId);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setUseCaches(false);
                conn.setDoInput(true);
                
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    
                    JSONArray jsonArray = new JSONArray(response.toString());
                    List<ChatMessage> messages = new ArrayList<>();
                    
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject msg = jsonArray.getJSONObject(i);
                        ChatMessage chatMsg = parseMessage(msg);
                        if (chatMsg != null && (chatMsg.hasDisplayableContent() || chatMsg.hasToolCalls() || chatMsg.isToolResult())) {
                            messages.add(chatMsg);
                        }
                    }
                    
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess(messages));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else if (responseCode == 404) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Session not found"));
                } else if (responseCode == 400) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Bad Request: Missing session ID"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + code));
                }
            } catch (java.net.SocketException e) {
                Log.e(TAG, "getMessages SocketException: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Connection error: " + e.getMessage()));
            } catch (java.io.IOException e) {
                Log.e(TAG, "getMessages IOException: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "getMessages failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Error: " + e.getMessage()));
            } finally {
                if (br != null) {
                    try { br.close(); } catch (Exception ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
    
    public void getMessagesWithOptions(String sessionId, int since, int limit, boolean last, MessagesCallback callback) {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = getSession();
            if (sessionId.isEmpty()) {
                callback.onError("Please configure Session ID");
                return;
            }
        }
        
        final String finalSessionId = sessionId;
        
        new Thread(() -> {
            HttpURLConnection conn = null;
            BufferedReader br = null;
            try {
                StringBuilder urlBuilder = new StringBuilder(baseUrl + "/messages?session=" + finalSessionId);
                
                if (since >= 0) {
                    urlBuilder.append("&since=").append(since);
                }
                if (limit > 0) {
                    urlBuilder.append("&limit=").append(limit);
                }
                if (last) {
                    urlBuilder.append("&last=true");
                }
                
                URL url = new URL(urlBuilder.toString());
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setUseCaches(false);
                conn.setDoInput(true);
                
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    
                    JSONArray jsonArray = new JSONArray(response.toString());
                    List<ChatMessage> messages = new ArrayList<>();
                    
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject msg = jsonArray.getJSONObject(i);
                        ChatMessage chatMsg = parseMessage(msg);
                        if (chatMsg != null && (chatMsg.hasDisplayableContent() || chatMsg.hasToolCalls() || chatMsg.isToolResult())) {
                            messages.add(chatMsg);
                        }
                    }
                    
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess(messages));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else if (responseCode == 404) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Session not found"));
                } else if (responseCode == 400) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Bad Request: Invalid parameters"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + code));
                }
            } catch (java.net.SocketException e) {
                Log.e(TAG, "getMessagesWithOptions SocketException: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Connection error: " + e.getMessage()));
            } catch (java.io.IOException e) {
                Log.e(TAG, "getMessagesWithOptions IOException: " + e.getMessage());
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "getMessagesWithOptions failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Error: " + e.getMessage()));
            } finally {
                if (br != null) {
                    try { br.close(); } catch (Exception ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
    
    public void getLastMessage(String sessionId, MessagesCallback callback) {
        getMessagesWithOptions(sessionId, -1, -1, true, callback);
    }
    
    public void getNewMessages(String sessionId, int sinceIndex, MessagesCallback callback) {
        getMessagesWithOptions(sessionId, sinceIndex, -1, false, callback);
    }
    
    public void getMessagesPaginated(String sessionId, int limit, MessagesCallback callback) {
        getMessagesWithOptions(sessionId, -1, limit, false, callback);
    }
    
    public void getSessionPreview(String sessionId, ApiCallback callback) {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        new Thread(() -> {
            HttpURLConnection conn = null;
            BufferedReader br = null;
            try {
                URL url = new URL(baseUrl + "/session?id=" + sessionId);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setUseCaches(false);

                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    
                    final String html = response.toString();
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess(html));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else if (responseCode == 400) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Bad Request: Missing session ID"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "getSessionPreview failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            } finally {
                if (br != null) {
                    try { br.close(); } catch (Exception ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
    
    public void stopSession(String sessionId, StopCallback callback) {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        if (sessionId == null || sessionId.isEmpty()) {
            callback.onError("Session ID is required");
            return;
        }
        
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(baseUrl + "/session/" + sessionId + "/stop");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setRequestProperty("Accept", "*/*");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setUseCaches(false);
                
                conn.getOutputStream().close();

                int responseCode = conn.getResponseCode();
                
                if (responseCode == 204) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess());
                } else if (responseCode == 404) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Session not found"));
                } else if (responseCode == 409) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Session is not in progress"));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "stopSession failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
    
    public void clearSession(String sessionId, ClearCallback callback) {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        if (sessionId == null || sessionId.isEmpty()) {
            callback.onError("Session ID is required");
            return;
        }
        
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(baseUrl + "/session/" + sessionId + "/clear");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setRequestProperty("Accept", "*/*");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setUseCaches(false);
                
                conn.getOutputStream().close();

                int responseCode = conn.getResponseCode();
                
                if (responseCode == 204 || responseCode == 200) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess());
                } else if (responseCode == 404) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Session not found"));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "clearSession failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
    
    public void retrySession(String sessionId, RetryCallback callback) {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        if (sessionId == null || sessionId.isEmpty()) {
            callback.onError("Session ID is required");
            return;
        }
        
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(baseUrl + "/session/" + sessionId + "/retry");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setRequestProperty("Accept", "*/*");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setUseCaches(false);
                
                conn.getOutputStream().close();

                int responseCode = conn.getResponseCode();
                
                if (responseCode == 204) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess());
                } else if (responseCode == 404) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Session not found"));
                } else if (responseCode == 409) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Session is already in progress"));
                } else if (responseCode == 400) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("No message to retry"));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "retrySession failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
    
    public static void testConnection(String serverUrl, String apiKey, ApiCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String url = serverUrl;
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "http://" + url;
                }
                
                if (url.endsWith("/")) {
                    url = url.substring(0, url.length() - 1);
                }
                
                URL testUrl = new URL(url + "/sessions");
                conn = (HttpURLConnection) testUrl.openConnection();
                conn.setRequestMethod("GET");
                if (apiKey != null && !apiKey.isEmpty()) {
                    conn.setRequestProperty("X-API-Key", apiKey);
                }
                conn.setRequestProperty("Connection", "close");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setUseCaches(false);

                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200 || responseCode == 204) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess("Connection successful"));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: HTTP " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "testConnection failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
    
    public void testConnection(ApiCallback callback) {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (baseUrl.isEmpty()) {
            callback.onError("Please configure server URL");
            return;
        }
        
        testConnection(baseUrl, apiKey, callback);
    }
    
    public static void getProviders(String serverUrl, String apiKey, ProvidersCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            BufferedReader br = null;
            try {
                String url = serverUrl;
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "http://" + url;
                }
                
                if (url.endsWith("/")) {
                    url = url.substring(0, url.length() - 1);
                }
                
                URL providersUrl = new URL(url + "/providers");
                conn = (HttpURLConnection) providersUrl.openConnection();
                conn.setRequestMethod("GET");
                if (apiKey != null && !apiKey.isEmpty()) {
                    conn.setRequestProperty("X-API-Key", apiKey);
                }
                conn.setRequestProperty("Connection", "close");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setUseCaches(false);

                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    
                    JSONArray jsonArray = new JSONArray(response.toString());
                    List<Provider> providers = new ArrayList<>();
                    
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject providerObj = jsonArray.getJSONObject(i);
                        String name = providerObj.optString("name", "");
                        JSONArray modelsArray = providerObj.optJSONArray("models");
                        
                        if (!name.isEmpty()) {
                            List<String> models = new ArrayList<>();
                            if (modelsArray != null) {
                                for (int j = 0; j < modelsArray.length(); j++) {
                                    models.add(modelsArray.getString(j));
                                }
                            }
                            providers.add(new Provider(name, models));
                        }
                    }
                    
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess(providers));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: HTTP " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "getProviders static failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            } finally {
                if (br != null) {
                    try { br.close(); } catch (Exception ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
}
