package net.wsdjeg.nova;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Nova Application 类
 * 负责在应用启动时应用主题设置
 */
public class NovaApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        
        // 应用保存的主题设置
        applyTheme();
    }

    /**
     * 应用主题设置
     */
    private void applyTheme() {
        SettingsManager settingsManager = new SettingsManager(this);
        int themeMode = settingsManager.getThemeMode();
        
        switch (themeMode) {
            case SettingsManager.THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case SettingsManager.THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case SettingsManager.THEME_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
