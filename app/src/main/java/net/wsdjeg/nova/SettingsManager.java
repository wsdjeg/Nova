package net.wsdjeg.nova;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {
    private static final String PREFS_NAME = "ChatAppSettings";
    private static final String KEY_URL = "url";
    private static final String KEY_PORT = "port";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_SESSION = "session";
    
    private SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveSettings(String url, String port, String apiKey, String session) {
        prefs.edit()
            .putString(KEY_URL, url)
            .putString(KEY_PORT, port)
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_SESSION, session)
            .apply();
    }

    // Keep for backward compatibility
    public void saveSettings(String url, String port, String apiKey) {
        saveSettings(url, port, apiKey, getSession());
    }
    
    public void setSession(String session) {
        prefs.edit()
            .putString(KEY_SESSION, session)
            .apply();
    }

    public String getUrl() {
        return prefs.getString(KEY_URL, "");
    }

    public String getPort() {
        return prefs.getString(KEY_PORT, "");
    }

    public String getApiKey() {
        return prefs.getString(KEY_API_KEY, "");
    }

    public String getSession() {
        return prefs.getString(KEY_SESSION, "");
    }

    public String getFullUrl() {
        String url = getUrl();
        String port = getPort();
        
        if (url.isEmpty()) {
            return "";
        }
        
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        
        // Remove trailing slash
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        
        if (!port.isEmpty()) {
            url = url + ":" + port;
        }
        
        return url;
    }

    public boolean hasValidSettings() {
        return !getFullUrl().isEmpty() && !getApiKey().isEmpty() && !getSession().isEmpty();
    }
}
