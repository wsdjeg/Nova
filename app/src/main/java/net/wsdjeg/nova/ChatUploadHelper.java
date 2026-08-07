package net.wsdjeg.nova;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 图片上传辅助类
 * 从 ChatActivity 提取，负责图片选择、批量上传和进度显示
 */
public class ChatUploadHelper {
    private static final String TAG = "ChatUploadHelper";

    private final AppCompatActivity activity;
    private final ApiClient apiClient;
    private final SettingsManager settingsManager;
    private String sessionId;

    // 上传进度跟踪
    private AlertDialog uploadDialog;
    private ProgressBar uploadProgressBar;
    private TextView uploadProgressText;
    private final AtomicInteger uploadCompleted = new AtomicInteger(0);
    private final AtomicInteger uploadSuccess = new AtomicInteger(0);
    private final AtomicInteger uploadFailed = new AtomicInteger(0);
    private int uploadTotal = 0;

    public ChatUploadHelper(AppCompatActivity activity, ApiClient apiClient,
                            SettingsManager settingsManager) {
        this.activity = activity;
        this.apiClient = apiClient;
        this.settingsManager = settingsManager;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 打开系统图片选择器（支持多选）
     */
    public void pickImage(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            activity.startActivityForResult(
                    Intent.createChooser(intent, activity.getString(R.string.menu_upload_image)),
                    requestCode);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "No image picker app found", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 处理图片选择结果（在 Activity.onActivityResult 中调用）
     */
    public void handleImageResult(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) return;

        android.content.ClipData clipData = data.getClipData();
        if (clipData != null && clipData.getItemCount() > 0) {
            List<Uri> uris = new ArrayList<>();
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri != null) {
                    uris.add(uri);
                }
            }
            if (uris.size() == 1) {
                uploadImage(uris.get(0));
            } else if (uris.size() > 1) {
                uploadMultipleImages(uris);
            }
        } else {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                uploadImage(imageUri);
            }
        }
    }

    /**
     * 从 Uri 读取图片数据并上传到会话的工作目录
     */
    private void uploadImage(Uri imageUri) {
        if (sessionId == null || sessionId.isEmpty()) {
            Toast.makeText(activity, activity.getString(R.string.upload_no_session),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = getFileNameFromUri(imageUri);
        if (fileName == null || fileName.isEmpty()) {
            fileName = "image_" + System.currentTimeMillis() + ".png";
        }

        String mime = activity.getContentResolver().getType(imageUri);
        String mimeType = (mime == null || mime.isEmpty()) ? "image/png" : mime;

        String uploadDir = settingsManager.getDefaultUploadPath(sessionId);
        if (uploadDir == null || uploadDir.isEmpty()) {
            uploadDir = "images/";
        }
        if (!uploadDir.endsWith("/")) {
            uploadDir = uploadDir + "/";
        }

        String relativePath = uploadDir + fileName;

        List<Uri> uris = new ArrayList<>();
        uris.add(imageUri);
        List<String> paths = new ArrayList<>();
        paths.add(relativePath);
        List<String> mimes = new ArrayList<>();
        mimes.add(mimeType);
        startUploadBatch(uris, paths, mimes);
    }

    /**
     * 多张图片批量上传
     */
    private void uploadMultipleImages(List<Uri> imageUris) {
        if (sessionId == null || sessionId.isEmpty()) {
            Toast.makeText(activity, activity.getString(R.string.upload_no_session),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String uploadDir = settingsManager.getDefaultUploadPath(sessionId);
        if (uploadDir == null || uploadDir.isEmpty()) {
            uploadDir = "images/";
        }
        if (!uploadDir.endsWith("/")) {
            uploadDir = uploadDir + "/";
        }

        List<Uri> uris = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        List<String> mimes = new ArrayList<>();
        for (Uri uri : imageUris) {
            String fileName = getFileNameFromUri(uri);
            if (fileName == null || fileName.isEmpty()) {
                fileName = "image_" + System.currentTimeMillis() + ".png";
            }
            String mime = activity.getContentResolver().getType(uri);
            String mimeType = (mime == null || mime.isEmpty()) ? "image/png" : mime;
            uris.add(uri);
            paths.add(uploadDir + fileName);
            mimes.add(mimeType);
        }
        startUploadBatch(uris, paths, mimes);
    }

    /**
     * 启动批量上传：显示进度对话框，逐个上传图片
     */
    private void startUploadBatch(List<Uri> uris, List<String> paths, List<String> mimes) {
        uploadTotal = uris.size();
        uploadCompleted.set(0);
        uploadSuccess.set(0);
        uploadFailed.set(0);

        showUploadDialog();

        for (int i = 0; i < uris.size(); i++) {
            performUpload(uris.get(i), paths.get(i), mimes.get(i));
        }
    }

    /**
     * 显示上传进度对话框（带进度条）
     */
    private void showUploadDialog() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (activity.getResources().getDisplayMetrics().density * 20);
        layout.setPadding(pad, pad / 2, pad, pad / 2);

        uploadProgressText = new TextView(activity);
        uploadProgressText.setGravity(Gravity.CENTER);
        uploadProgressText.setText(activity.getString(R.string.upload_progress_format, 0, uploadTotal));
        uploadProgressText.setTextSize(14);

        uploadProgressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        uploadProgressBar.setMax(uploadTotal);
        uploadProgressBar.setProgress(0);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pbParams.topMargin = pad / 2;

        layout.addView(uploadProgressText);
        layout.addView(uploadProgressBar, pbParams);

        uploadDialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.upload_progress_title)
                .setView(layout)
                .setCancelable(false)
                .create();
        uploadDialog.show();
    }

    /**
     * 单张图片上传完成回调：更新进度，全部完成时显示汇总
     */
    private void onSingleUploadComplete(boolean success) {
        int completed = uploadCompleted.incrementAndGet();
        if (success) {
            uploadSuccess.incrementAndGet();
        } else {
            uploadFailed.incrementAndGet();
        }

        activity.runOnUiThread(() -> {
            if (uploadProgressBar != null) {
                uploadProgressBar.setProgress(completed);
            }
            if (uploadProgressText != null) {
                uploadProgressText.setText(
                        activity.getString(R.string.upload_progress_format, completed, uploadTotal));
            }

            if (completed >= uploadTotal) {
                if (uploadDialog != null && uploadDialog.isShowing()) {
                    uploadDialog.dismiss();
                }
                int sc = uploadSuccess.get();
                int fc = uploadFailed.get();
                if (fc == 0) {
                    Toast.makeText(activity,
                            activity.getString(R.string.upload_batch_success, sc),
                            Toast.LENGTH_LONG).show();
                } else if (sc > 0) {
                    Toast.makeText(activity,
                            activity.getString(R.string.upload_batch_partial, sc, fc),
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(activity,
                            activity.getString(R.string.upload_batch_all_failed),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /**
     * 执行实际上传操作：读取图片数据并调用 API
     */
    private void performUpload(Uri imageUri, String relativePath, String mimeType) {
        final String finalRelativePath = relativePath;

        new Thread(() -> {
            try {
                InputStream is = activity.getContentResolver().openInputStream(imageUri);
                if (is == null) {
                    onSingleUploadComplete(false);
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

                apiClient.uploadFile(sessionId, fileData, finalRelativePath, mimeType,
                        new ApiClient.UploadCallback() {
                            @Override
                            public void onSuccess(String path, String fullPath, long size) {
                                Log.i(TAG, "Image uploaded: " + fullPath + " (" + size + " bytes)");
                                onSingleUploadComplete(true);
                            }

                            @Override
                            public void onError(String error) {
                                Log.e(TAG, "Image upload failed: " + error);
                                onSingleUploadComplete(false);
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Failed to read image from Uri", e);
                onSingleUploadComplete(false);
            }
        }).start();
    }

    /**
     * 从 Uri 查询文件名
     */
    private String getFileNameFromUri(Uri uri) {
        String fileName = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = activity.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to get filename from Uri", e);
            }
        }
        if (fileName == null) {
            fileName = uri.getLastPathSegment();
        }
        return fileName;
    }
}

