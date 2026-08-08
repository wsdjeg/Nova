package net.wsdjeg.nova;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
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

    /**
     * 删除消息的回调接口
     * 用于 DELETE /session/:id/messages/:index API
     */
    public interface DeleteMessageCallback {
        void onSuccess();
        void onError(String error);
    }
    
    /**
     * 文件上传的回调接口
     * 用于 POST /session/:id/upload API
     */
    public interface UploadCallback {
        void onSuccess(String path, String fullPath, long size);
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
    
    /**
     * 获取/设置上传目录的回调接口
     * 用于 GET/PUT /session/:id/upload-dir API
     */
    public interface UploadDirCallback {
        void onSuccess(String uploadDir); // uploadDir can be null
        void onError(String error);
    }

    /**
     * 微信登录状态轮询的回调接口
     * 用于 GET /weixin/login/status API
     */
    public interface WeChatLoginCallback {
        void onSuccess(WeChatLoginResult result);
        void onError(String error);
    }

    /**
     * 微信凭证写入的回调接口
     * 用于 POST /weixin/credentials API
     */
    public interface WeChatCredentialsCallback {
        void onSuccess(String message);
        void onError(String error);
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
                        // 解析 cleared_at（Unix 时间戳，秒）
                        long clearedAt = sessionObj.optLong("cleared_at", 0);
                        // 排序时间优先级：last_message.created > cleared_at > session ID 解析时间
                        long lastMessageTime = TimeUtils.parseSessionIdToTimestamp(id);
                        if (lastMessageTime < 0) {
                            lastMessageTime = System.currentTimeMillis();
                        }
                        String lastMessageContent = "";
                        String lastMessageRole = "";
                        
                        JSONObject lastMsgObj = sessionObj.optJSONObject("last_message");
                        if (lastMsgObj != null) {
                            lastMessageContent = lastMsgObj.optString("content", "");
                            lastMessageRole = lastMsgObj.optString("role", "");
                            lastMessageTime = lastMsgObj.optLong("created", System.currentTimeMillis()) * 1000;
                            Log.d(TAG, "Session " + id + " last_message: content=" + lastMessageContent + ", role=" + lastMessageRole);
                        } else if (clearedAt > 0) {
                            lastMessageTime = clearedAt * 1000;
                            Log.w(TAG, "Session " + id + " last_message is NULL, using cleared_at: " + lastMessageTime);
                        } else {
                            Log.w(TAG, "Session " + id + " last_message is NULL! Using session ID time: " + lastMessageTime);
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
                            session.setClearedAt(clearedAt);
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
                    // 解析 cleared_at（Unix 时间戳，秒）
                    long clearedAt = sessionObj.optLong("cleared_at", 0);
                    // 排序时间优先级：last_message.created > cleared_at > session ID 解析时间
                    long lastMessageTime = TimeUtils.parseSessionIdToTimestamp(id);
                    if (lastMessageTime < 0) {
                        lastMessageTime = System.currentTimeMillis();
                    }
                    String lastMessageContent = "";
                    String lastMessageRole = "";
                    
                    JSONObject lastMsgObj = sessionObj.optJSONObject("last_message");
                    if (lastMsgObj != null) {
                        lastMessageContent = lastMsgObj.optString("content", "");
                        lastMessageRole = lastMsgObj.optString("role", "");
                        lastMessageTime = lastMsgObj.optLong("created", System.currentTimeMillis()) * 1000;
                    } else if (clearedAt > 0) {
                        lastMessageTime = clearedAt * 1000;
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
                        session.setClearedAt(clearedAt);
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
     * 删除会话中的指定消息
     * API 端点: DELETE /session/:id/messages/:index
     *
     * @param sessionId 会话 ID
     * @param messageIndex 消息在服务端的 1-based 索引
     * @param callback 回调
     */
    public void deleteMessage(String sessionId, int messageIndex, DeleteMessageCallback callback) {
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
        
        if (messageIndex < 1) {
            callback.onError("Invalid message index");
            return;
        }
        
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(baseUrl + "/session/" + sessionId + "/messages/" + messageIndex);
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
                } else if (responseCode == 400) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Invalid or out-of-range message index"));
                } else if (responseCode == 404) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Session not found"));
                } else if (responseCode == 409) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Session is in progress, cannot delete message"));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "deleteMessage failed", e);
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
     * 上传文件到会话的工作目录
     * API 端点: POST /session/:id/upload?path=relative/path
     * 请求体: 原始二进制数据（binary-safe）
     * 响应格式: { "path": "...", "full_path": "...", "size": 123 }
     *
     * @param sessionId    会话 ID
     * @param fileData     文件二进制数据
     * @param relativePath 相对路径（如 images/photo.png）
     * @param contentType  Content-Type（如 image/png），可为 null
     * @param callback     回调
     */
    public void uploadFile(String sessionId, byte[] fileData, String relativePath,
                           String contentType, UploadCallback callback) {
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
        
        if (fileData == null || fileData.length == 0) {
            callback.onError("File data is empty");
            return;
        }
        
        if (relativePath == null || relativePath.isEmpty()) {
            callback.onError("Relative path is required");
            return;
        }
        
        new Thread(() -> {
            HttpURLConnection conn = null;
            BufferedReader br = null;
            try {
                String encodedPath = URLEncoder.encode(relativePath, "UTF-8");
                URL url = new URL(baseUrl + "/session/" + sessionId + "/upload?path=" + encodedPath);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                if (contentType != null && !contentType.isEmpty()) {
                    conn.setRequestProperty("Content-Type", contentType);
                } else {
                    conn.setRequestProperty("Content-Type", "application/octet-stream");
                }
                conn.setDoOutput(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(60000);
                conn.setUseCaches(false);
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(fileData, 0, fileData.length);
                }
                
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    String path = jsonResponse.optString("path", "");
                    String fullPath = jsonResponse.optString("full_path", "");
                    long size = jsonResponse.optLong("size", 0);
                    
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onSuccess(path, fullPath, size));
                } else if (responseCode == 400) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Bad Request: Missing file path"));
                } else if (responseCode == 403) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Forbidden: Path traversal or absolute path rejected"));
                } else if (responseCode == 404) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Session not found"));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Unauthorized: Invalid API Key"));
                } else if (responseCode == 500) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Server error: Failed to write file"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "uploadFile failed", e);
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
     * 获取会话的上传目录
     * API 端点: GET /session/:id/upload-dir
     * 响应格式: { "upload_dir": "/path/to/dir" } 或 { "upload_dir": null }
     */
    public void getUploadDir(String sessionId, UploadDirCallback callback) {
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
                URL url = new URL(baseUrl + "/session/" + sessionId + "/upload-dir");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setUseCaches(false);

                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    JSONObject json = new JSONObject(sb.toString());
                    String uploadDir = json.isNull("upload_dir") ? null : json.optString("upload_dir", null);
                    final String result = uploadDir;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess(result));
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
                Log.e(TAG, "getUploadDir failed", e);
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
     * 设置会话的上传目录
     * API 端点: PUT /session/:id/upload-dir
     * 请求格式: { "upload_dir": "/path/to/dir" } 或 { "upload_dir": null }
     */
    public void setUploadDir(String sessionId, String uploadDir, UpdateSessionCallback callback) {
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
                URL url = new URL(baseUrl + "/session/" + sessionId + "/upload-dir");
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
                if (uploadDir == null || uploadDir.isEmpty()) {
                    requestBody.put("upload_dir", JSONObject.NULL);
                } else {
                    requestBody.put("upload_dir", uploadDir);
                }
                
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
                        callback.onError("Bad Request: Invalid upload_dir or directory does not exist"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "setUploadDir failed", e);
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
     * 设置会话的标题
     * API 端点: PUT /session/:id/title
     * 请求格式: { "title": "会话标题" }
     */
    public void setSessionTitle(String sessionId, String title, UpdateSessionCallback callback) {
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
                URL url = new URL(baseUrl + "/session/" + sessionId + "/title");
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
                requestBody.put("title", title != null ? title : "");
                
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
                        callback.onError("Bad Request: Invalid title"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "setSessionTitle failed", e);
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
        String toolCallId = msg.optString("tool_call_id", "");
        
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
            return ChatMessage.createToolResult(content, created, toolCallState,
                    toolCallId.isEmpty() ? null : toolCallId);
        }
        
        // 如果有工具调用
        if (toolCalls != null && !toolCalls.isEmpty()) {
            Log.d(TAG, "parseMessage: creating ChatMessage with toolCalls, role=" + role + ", content=" + (content.isEmpty() ? "(empty)" : content.substring(0, Math.min(50, content.length()))));
            return new ChatMessage(role, content, created, toolCalls);
        }
        
        // 否则创建正常消息
        if (!content.isEmpty()) {
            ChatMessage cm = new ChatMessage(role, content, created);
            if (!toolCallId.isEmpty()) {
                cm.toolCallId = toolCallId;
            }
            return cm;
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
                        if (chatMsg != null) {
                            chatMsg.rawIndex = i;
                            if (chatMsg.hasDisplayableContent() || chatMsg.hasToolCalls() || chatMsg.isToolResult()) {
                                messages.add(chatMsg);
                            }
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
                        if (chatMsg != null) {
                            chatMsg.rawIndex = i;
                            if (chatMsg.hasDisplayableContent() || chatMsg.hasToolCalls() || chatMsg.isToolResult()) {
                                messages.add(chatMsg);
                            }
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
    
    /**
     * 轮询微信登录状态
     * API 端点: GET /weixin/login/status
     * 第一次调用自动启动登录流程，后续调用返回当前状态。
     *
     * @param callback 回调
     */
    public void getWeChatLoginStatus(WeChatLoginCallback callback) {
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
                URL url = new URL(baseUrl + "/weixin/login/status");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000); // 长轮询可能需要较长时间
                conn.setUseCaches(false);
                
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    
                    JSONObject json = new JSONObject(response.toString());
                    WeChatLoginResult result = new WeChatLoginResult();
                    result.status = json.optString("status", "");
                    result.message = json.optString("message", "");
                    result.qrcodeUrl = json.optString("qrcode_url", "");
                    result.sessionKey = json.optString("session_key", "");
                    result.isFresh = json.optBoolean("is_fresh", false);
                    result.botToken = json.optString("bot_token", "");
                    result.accountId = json.optString("account_id", "");
                    result.baseUrl = json.optString("base_url", "");
                    result.userId = json.optString("user_id", "");
                    
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess(result));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else if (responseCode == 500) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Server error: Failed to start login flow"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "getWeChatLoginStatus failed", e);
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
     * 手动写入微信凭证
     * API 端点: POST /weixin/credentials
     *
     * @param id      Account ID (bot ID)
     * @param key     Bot token
     * @param baseUrl API base URL (optional)
     * @param userId  User ID (optional)
     * @param callback 回调
     */
    public void writeWeChatCredentials(String id, String key, String baseUrl,
                                        String userId, WeChatCredentialsCallback callback) {
        String apiBaseUrl = getBaseUrl();
        String apiKey = getApiKey();
        
        if (apiBaseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        if (id == null || id.isEmpty() || key == null || key.isEmpty()) {
            callback.onError("Account ID and Bot Token are required");
            return;
        }
        
        new Thread(() -> {
            HttpURLConnection conn = null;
            BufferedReader br = null;
            try {
                URL url = new URL(apiBaseUrl + "/weixin/credentials");
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
                requestBody.put("id", id);
                requestBody.put("key", key);
                if (baseUrl != null && !baseUrl.isEmpty()) {
                    requestBody.put("base_url", baseUrl);
                }
                if (userId != null && !userId.isEmpty()) {
                    requestBody.put("user_id", userId);
                }
                
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    
                    JSONObject json = new JSONObject(response.toString());
                    String message = json.optString("message", "Credentials saved");
                    
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess(message));
                } else if (responseCode == 400) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Bad Request: Invalid JSON or missing required fields"));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "writeWeChatCredentials failed", e);
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
