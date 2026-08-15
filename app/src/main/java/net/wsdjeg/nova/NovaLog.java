package net.wsdjeg.nova;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Nova 应用内日志引擎
 *
 * 特性：
 * - 环形内存缓冲区（默认 4000 条），应用内随时读取，不依赖 logcat 权限
 * - 异步持久化到 files/logs/nova.log，超过 1MB 自动轮转（保留一份 .1 备份）
 * - 同时透传到 android.util.Log，保留 logcat 兼容
 * - 监听器机制，日志页面可实时刷新
 * - 未捕获崩溃自动记录（链回系统默认处理器，不影响原有崩溃流程）
 * - net() 便捷方法用于记录 HTTP 请求
 */
public final class NovaLog {

    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;

    /** 单条日志记录 */
    public static final class Entry {
        public final long seq;        // 全局递增序号
        public final long time;       // System.currentTimeMillis()
        public final int level;       // VERBOSE..ERROR
        public final String tag;
        public final String message;
        public final String stack;    // 异常堆栈，可为 null

        Entry(long seq, long time, int level, String tag, String message, String stack) {
            this.seq = seq;
            this.time = time;
            this.level = level;
            this.tag = tag;
            this.message = message;
            this.stack = stack;
        }
    }

    /** 日志新增监听器（可能在任意线程回调） */
    public interface Listener {
        void onLogAdded(Entry entry);
    }

    private static final int MAX_BUFFER = 4000;
    private static final long MAX_FILE_SIZE = 1024 * 1024; // 1MB

    private static final ArrayDeque<Entry> sBuffer = new ArrayDeque<>();
    private static final AtomicLong sSeq = new AtomicLong(0);
    private static final List<Listener> sListeners = new CopyOnWriteArrayList<>();

    private static final Object sFileLock = new Object();
    private static volatile Context sAppContext;
    private static volatile boolean sInited = false;
    private static volatile ExecutorService sFileExecutor;

    private static final Object SDF_LOCK = new Object();
    private static SimpleDateFormat sFullFormat;   // yyyy-MM-dd HH:mm:ss.SSS
    private static SimpleDateFormat sShortFormat;  // MM-dd HH:mm:ss.SSS

    private NovaLog() {
    }

    // ------------------------------------------------------------------
    // 初始化
    // ------------------------------------------------------------------

