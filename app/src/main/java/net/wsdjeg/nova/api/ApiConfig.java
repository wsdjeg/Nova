package net.wsdjeg.nova.api;

import net.wsdjeg.nova.SettingsManager;

/**
 * API 配置持有类
 * 封装 baseUrl/apiKey 的获取逻辑，支持多账号：
 * - 通过 SettingsManager 使用全局默认设置
 * - 或直接指定 baseUrl + apiKey（多账号覆盖模式）
 */
public class ApiConfig {
    private final SettingsManager settingsManager;
    private final String overrideBaseUrl;
    private final String overrideApiKey;

    public ApiConfig(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
        this.overrideBaseUrl = null;
        this.overrideApiKey = null;
    }

    public ApiConfig(String baseUrl, String apiKey) {
        this.settingsManager = null;
        this.overrideBaseUrl = baseUrl;
        this.overrideApiKey = apiKey;
    }

    public String getBaseUrl() {
        if (overrideBaseUrl != null) {
            return overrideBaseUrl;
        }
        if (settingsManager != null) {
            return settingsManager.getFullUrl();
        }
        return "";
    }

    public String getApiKey() {
        if (overrideApiKey != null) {
            return overrideApiKey;
        }
        if (settingsManager != null) {
            return settingsManager.getApiKey();
        }
        return "";
    }

    public String getSession() {
        if (settingsManager != null) {
            return settingsManager.getSession();
        }
        return "";
    }

    public boolean hasValidSettings() {
        return !getBaseUrl().isEmpty() && !getApiKey().isEmpty();
    }
}

