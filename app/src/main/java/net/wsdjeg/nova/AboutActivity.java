package net.wsdjeg.nova;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.noties.markwon.Markwon;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.MarkwonSpansFactory;
import org.commonmark.node.Code;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.html.HtmlPlugin;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AboutActivity extends AppCompatActivity {

    private static final String TAG = "AboutActivity";

    // GitHub API：获取所有 releases（含 prerelease）
    private static final String GITHUB_API_RELEASES =
            "https://api.github.com/repos/wsdjeg/Nova/releases";
    // GitHub API：直接通过 tag 获取 prerelease（fallback，当 /releases 列表未返回 prerelease 时使用）
    private static final String GITHUB_API_PRERELEASE_TAG =
            "https://api.github.com/repos/wsdjeg/Nova/releases/tags/prerelease";
    // GitHub 仓库
    private static final String GITHUB_URL = "https://github.com/wsdjeg/Nova";
    // chat.nvim 官网
    private static final String CHAT_NVIM_URL = "https://nvim.chat";

    private OkHttpClient httpClient;
    private Markwon markwon;
    private AlertDialog downloadDialog;
    private ProgressBar downloadProgressBar;
    private TextView downloadProgressText;
    private Call downloadCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        // 设置 Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("关于");

        // 设置返回箭头颜色为白色
        Drawable navigationIcon = toolbar.getNavigationIcon();
        if (navigationIcon != null) {
            navigationIcon.setColorFilter(
                    ContextCompat.getColor(this, android.R.color.white),
                    PorterDuff.Mode.SRC_IN);
            toolbar.setNavigationIcon(navigationIcon);
        }

        setVersionText();
        setupLinks();
        setupCheckUpdate();

        httpClient = new OkHttpClient();

        // 初始化 Markwon，用于渲染 Markdown 格式的更新日志
        markwon = Markwon.builder(this)
            .usePlugin(new AbstractMarkwonPlugin() {
                @Override
                public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                    builder.headingTextSizeMultipliers(new float[]{
                        1.28f, // H1 -> 18sp
                        1.14f, // H2 -> 16sp
                        1.07f, // H3 -> 15sp
                        1.00f, // H4 -> 14sp
                        0.93f, // H5 -> 13sp
                        0.86f  // H6 -> 12sp
                    });
                }
                @Override
                public void configureSpansFactory(@NonNull MarkwonSpansFactory.Builder builder) {
                    builder.setFactory(Code.class, (configuration, renderProps) ->
                        new InlineCodeSpan(0, getResources().getDisplayMetrics().density));
                }
            })
            .usePlugin(TablePlugin.create(this))
            .usePlugin(TaskListPlugin.create(this))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .build();
    }

    // ── 版本信息 ──────────────────────────────────────────────────

    private void setVersionText() {
        TextView tvVersion = findViewById(R.id.tv_version);
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            // 显示 APK 构建日期（文件修改时间近似为安装时间）
            long installTime = new File(pInfo.applicationInfo.sourceDir).lastModified();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            tvVersion.setText("Version " + version + " (" + sdf.format(new Date(installTime)) + ")");
        } catch (PackageManager.NameNotFoundException e) {
            tvVersion.setText("Version 1.0.0");
        }
    }

    private String getCurrentVersion() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }

    // ── 链接 ──────────────────────────────────────────────────────

    private void setupLinks() {
        TextView tvGithub = findViewById(R.id.tv_github);
        tvGithub.setOnClickListener(v -> openUrl(GITHUB_URL));

        TextView tvChatNvim = findViewById(R.id.tv_chat_nvim);
        tvChatNvim.setOnClickListener(v -> openUrl(CHAT_NVIM_URL));
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    // ── 检查更新 ──────────────────────────────────────────────────

    private void setupCheckUpdate() {
        TextView tvCheckUpdate = findViewById(R.id.tv_check_update);
        tvCheckUpdate.setOnClickListener(v -> checkForUpdates());
    }

    /**
     * 从 GitHub API 获取 releases，检查是否有新版本。
     *
     * 开发版（versionName 含 -dev）：
     *   CI 构建时会在版本号中注入 commit hash（如 3.0-dev-abc1234）。
     *   GitHub 上只有一个 prerelease 且总是最新的，只需比较 commit hash。
     *   hash 相同 -> 已是最新；hash 不同 -> 有新开发版可更新。
     *
     * 稳定版（versionName 不含 -dev）：
     *   遍历所有 release，分别找到最新稳定版和最新预发布版，
     *   通过版本号比较选出真正最新的，再与当前版本比较决定是否提示更新。
     */
    private void checkForUpdates() {
        ProgressDialog loading = new ProgressDialog(this);
        loading.setMessage("正在检查更新...");
        loading.setCancelable(false);
        loading.show();

        Request request = new Request.Builder()
                .url(GITHUB_API_RELEASES)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Nova-Android")
                .header("Cache-Control", "no-cache")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    loading.dismiss();
                    Toast.makeText(AboutActivity.this,
                            "检查更新失败: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                runOnUiThread(() -> loading.dismiss());

                if (!response.isSuccessful()) {
                    final int code = response.code();
                    runOnUiThread(() -> Toast.makeText(AboutActivity.this,
                            "检查更新失败: HTTP " + code,
                            Toast.LENGTH_SHORT).show());
                    return;
                }

                try {
                    String bodyStr = response.body().string();
                    JSONArray releases = new JSONArray(bodyStr);

                    if (releases.length() == 0) {
                        runOnUiThread(() -> Toast.makeText(AboutActivity.this,
                                "暂无可用版本", Toast.LENGTH_SHORT).show());
                        return;
                    }

                    String currentVersion = getCurrentVersion();
                    boolean isDev = currentVersion.contains("-dev");

                    if (isDev) {
                        // 开发版：只需找到 prerelease，比较 commit hash
                        JSONObject prerelease = null;
                        for (int i = 0; i < releases.length(); i++) {
                            JSONObject release = releases.getJSONObject(i);
                            if (release.optBoolean("prerelease", false)) {
                                prerelease = release;
                                break;
                            }
                        }

                        if (prerelease == null) {
                            // GitHub /releases 列表 API 有时不返回 prerelease
                            // （CI 使用 delete + recreate 方式更新 prerelease 时可能出现此问题）
                            // Fallback: 直接通过 tag 获取 prerelease
                            fetchPrereleaseByTag(currentVersion);
                            return;
                        }

                        String remoteHash = extractDevCommitHash(extractVersion(prerelease));
                        String localHash = extractDevCommitHash(currentVersion);

                        // commit hash 相同 -> 已是最新
                        if (remoteHash != null && remoteHash.equals(localHash)) {
                            final String curVer = currentVersion;
                            runOnUiThread(() -> Toast.makeText(AboutActivity.this,
                                    "当前已是最新开发版本 (v" + curVer + ")",
                                    Toast.LENGTH_SHORT).show());
                            return;
                        }

                        // 有新开发版，展示更新对话框
                        showReleaseUpdate(prerelease, true);
                        return;
                    }

                    // 稳定版：遍历 releases 找到最新版本进行比较
                    JSONObject latestStable = null;
                    JSONObject latestPrerelease = null;

                    for (int i = 0; i < releases.length(); i++) {
                        JSONObject release = releases.getJSONObject(i);
                        boolean isPre = release.optBoolean("prerelease", false);
                        if (isPre) {
                            if (latestPrerelease == null) {
                                latestPrerelease = release;
                            }
                        } else {
                            if (latestStable == null) {
                                latestStable = release;
                            }
                        }
                        // 两者都找到后提前退出
                        if (latestStable != null && latestPrerelease != null) break;
                    }

                    // 选择要展示的 release：
                    // 优先稳定版；仅当预发布版本号严格大于稳定版时才选预发布
                    JSONObject latest;
                    if (latestStable != null && latestPrerelease != null) {
                        String stableVer = extractVersion(latestStable);
                        String preVer = extractVersion(latestPrerelease);
                        latest = (compareVersions(preVer, stableVer) > 0)
                                ? latestPrerelease : latestStable;
                    } else {
                        latest = (latestStable != null) ? latestStable : latestPrerelease;
                    }

                    if (latest == null) {
                        runOnUiThread(() -> Toast.makeText(AboutActivity.this,
                                "暂无可用版本", Toast.LENGTH_SHORT).show());
                        return;
                    }

                    // 版本比较：获取到的版本不比当前版本新时，提示已最新
                    String latestVer = extractVersion(latest);
                    if (compareVersions(latestVer, currentVersion) <= 0) {
                        final String curVer = currentVersion;
                        runOnUiThread(() -> Toast.makeText(AboutActivity.this,
                                "当前已是最新版本 (v" + curVer + ")",
                                Toast.LENGTH_SHORT).show());
                        return;
                    }

                    showReleaseUpdate(latest, latest.optBoolean("prerelease", false));

                } catch (Exception e) {
                    Log.e(TAG, "解析 release JSON 失败", e);
                    runOnUiThread(() -> Toast.makeText(AboutActivity.this,
                            "解析更新信息失败", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    /**
     * Fallback: 直接通过 tag 获取 prerelease。
     * 当 /releases 列表 API 未返回 prerelease 时调用。
     * GitHub 的 /releases 列表端点在某些情况下（如 CI 使用 delete + recreate
     * 方式更新 prerelease）可能不返回 prerelease，但 /releases/tags/prerelease
     * 可以正常获取。
     */
    private void fetchPrereleaseByTag(String currentVersion) {
        Request request = new Request.Builder()
                .url(GITHUB_API_PRERELEASE_TAG)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Nova-Android")
                .header("Cache-Control", "no-cache")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(AboutActivity.this,
                        "暂无开发版可用",
                        Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    final int code = response.code();
                    runOnUiThread(() -> {
                        if (code == 404) {
                            Toast.makeText(AboutActivity.this,
                                    "暂无开发版可用",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(AboutActivity.this,
                                    "检查更新失败: HTTP " + code,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }

                try {
                    JSONObject prerelease = new JSONObject(response.body().string());

                    String remoteHash = extractDevCommitHash(extractVersion(prerelease));
                    String localHash = extractDevCommitHash(currentVersion);

                    // commit hash 相同 -> 已是最新
                    if (remoteHash != null && remoteHash.equals(localHash)) {
                        final String curVer = currentVersion;
                        runOnUiThread(() -> Toast.makeText(AboutActivity.this,
                                "当前已是最新开发版本 (v" + curVer + ")",
                                Toast.LENGTH_SHORT).show());
                        return;
                    }

                    // 有新开发版，展示更新对话框
                    showReleaseUpdate(prerelease, true);

                } catch (Exception e) {
                    Log.e(TAG, "解析 prerelease JSON 失败", e);
                    runOnUiThread(() -> Toast.makeText(AboutActivity.this,
                            "暂无开发版可用",
                            Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    /**
     * 稳定版 tag_name: "v3.0.0" -> "3.0.0"
     * 预发布 tag_name: "prerelease"（无法提取版本号，改用 name）
     * name: "Release v3.0.0" / "PreRelease v3.0-dev-abc1234" -> "3.0.0" / "3.0-dev-abc1234"
     */
    private String extractVersion(JSONObject release) {
        // 稳定版优先从 tag_name 提取
        String tagName = release.optString("tag_name", "");
        if (tagName.startsWith("v") && !tagName.equals("prerelease")) {
            return tagName.substring(1);
        }
        // 预发布从 name 中提取版本号
        String name = release.optString("name", "");
        Matcher m = Pattern.compile("v(\\d[\\d.]*[-\\w]*)").matcher(name);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    /**
     * 比较两个版本号字符串。
     * 支持 "3.0.0"、"3.1"、"3.1-dev" 等格式。
     * -dev 后缀视为同版本的预发布（低于正式版）。
     *
     * @return >0 表示 v1 > v2，0 表示相等，<0 表示 v1 < v2
     */
    private int compareVersions(String v1, String v2) {
        if (v1 == null || v1.isEmpty()) return -1;
        if (v2 == null || v2.isEmpty()) return 1;

        // 处理 -dev 后缀
        boolean isDev1 = v1.contains("-dev");
        boolean isDev2 = v2.contains("-dev");

        // 去掉 -dev 及之后的 commit hash，得到基础版本号
        String base1 = v1.replaceAll("-dev.*$", "").replaceAll("\\.$", "");
        String base2 = v2.replaceAll("-dev.*$", "").replaceAll("\\.$", "");

        String[] parts1 = base1.split("\\.");
        String[] parts2 = base2.split("\\.");

        int maxLen = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < maxLen; i++) {
            int p1 = 0, p2 = 0;
            try {
                p1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            } catch (NumberFormatException ignored) {}
            try {
                p2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            } catch (NumberFormatException ignored) {}
            if (p1 != p2) return p1 - p2;
        }

        // 基础版本号相同，-dev 版本低于正式版
        if (isDev1 && !isDev2) return -1;
        if (!isDev1 && isDev2) return 1;
        return 0;
    }

    /**
     * 从版本号中提取 dev 后的 commit hash。
     * 例如 "3.0-dev-abc1234" -> "abc1234"
     * 如果没有 commit hash（例如 "3.0-dev"），返回 null。
     */
    private String extractDevCommitHash(String version) {
        if (version == null) return null;
        int idx = version.indexOf("-dev-");
        if (idx >= 0) {
            return version.substring(idx + 5);
        }
        return null;
    }

    /**
     * 从 release JSON 对象中提取信息并展示更新对话框。
     */
    private void showReleaseUpdate(JSONObject release, boolean isPrerelease) {
        String releaseName = release.optString("name", "");
        String releaseBody = release.optString("body", "");
        String publishedAt = release.optString("published_at", "");

        // 查找 APK 下载链接
        String apkUrl = null;
        String apkName = null;
        long apkSize = 0;
        JSONArray assets = release.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                try {
                    JSONObject asset = assets.getJSONObject(i);
                    String name = asset.optString("name", "");
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", "");
                        apkName = name;
                        apkSize = asset.optLong("size", 0);
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }

        final String finalApkUrl = apkUrl;
        final String finalApkName = apkName;
        final long finalApkSize = apkSize;

        runOnUiThread(() -> showUpdateDialog(
                releaseName, releaseBody, publishedAt,
                isPrerelease, finalApkUrl, finalApkName, finalApkSize));
    }

    /**
     * 展示更新信息对话框，用户可选择下载安装或浏览器打开。
     * 更新内容使用 Markwon 渲染 Markdown 格式。
     */
    private void showUpdateDialog(String releaseName, String releaseBody,
                                  String publishedAt, boolean isPrerelease,
                                  String apkUrl, String apkName, long apkSize) {

        String currentVersion = getCurrentVersion();

        // 格式化发布时间
        String dateStr;
        try {
            SimpleDateFormat input = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
            input.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = input.parse(publishedAt);
            SimpleDateFormat output = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm", Locale.getDefault());
            dateStr = output.format(date);
        } catch (Exception e) {
            dateStr = publishedAt;
        }

        // 构建信息部分（纯文本）
        StringBuilder info = new StringBuilder();
        info.append("当前版本: v").append(currentVersion).append("\n");
        info.append("最新版本: ").append(releaseName).append("\n");
        if (isPrerelease) {
            info.append("类型: 开发版 (Prerelease)\n");
        }
        info.append("发布时间: ").append(dateStr).append("\n");
        if (apkSize > 0) {
            info.append("安装包: ").append(formatFileSize(apkSize));
        }

        // 加载自定义布局
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_update_info, null);
        TextView tvInfo = dialogView.findViewById(R.id.tv_update_info);
        TextView tvBody = dialogView.findViewById(R.id.tv_update_body);

        tvInfo.setText(info.toString());

        // 使用 Markwon 渲染 Markdown 更新内容
        String bodyContent = (releaseBody != null && !releaseBody.isEmpty())
                ? releaseBody
                : "*暂无更新说明*";
        markwon.setMarkdown(tvBody, bodyContent);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("发现新版本");
        builder.setView(dialogView);

        if (apkUrl != null) {
            builder.setPositiveButton("下载并安装", (dialog, which) ->
                    downloadAndInstallApk(apkUrl, apkName));
            builder.setNegativeButton("稍后再说", null);
        } else {
            builder.setPositiveButton("确定", null);
        }

        builder.setNeutralButton("浏览器打开", (dialog, which) ->
                openUrl("https://github.com/wsdjeg/Nova/releases"));

        builder.show();
    }

    // ── 下载 APK ──────────────────────────────────────────────────

    /**
     * 下载 APK 文件并自动触发安装界面。
     * 使用 OkHttp 异步下载，通过进度对话框显示下载进度。
     */
    private void downloadAndInstallApk(String apkUrl, String apkName) {
        // 清理旧的 APK 文件
        cleanOldApkFiles();

        // 创建下载进度对话框
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_download_progress, null);
        downloadProgressBar = dialogView.findViewById(R.id.progress_bar);
        downloadProgressText = dialogView.findViewById(R.id.progress_text);
        downloadProgressBar.setMax(100);
        downloadProgressBar.setProgress(0);
        downloadProgressText.setText("准备下载...");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("正在下载更新");
        builder.setView(dialogView);
        builder.setCancelable(false);
        builder.setNegativeButton("取消", (dialog, which) -> {
            if (downloadCall != null && !downloadCall.isCanceled()) {
                downloadCall.cancel();
            }
        });
        downloadDialog = builder.show();

        // 发起下载请求
        Request request = new Request.Builder().url(apkUrl).build();
        downloadCall = httpClient.newCall(request);

        downloadCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    if (downloadDialog != null && downloadDialog.isShowing()) {
                        downloadDialog.dismiss();
                    }
                    Toast.makeText(AboutActivity.this,
                            "下载失败: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    final int code = response.code();
                    runOnUiThread(() -> {
                        if (downloadDialog != null && downloadDialog.isShowing()) {
                            downloadDialog.dismiss();
                        }
                        Toast.makeText(AboutActivity.this,
                                "下载失败: HTTP " + code,
                                Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                // 保存到应用专属目录（无需存储权限）
                File downloadDir = getExternalFilesDir(null);
                if (downloadDir == null) {
                    downloadDir = getFilesDir();
                }
                String fileName = (apkName != null) ? apkName : "nova-update.apk";
                File apkFile = new File(downloadDir, fileName);

                InputStream inputStream = null;
                FileOutputStream outputStream = null;
                try {
                    inputStream = response.body().byteStream();
                    outputStream = new FileOutputStream(apkFile);

                    long contentLength = response.body().contentLength();
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalRead = 0;
                    int lastProgress = -1;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        // 检查是否已取消
                        if (call.isCanceled()) {
                            apkFile.delete();
                            return;
                        }
                        outputStream.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;

                        if (contentLength > 0) {
                            int progress = (int) (totalRead * 100 / contentLength);
                            if (progress != lastProgress) {
                                lastProgress = progress;
                                final int fp = progress;
                                final long ft = totalRead;
                                final long fc = contentLength;
                                runOnUiThread(() -> {
                                    downloadProgressBar.setProgress(fp);
                                    downloadProgressText.setText(
                                            fp + "%  (" + formatFileSize(ft)
                                                    + " / " + formatFileSize(fc) + ")");
                                });
                            }
                        }
                    }

                    outputStream.flush();

                    runOnUiThread(() -> {
                        if (downloadDialog != null && downloadDialog.isShowing()) {
                            downloadDialog.dismiss();
                        }
                        Toast.makeText(AboutActivity.this,
                                "下载完成，正在安装...",
                                Toast.LENGTH_SHORT).show();
                        installApk(apkFile);
                    });

                } catch (Exception e) {
                    Log.e(TAG, "下载文件失败", e);
                    // 清理不完整的文件
                    if (apkFile.exists()) {
                        apkFile.delete();
                    }
                    runOnUiThread(() -> {
                        if (downloadDialog != null && downloadDialog.isShowing()) {
                            downloadDialog.dismiss();
                        }
                        Toast.makeText(AboutActivity.this,
                                "下载失败: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                } finally {
                    if (inputStream != null) inputStream.close();
                    if (outputStream != null) outputStream.close();
                }
            }
        });
    }

    // ── 安装 APK ──────────────────────────────────────────────────

    /**
     * 通过 FileProvider 共享 APK 文件，触发系统安装界面。
     */
    private void installApk(File apkFile) {
        try {
            Uri apkUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", apkFile);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "启动安装失败", e);
            Toast.makeText(this,
                    "安装失败: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ── 工具方法 ──────────────────────────────────────────────────

    /**
     * 清理下载目录中的旧 APK 文件。
     */
    private void cleanOldApkFiles() {
        try {
            File downloadDir = getExternalFilesDir(null);
            if (downloadDir == null) return;
            File[] files = downloadDir.listFiles();
            if (files == null) return;
            for (File f : files) {
                if (f.getName().endsWith(".apk")) {
                    f.delete();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "清理旧 APK 失败", e);
        }
    }

    /**
     * 格式化文件大小。
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024)
            return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.getDefault(), "%.2f GB",
                bytes / (1024.0 * 1024 * 1024));
    }

    // ── 菜单 ──────────────────────────────────────────────────────

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

