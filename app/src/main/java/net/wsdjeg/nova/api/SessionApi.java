package net.wsdjeg.nova.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.wsdjeg.nova.ApiClient.CreateSessionCallback;
import net.wsdjeg.nova.ApiClient.DeleteSessionCallback;
import net.wsdjeg.nova.ApiClient.SessionCallback;
import net.wsdjeg.nova.ApiClient.SessionsCallback;
import net.wsdjeg.nova.Session;
import net.wsdjeg.nova.TimeUtils;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 会话 CRUD API
 * 包含:
 * - GET /sessions 列出会话
 * - GET /sessions/:id 单个会话详情
 * - POST /session/new 创建会话
 * - DELETE /session/:id 删除会话
 */
public class SessionApi {
    private static final String TAG = "SessionApi";

    /** HTTP 409 Conflict */
    private static final int HTTP_CONFLICT = 409;

    /**
     * 创建会话的最大尝试次数。
     * 会话 ID 由服务端按秒生成（YYYY-MM-DD-HH-MM-SS），同一秒内重复创建会返回 409，
     * 客户端等待跨过秒边界后自动重试，对用户透明。
     */
    private static final int MAX_CREATE_SESSION_ATTEMPTS = 3;

    private final ApiConfig config;

    public SessionApi(ApiConfig config) {
        this.config = config;
    }

    public void getSessions(String accountId, SessionsCallback callback) {
        String baseUrl = config.getBaseUrl();
        String apiKey = config.getApiKey();

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
                            // 解析 token 用量统计
                            JSONObject usageObj = sessionObj.optJSONObject("usage");
                            if (usageObj != null) {
                                session.setUsageTotalTokens(usageObj.optLong("total_tokens", 0));
                                session.setUsagePromptTokens(usageObj.optLong("prompt_tokens", 0));
                                session.setUsageCompletionTokens(usageObj.optLong("completion_tokens", 0));
                            }
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
     * 响应格式: { "id": "xxx", "title": "...", "cwd": "...", "provider": "...", "model": "...", "in_progress": false, "pin": false, "message_count": 5, "last_message": {...}, "usage": {...} }
     */
    public void getSession(String sessionId, String accountId, SessionCallback callback) {
        String baseUrl = config.getBaseUrl();
        String apiKey = config.getApiKey();

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
                        // 解析 token 用量统计
                        JSONObject usageObj = sessionObj.optJSONObject("usage");
                        if (usageObj != null) {
                            session.setUsageTotalTokens(usageObj.optLong("total_tokens", 0));
                            session.setUsagePromptTokens(usageObj.optLong("prompt_tokens", 0));
                            session.setUsageCompletionTokens(usageObj.optLong("completion_tokens", 0));
                        }

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

    /**
     * 创建新会话
     * API 端点: POST /session/new
     * 响应格式: { "id": "xxx", "cwd": "...", "provider": "...", "model": "...", "pin": false, "usage": {...} }
     *
     * 409 语义（服务端同秒会话 ID 冲突）:
     * 等待跨过秒边界后自动重试（最多 MAX_CREATE_SESSION_ATTEMPTS 次），
     * 全部失败时把服务端返回的 error 信息透传给调用方。
     */
    public void createSession(String cwd, String provider, String model, String accountId, CreateSessionCallback callback) {
        String baseUrl = config.getBaseUrl();
        String apiKey = config.getApiKey();

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }

        new Thread(() -> {
            for (int attempt = 1; attempt <= MAX_CREATE_SESSION_ATTEMPTS; attempt++) {
                CreateResult result = performCreateSession(baseUrl, apiKey, cwd, provider, model, accountId);

                if (result.session != null) {
                    final Session session = result.session;
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onSuccess(session));
                    return;
                }

                if (result.responseCode == HTTP_CONFLICT && attempt < MAX_CREATE_SESSION_ATTEMPTS) {
                    Log.w(TAG, "createSession got 409 (same-second session id conflict), attempt "
                        + attempt + "/" + MAX_CREATE_SESSION_ATTEMPTS + ", retrying after second boundary");
                    if (!sleepPastNextSecond()) {
                        new Handler(Looper.getMainLooper()).post(() ->
                            callback.onError("Session creation interrupted"));
                        return;
                    }
                    continue;
                }

                final String error = result.error != null ? result.error : ("Error: " + result.responseCode);
                new Handler(Looper.getMainLooper()).post(() ->
                    callback.onError(error));
                return;
            }
        }).start();
    }

