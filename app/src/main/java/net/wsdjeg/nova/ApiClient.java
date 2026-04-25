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
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * API 客户端
 * 支持多账号：可以指定 baseUrl 和 apiKey，或使用 SettingsManager 的默认设置
 */
public class ApiClient {
    private static final String TAG = "ApiClient";
    
    private final SettingsManager settingsManager;
    private final String overrideBaseUrl;   // 覆盖的 baseUrl（用于多账号）
    private final String overrideApiKey;    // 覆盖的 apiKey（用于多账号）
    
    public interface ApiCallback {
        void onSuccess(String response);
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
    
    /**
     * 创建会话回调接口
     */
    public interface CreateSessionCallback {
        void onSuccess(Session session);
        void onError(String error);
    }
    
    /**
     * 删除会话回调接口
     */
    public interface DeleteSessionCallback {
        void onSuccess();
        void onError(String error);
    }
    
    /**
     * 停止生成回调接口
     */
    public interface StopCallback {
        void onSuccess();
        void onError(String error);
    }
    
    /**
     * 重试回调接口
     */
    public interface RetryCallback {
        void onSuccess();
        void onError(String error);
    }
    
    /**
     * Chat message model for API response
     */
    public static class ChatMessage {
        public String role;
        public String content;
        public long created; // Lua os.time() 时间戳（秒）
        
        public ChatMessage(String role, String content, long created) {
            this.role = role;
            this.content = content;
            this.created = created;
        }
    }
    
    /**
     * 默认构造函数：使用 SettingsManager 的设置
     */
    public ApiClient(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
        this.overrideBaseUrl = null;
        this.overrideApiKey = null;
    }
    
    /**
     * 多账号构造函数：直接指定 baseUrl 和 apiKey
     */
    public ApiClient(String baseUrl, String apiKey) {
        this.settingsManager = null;
        this.overrideBaseUrl = baseUrl;
        this.overrideApiKey = apiKey;
    }
    
    /**
     * 获取 baseUrl（优先使用覆盖值）
     */
    private String getBaseUrl() {
        if (overrideBaseUrl != null) {
            return overrideBaseUrl;
        }
        if (settingsManager != null) {
            return settingsManager.getFullUrl();
        }
        return "";
    }
    
    /**
     * 获取 apiKey（优先使用覆盖值）
     */
    private String getApiKey() {
        if (overrideApiKey != null) {
            return overrideApiKey;
        }
        if (settingsManager != null) {
            return settingsManager.getApiKey();
        }
        return "";
    }
    
    /**
     * 获取 session（仅从 SettingsManager 获取）
     */
    private String getSession() {
        if (settingsManager != null) {
            return settingsManager.getSession();
        }
        return "";
    }
    
    /**
     * 检查是否有有效的 API 设置
     */
    public boolean hasValidSettings() {
        return !getBaseUrl().isEmpty() && !getApiKey().isEmpty();
    }
    
    /**
     * Send a message to a specific chat session (指定 session ID).
     * POST /
     * Request body: {"session": "session-id", "content": "message"}
     * Returns 204 on success.
     */
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
            try {
                URL url = new URL(baseUrl + "/");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);

                // Build request body: {"session": "...", "content": "..."}
                JSONObject requestBody = new JSONObject();
                requestBody.put("session", sessionId);
                requestBody.put("content", content);
                
