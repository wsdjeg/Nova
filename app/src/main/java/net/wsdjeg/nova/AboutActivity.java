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
    // GitHub 仓库
    private static final String GITHUB_URL = "https://github.com/wsdjeg/Nova";
    // chat.nvim 官网
    private static final String CHAT_NVIM_URL = "https://nvim.chat";

    private OkHttpClient httpClient;
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
     * 从 GitHub API 获取最新 release 信息，展示更新对话框。
     * releases 接口按时间倒序返回，第一个即最新版本（含 prerelease）。
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

                    // 第一个就是最新的 release
                    JSONObject latest = releases.getJSONObject(0);
                    String releaseName = latest.optString("name", "");
                    String releaseBody = latest.optString("body", "");
                    String publishedAt = latest.optString("published_at", "");
                    boolean isPrerelease = latest.optBoolean("prerelease", false);

                    // 查找 APK 下载链接
                    String apkUrl = null;
                    String apkName = null;
                    long apkSize = 0;
                    JSONArray assets = latest.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String name = asset.optString("name", "");
                            if (name.endsWith(".apk")) {
                                apkUrl = asset.optString("browser_download_url", "");
                                apkName = name;
                                apkSize = asset.optLong("size", 0);
                                break;
                            }
                        }
                    }

                    final String finalApkUrl = apkUrl;
                    final String finalApkName = apkName;
                    final long finalApkSize = apkSize;

                    runOnUiThread(() -> showUpdateDialog(
                            releaseName, releaseBody, publishedAt,
                            isPrerelease, finalApkUrl, finalApkName, finalApkSize));

                } catch (Exception e) {
                    Log.e(TAG, "解析 release JSON 失败", e);
                    runOnUiThread(() -> Toast.makeText(AboutActivity.this,
                            "解析更新信息失败", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    /**
     * 展示更新信息对话框，用户可选择下载安装或浏览器打开。
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

        // 构建消息
        StringBuilder msg = new StringBuilder();
        msg.append("当前版本: v").append(currentVersion).append("\n");
        msg.append("最新版本: ").append(releaseName).append("\n");
        if (isPrerelease) {
            msg.append("类型: 开发版 (Prerelease)\n");
        }
        msg.append("发布时间: ").append(dateStr).append("\n");
        if (apkSize > 0) {
            msg.append("安装包: ").append(formatFileSize(apkSize)).append("\n");
        }
        msg.append("\n更新内容:\n").append(releaseBody);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("发现新版本");
        builder.setMessage(msg.toString());

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

