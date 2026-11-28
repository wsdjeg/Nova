package net.wsdjeg.nova.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import net.wsdjeg.nova.ApiClient.TrashCallback;
import net.wsdjeg.nova.ApiClient.TrashPurgeCallback;
import net.wsdjeg.nova.ApiClient.TrashRestoreCallback;
import net.wsdjeg.nova.TrashEntry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话回收站 API
 *
 * 对应 chat.nvim HTTP Server 的三个端点：
 * - GET    /trash                  列出回收站条目（裸数组，与 GET /sessions 同形状）
 * - POST   /trash/{target}/restore  恢复会话，返回 200 {"id": "..."}
 * - DELETE /trash/{target}          永久清除该快照，返回 204
 *
 * {target} 支持两种寻址方式：
 * - 会话 ID（如 2024-01-15-10-30-00）
 * - 回收站文件名（如 2024-01-15-10-30-00~1.json）
 * 同一会话被删除多次会留下多份快照，它们的 ID 相同，只有文件名能区分，
 * 因此调用方应优先传 {@link TrashEntry#getTarget()}（文件名）。
 *
 * 服务端错误语义：
 * - 404：回收站中不存在该条目
 * - 409：同名 ID 的活跃会话已重新存在，恢复操作拒绝覆盖
 * - 400：文件存在但内容不可用
 * 这三个状态码都会带上服务端的 error 文本，回调里一并交给界面决定文案。
 *
 * 恢复只把会话写回服务端缓存并载入内存（随后会出现在 GET /sessions），
 * 不会打开任何人的编辑器窗口 —— 这是面向远程客户端的设计。
 */
public class TrashApi {
    private static final String TAG = "TrashApi";

    /** 网络异常（拿不到 HTTP 状态码）时回调里使用的伪状态码 */
    public static final int NETWORK_ERROR = -1;

    private final ApiConfig config;

    public TrashApi(ApiConfig config) {
        this.config = config;
    }

    /**
     * 列出回收站
     * API 端点: GET /trash
     * 响应格式: [
     *   { "id": "...", "file": "...", "deleted_at": 1730000000,
     *     "title": "...", "preview": "...", "message_count": 12 }
     * ]
     */
    public void listTrash(TrashCallback callback) {
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
                URL url = new URL(baseUrl + "/trash");
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
                    br = new BufferedReader(new InputStreamReader(
                            conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }

                    JSONArray jsonArray = new JSONArray(response.toString());
                    List<TrashEntry> entries = new ArrayList<>();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject obj = jsonArray.optJSONObject(i);
                        if (obj == null) {
                            continue;
                        }
                        String id = obj.optString("id", "");
                        if (id.isEmpty()) {
                            // 没有 id 的条目无法寻址（服务端也拒绝这类快照），跳过
                            continue;
                        }
                        TrashEntry entry = new TrashEntry(id);
                        entry.setFile(obj.optString("file", ""));
                        entry.setDeletedAt(obj.optLong("deleted_at", 0));
                        entry.setTitle(obj.optString("title", ""));
                        entry.setPreview(obj.optString("preview", ""));
                        entry.setMessageCount(obj.optInt("message_count", 0));
                        entries.add(entry);
                    }

                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onSuccess(entries));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError("Unauthorized: Invalid API Key"));
                } else {
                    final String message = readErrorMessage(conn);
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError(message != null && !message.isEmpty()
                                ? message : ("Error: " + code)));
                }
            } catch (Exception e) {
                Log.e(TAG, "listTrash failed", e);
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
     * 恢复回收站条目
     * API 端点: POST /trash/{target}/restore
     * 响应格式: 200 {"id": "..."}
     *
     * @param target   会话 ID 或回收站文件名
     * @param callback 成功时带回恢复后的会话 ID（服务端未返回时为 null）
     */
    public void restoreTrash(String target, TrashRestoreCallback callback) {
        String baseUrl = config.getBaseUrl();
        String apiKey = config.getApiKey();

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError(NETWORK_ERROR, "Please configure API settings");
            return;
        }

        if (target == null || target.isEmpty()) {
            callback.onError(NETWORK_ERROR, "Trash target is required");
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            BufferedReader br = null;
            try {
                URL url = new URL(baseUrl + "/trash/" + encodeSegment(target) + "/restore");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setRequestProperty("Connection", "close");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setUseCaches(false);
                // 该端点没有请求体。HttpURLConnection 手动设置 Content-Length 会被
                // 忽略（属于受限请求头），改为打开输出流后立即关闭：这样它会自己
                // 算出并发送 Content-Length: 0，服务端不必等待 body 就能应答。
                // 注意要先设好超时，再触发可能的连接建立。
                conn.setDoOutput(true);
                conn.getOutputStream().close();

                int responseCode = conn.getResponseCode();

                if (responseCode == 200 || responseCode == 204) {
                    String sessionId = null;
                    if (responseCode == 200) {
                        br = new BufferedReader(new InputStreamReader(
                                conn.getInputStream(), StandardCharsets.UTF_8));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            response.append(line);
                        }
                        String body = response.toString().trim();
                        if (!body.isEmpty()) {
                            try {
                                JSONObject json = new JSONObject(body);
                                String id = json.optString("id", "");
                                if (!id.isEmpty()) {
                                    sessionId = id;
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "restoreTrash: unexpected body: " + body);
                            }
                        }
                    }
                    final String restoredId = sessionId;
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onSuccess(restoredId));
                } else {
                    final String message = readErrorMessage(conn);
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError(code, message));
                }
            } catch (Exception e) {
                Log.e(TAG, "restoreTrash failed", e);
                new Handler(Looper.getMainLooper()).post(() ->
                    callback.onError(NETWORK_ERROR, "Network error: " + e.getMessage()));
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
     * 永久清除回收站条目
     * API 端点: DELETE /trash/{target}
     * 响应: 204
     *
     * @param target   会话 ID 或回收站文件名
     * @param callback 成功时无返回值
     */
    public void purgeTrash(String target, TrashPurgeCallback callback) {
        String baseUrl = config.getBaseUrl();
        String apiKey = config.getApiKey();

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError(NETWORK_ERROR, "Please configure API settings");
            return;
        }

        if (target == null || target.isEmpty()) {
            callback.onError(NETWORK_ERROR, "Trash target is required");
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(baseUrl + "/trash/" + encodeSegment(target));
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
                } else {
                    final String message = readErrorMessage(conn);
                    final int code = responseCode;
                    new Handler(Looper.getMainLooper()).post(() ->
                        callback.onError(code, message));
                }
            } catch (Exception e) {
                Log.e(TAG, "purgeTrash failed", e);
                new Handler(Looper.getMainLooper()).post(() ->
                    callback.onError(NETWORK_ERROR, "Network error: " + e.getMessage()));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    /**
     * 对路径片段做百分号编码。
     * URLEncoder 面向表单（空格转 "+"），这里必须换成 %20，
     * 否则文件名里的空格会被服务端当成字面 "+"。
     */
    private static String encodeSegment(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * 读取错误响应体中的 error 字段（服务端错误格式: {"error": "..."}），
     * 解析失败时返回响应体原文，无响应体时返回 null。
     */
    private static String readErrorMessage(HttpURLConnection conn) {
        BufferedReader br = null;
        try {
            InputStream es = conn.getErrorStream();
            if (es == null) {
                return null;
            }
            br = new BufferedReader(new InputStreamReader(es, StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            String body = response.toString().trim();
            if (body.isEmpty()) {
                return null;
            }
            try {
                JSONObject json = new JSONObject(body);
                String error = json.optString("error", "");
                if (!error.isEmpty()) {
                    return error;
                }
            } catch (Exception ignored) {
            }
            return body;
        } catch (Exception e) {
            return null;
        } finally {
            if (br != null) {
                try { br.close(); } catch (Exception ignored) {}
            }
        }
    }
}

