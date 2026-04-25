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
    private static final String KEY_ACCOUNT_TAG_COLOR_INDEX = "account_tag_color_index";
    
    // 主题模式常量
    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;
    
    // 自动分配颜色模式（使用索引 -1 表示自动）
    public static final int AUTO_COLOR_INDEX = -1;
    
    // 账户标签颜色选项
    public static final String[] ACCOUNT_TAG_COLORS = {
        "#FF6B6B",  // 红色
        "#4ECDC4",  // 青色
        "#45B7D1",  // 蓝色
        "#96CEB4",  // 绿色
        "#FFEAA7",  // 黄色
        "#DDA0DD",  // 紫色
        "#98D8C8",  // 薄荷绿
        "#F7DC6F",  // 金色
    };
    
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
    
    /**
     * 设置账户标签颜色索引
     * @param colorIndex 颜色索引 (0-7)，或 AUTO_COLOR_INDEX (-1) 表示自动分配
     */
    public void setAccountTagColorIndex(int colorIndex) {
        prefs.edit()
            .putInt(KEY_ACCOUNT_TAG_COLOR_INDEX, colorIndex)
            .apply();
    }
    
    /**
     * 获取账户标签颜色索引
     * @return 颜色索引 (0-7)，或 AUTO_COLOR_INDEX (-1) 表示自动分配
     */
    public int getAccountTagColorIndex() {
        return prefs.getInt(KEY_ACCOUNT_TAG_COLOR_INDEX, 2); // 默认蓝色
    }
    
    /**
     * 是否使用自动分配颜色模式
     */
    public boolean isAutoColorMode() {
        return getAccountTagColorIndex() == AUTO_COLOR_INDEX;
    }
    
    /**
     * 获取账户标签颜色
     * @return 颜色字符串 (如 "#45B7D1")，如果是自动模式返回 null
     */
    public String getAccountTagColor() {
        int index = getAccountTagColorIndex();
        if (index == AUTO_COLOR_INDEX) {
            return null; // 自动模式
        }
        if (index >= 0 && index < ACCOUNT_TAG_COLORS.length) {
            return ACCOUNT_TAG_COLORS[index];
        }
        return ACCOUNT_TAG_COLORS[2]; // 默认蓝色
    }
    
    /**
     * 根据账号ID自动分配颜色
     * 使用账号ID的哈希值映射到颜色数组
     * @param accountId 账号ID
     * @return 颜色字符串
     */
    public static String getAutoAssignedColor(String accountId) {
        if (accountId == null || accountId.isEmpty()) {
            return ACCOUNT_TAG_COLORS[2]; // 默认蓝色
        }
        int hash = Math.abs(accountId.hashCode());
        int index = hash % ACCOUNT_TAG_COLORS.length;
        return ACCOUNT_TAG_COLORS[index];
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