    /** 在 Application.onCreate 中调用，记录启动信息并安装崩溃处理器 */
    public static void init(Context context) {
        if (sInited || context == null) {
            return;
        }
        sInited = true;
        sAppContext = context.getApplicationContext();

        String versionName = "?";
        long versionCode = 0;
        try {
            PackageInfo pi = sAppContext.getPackageManager()
                    .getPackageInfo(sAppContext.getPackageName(), 0);
            versionName = pi.versionName;
            versionCode = (Build.VERSION.SDK_INT >= 28) ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Throwable ignored) {
        }

        i("App", "Nova v" + versionName + " (" + versionCode + ") starting, pid="
                + android.os.Process.myPid());
        i("App", "device=" + Build.MANUFACTURER + " " + Build.MODEL
                + ", android=" + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");

        installCrashHandler();
    }

    private static void installCrashHandler() {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                Entry e = buildEntry(ERROR, "Crash",
                        "Uncaught exception in thread " + thread.getName(), throwable);
                addToBuffer(e);
                notifyListeners(e);
                // 崩溃时同步写盘，确保记录不丢
                writeFileLine(formatFull(e));
            } catch (Throwable ignored) {
            }
            if (prev != null) {
                prev.uncaughtException(thread, throwable);
            }
        });
    }

    // ------------------------------------------------------------------
    // 写入 API
    // ------------------------------------------------------------------

    public static void v(String tag, String msg) {
        log(VERBOSE, tag, msg, null);
    }

    public static void d(String tag, String msg) {
        log(DEBUG, tag, msg, null);
    }

    public static void i(String tag, String msg) {
        log(INFO, tag, msg, null);
    }

    public static void w(String tag, String msg) {
        log(WARN, tag, msg, null);
    }

    public static void e(String tag, String msg) {
        log(ERROR, tag, msg, null);
    }

    public static void w(String tag, String msg, Throwable t) {
        log(WARN, tag, msg, t);
    }

    public static void e(String tag, String msg, Throwable t) {
        log(ERROR, tag, msg, t);
    }

    /** 记录一次 HTTP 请求（供网络层调用） */
    public static void net(String method, String url, int responseCode, long costMs, long bytes) {
        int level;
        if (responseCode >= 400) {
            level = ERROR;
        } else if (responseCode >= 300) {
            level = WARN;
        } else {
            level = DEBUG;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(method).append(' ').append(url)
                .append(" -> ").append(responseCode)
                .append(" (").append(costMs).append("ms");
        if (bytes >= 0) {
            sb.append(", ").append(humanBytes(bytes));
        }
        sb.append(')');
        log(level, "NET", sb.toString(), null);
    }

    /** 核心写入方法，任何异常都不会向外抛出 */
    public static void log(int level, String tag, String msg, Throwable t) {
        try {
            // 透传到 logcat
            String sTag = (tag == null || tag.isEmpty()) ? "Nova" : tag;
            String sMsg = msg == null ? "" : msg;
            switch (level) {
                case VERBOSE:
                    Log.v(sTag, sMsg);
                    break;
                case DEBUG:
                    Log.d(sTag, sMsg);
                    break;
                case INFO:
                    Log.i(sTag, sMsg);
                    break;
                case WARN:
                    Log.w(sTag, sMsg, t);
                    break;
                case ERROR:
                default:
                    Log.e(sTag, sMsg, t);
                    break;
            }
        } catch (Throwable ignored) {
        }

        try {
            Entry e = buildEntry(level, tag, msg, t);
            addToBuffer(e);
            appendToFileAsync(e);
            notifyListeners(e);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    // 读取 API
    // ------------------------------------------------------------------

    /** 返回缓冲区快照（新列表，可安全持有） */
    public static List<Entry> snapshot() {
        synchronized (sBuffer) {
            return new ArrayList<>(sBuffer);
        }
    }

    /** 清空内存缓冲区与持久化文件 */
    public static void clear() {
        synchronized (sBuffer) {
            sBuffer.clear();
        }
        if (sFileExecutor != null) {
            sFileExecutor.execute(() -> {
                synchronized (sFileLock) {
                    try {
                        File dir = new File(sAppContext.getFilesDir(), "logs");
                        new File(dir, "nova.log").delete();
                        new File(dir, "nova.log.1").delete();
                    } catch (Throwable ignored) {
                    }
                }
            });
        }
    }

    /** 将一组日志导出为纯文本 */
    public static String dump(List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            sb.append(formatFull(e)).append('\n');
        }
        return sb.toString();
    }

    public static void register(Listener l) {
        if (l != null && !sListeners.contains(l)) {
            sListeners.add(l);
        }
    }

    public static void unregister(Listener l) {
        if (l != null) {
            sListeners.remove(l);
        }
    }

    /** 级别对应的单字母：V/D/I/W/E */
    public static String levelLetter(int level) {
        switch (level) {
            case VERBOSE:
                return "V";
            case DEBUG:
                return "D";
            case INFO:
                return "I";
            case WARN:
                return "W";
            case ERROR:
                return "E";
            default:
                return "?";
        }
    }

    /** 短时间格式：MM-dd HH:mm:ss.SSS（列表显示用） */
    public static String formatShort(long time) {
        synchronized (SDF_LOCK) {
            if (sShortFormat == null) {
                sShortFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
            }
            return sShortFormat.format(new Date(time));
        }
    }

    /** 完整单条格式（含堆栈），用于导出与详情弹窗 */
    public static String formatFull(Entry e) {
        StringBuilder sb = new StringBuilder(128);
        synchronized (SDF_LOCK) {
            if (sFullFormat == null) {
                sFullFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
            }
            sb.append(sFullFormat.format(new Date(e.time)));
        }
        sb.append(' ').append(levelLetter(e.level))
                .append('/').append(e.tag)
                .append(": ").append(e.message);
        if (e.stack != null && !e.stack.isEmpty()) {
            sb.append('\n').append(e.stack);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private static Entry buildEntry(int level, String tag, String msg, Throwable t) {
        String stack = null;
        if (t != null) {
            try {
                stack = Log.getStackTraceString(t);
            } catch (Throwable ignored) {
            }
        }
        return new Entry(
                sSeq.incrementAndGet(),
                System.currentTimeMillis(),
                level,
                (tag == null || tag.isEmpty()) ? "Nova" : tag,
                msg == null ? "" : msg,
                stack);
    }

    private static void addToBuffer(Entry e) {
        synchronized (sBuffer) {
            while (sBuffer.size() >= MAX_BUFFER) {
                sBuffer.pollFirst();
            }
            sBuffer.addLast(e);
        }
    }

    private static void notifyListeners(Entry e) {
        for (Listener l : sListeners) {
            try {
                l.onLogAdded(e);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void appendToFileAsync(Entry e) {
        if (sAppContext == null) {
            return;
        }
        ExecutorService ex = sFileExecutor;
        if (ex == null) {
            synchronized (sFileLock) {
                if (sFileExecutor == null) {
                    sFileExecutor = Executors.newSingleThreadExecutor();
                }
                ex = sFileExecutor;
            }
        }
        final String line = formatFull(e);
        ex.execute(() -> writeFileLine(line));
    }

    /** 必须在 sFileLock 内调用；轮转策略：nova.log -> nova.log.1 */
    private static void writeFileLine(String line) {
        synchronized (sFileLock) {
            if (sAppContext == null) {
                return;
            }
            try {
                File dir = new File(sAppContext.getFilesDir(), "logs");
                if (!dir.exists() && !dir.mkdirs()) {
                    return;
                }
                File file = new File(dir, "nova.log");
                if (file.length() > MAX_FILE_SIZE) {
                    File backup = new File(dir, "nova.log.1");
                    //noinspection ResultOfMethodCallIgnored
                    backup.delete();
                    //noinspection ResultOfMethodCallIgnored
                    file.renameTo(backup);
                }
                try (FileWriter writer = new FileWriter(file, true)) {
                    writer.append(line).append('\n');
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1fKB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1fMB", bytes / 1024.0 / 1024.0);
    }
}

