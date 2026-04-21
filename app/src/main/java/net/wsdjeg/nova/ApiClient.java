package net.wsdjeg.nova;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class ApiClient {
    private static final String TAG = "ApiClient";
    
    private final SettingsManager settingsManager;
    
    public interface ApiCallback {
        void onSuccess(String response);
        void onError(String error);
    }
    
    public interface SessionsCallback {
        void onSuccess(String[] sessions);
        void onError(String error);
    }
    
    public interface MessagesCallback {
        void onSuccess(List<ChatMessage> messages);
        void onError(String error);
    }
    
    /**
     * Chat message model for API response
     */
    public static class ChatMessage {
        public String role;
        public String content;
        
        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
    
    public ApiClient(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }
    
    /**
     * Send a message to a specific chat session.
     * POST /
     * Request body: {"session": "session-id", "content": "message"}
     * Returns 204 on success.
     */
    public void sendMessage(String content, ApiCallback callback) {
        String baseUrl = settingsManager.getFullUrl();
        String apiKey = settingsManager.getApiKey();
        String session = settingsManager.getSession();
        
        if (baseUrl.isEmpty()) {
            callback.onError("Please configure API URL in settings");
            return;
        }
        
        if (apiKey.isEmpty()) {
            callback.onError("Please configure API Key in settings");
            return;
        }
        
        if (session.isEmpty()) {
            callback.onError("Please configure Session ID in settings");
            return;
        }
        
        new Thread(() -> {
            try {
                URL url = new URL(baseUrl + "/");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);

                // Build request body: {"session": "...", "content": "..."}
                JSONObject requestBody = new JSONObject();
                requestBody.put("session", session);
                requestBody.put("content", content);
                
                // Send request
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                // Read response
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 204) {
                    // Success - 204 No Content
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess("Message sent successfully"));
                } else if (responseCode == 200 || responseCode == 201) {
                    // Read success response
                    BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    br.close();
                    
                    String result = response.toString();
                    // Try to extract response field
                    try {
                        JSONObject jsonResponse = new JSONObject(result);
                        result = jsonResponse.optString("response", result);
                    } catch (Exception e) {
                        // Not JSON, use raw response
                    }
                    
                    final String finalResult = result;
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess(finalResult));
                } else {
                    // Error response
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
                    
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError(errorMessage));
                }
            } catch (Exception e) {
                Log.e(TAG, "sendMessage failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            }
        }).start();
    }
    
    /**
     * Get all active session IDs.
     * GET /sessions
     * Returns JSON array: ["session1", "session2", ...]
     */
    public void getSessions(SessionsCallback callback) {
        String baseUrl = settingsManager.getFullUrl();
        String apiKey = settingsManager.getApiKey();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        new Thread(() -> {
            try {
                URL url = new URL(baseUrl + "/sessions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    br.close();
                    
                    // Parse JSON array
                    JSONArray jsonArray = new JSONArray(response.toString());
                    String[] sessions = new String[jsonArray.length()];
                    for (int i = 0; i < jsonArray.length(); i++) {
                        sessions[i] = jsonArray.getString(i);
                    }
                    
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess(sessions));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "getSessions failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            }
        }).start();
    }
    
    /**
     * Get messages for a specific session.
     * GET /messages?session=xxx
     * Returns JSON array with messages containing role and content.
     */
    public void getMessages(String sessionId, MessagesCallback callback) {
        String baseUrl = settingsManager.getFullUrl();
        String apiKey = settingsManager.getApiKey();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = settingsManager.getSession();
            if (sessionId.isEmpty()) {
                callback.onError("Please configure Session ID");
                return;
            }
        }
        
        final String finalSessionId = sessionId;
        
        new Thread(() -> {
            try {
                URL url = new URL(baseUrl + "/messages?session=" + finalSessionId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    br.close();
                    
                    // Parse JSON array
                    JSONArray jsonArray = new JSONArray(response.toString());
                    List<ChatMessage> messages = new ArrayList<>();
                    
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject msg = jsonArray.getJSONObject(i);
                        String role = msg.optString("role", "");
                        String content = msg.optString("content", "");
                        
                        // Only add messages with content
                        if (!content.isEmpty()) {
                            messages.add(new ChatMessage(role, content));
                        }
                    }
                    
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess(messages));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else if (responseCode == 404) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Session not found"));
                } else if (responseCode == 400) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Bad Request: Missing session ID"));
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "getMessages failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            }
        }).start();
    }
    
    /**
     * Get HTML preview of a session.
     * GET /session?id=session-id
     * Returns HTML content.
     */
    public void getSessionPreview(String sessionId, ApiCallback callback) {
        String baseUrl = settingsManager.getFullUrl();
        String apiKey = settingsManager.getApiKey();
        
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            callback.onError("Please configure API settings");
            return;
        }
        
        new Thread(() -> {
            try {
                URL url = new URL(baseUrl + "/session?id=" + sessionId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-API-Key", apiKey);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    br.close();
                    
                    final String html = response.toString();
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onSuccess(html));
                } else if (responseCode == 401) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Unauthorized: Invalid API Key"));
                } else if (responseCode == 404) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Session not found"));
                } else if (responseCode == 400) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Bad Request: Missing session ID"));
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        callback.onError("Error: " + responseCode));
                }
            } catch (Exception e) {
                Log.e(TAG, "getSessionPreview failed", e);
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("Network error: " + e.getMessage()));
            }
        }).start();
    }
}
