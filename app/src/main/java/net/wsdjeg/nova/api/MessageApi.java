package net.wsdjeg.nova.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import net.wsdjeg.nova.ApiClient.DeleteMessageCallback;
import net.wsdjeg.nova.ApiClient.MessagesCallback;
import net.wsdjeg.nova.ChatMessage;
import net.wsdjeg.nova.ToolCall;
import net.wsdjeg.nova.ToolCallFunction;
import net.wsdjeg.nova.ToolCallState;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 消息查询与解析 API
 * 包含:
 * - GET /messages?session=:id 获取会话消息
 * - GET /messages?since=&limit=&last= 分页/增量查询
 * - DELETE /session/:id/messages/:index 删除消息
 */
public class MessageApi {
    private static final String TAG = "MessageApi";

    private final ApiConfig config;

    public MessageApi(ApiConfig config) {
        this.config = config;
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
        String baseUrl = config.getBaseUrl();
        String apiKey = config.getApiKey();

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }

        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = config.getSession();
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
        String baseUrl = config.getBaseUrl();
        String apiKey = config.getApiKey();

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }

        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = config.getSession();
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

    /**
     * 删除会话中的指定消息
     * API 端点: DELETE /session/:id/messages/:index
     *
     * @param sessionId    会话 ID
     * @param messageIndex  消息在服务端的 1-based 索引
     * @param callback     回调
     */
    public void deleteMessage(String sessionId, int messageIndex, DeleteMessageCallback callback) {
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
}

