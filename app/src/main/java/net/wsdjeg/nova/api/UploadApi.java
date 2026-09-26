package net.wsdjeg.nova.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import net.wsdjeg.nova.ApiClient.UpdateSessionCallback;
import net.wsdjeg.nova.ApiClient.UploadCallback;
import net.wsdjeg.nova.ApiClient.UploadDirCallback;
import org.json.JSONObject;

/**
 * 文件上传 API
 * 包含:
 * - POST /session/:id/upload?path=relative/path 上传文件
 * - GET /session/:id/upload-dir 获取上传目录
 * - PUT /session/:id/upload-dir 设置上传目录
 */
public class UploadApi {
    private static final String TAG = "UploadApi";

    private final ApiConfig config;

    public UploadApi(ApiConfig config) {
        this.config = config;
    }

    /**
     * 上传文件到会话的工作目录
     * API 端点: POST /session/:id/upload?path=relative/path
     * 请求体: 原始二进制数据（binary-safe）
     * 响应格式: { "path": "...", "full_path": "...", "size": 123 }
     *
     * @param sessionId    会话 ID
     * @param fileData     文件二进制数据
     * @param relativePath 相对路径（如 images/photo.png）
     * @param contentType  Content-Type（如 image/png），可为 null
     * @param callback     回调
     */
    public void uploadFile(String sessionId, byte[] fileData, String relativePath,
                           String contentType, UploadCallback callback) {
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

        if (fileData == null || fileData.length == 0) {
            callback.onError("File data is empty");
            return;
        }

        if (relativePath == null || relativePath.isEmpty()) {
            callback.onError("Relative path is required");
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            BufferedReader br = null;
            try {
                String encodedPath = URLEncoder.encode(relativePath, "UTF-8");
                URL url = new URL(baseUrl + "/session/" + sessionId + "/upload?path=" + encodedPath);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                if (contentType != null && !contentType.isEmpty()) {
                    conn.setRequestProperty("Content-Type", contentType);
                } else {
                    conn.setRequestProperty("Content-Type", "application/octet-stream");
                }
                conn.setDoOutput(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(60000);
                conn.setUseCaches(false);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(fileData, 0, fileData.length);
                }

                int responseCode = conn.getResponseCode();

                if (responseCode == 200) {
                    br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    String path = jsonResponse.optString("path", "");
                    String fullPath = jsonResponse.optString("full_path", "");
                    long size = jsonResponse.optLong("size", 0);

                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onSuccess(path, fullPath, size));
                } else if (responseCode == 400) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Bad Request: Missing file path"));
                } else if (responseCode == 403) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Forbidden: Path traversal or absolute path rejected"));
                } else if (responseCode == 404) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Session not found"));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Unauthorized: Invalid API Key"));
                } else if (responseCode == 500) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Server error: Failed to write file"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "uploadFile failed", e);
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
     * 获取会话的上传目录
     * API 端点: GET /session/:id/upload-dir
     * 响应格式: { "upload_dir": "/path/to/dir" } 或 { "upload_dir": null }
     */
    public void getUploadDir(String sessionId, UploadDirCallback callback) {
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
                URL url = new URL(baseUrl + "/session/" + sessionId + "/upload-dir");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setUseCaches(false);

                int responseCode = conn.getResponseCode();

                if (responseCode == 200) {
                    br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    JSONObject json = new JSONObject(sb.toString());
                    String uploadDir = json.isNull("upload_dir") ? null : json.optString("upload_dir", null);
                    final String result = uploadDir;
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onSuccess(result));
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
                Log.e(TAG, "getUploadDir failed", e);
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
     * 设置会话的上传目录
     * API 端点: PUT /session/:id/upload-dir
     * 请求格式: { "upload_dir": "/path/to/dir" } 或 { "upload_dir": null }
     */
    public void setUploadDir(String sessionId, String uploadDir, UpdateSessionCallback callback) {
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
                URL url = new URL(baseUrl + "/session/" + sessionId + "/upload-dir");
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
                if (uploadDir == null || uploadDir.isEmpty()) {
                    requestBody.put("upload_dir", JSONObject.NULL);
                } else {
                    requestBody.put("upload_dir", uploadDir);
                }

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
                        callback.onError("Bad Request: Invalid upload_dir or directory does not exist"));
                } else {
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Error: " + code));
                }
            } catch (Exception e) {
                Log.e(TAG, "setUploadDir failed", e);
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

