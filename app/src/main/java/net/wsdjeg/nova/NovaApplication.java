package net.wsdjeg.nova;

import android.app.Application;
import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Nova Application class
 * Responsible for applying theme settings on app startup
 */
public class NovaApplication extends Application {

    private static Context appContext;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
        
        // Apply saved theme settings
        applyTheme();
    }

    /**
     * Get application context (for accessing resources from non-Activity classes)
     */
    public static Context getAppContext() {
        return appContext;
    }

    /**
     * Apply theme and language settings
     */
    private void applyTheme() {
        SettingsManager settingsManager = new SettingsManager(this);
        
        // Apply theme
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
        
        // Apply language
        int language = settingsManager.getLanguage();
        SettingsManager.applyLanguage(language);
    }
}

