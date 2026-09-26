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
import net.wsdjeg.nova.ApiClient.ApiCallback;
import net.wsdjeg.nova.ApiClient.MessageCallback;
import org.json.JSONObject;

/**
 * 消息发送 API
 * API 端点: POST /
 * 请求体: { "session": "...", "content": "..." }
 */
public class ChatApi {
    private static final String TAG = "ChatApi";

    private final ApiConfig config;

    public ChatApi(ApiConfig config) {
        this.config = config;
    }

    public void sendMessage(String sessionId, String content, ApiCallback callback) {
        String baseUrl = config.getBaseUrl();
        String apiKey = config.getApiKey();

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
        String session = config.getSession();
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
}

