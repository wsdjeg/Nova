package net.wsdjeg.nova.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import net.wsdjeg.nova.ApiClient.WeChatCredentialsCallback;
import net.wsdjeg.nova.ApiClient.WeChatLoginCallback;
import net.wsdjeg.nova.WeChatLoginResult;
import org.json.JSONObject;

/**
 * 微信集成 API
 * 包含:
 * - GET /weixin/login/status 轮询微信登录状态
 * - POST /weixin/credentials 手动写入微信凭证
 * - DELETE /weixin/credentials 清除微信凭证（退出登录）
 */
public class WeChatApi {
    private static final String TAG = "WeChatApi";

    private final ApiConfig config;

    public WeChatApi(ApiConfig config) {
        this.config = config;
    }

    /**
     * 轮询微信登录状态
     * API 端点: GET /weixin/login/status
     * 第一次调用自动启动登录流程，后续调用返回当前状态。
     *
     * @param callback 回调
     */
    public void getWeChatLoginStatus(WeChatLoginCallback callback) {
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
                    result.isRunning = json.optBoolean("is_running", false);

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
        String apiBaseUrl = config.getBaseUrl();
        String apiKey = config.getApiKey();

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

    /**
     * 清除微信凭证（退出登录）
     * API 端点: DELETE /weixin/credentials
     * 清除后可重新启动登录流程
     *
     * @param callback 回调
     */
    public void deleteWeChatCredentials(WeChatCredentialsCallback callback) {
        String apiBaseUrl = config.getBaseUrl();
        String apiKey = config.getApiKey();

        if (apiBaseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(apiBaseUrl + "/weixin/credentials");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("DELETE");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setUseCaches(false);

                int responseCode = conn.getResponseCode();

                if (responseCode == 200 || responseCode == 204) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onSuccess("Credentials deleted"));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Unauthorized: Invalid API Key"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "deleteWeChatCredentials failed", e);
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

