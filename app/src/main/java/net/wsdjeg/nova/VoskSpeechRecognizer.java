package net.wsdjeg.nova;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.SpeechService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Vosk 离线语音识别器
 *
 * 功能：
 * - 加载 Vosk 模型（从 assets 或外部存储）
 * - 通过麦克风进行连续语音识别
 * - 通过回调返回部分识别结果和最终结果
 *
 * 模型存放路径：
 *   /sdcard/Android/data/net.wsdjeg.nova/files/vosk-model/
 *
 * 使用流程：
 *   1. new VoskSpeechRecognizer(context)
 *   2. setListener(listener)
 *   3. initModel() — 异步加载模型
 *   4. startListening() — 开始识别
 *   5. stopListening() — 停止识别
 *   6. destroy() — 释放资源
 */
public class VoskSpeechRecognizer {

    private static final String TAG = "VoskSpeechRecognizer";

    /** 模型目录名（放在应用外部存储下） */
    private static final String MODEL_DIR_NAME = "vosk-model";

    /** Vosk 中文小模型在 assets 中的名称 */
    private static final String ASSET_MODEL_NAME = "vosk-model-small-cn";

    // ========================================================================
    // 回调接口
    // ========================================================================

    /**
     * 语音识别结果回调接口
     */
    public interface RecognitionListener {

        /**
         * 模型加载完成，可以开始识别
         */
        void onModelReady();

        /**
         * 模型加载失败
         *
         * @param error 错误信息
         */
        void onModelError(String error);

        /**
         * 部分识别结果（实时更新，用户说话过程中持续回调）
         *
         * @param partialText 部分识别文本
         */
        void onPartialResult(String partialText);

        /**
         * 最终识别结果（一句话说完后的完整结果）
         *
         * @param finalText 最终识别文本
         */
        void onFinalResult(String finalText);

        /**
         * 识别超时（一段时间没有检测到语音）
         */
        void onTimeout();

        /**
         * 识别发生错误
         *
         * @param error 错误信息
         */
        void onError(String error);
    }


    // ========================================================================
    // 字段
    // ========================================================================

    private final Context context;
    private RecognitionListener listener;

    private Model model;
    private Recognizer recognizer;
    private SpeechService speechService;

    private boolean modelInitialized = false;
    private boolean isListening = false;

    /** 模型加载错误信息（供 UI 层查询） */
    private String modelError = null;

    /** 采样率，Vosk 默认 16000Hz */
    private static final float SAMPLE_RATE = 16000.0f;

    // ========================================================================
    // 构造函数
    // ========================================================================

