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
import net.wsdjeg.nova.ApiClient.ProvidersCallback;
import net.wsdjeg.nova.ApiClient.SkillsCallback;
import net.wsdjeg.nova.Provider;
import net.wsdjeg.nova.Skill;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Provider/Skill 查询 API
 * 包含:
 * - GET /providers 获取可用模型列表
 * - GET /skills 获取已注册的 skills（slash 命令）
 */
public class ProviderApi {
    private static final String TAG = "ProviderApi";

    private final ApiConfig config;

    public ProviderApi(ApiConfig config) {
        this.config = config;
    }

    public void getProviders(ProvidersCallback callback) {
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
     * 静态方法：直接指定服务器地址查询 providers
     * 用于账号编辑等未创建 ApiClient 实例的场景
     */
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
     * 获取所有已注册的 skills（slash 命令）
     * API 端点: GET /skills
     * 响应格式: [ { "name": "clear", "description": "...", "builtin": true }, ... ]
     */
    public void getSkills(SkillsCallback callback) {
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
                URL url = new URL(baseUrl + "/skills");
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

                    JSONArray jsonArray = new JSONArray(response.toString());
                    List<Skill> skills = new ArrayList<>();

                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject skillObj = jsonArray.getJSONObject(i);
                        String name = skillObj.optString("name", "");
                        String description = skillObj.optString("description", "");
                        boolean builtin = skillObj.optBoolean("builtin", false);

                        if (!name.isEmpty()) {
                            skills.add(new Skill(name, description, builtin));
                        }
                    }

                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onSuccess(skills));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Unauthorized: Invalid API Key"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "getSkills failed", e);
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

