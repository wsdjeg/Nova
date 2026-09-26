package net.wsdjeg.nova.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.wsdjeg.nova.ApiClient.ApiCallback;
import net.wsdjeg.nova.ApiClient.ClearLogsCallback;
import net.wsdjeg.nova.ApiClient.LogsCallback;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 服务器相关 API
 * 包含:
 * - GET /logs 服务端运行时日志查询
 * - DELETE /logs 清空日志
 * - GET /sessions 连接测试
 * - GET /session?id=:id 会话预览（HTML）
 */
public class ServerApi {
    private static final String TAG = "ServerApi";

    private final ApiConfig config;

    public ServerApi(ApiConfig config) {
        this.config = config;
    }

    /**
     * 获取服务端运行时日志
     * API 端点: GET /logs
     * 响应格式: { "logs": ["[ HH:MM:SS:mmm ] [ Level ] [ name ] message", ...], "count": 2 }
     *
     * @param level    级别过滤: "error" / "warn" / "info" / "debug"（保留 >= 该级别），null 表示不过滤
     * @param name     logger 名称子串过滤（如 "chat.nvim"），null 表示不过滤
     * @param tail     仅返回最后 N 行，<= 0 表示返回全部
     * @param callback 回调
     */
    public void getLogs(String level, String name, int tail, LogsCallback callback) {
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
                StringBuilder urlBuilder = new StringBuilder(baseUrl + "/logs");
                StringBuilder query = new StringBuilder();
                if (level != null && !level.isEmpty()) {
                    query.append("level=").append(URLEncoder.encode(level, "UTF-8"));
                }
                if (name != null && !name.isEmpty()) {
                    if (query.length() > 0) {
                        query.append("&");
                    }
                    query.append("name=").append(URLEncoder.encode(name, "UTF-8"));
                }
                if (tail > 0) {
                    if (query.length() > 0) {
                        query.append("&");
                    }
                    query.append("tail=").append(tail);
                }
                if (query.length() > 0) {
                    urlBuilder.append("?").append(query);
                }

                URL url = new URL(urlBuilder.toString());
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
                    br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }

                    JSONObject json = new JSONObject(response.toString());
                    JSONArray logsArray = json.optJSONArray("logs");
                    List<String> logs = new ArrayList<>();
                    if (logsArray != null) {
                        for (int i = 0; i < logsArray.length(); i++) {
                            logs.add(logsArray.getString(i));
                        }
                    }

                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onSuccess(logs));
                } else if (responseCode == 400) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Bad Request: Invalid level parameter"));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Unauthorized: Invalid API Key"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "getLogs failed", e);
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
     * 清空服务端运行时日志
     * API 端点: DELETE /logs
     */
    public void clearLogs(ClearLogsCallback callback) {
        String baseUrl = config.getBaseUrl();
        String apiKey = config.getApiKey();

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(baseUrl + "/logs");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("DELETE");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setUseCaches(false);

                int responseCode = conn.getResponseCode();

                if (responseCode == 204 || responseCode == 200) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onSuccess());
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Unauthorized: Invalid API Key"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "clearLogs failed", e);
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
     * 静态方法：测试服务器连接
     * 用于账号编辑等未创建 ApiClient 实例的场景
     */
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

    /**
     * 实例方法：使用当前配置测试服务器连接
     */
    public void testConnection(ApiCallback callback) {
        String baseUrl = config.getBaseUrl();
        String apiKey = config.getApiKey();

        if (baseUrl.isEmpty()) {
            callback.onError("Please configure server URL");
            return;
        }

        testConnection(baseUrl, apiKey, callback);
    }

    /**
     * 获取会话预览（HTML 格式）
     * API 端点: GET /session?id=:id
     */
    public void getSessionPreview(String sessionId, ApiCallback callback) {
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
}

