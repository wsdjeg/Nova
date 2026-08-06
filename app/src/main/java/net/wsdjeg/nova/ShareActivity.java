package net.wsdjeg.nova;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 分享接收 Activity
 *
 * 接收来自其他 App 的 ACTION_SEND / ACTION_SEND_MULTIPLE。
 *
 * 文本分享：选择会话后直接跳转 ChatActivity，文本预填到输入框。
 * 图片/文件分享：弹出确认对话框（文件列表 + 会话选择），用户确认后才上传，
 *               上传完成后跳转 ChatActivity。
 */
public class ShareActivity extends AppCompatActivity {

    private static final String TAG = "ShareActivity";

    public static final String EXTRA_SHARED_TEXT = "shared_text";

    private SessionManager sessionManager;
    private SettingsManager settingsManager;
    private ApiClient apiClient;

    private List<Session> sessionList = new ArrayList<>();

    // 分享的数据
    private String sharedText = null;
    private final List<Uri> fileUris = new ArrayList<>();
    private final List<SharedFileInfo> fileInfos = new ArrayList<>();

    // 上传进度状态
    private int uploadCompleted = 0;
    private int uploadSuccess = 0;
    private int uploadFailed = 0;
    private int uploadTotal = 0;
    private AlertDialog progressDialogRef = null;
    private String pendingSessionId = null;
    private String pendingSessionTitle = null;
    private TextView progressTextView = null;
    private ProgressBar progressProgressBar = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);
        settingsManager = new SettingsManager(this);
        apiClient = new ApiClient(this);

        // 解析 Intent
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        Log.d(TAG, "onCreate: action=" + action + " type=" + type);

        if (Intent.ACTION_SEND.equals(action)) {
            if (type != null && type.startsWith("text/")) {
                sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText == null || sharedText.isEmpty()) {
                    sharedText = intent.getStringExtra(Intent.EXTRA_TITLE);
                }
                handleTextShare();
            } else {
                Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (uri != null) {
                    fileUris.add(uri);
                    fileInfos.add(queryFileInfo(uri));
                }
                handleFileShare();
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (uris != null) {
                for (Uri uri : uris) {
                    if (uri != null) {
                        fileUris.add(uri);
                        fileInfos.add(queryFileInfo(uri));
                    }
                }
            }
            handleFileShare();
        } else {
            finish();
        }
    }

    // ==================== 文本分享 ====================

    private void handleTextShare() {
        if (sharedText == null || sharedText.isEmpty()) {
            Toast.makeText(this, getString(R.string.share_empty_text), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        sessionList = sessionManager.loadAllSessions();
        if (sessionList.isEmpty()) {
            Toast.makeText(this, getString(R.string.share_no_sessions), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        showSessionPicker(session -> {
            // 追加到已有草稿，避免覆盖用户未发送的内容
            String existingDraft = sessionManager.getDraft(session.getSessionId());
            String newDraft;
            if (existingDraft != null && !existingDraft.isEmpty()) {
                newDraft = existingDraft + "\n" + sharedText;
            } else {
                newDraft = sharedText;
            }
            sessionManager.saveDraft(session.getSessionId(), newDraft);

            Intent chatIntent = new Intent(ShareActivity.this, ChatActivity.class);
            chatIntent.putExtra(ChatActivity.EXTRA_SESSION_ID, session.getSessionId());
            chatIntent.putExtra(ChatActivity.EXTRA_SESSION_TITLE, session.getTitle());
            chatIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(chatIntent);
            finish();
        });
    }

    // ==================== 图片/文件分享 ====================

    private void handleFileShare() {
        if (fileUris.isEmpty()) {
            Toast.makeText(this, getString(R.string.share_no_files), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        sessionList = sessionManager.loadAllSessions();
        if (sessionList.isEmpty()) {
            Toast.makeText(this, getString(R.string.share_no_sessions), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        showFileShareConfirmDialog();
    }

    /**
     * 显示确认对话框：文件列表 + 会话选择 + 上传按钮
     */
    private void showFileShareConfirmDialog() {
        int dp8 = dp(8);
        int dp16 = dp(16);
        int dp20 = dp(20);

        // ---- 根布局 ----
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp20, dp16, dp20, dp8);

        // ---- 文件列表标题 ----
        TextView tvFileTitle = new TextView(this);
        tvFileTitle.setText(getString(R.string.share_files_to_upload, fileInfos.size()));
        tvFileTitle.setTextSize(14);
        tvFileTitle.setPadding(0, 0, 0, dp8);
        root.addView(tvFileTitle);

        // ---- 文件列表 RecyclerView ----
        RecyclerView rvFiles = new RecyclerView(this);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        rvFiles.setLayoutManager(llm);
        FileListAdapter adapter = new FileListAdapter(fileInfos);
        rvFiles.setAdapter(adapter);

        // 计算列表高度：每项 48dp + 16dp padding，最大 240dp
        int itemHeight = dp(48);
        int listHeight = Math.min(dp(240), itemHeight * fileInfos.size() + dp(16));
        LinearLayout.LayoutParams rvParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, listHeight);
        rvFiles.setLayoutParams(rvParams);
        root.addView(rvFiles);

        // ---- 会话选择标题 ----
        TextView tvSessionTitle = new TextView(this);
        tvSessionTitle.setText(getString(R.string.share_select_session));
        tvSessionTitle.setTextSize(14);
        tvSessionTitle.setPadding(0, dp16, 0, dp8);
        root.addView(tvSessionTitle);

        // ---- 会话选择 Spinner ----
        Spinner spinner = new Spinner(this);
        List<String> sessionLabels = new ArrayList<>();
        for (Session s : sessionList) {
            String label = s.getTitle();
            if (label == null || label.isEmpty()) {
                label = s.getSessionId();
            }
            sessionLabels.add(label);
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, sessionLabels);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);
        root.addView(spinner);

        // ---- 对话框 ----
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.share_title)
                .setView(root)
                .setPositiveButton(R.string.share_upload, null) // override 防止自动关闭
                .setNegativeButton(R.string.cancel, (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .create();

        dialog.setOnShowListener(d -> {
            Button btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnPositive.setOnClickListener(v -> {
                int selected = spinner.getSelectedItemPosition();
                if (selected < 0 || selected >= sessionList.size()) {
                    return;
                }
                Session selectedSession = sessionList.get(selected);
                dialog.dismiss();
                doUpload(selectedSession);
            });
        });

        dialog.show();
    }

    // ==================== 上传逻辑 ====================

    private void doUpload(Session session) {
        final String sessionId = session.getSessionId();
        final String sessionTitle = session.getTitle();

        // 获取上传路径
        String uploadDir = settingsManager.getDefaultUploadPath(sessionId);
        if (uploadDir == null || uploadDir.isEmpty()) {
            uploadDir = "images/";
        }
        if (!uploadDir.endsWith("/")) {
            uploadDir = uploadDir + "/";
        }

        uploadTotal = fileUris.size();
        uploadCompleted = 0;
        uploadSuccess = 0;
        uploadFailed = 0;
        pendingSessionId = sessionId;
        pendingSessionTitle = sessionTitle;

        // 构建上传列表
        final List<String> paths = new ArrayList<>();
        final List<String> mimes = new ArrayList<>();
        for (SharedFileInfo info : fileInfos) {
            String fileName = info.name;
            if (fileName == null || fileName.isEmpty()) {
                fileName = "file_" + System.currentTimeMillis();
            }
            String mimeType = info.mimeType;
            if (mimeType == null || mimeType.isEmpty()) {
                mimeType = "application/octet-stream";
            }
            paths.add(uploadDir + fileName);
            mimes.add(mimeType);
        }

        // ---- 进度对话框 ----
        int dp8 = dp(8);
        int dp20 = dp(20);

        LinearLayout progressLayout = new LinearLayout(this);
        progressLayout.setOrientation(LinearLayout.VERTICAL);
        progressLayout.setPadding(dp20, dp8, dp20, dp8);

        progressTextView = new TextView(this);
        progressTextView.setGravity(Gravity.CENTER);
        progressTextView.setText(getString(R.string.upload_progress_format, 0, uploadTotal));
        progressTextView.setTextSize(14);

        progressProgressBar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progressProgressBar.setMax(uploadTotal);
        progressProgressBar.setProgress(0);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pbParams.topMargin = dp8;
        progressLayout.addView(progressTextView);
        progressLayout.addView(progressProgressBar, pbParams);

        progressDialogRef = new AlertDialog.Builder(this)
                .setTitle(R.string.share_uploading)
                .setView(progressLayout)
                .setCancelable(false)
                .create();
        progressDialogRef.show();

        // ---- 逐个上传 ----
        Handler mainHandler = new Handler(Looper.getMainLooper());

        for (int i = 0; i < fileUris.size(); i++) {
            final Uri uri = fileUris.get(i);
            final String relativePath = paths.get(i);
            final String mimeType = mimes.get(i);

            new Thread(() -> {
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    if (is == null) {
                        mainHandler.post(() -> {
                            uploadFailed++;
                            onSingleUploadDone();
                        });
                        return;
                    }
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }
                    is.close();
                    byte[] fileData = baos.toByteArray();

                    apiClient.uploadFile(sessionId, fileData, relativePath, mimeType,
                        new ApiClient.UploadCallback() {
                            @Override
                            public void onSuccess(String path, String fullPath, long size) {
                                Log.i(TAG, "Upload success: " + fullPath);
                                mainHandler.post(() -> {
                                    uploadSuccess++;
                                    onSingleUploadDone();
                                });
                            }

                            @Override
                            public void onError(String error) {
                                Log.e(TAG, "Upload failed: " + error);
                                mainHandler.post(() -> {
                                    uploadFailed++;
                                    onSingleUploadDone();
                                });
                            }
                        });
                } catch (Exception e) {
                    Log.e(TAG, "Failed to read file", e);
                    mainHandler.post(() -> {
                        uploadFailed++;
                        onSingleUploadDone();
                    });
                }
            }).start();
        }
    }

    private void onSingleUploadDone() {
        uploadCompleted++;
        if (progressProgressBar != null) {
            progressProgressBar.setProgress(uploadCompleted);
        }
        if (progressTextView != null) {
            progressTextView.setText(getString(R.string.upload_progress_format,
                    uploadCompleted, uploadTotal));
        }

        if (uploadCompleted >= uploadTotal) {
            // 全部完成
            if (progressDialogRef != null && progressDialogRef.isShowing()) {
                progressDialogRef.dismiss();
            }
            // 显示结果
            if (uploadFailed == 0) {
                Toast.makeText(this, getString(R.string.upload_batch_success, uploadSuccess),
                        Toast.LENGTH_LONG).show();
            } else if (uploadSuccess > 0) {
                Toast.makeText(this, getString(R.string.upload_batch_partial, uploadSuccess, uploadFailed),
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, getString(R.string.upload_batch_all_failed),
                        Toast.LENGTH_LONG).show();
            }

            // 跳转 ChatActivity
            Intent chatIntent = new Intent(this, ChatActivity.class);
            chatIntent.putExtra(ChatActivity.EXTRA_SESSION_ID, pendingSessionId);
            chatIntent.putExtra(ChatActivity.EXTRA_SESSION_TITLE, pendingSessionTitle);
            chatIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(chatIntent);
            finish();
        }
    }

    // ==================== 会话选择对话框（文本分享用） ====================

    private void showSessionPicker(SessionPickerCallback callback) {
        List<String> labels = new ArrayList<>();
        for (Session s : sessionList) {
            String label = s.getTitle();
            if (label == null || label.isEmpty()) {
                label = s.getSessionId();
            }
            labels.add(label);
        }

        final int[] selectedIndex = {0};
        String[] labelArray = labels.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle(R.string.share_select_session)
                .setSingleChoiceItems(labelArray, 0, (d, which) -> selectedIndex[0] = which)
                .setPositiveButton(R.string.ok, (d, which) -> {
                    int idx = selectedIndex[0];
                    if (idx >= 0 && idx < sessionList.size()) {
                        callback.onSessionSelected(sessionList.get(idx));
                    }
                })
                .setNegativeButton(R.string.cancel, (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    // ==================== 工具方法 ====================

    private int dp(int value) {
        return (int) (getResources().getDisplayMetrics().density * value);
    }

    /**
     * 查询文件信息：文件名、大小、MIME 类型
     */
    private SharedFileInfo queryFileInfo(Uri uri) {
        SharedFileInfo info = new SharedFileInfo();
        info.uri = uri;
        info.mimeType = getContentResolver().getType(uri);

        String name = null;
        long size = -1;

        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        name = cursor.getString(nameIndex);
                    }
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (sizeIndex >= 0) {
                        size = cursor.getLong(sizeIndex);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to query file info", e);
            }
        }

        if (name == null || name.isEmpty()) {
            name = uri.getLastPathSegment();
        }

        info.name = name;
        info.size = size;
        return info;
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size < 0) return "?";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }

    // ==================== 回调接口 ====================

    interface SessionPickerCallback {
        void onSessionSelected(Session session);
    }

    // ==================== 数据类 ====================

    private static class SharedFileInfo {
        Uri uri;
        String name;
        long size;
        String mimeType;
    }

    // ==================== 文件列表 Adapter ====================

    private class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.FileViewHolder> {

        private final List<SharedFileInfo> items;

        FileListAdapter(List<SharedFileInfo> items) {
            this.items = items;
        }

        @Override
        public FileViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            int dp8 = dp(8);
            int dp16 = dp(16);

            LinearLayout layout = new LinearLayout(parent.getContext());
            layout.setOrientation(LinearLayout.HORIZONTAL);
            layout.setPadding(dp16, dp8, dp16, dp8);
            layout.setGravity(Gravity.CENTER_VERTICAL);

            // 图标
            ImageView icon = new ImageView(parent.getContext());
            int iconSize = dp(32);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.rightMargin = dp8;
            icon.setId(android.R.id.icon);
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            layout.addView(icon, iconParams);

            // 文本容器
            LinearLayout textContainer = new LinearLayout(parent.getContext());
            textContainer.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT);
            textParams.weight = 1;
            layout.addView(textContainer, textParams);

            TextView tvName = new TextView(parent.getContext());
            tvName.setTextSize(14);
            tvName.setMaxLines(1);
            tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvName.setId(android.R.id.text1);

            TextView tvInfo = new TextView(parent.getContext());
            tvInfo.setTextSize(12);
            tvInfo.setTextColor(0xFF888888);
            tvInfo.setId(android.R.id.text2);

            textContainer.addView(tvName);
            textContainer.addView(tvInfo);

            return new FileViewHolder(layout);
        }

        @Override
        public void onBindViewHolder(FileViewHolder holder, int position) {
            SharedFileInfo info = items.get(position);

            String name = info.name != null ? info.name : "unknown";
            String sizeStr = formatFileSize(info.size);
            String mime = info.mimeType != null ? info.mimeType : "*/*";

            holder.tvName.setText(name);
            holder.tvInfo.setText(sizeStr + "  ·  " + mime);

            // 简单图标判断
            if (mime != null && mime.startsWith("image/")) {
                holder.icon.setImageResource(android.R.drawable.ic_menu_gallery);
            } else {
                holder.icon.setImageResource(android.R.drawable.ic_menu_edit);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class FileViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            TextView tvInfo;
            ImageView icon;

            FileViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(android.R.id.text1);
                tvInfo = itemView.findViewById(android.R.id.text2);
                icon = itemView.findViewById(android.R.id.icon);
            }
        }
    }
}

