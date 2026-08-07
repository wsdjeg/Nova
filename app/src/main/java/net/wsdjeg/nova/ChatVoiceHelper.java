package net.wsdjeg.nova;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.Locale;

/**
 * 语音识别辅助类
 * 从 ChatActivity 提取，负责 Vosk 离线识别和系统语音识别的切换管理
 */
public class ChatVoiceHelper {
    private static final String TAG = "ChatVoiceHelper";

    public interface VoiceCallback {
        EditText getEditText();
        ImageButton getSendButton();
        boolean isInProgress();
        void onButtonStateChanged(int state);
        void onUpdateButtonAppearance();
    }

    private final AppCompatActivity activity;
    private final VoiceCallback callback;

    private VoskSpeechRecognizer voskRecognizer;
    private boolean isVoskListening = false;
    private String voskBaseText = "";
    private android.animation.ObjectAnimator pulseAnimator;

    public ChatVoiceHelper(AppCompatActivity activity, VoiceCallback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    public boolean isListening() {
        return isVoskListening;
    }

    /**
     * 初始化 Vosk 离线语音识别
     */
    public void init() {
        try {
            voskRecognizer = new VoskSpeechRecognizer(activity);
            voskRecognizer.setListener(new VoskSpeechRecognizer.RecognitionListener() {
                @Override
                public void onModelReady() {
                    Log.i(TAG, "Vosk model ready");
                }

                @Override
                public void onModelError(String error) {
                    Log.w(TAG, "Vosk model error: " + error);
                    if (voskRecognizer != null) {
                        voskRecognizer.setModelError(error);
                    }
                }

                @Override
                public void onFinalResult(String text) {
                    if (text != null && !text.trim().isEmpty()) {
                        activity.runOnUiThread(() -> {
                            voskBaseText += text.trim();
                            callback.getEditText().setText(voskBaseText);
                            callback.getEditText().setSelection(callback.getEditText().length());
                        });
                    }
                }

                @Override
                public void onPartialResult(String text) {
                    if (text != null && !text.trim().isEmpty()) {
                        activity.runOnUiThread(() -> {
                            callback.getEditText().setText(voskBaseText + text.trim());
                            callback.getEditText().setSelection(callback.getEditText().length());
                        });
                    }
                }

                @Override
                public void onError(String error) {
                    activity.runOnUiThread(() -> {
                        isVoskListening = false;
                        voskBaseText = "";
                        callback.onButtonStateChanged(ChatActivity.STATE_NORMAL);
                        callback.onUpdateButtonAppearance();
                        stopListeningPulse();
                        Toast.makeText(activity,
                                activity.getString(R.string.voice_recognize_failed, error),
                                Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onTimeout() {
                    activity.runOnUiThread(() -> {
                        isVoskListening = false;
                        voskBaseText = "";
                        callback.onButtonStateChanged(ChatActivity.STATE_NORMAL);
                        callback.onUpdateButtonAppearance();
                        stopListeningPulse();
                        String currentText = callback.getEditText().getText().toString().trim();
                        if (!currentText.isEmpty()) {
                            Toast.makeText(activity,
                                    activity.getString(R.string.voice_recognize_ended),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
            voskRecognizer.initModel();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create VoskSpeechRecognizer", e);
            voskRecognizer = null;
        }
    }

    /**
     * 优先使用 Vosk 离线识别，不可用时回退到 Android 系统语音识别
     */
    public void startVoiceInput(int requestCodeVoiceInput, int requestCodePermission) {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    requestCodePermission);
            return;
        }

        if (voskRecognizer != null && voskRecognizer.isModelReady()) {
            startVoskListening();
            return;
        }

        if (voskRecognizer != null && !voskRecognizer.isModelReady()) {
            if (voskRecognizer.hasModelError()) {
                String errMsg = voskRecognizer.getModelError();
                Toast.makeText(activity, errMsg, Toast.LENGTH_LONG).show();
                voskRecognizer.clearModelError();
                Log.w(TAG, "Vosk model error shown to user: " + errMsg);
            } else {
                Toast.makeText(activity,
                        activity.getString(R.string.voice_model_loading),
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, activity.getString(R.string.speak));
        try {
            activity.startActivityForResult(intent, requestCodeVoiceInput);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity,
                    activity.getString(R.string.no_speech_engine),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(activity,
                    activity.getString(R.string.voice_start_failed, e.getMessage()),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 处理权限请求结果（在 Activity.onRequestPermissionsResult 中调用）
     */
    public boolean handlePermissionResult(int requestCode, int[] grantResults,
                                          int expectedCode, int requestCodeVoiceInput,
                                          int requestCodePermission) {
        if (requestCode == expectedCode) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceInput(requestCodeVoiceInput, requestCodePermission);
            } else {
                Toast.makeText(activity,
                        activity.getString(R.string.need_mic_permission),
                        Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return false;
    }

    private void startVoskListening() {
        if (voskRecognizer == null || isVoskListening) return;
        if (!voskRecognizer.isModelReady()) {
            Toast.makeText(activity,
                    activity.getString(R.string.voice_model_loading_start),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            voskBaseText = callback.getEditText().getText().toString().trim();
            if (!voskBaseText.isEmpty()) {
                voskBaseText += " ";
            }
            voskRecognizer.startListening();
            isVoskListening = true;
            callback.onButtonStateChanged(ChatActivity.STATE_LISTENING);
            callback.onUpdateButtonAppearance();
            startListeningPulse();
        } catch (Exception e) {
            isVoskListening = false;
            Toast.makeText(activity,
                    activity.getString(R.string.voice_start_error, e.getMessage()),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 停止 Vosk 监听
     */
    public void stopListening() {
        if (voskRecognizer == null || !isVoskListening) return;
        try {
            voskRecognizer.stopListening();
        } catch (Exception e) {
            // ignore
        } finally {
            isVoskListening = false;
            voskBaseText = "";
            if (callback.isInProgress()) {
                callback.onButtonStateChanged(ChatActivity.STATE_SENDING);
            } else {
                callback.onButtonStateChanged(ChatActivity.STATE_NORMAL);
            }
            callback.onUpdateButtonAppearance();
            stopListeningPulse();
        }
    }

    private void startListeningPulse() {
        stopListeningPulse();
        pulseAnimator = android.animation.ObjectAnimator.ofFloat(callback.getSendButton(), "alpha", 1f, 0.4f);
        pulseAnimator.setDuration(600);
        pulseAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        pulseAnimator.start();
    }

    private void stopListeningPulse() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        if (callback.getSendButton() != null) {
            callback.getSendButton().setAlpha(1f);
        }
    }

    /**
     * 销毁，释放资源（在 Activity.onDestroy 中调用）
     */
    public void destroy() {
        stopListeningPulse();
        stopListening();
        if (voskRecognizer != null) {
            voskRecognizer.destroy();
            voskRecognizer = null;
        }
    }
}