    public void createSession(String cwd, String provider, String model, CreateSessionCallback callback) {
        createSession(cwd, provider, model, null, callback);
    }

    /**
     * 执行一次创建会话请求（不含 409 重试逻辑）
     */
    private CreateResult performCreateSession(String baseUrl, String apiKey, String cwd, String provider, String model, String accountId) {
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
                    return CreateResult.failure("No session_id in response", responseCode);
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
                // 解析 token 用量统计（新会话全 0，解析保持结构一致）
                JSONObject usageObj = jsonResponse.optJSONObject("usage");
                if (usageObj != null) {
                    session.setUsageTotalTokens(usageObj.optLong("total_tokens", 0));
                    session.setUsagePromptTokens(usageObj.optLong("prompt_tokens", 0));
                    session.setUsageCompletionTokens(usageObj.optLong("completion_tokens", 0));
                }

                return CreateResult.success(session);
            } else if (responseCode == HTTP_CONFLICT) {
                // 同秒会话 ID 冲突：读取服务端 error 信息（外层会等待跨秒后重试）
                String message = readErrorMessage(conn);
                if (message == null || message.isEmpty()) {
                    message = "Session creation too frequent, please retry later";
                }
                return CreateResult.failure(message, responseCode);
            } else if (responseCode == 401) {
                return CreateResult.failure("Unauthorized: Invalid API Key", responseCode);
            } else if (responseCode == 400) {
                return CreateResult.failure("Bad Request: Invalid parameters", responseCode);
            } else {
                return CreateResult.failure("Error: " + responseCode, responseCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "createSession failed", e);
            return CreateResult.failure("Network error: " + e.getMessage(), -1);
        } finally {
            if (br != null) {
                try { br.close(); } catch (Exception ignored) {}
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 等待跨过下一个整秒边界。
     * 会话 ID 由服务端按秒生成，等待总时长超过 1 秒，
     * 即使客户端与服务端时钟存在偏移，下一次请求也会落在新的秒内。
     *
     * @return true 表示等待完成；false 表示线程被中断（调用方应终止重试）
     */
    private static boolean sleepPastNextSecond() {
        long now = System.currentTimeMillis();
        // 跳到「下下一秒」的开头并留 50ms 缓冲，保证等待时长严格大于 1 秒
        long target = (now / 1000 + 2) * 1000 + 50;
        try {
            Thread.sleep(target - now);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 读取错误响应体中的 error 字段（服务端错误格式: {"error": "..."}），
     * 解析失败时返回响应体原文，无响应体时返回 null。
     */
    private static String readErrorMessage(HttpURLConnection conn) {
        BufferedReader br = null;
        try {
            InputStream es = conn.getErrorStream();
            if (es == null) {
                return null;
            }
            br = new BufferedReader(new InputStreamReader(es, "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            String body = response.toString().trim();
            if (body.isEmpty()) {
                return null;
            }
            try {
                JSONObject json = new JSONObject(body);
                String error = json.optString("error", "");
                if (!error.isEmpty()) {
                    return error;
                }
            } catch (Exception ignored) {
            }
            return body;
        } catch (Exception e) {
            return null;
        } finally {
            if (br != null) {
                try { br.close(); } catch (Exception ignored) {}
            }
        }
    }

    public void deleteSession(String sessionId, DeleteSessionCallback callback) {
        String baseUrl = config.getBaseUrl();
        String apiKey = config.getApiKey();

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
     * createSession 单次请求的结果封装（供 409 自动重试使用）
     */
    private static final class CreateResult {
        /** 创建成功时非空 */
        final Session session;
        /** 失败时的错误信息 */
        final String error;
        /** 失败时的 HTTP 状态码（网络异常为 -1） */
        final int responseCode;

        private CreateResult(Session session, String error, int responseCode) {
            this.session = session;
            this.error = error;
            this.responseCode = responseCode;
        }

        static CreateResult success(Session session) {
            return new CreateResult(session, null, 200);
        }

        static CreateResult failure(String error, int responseCode) {
            return new CreateResult(null, error, responseCode);
        }
    }
}

