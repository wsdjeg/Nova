package com.example.myandroidapp;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {
    private static final String PREFS_NAME = "ChatAppSettings";
    private static final String KEY_URL = "url";
    private static final String KEY_PORT = "port";
    private static final String KEY_API_KEY = "api_key";
    
    private SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveSettings(String url, String port, String apiKey) {
        prefs.edit()
            .putString(KEY_URL, url)
            .putString(KEY_PORT, port)
            .putString(KEY_API_KEY, apiKey)
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

    public String getFullUrl() {
        String url = getUrl();
        String port = getPort();
        
        if (url.isEmpty()) {
            return "";
        }
        
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        
        if (!port.isEmpty()) {
            url = url + ":" + port;
        }
        
        return url;
    }
}
