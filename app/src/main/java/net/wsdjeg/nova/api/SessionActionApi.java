package net.wsdjeg.nova.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.net.HttpURLConnection;
import java.net.URL;
import net.wsdjeg.nova.ApiClient.ClearCallback;
import net.wsdjeg.nova.ApiClient.RetryCallback;
import net.wsdjeg.nova.ApiClient.StopCallback;

/**
 * 会话操作 API
 * 包含:
 * - POST /session/:id/stop 停止生成
 * - POST /session/:id/clear 清空消息
 * - POST /session/:id/retry 重试最后一条消息
 */
public class SessionActionApi {
    private static final String TAG = "SessionActionApi";

    private final ApiConfig config;

    public SessionActionApi(ApiConfig config) {
        this.config = config;
    }

    public void stopSession(String sessionId, StopCallback callback) {
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
}