                // Send request
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // Read response
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 204) {
                    // Success - 204 No Content
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess("Message sent successfully"));
                } else if (responseCode == 200 || responseCode == 201) {
                    // Read success response
                    BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    br.close();
                    
                    String result = response.toString();
                    // Try to extract response field
                    try {
                        JSONObject jsonResponse = new JSONObject(result);
                        result = jsonResponse.optString("response", result);
                    } catch (Exception e) {
                        // Not JSON, use raw response
                    }
                    
                    final String finalResult = result;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess(finalResult));
                } else {
                    // Error response
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
                    
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError(errorMessage));
                }
            } catch (Exception e) {
                Log.e(TAG, "sendMessage failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            }
        }).start();
    }
    
    /**
     * Send a message to the current session (从 SettingsManager 获取 session).
     * 兼容旧版本方法。
     */
    public void sendMessage(String content, ApiCallback callback) {
        String session = getSession();
        sendMessage(session, content, callback);
    }
    
    /**
     * Get all active sessions with details.
     * GET /sessions
     * Returns JSON array with session objects: [{id, cwd, provider, model}, ...]
     * 
     * @param accountId 用于标记会话所属账号（可选）
     */
    public void getSessions(String accountId, SessionsCallback callback) {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        new Thread(() -> {
            try {
                URL url = new URL(baseUrl + "/sessions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);
                
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    br.close();
                    
                    // Parse JSON array with session objects
                    JSONArray jsonArray = new JSONArray(response.toString());
                    List<Session> sessions = new ArrayList<>();
                    
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject sessionObj = jsonArray.getJSONObject(i);
                        String id = sessionObj.optString("id", "");
                        String cwd = sessionObj.optString("cwd", "");
                        String provider = sessionObj.optString("provider", "");
                        String model = sessionObj.optString("model", "");
                        boolean inProgress = sessionObj.optBoolean("in_progress", false);
                        
                        if (!id.isEmpty()) {
                            Session session = new Session(id);
                            session.setAccountId(accountId);  // 设置账号 ID
                            session.setCwd(cwd);
                            session.setProvider(provider);
                            session.setModel(model);
                            session.setInProgress(inProgress);
                            sessions.add(session);
                        }
                    }
                    
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess(sessions));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "getSessions failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            }
        }).start();
    }
    
    /**
     * Get all active sessions (兼容旧接口)
     */
    public void getSessions(SessionsCallback callback) {
        getSessions(null, callback);
    }
    
    /**
     * Create a new chat session.
     * Request body: {"cwd": "...", "provider": "...", "model": "..."} (all optional)
     * Returns 201 with {"id": "session-id"}
     * 
     * @param accountId 用于标记会话所属账号（可选）
     */
    public void createSession(String cwd, String provider, String model, String accountId, CreateSessionCallback callback) {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        new Thread(() -> {
            try {
                URL url = new URL(baseUrl + "/session/new");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);

                // Build request body (all fields optional)
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
                
                // Send request
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                
                if (responseCode == 201) {
                    // Read response to get session ID
                    BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    br.close();
                    
                    // Parse response: {"id": "session-id"}
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    String sessionId = jsonResponse.getString("id");
                    
                    // Create session object with returned ID
                    Session session = new Session(sessionId);
                    session.setAccountId(accountId);  // 设置账号 ID
                    session.setCwd(cwd != null ? cwd : "");
                    session.setProvider(provider != null ? provider : "");
                    session.setModel(model != null ? model : "");
                    
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess(session));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else if (responseCode == 400) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Bad Request: Invalid parameters"));
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "createSession failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            }
        }).start();
    }
    
    /**
     * Create a new chat session (兼容旧接口)
     */
    public void createSession(String cwd, String provider, String model, CreateSessionCallback callback) {
        createSession(cwd, provider, model, null, callback);
    }
    
    /**
     * Delete a specific session.
     * DELETE /session/:id
     * Returns 204 on success, 409 if session is in progress.
     */
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
            try {
                URL url = new URL(baseUrl + "/session/" + sessionId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("DELETE");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                
                if (responseCode == 204) {
                    // Success - 204 No Content
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
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "deleteSession failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            }
        }).start();
    }
    
    /**
     * Get messages for a specific session.
     * GET /messages?session=xxx
     * Returns JSON array with messages containing role, content, and created timestamp.
     */
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
            try {
                URL url = new URL(baseUrl + "/messages?session=" + finalSessionId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    br.close();
                    
                    // Parse JSON array
                    JSONArray jsonArray = new JSONArray(response.toString());
                    List<ChatMessage> messages = new ArrayList<>();
                    
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject msg = jsonArray.getJSONObject(i);
                        String role = msg.optString("role", "");
                        String content = msg.optString("content", "");
                        // 获取 created 时间戳，如果不存在则使用当前时间
                        long created = msg.optLong("created", System.currentTimeMillis() / 1000);
                        
                        // Only add messages with content
                        if (!content.isEmpty()) {
                            messages.add(new ChatMessage(role, content, created));
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
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "getMessages failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            }
        }).start();
    }
    
    /**
     * Get HTML preview of a session.
     * GET /session?id=session-id
     * Returns HTML content.
     */
    public void getSessionPreview(String sessionId, ApiCallback callback) {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        new Thread(() -> {
            try {
                URL url = new URL(baseUrl + "/session?id=" + sessionId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    br.close();
                    
                    final String html = response.toString();
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess(html));
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
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "getSessionPreview failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            }
        }).start();
    }
    
    /**
     * Stop ongoing generation for a session.
     * POST /session/:id/stop
     * Returns 204 on success, 404 if session not found, 409 if not in progress.
     */
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
            try {
                URL url = new URL(baseUrl + "/session/" + sessionId + "/stop");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);

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
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "stopSession failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            }
        }).start();
    }
    
    /**
     * Retry the last message for a session.
     * POST /session/:id/retry
     * Returns 204 on success, 404 if session not found, 409 if in progress, 400 if no message to retry.
     */
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
            try {
                URL url = new URL(baseUrl + "/session/" + sessionId + "/retry");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);

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
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "retrySession failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            }
        }).start();
    }
    
    /**
     * Test connection to a server.
     * 静态方法，用于测试与指定服务器的连接
     * GET /sessions 端点用于验证连接和认证
     */
    public static void testConnection(String serverUrl, String apiKey, ApiCallback callback) {
        new Thread(() -> {
            try {
                // 确保 URL 格式正确
                String url = serverUrl;
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "http://" + url;
                }
                
                // 移除末尾斜杠
                if (url.endsWith("/")) {
                    url = url.substring(0, url.length() - 1);
                }
                
                URL testUrl = new URL(url + "/sessions");
                HttpURLConnection conn = (HttpURLConnection) testUrl.openConnection();
                conn.setRequestMethod("GET");
                if (apiKey != null && !apiKey.isEmpty()) {
                    conn.setRequestProperty("X-API-Key", apiKey);
                }
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200 || responseCode == 204) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess("Connection successful"));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: HTTP " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "testConnection failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Connection failed: " + e.getMessage()));
            }
        }).start();
    }
    
    /**
     * Test connection with current settings.
     * 使用当前配置测试连接
     */
    public void testConnection(ApiCallback callback) {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (baseUrl.isEmpty()) {
            callback.onError("Please configure server URL");
            return;
        }
        
        testConnection(baseUrl, apiKey, callback);
    }
}
