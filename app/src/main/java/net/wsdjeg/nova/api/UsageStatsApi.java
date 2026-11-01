package net.wsdjeg.nova.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.wsdjeg.nova.ApiClient.ClearUsageStatsCallback;
import net.wsdjeg.nova.ApiClient.UsageStatsCallback;
import net.wsdjeg.nova.UsageRecord;
import net.wsdjeg.nova.UsageStats;

import org.json.JSONObject;

/**
 * 用量统计 API
 * 包含:
 * - GET /usage-stats 按 provider/model 查询请求与 token 统计（支持 hours/days 窗口）
 * - DELETE /usage-stats 清空全部统计（total/hourly/daily 三层）
 */
public class UsageStatsApi {
    private static final String TAG = "UsageStatsApi";

    private final ApiConfig config;

    public UsageStatsApi(ApiConfig config) {
        this.config = config;
    }

    /**
     * 获取用量统计
     * API 端点: GET /usage-stats
     * 响应格式:
     * { "window": "total", "stats": { "<provider>": { "<model>": { "requests": 12, ... } } } }
     *
     * @param hours    最近 N 小时窗口（<= 0 表示忽略，优先级高于 days）
     * @param days     最近 N 天窗口（<= 0 表示忽略）
     * @param callback 回调
     */
    public void getUsageStats(int hours, int days, UsageStatsCallback callback) {
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
                StringBuilder urlBuilder = new StringBuilder(baseUrl + "/usage-stats");
                String query = null;
                if (hours > 0) {
                    query = "hours=" + hours;
                } else if (days > 0) {
                    query = "days=" + days;
                }
                if (query != null) {
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
                    String window = json.optString("window", "total");
                    JSONObject statsObj = json.optJSONObject("stats");

                    Map<String, Map<String, UsageRecord>> stats = new LinkedHashMap<>();
                    if (statsObj != null) {
                        Iterator<String> providerIt = statsObj.keys();
                        while (providerIt.hasNext()) {
                            String provider = providerIt.next();
                            JSONObject modelsObj = statsObj.optJSONObject(provider);
                            if (modelsObj == null) {
                                continue;
                            }
                            Map<String, UsageRecord> models = new LinkedHashMap<>();
                            Iterator<String> modelIt = modelsObj.keys();
                            while (modelIt.hasNext()) {
                                String model = modelIt.next();
                                JSONObject recordObj = modelsObj.optJSONObject(model);
                                if (recordObj == null) {
                                    continue;
                                }
                                models.put(model, new UsageRecord(
                                        recordObj.optLong("requests", 0),
                                        recordObj.optLong("total_tokens", 0),
                                        recordObj.optLong("prompt_tokens", 0),
                                        recordObj.optLong("completion_tokens", 0),
                                        recordObj.optLong("cached_tokens", 0)));
                            }
                            if (!models.isEmpty()) {
                                stats.put(provider, models);
                            }
                        }
                    }

                    final UsageStats result = new UsageStats(window, stats);
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onSuccess(result));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Unauthorized: Invalid API Key"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "getUsageStats failed", e);
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
     * 清空全部用量统计
     * API 端点: DELETE /usage-stats
     */
    public void clearUsageStats(ClearUsageStatsCallback callback) {
        String baseUrl = config.getBaseUrl();
        String apiKey = config.getApiKey();

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(baseUrl + "/usage-stats");
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
                Log.e(TAG, "clearUsageStats failed", e);
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