    /**
     * 创建 VoskSpeechRecognizer 实例
     *
     * @param context 应用上下文
     */
    public VoskSpeechRecognizer(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 设置识别结果回调监听器
     *
     * @param listener 回调监听器
     */
    public void setListener(RecognitionListener listener) {
        this.listener = listener;
    }

    /**
     * 设置模型错误信息（由外部在 onModelError 回调中调用）
     */
    public void setModelError(String error) {
        this.modelError = error;
    }

    /**
     * 是否有模型错误
     */
    public boolean hasModelError() {
        return modelError != null;
    }

    /**
     * 获取模型错误信息
     */
    public String getModelError() {
        return modelError;
    }

    /**
     * 清除模型错误信息（显示给用户后清除）
     */
    public void clearModelError() {
        this.modelError = null;
    }


    // ========================================================================
    // 模型加载
    // ========================================================================

    /**
     * 初始化 Vosk 模型（异步执行）
     *
     * 首先检查外部存储是否已有模型，如果没有则从 assets 复制。
     * 模型加载在后台线程执行，完成后通过回调通知。
    public void initModel() {
        new Thread(() -> {
            try {
                String modelPath = getModelPath();

                // 如果外部存储没有模型，尝试从 assets 复制
                File modelDir = new File(modelPath);
                if (!modelDir.exists() || modelDir.list() == null || modelDir.list().length == 0) {
                    Log.i(TAG, "Model not found in external storage, copying from assets...");

                    // 诊断：列出 assets 根目录所有内容
                    try {
                        String[] rootAssets = context.getAssets().list("");
                        Log.i(TAG, "Assets root has " + (rootAssets != null ? rootAssets.length : 0) + " entries");
                        if (rootAssets != null) {
                            for (String name : rootAssets) {
                                String[] sub = context.getAssets().list(name);
                                int count = (sub != null) ? sub.length : -1;
                                Log.i(TAG, "  assets/" + name + (count >= 0 ? " (" + count + " items)" : " (file)"));
                            }
                        }
                    boolean copied = copyModelFromAssets(ASSET_MODEL_NAME, modelPath);
                    if (!copied) {
                        // 列出实际存在的目录名帮助诊断
                        StringBuilder found = new StringBuilder();
                        try {
                            String[] root = context.getAssets().list("");
                            if (root != null) {
                                for (String n : root) {
                                    String[] sub = context.getAssets().list(n);
                                    if (sub != null && sub.length > 0 && n.contains("vosk")) {
                                        found.append(n).append("(").append(sub.length).append(") ");
                                    }
                                }
                            }
                        } catch (IOException ignored) {}
                        
                        String detail = "语音模型未找到: assets/" + ASSET_MODEL_NAME;
                        if (found.length() > 0) {
                            detail += "\n实际找到: " + found.toString().trim();
                        } else {
                            detail += "\nassets 中没有 vosk 相关目录";
                        }
                        Log.w(TAG, detail);
                        modelError = detail;
                        if (listener != null) {
                            listener.onModelError(modelError);
                        }
                        return;
                    }
                        modelError = "语音模型未找到，请确保 assets/" + ASSET_MODEL_NAME + " 目录存在";
                        if (listener != null) {
                            listener.onModelError(modelError);
                        }
                        return;
                    }
                recognizer = new Recognizer(model, SAMPLE_RATE);
                modelInitialized = true;
                modelError = null;
                Log.i(TAG, "Vosk model loaded successfully");

                if (listener != null) {
                    listener.onModelReady();
                }

            } catch (IOException e) {
                Log.e(TAG, "Failed to load Vosk model", e);
                modelError = "模型加载失败: " + e.getMessage();
                if (listener != null) {
                    listener.onModelError(modelError);
                }
            }
        }).start();
    }

    /**
     * 获取模型存储路径
     * 优先使用应用专属外部存储目录
     *
     * @return 模型目录的绝对路径
     */
    private String getModelPath() {
        File externalDir = context.getExternalFilesDir(null);
        if (externalDir != null) {
            return new File(externalDir, MODEL_DIR_NAME).getAbsolutePath();
        }
        // 降级到内部存储
        return new File(context.getFilesDir(), MODEL_DIR_NAME).getAbsolutePath();
    }

    /**
     * 从 assets 复制模型到外部存储
     *
     * @param assetName  assets 中的模型目录名
     * @param targetPath 目标路径
     * @return 是否复制成功
     */
    private boolean copyModelFromAssets(String assetName, String targetPath) {
        try {
            String[] files = context.getAssets().list(assetName);
            if (files == null || files.length == 0) {
                Log.w(TAG, "No model files found in assets: " + assetName);
                return false;
            }

            File targetDir = new File(targetPath);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            copyAssetDir(assetName, targetDir);
            Log.i(TAG, "Model copied from assets to: " + targetPath);
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Failed to copy model from assets", e);
            return false;
        }
    }

    /**
     * 递归复制 assets 目录
     */
    private void copyAssetDir(String assetPath, File targetDir) throws IOException {
        String[] children = context.getAssets().list(assetPath);
        if (children == null) {
            // 是文件
            copyAssetFile(assetPath, targetDir);
            return;
        }

        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        if (children.length == 0) {
            // 空目录或文件
            copyAssetFile(assetPath, targetDir);
            return;
        }

        for (String child : children) {
            String childAssetPath = assetPath + "/" + child;
            File childTarget = new File(targetDir, child);
            String[] subChildren = context.getAssets().list(childAssetPath);

            if (subChildren != null && subChildren.length > 0) {
                // 子目录
                copyAssetDir(childAssetPath, childTarget);
            } else {
                // 文件
                copyAssetFile(childAssetPath, targetDir);
            }
        }
    }

    /**
     * 复制单个 asset 文件到目标目录
     */
    private void copyAssetFile(String assetPath, File targetDir) throws IOException {
        String fileName = assetPath.substring(assetPath.lastIndexOf('/') + 1);
        File targetFile = new File(targetDir, fileName);

        InputStream is = context.getAssets().open(assetPath);
        FileOutputStream fos = new FileOutputStream(targetFile);

        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            fos.write(buffer, 0, bytesRead);
        }

        fos.flush();
        fos.close();
        is.close();
    }


