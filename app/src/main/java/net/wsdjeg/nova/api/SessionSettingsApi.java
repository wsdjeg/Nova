package net.wsdjeg.nova.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.wsdjeg.nova.ApiClient.UpdateSessionCallback;
import org.json.JSONObject;

/**
 * 会话属性更新 API
 * 包含:
 * - PUT /session/:id/cwd 设置工作目录
 * - PUT /session/:id/title 设置标题
 * - PUT /session/:id/pin 设置置顶状态
 * - PUT /session/:id/provider + PUT /session/:id/model 更新模型配置
 */
public class SessionSettingsApi {
    private static final String TAG = "SessionSettingsApi";

    private final ApiConfig config;

    public SessionSettingsApi(ApiConfig config) {
        this.config = config;
    }

    /**
     * 设置会话的工作目录
     * API 端点: PUT /session/:id/cwd
     */
    public void setSessionCwd(String sessionId, String cwd, UpdateSessionCallback callback) {
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
     * 设置会话的标题
     * API 端点: PUT /session/:id/title
     * 请求格式: { "title": "会话标题" }
     */
    public void setSessionTitle(String sessionId, String title, UpdateSessionCallback callback) {
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
}

