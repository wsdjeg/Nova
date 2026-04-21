package com.example.myandroidapp;

import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public class ApiClient {
    
    private final SettingsManager settingsManager;
    
    public interface ApiCallback {
        void onSuccess(String response);
        void onError(String error);
    }
    
    public interface Callback {
        void onSuccess(String response);
        void onError(String error);
    }
    
    public ApiClient(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }
    
    public void sendMessage(String message, ApiCallback callback) {
        String url = settingsManager.getUrl();
        String port = settingsManager.getPort();
        String apiKey = settingsManager.getApiKey();
        
        String fullUrl;
        if (port != null && !port.isEmpty()) {
            fullUrl = url + ":" + port;
        } else {
            fullUrl = url;
        }
        
        new Thread(() -> {
            try {
                URL apiUrl = new URL(fullUrl);
                HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                if (apiKey != null && !apiKey.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                }
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);

                // 构建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("message", message);
                
                // 发送请求
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // 读取响应
                int responseCode = conn.getResponseCode();
                BufferedReader br;
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                } else {
                    br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                }

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();

                // 解析响应
                String result;
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    result = jsonResponse.optString("response", response.toString());
                } else {
                    result = "Error: " + responseCode + " - " + response.toString();
                }

                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Request failed: " + e.getMessage()));
            }
        }).start();
    }
    
    // 保留静态方法以兼容旧代码
    public static void sendMessage(String url, String apiKey, String message, Callback callback) {
        new Thread(() -> {
            try {
                URL apiUrl = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);

                // 构建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("message", message);
                
                // 发送请求
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // 读取响应
                int responseCode = conn.getResponseCode();
                BufferedReader br;
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                } else {
                    br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                }

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();

                // 解析响应
                String result;
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    result = jsonResponse.optString("response", response.toString());
                } else {
                    result = "Error: " + responseCode + " - " + response.toString();
                }

                new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Request failed: " + e.getMessage()));
            }
        }).start();
    }
}