    // ========================================================================
    // 识别控制
    // ========================================================================

    /**
     * 开始语音识别
     *
     * 需要在 initModel() 成功（onModelReady 回调）后调用。
     * 启动后会通过麦克风采集音频进行连续识别。
     */
    public void startListening() {
        if (!modelInitialized || recognizer == null) {
            if (listener != null) {
                listener.onError("模型未初始化，请先调用 initModel()");
            }
            return;
        }

        if (isListening) {
            Log.w(TAG, "Already listening");
            return;
        }

        try {
            // 创建 SpeechService，使用麦克风采集
            speechService = new SpeechService(recognizer, SAMPLE_RATE);

            // 开始识别，结果通过回调返回
            speechService.startListening(new org.vosk.android.RecognitionListener() {
                @Override
                public void onPartialResult(String s) {
                    try {
                        JSONObject json = new JSONObject(s);
                        String text = json.optString("partial", "");
                        if (!text.isEmpty() && listener != null) {
                            listener.onPartialResult(text);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing partial result", e);
                    }
                }

                @Override
                public void onFinalResult(String s) {
                    try {
                        JSONObject json = new JSONObject(s);
                        String text = json.optString("text", "");
                        if (!text.isEmpty() && listener != null) {
                            listener.onFinalResult(text);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing final result", e);
                    }
                }

                @Override
                public void onResult(String s) {
                    try {
                        JSONObject json = new JSONObject(s);
                        String text = json.optString("text", "");
                        if (!text.isEmpty() && listener != null) {
                            listener.onFinalResult(text);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing result", e);
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Recognition error", e);
                    if (listener != null) {
                        listener.onError("识别错误: " + e.getMessage());
                    }
                }

                @Override
                public void onTimeout() {
                    Log.i(TAG, "Recognition timeout");
                    isListening = false;
                    if (listener != null) {
                        listener.onTimeout();
                    }
                }
            });

            isListening = true;
            Log.i(TAG, "Speech recognition started");

        } catch (IOException e) {
            Log.e(TAG, "Failed to start listening", e);
            if (listener != null) {
                listener.onError("启动识别失败: " + e.getMessage());
            }
        }
    }

    /**
     * 停止语音识别
     */
    public void stopListening() {
        if (speechService != null) {
            speechService.stop();
            speechService = null;
        }
        isListening = false;
        Log.i(TAG, "Speech recognition stopped");
    }


    // ========================================================================
    // 状态查询
    // ========================================================================

    /**
     * 模型是否已加载完成
     *
     * @return true 如果模型已加载
     */
    public boolean isModelReady() {
        return modelInitialized;
    }

    /**
     * 是否正在识别中
     *
     * @return true 如果正在监听
     */
    public boolean isListening() {
        return isListening;
    }

    // ========================================================================
    // 资源释放
    // ========================================================================

    /**
     * 释放所有资源
     *
     * 停止识别并释放模型、识别器等资源。
     * 调用后不应再使用此实例。
     */
    public void destroy() {
        stopListening();

        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }

        if (model != null) {
            model.close();
            model = null;
        }

        modelInitialized = false;
        Log.i(TAG, "VoskSpeechRecognizer destroyed");
    }
}

