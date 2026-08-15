package net.wsdjeg.nova;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Nova Application class
 * - 应用主题/语言设置
 * - 初始化 NovaLog 日志引擎
 * - 自动记录 Activity 生命周期与低内存事件
 */
public class NovaApplication extends Application {

    private static final String TAG = "App";

    private static Context appContext;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();

        // 初始化应用内日志引擎（记录启动信息 + 安装崩溃处理器）
        NovaLog.init(this);

        // Apply saved theme settings
        applyTheme();

        registerLifecycleLogger();
        NovaLog.i(TAG, "NovaApplication onCreate done");
    }

    /**
     * 自动记录所有 Activity 的生命周期事件，便于排查界面问题
     */
    private void registerLifecycleLogger() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                NovaLog.i("Life", shortName(activity) + " created"
                        + (savedInstanceState != null ? " (restore)" : ""));
            }

            @Override
            public void onActivityStarted(Activity activity) {
                NovaLog.d("Life", shortName(activity) + " started");
            }

            @Override
            public void onActivityResumed(Activity activity) {
                NovaLog.d("Life", shortName(activity) + " resumed");
            }

            @Override
            public void onActivityPaused(Activity activity) {
                NovaLog.d("Life", shortName(activity) + " paused");
            }

            @Override
            public void onActivityStopped(Activity activity) {
                NovaLog.d("Life", shortName(activity) + " stopped");
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                NovaLog.i("Life", shortName(activity) + " destroyed"
                        + (activity.isFinishing() ? " (finishing)" : ""));
            }
        });
    }

    private static String shortName(Activity activity) {
        return activity.getComponentName() != null
                ? activity.getComponentName().flattenToShortString()
                : activity.getClass().getSimpleName();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            NovaLog.w(TAG, "Low memory! trim level=" + level);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        NovaLog.i(TAG, "Configuration changed: orientation="
                + (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ? "landscape" : "portrait")
                + ", uiMode=0x" + Integer.toHexString(newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK));
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

