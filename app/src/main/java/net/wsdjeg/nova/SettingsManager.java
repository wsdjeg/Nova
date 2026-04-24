package net.wsdjeg.nova;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {
    private static final String PREFS_NAME = "ChatAppSettings";
    private static final String KEY_URL = "url";
    private static final String KEY_PORT = "port";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_SESSION = "session";
    private static final String KEY_THEME_MODE = "theme_mode";
    
    // 主题模式常量
    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;
    
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
    
    public void setSession(String session) {
        prefs.edit()
            .putString(KEY_SESSION, session)
            .apply();
    }
    
    /**
     * 设置主题模式
     * @param mode THEME_SYSTEM, THEME_LIGHT, 或 THEME_DARK
     */
    public void setThemeMode(int mode) {
        prefs.edit()
            .putInt(KEY_THEME_MODE, mode)
            .apply();
    }
    
    /**
     * 获取主题模式
     * @return THEME_SYSTEM, THEME_LIGHT, 或 THEME_DARK
     */
    public int getThemeMode() {
        return prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM);
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

    /**
     * 检查 API 设置是否有效（不检查 Session）
     */
    public boolean hasValidSettings() {
        return !getFullUrl().isEmpty() && !getApiKey().isEmpty();
    }
    
    /**
     * 检查 Session 是否已选择
     */
    public boolean hasSession() {
        String session = getSession();
        return session != null && !session.isEmpty();
    }
}
