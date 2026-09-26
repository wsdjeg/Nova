package net.wsdjeg.nova.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.wsdjeg.nova.ApiClient.BridgeCallback;
import net.wsdjeg.nova.ApiClient.BridgesCallback;
import net.wsdjeg.nova.ApiClient.UpdateSessionCallback;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 集成桥接 API
 * 包含:
 * - GET /session/:id/bridge 获取会话已绑定的集成列表
 * - PUT /session/:id/bridge/:platform 绑定集成
 * - DELETE /session/:id/bridge/:platform 解绑指定集成
 * - DELETE /session/:id/bridge 解绑所有集成
 */
public class BridgeApi {
    private static final String TAG = "BridgeApi";

    private final ApiConfig config;

    public BridgeApi(ApiConfig config) {
        this.config = config;
    }

    /**
     * 获取会话已绑定的集成列表
     * API 端点: GET /session/:id/bridge
     * 响应格式: { "bridges": ["discord", "lark"] }
     */
    public void getBridges(String sessionId, BridgesCallback callback) {
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
                URL url = new URL(baseUrl + "/session/" + sessionId + "/bridge");
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
                    JSONArray bridgesArray = json.optJSONArray("bridges");
                    List<String> bridges = new ArrayList<>();
                    if (bridgesArray != null) {
                        for (int i = 0; i < bridgesArray.length(); i++) {
                            bridges.add(bridgesArray.getString(i));
                        }
                    }

                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onSuccess(bridges));
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
                Log.e(TAG, "getBridges failed", e);
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
     * 绑定集成到会话
     * API 端点: PUT /session/:id/bridge/:platform
     * 响应格式: { "session": "...", "bridge": "discord" }
     * 可用平台: discord, dingtalk, lark, slack, telegram, wecom, weixin
     */
    public void bridgeIntegration(String sessionId, String platform, BridgeCallback callback) {
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

        if (platform == null || platform.isEmpty()) {
            callback.onError("Platform is required");
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            BufferedReader br = null;
            try {
                URL url = new URL(baseUrl + "/session/" + sessionId + "/bridge/" + platform);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setUseCaches(false);

                int responseCode = conn.getResponseCode();

                if (responseCode == 200) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onSuccess(platform));
                } else if (responseCode == 400) {
                    br = new BufferedReader(new InputStreamReader(conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    String errorMsg = "Unknown integration platform";
                    try {
                        JSONObject json = new JSONObject(response.toString());
                        JSONArray available = json.optJSONArray("available");
                        if (available != null) {
                            StringBuilder sb = new StringBuilder("Unknown platform. Available: ");
                            for (int i = 0; i < available.length(); i++) {
                                if (i > 0) sb.append(", ");
                                sb.append(available.getString(i));
                            }
                            errorMsg = sb.toString();
                        }
                    } catch (Exception ignored) {}
                    final String finalMsg = errorMsg;
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError(finalMsg));
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
                Log.e(TAG, "bridgeIntegration failed", e);
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
     * 解绑指定集成
     * API 端点: DELETE /session/:id/bridge/:platform
     */
    public void unbridgeIntegration(String sessionId, String platform, UpdateSessionCallback callback) {
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

        if (platform == null || platform.isEmpty()) {
            callback.onError("Platform is required");
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(baseUrl + "/session/" + sessionId + "/bridge/" + platform);
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
                } else if (responseCode == 400) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Unknown integration platform"));
                } else if (responseCode == 404) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Session not found or integration not bound"));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Unauthorized: Invalid API Key"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "unbridgeIntegration failed", e);
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
     * 解绑所有集成
     * API 端点: DELETE /session/:id/bridge
     */
    public void unbridgeAll(String sessionId, UpdateSessionCallback callback) {
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
                URL url = new URL(baseUrl + "/session/" + sessionId + "/bridge");
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
                Log.e(TAG, "unbridgeAll failed", e);
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

