package net.wsdjeg.nova;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 微信登录 Activity
 *
 * 扫码登录：调用 GET /weixin/login/status 轮询状态，显示二维码图片
 * 二维码直接在应用内显示，无需跳转浏览器。
 * API 支持自动检测已登录状态（connected），避免重复扫码。
 */
public class WeChatLoginActivity extends AppCompatActivity {

    private static final String TAG = "WeChatLoginActivity";
    private static final int POLL_INTERVAL_MS = 2000; // 轮询间隔 2 秒
    private static final int MAX_POLL_COUNT = 240;   // 最大轮询次数（约 8 分钟）

    private ImageView ivQrCode;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView tvSubStatus;
    private Button btnStartLogin;

    private ApiClient apiClient;
    private AccountManager accountManager;
    private Handler pollHandler;
    private final AtomicBoolean isPolling = new AtomicBoolean(false);
    private int pollCount = 0;
    private String currentQrUrl = "";
    private boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weixin_login);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        accountManager = AccountManager.getInstance(this);
        pollHandler = new Handler(Looper.getMainLooper());

        initViews();

        // 获取当前活跃账号创建 ApiClient
        Account account = accountManager.getActiveAccount();
        if (account != null) {
            apiClient = new ApiClient(account.getUrl(), account.getApiKey());
            // 自动开始登录流程，API 会检测是否已登录
            startLoginFlow();
        } else {
            tvStatus.setText(R.string.please_add_account);
            btnStartLogin.setEnabled(false);
        }
    }

    private void initViews() {
        ivQrCode = findViewById(R.id.iv_qrcode);
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);
        tvSubStatus = findViewById(R.id.tv_sub_status);
        btnStartLogin = findViewById(R.id.btn_start_login);

        btnStartLogin.setOnClickListener(v -> {
            if (isPolling.get()) {
                stopPolling();
                btnStartLogin.setText(isConnected ? R.string.weixin_relogin : R.string.weixin_start_login);
            } else if (isConnected) {
                // 已登录状态下点击"重新登录"，先清除凭证再重新登录
                reloginWeChat();
            } else {
                startLoginFlow();
            }
        });
    }

    /**
     * 启动微信扫码登录流程
     * 第一次调用 GET /weixin/login/status 会自动启动登录流程
     * 如果已登录，API 返回 connected 状态
     */
    private void startLoginFlow() {
        if (apiClient == null) {
            Toast.makeText(this, R.string.please_add_account, Toast.LENGTH_SHORT).show();
            return;
        }

        pollCount = 0;
        currentQrUrl = "";
        isPolling.set(true);
        isConnected = false;
        btnStartLogin.setText(R.string.weixin_stop_polling);
        progressBar.setVisibility(View.VISIBLE);
        ivQrCode.setVisibility(View.GONE);
        tvStatus.setText(R.string.weixin_starting);
        tvSubStatus.setText("");

        pollLoginStatus();
    }

    /**
     * 停止轮询
     */
    private void stopPolling() {
        isPolling.set(false);
        pollHandler.removeCallbacksAndMessages(null);
        progressBar.setVisibility(View.GONE);
        btnStartLogin.setText(isConnected ? R.string.weixin_relogin : R.string.weixin_start_login);
    }

    /**
     * 重新登录微信
     * 先调用 DELETE /weixin/credentials 清除凭证，再启动新的登录流程
     */
    private void reloginWeChat() {
        if (apiClient == null) {
            Toast.makeText(this, R.string.please_add_account, Toast.LENGTH_SHORT).show();
            return;
        }

        btnStartLogin.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText(R.string.weixin_deleting_credentials);
        tvSubStatus.setText("");

        apiClient.deleteWeChatCredentials(new ApiClient.WeChatCredentialsCallback() {
            @Override
            public void onSuccess(String message) {
                btnStartLogin.setEnabled(true);
                Toast.makeText(WeChatLoginActivity.this,
                    R.string.weixin_delete_credentials_success, Toast.LENGTH_SHORT).show();
                startLoginFlow();
            }

            @Override
            public void onError(String error) {
                btnStartLogin.setEnabled(true);
                progressBar.setVisibility(View.GONE);
                tvStatus.setText(getString(R.string.weixin_delete_credentials_failed, error));
                btnStartLogin.setText(R.string.weixin_relogin);
            }
        });
    }

    /**
     * 轮询登录状态
     */
    private void pollLoginStatus() {
        if (!isPolling.get()) {
            return;
        }

        if (pollCount >= MAX_POLL_COUNT) {
            isPolling.set(false);
            progressBar.setVisibility(View.GONE);
            btnStartLogin.setText(R.string.weixin_start_login);
            tvStatus.setText(R.string.weixin_poll_timeout);
            tvSubStatus.setText("");
            return;
        }

        pollCount++;

        apiClient.getWeChatLoginStatus(new ApiClient.WeChatLoginCallback() {
            @Override
            public void onSuccess(WeChatLoginResult result) {
                if (!isPolling.get()) {
                    return;
                }

                handleLoginStatus(result);
            }

            @Override
            public void onError(String error) {
                if (!isPolling.get()) {
                    return;
                }
                Log.e(TAG, "Poll error: " + error);
                tvStatus.setText(getString(R.string.weixin_poll_error, error));
                // 出错后继续轮询
                pollHandler.postDelayed(() -> pollLoginStatus(), POLL_INTERVAL_MS);
            }
        });
    }

    /**
     * 处理登录状态
     */
    private void handleLoginStatus(WeChatLoginResult result) {
        if (result.isConnected()) {
            // 已登录，无需扫码
            isPolling.set(false);
            isConnected = true;
            progressBar.setVisibility(View.GONE);
            btnStartLogin.setText(R.string.weixin_relogin);
            tvStatus.setText(R.string.weixin_already_connected);
            tvSubStatus.setText(getString(R.string.weixin_connected_account, result.accountId));
            ivQrCode.setVisibility(View.GONE);

        } else if (result.isInit()) {
            // 登录流程已启动，继续轮询获取二维码
            tvStatus.setText(R.string.weixin_status_init);
            tvSubStatus.setText(result.message);
            pollHandler.postDelayed(() -> pollLoginStatus(), POLL_INTERVAL_MS);

        } else if (result.isWaiting()) {
            // 二维码已就绪，等待扫码
            tvStatus.setText(R.string.weixin_status_wait);
            tvSubStatus.setText(R.string.weixin_scan_hint);

            // 加载二维码图片
            if (!result.qrcodeUrl.isEmpty() && !result.qrcodeUrl.equals(currentQrUrl)) {
                currentQrUrl = result.qrcodeUrl;
                loadQrCodeImage(result.qrcodeUrl);
            }

            progressBar.setVisibility(View.GONE);
            pollHandler.postDelayed(() -> pollLoginStatus(), POLL_INTERVAL_MS);

        } else if (result.isScanned()) {
            // 已扫码，等待确认
            tvStatus.setText(R.string.weixin_status_scanned);
            tvSubStatus.setText(R.string.weixin_confirm_hint);
            progressBar.setVisibility(View.VISIBLE);
            pollHandler.postDelayed(() -> pollLoginStatus(), POLL_INTERVAL_MS);

        } else if (result.isConfirmed()) {
            // 登录成功
            isPolling.set(false);
            isConnected = true;
            progressBar.setVisibility(View.GONE);
            btnStartLogin.setText(R.string.weixin_relogin);
            tvStatus.setText(R.string.weixin_login_success);
            tvSubStatus.setText(result.message);
            ivQrCode.setVisibility(View.GONE);

            // 显示返回的凭证信息
            String credInfo = "Account ID: " + result.accountId + "\n"
                    + "Base URL: " + result.baseUrl;
            if (!result.userId.isEmpty()) {
                credInfo += "\nUser ID: " + result.userId;
            }
            Toast.makeText(this, getString(R.string.weixin_login_success) + "\n" + credInfo,
                    Toast.LENGTH_LONG).show();

        } else if (result.isExpired()) {
            // 二维码过期，服务器会自动刷新（最多3次），继续轮询
            tvStatus.setText(R.string.weixin_status_expired);
            tvSubStatus.setText(R.string.weixin_expired_hint);
            progressBar.setVisibility(View.VISIBLE);
            ivQrCode.setVisibility(View.GONE);
            currentQrUrl = ""; // 重置二维码 URL，以便重新加载新的二维码
            pollHandler.postDelayed(() -> pollLoginStatus(), POLL_INTERVAL_MS);

        } else {
            // 未知状态
            tvStatus.setText(getString(R.string.weixin_status_unknown, result.status));
            pollHandler.postDelayed(() -> pollLoginStatus(), POLL_INTERVAL_MS);
        }
    }

    /**
     * 异步加载二维码图片，直接在应用内显示
     */
    private void loadQrCodeImage(String qrUrl) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            InputStream is = null;
            try {
                URL url = new URL(qrUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setUseCaches(false);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    is = conn.getInputStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    if (bitmap != null) {
                        runOnUiThread(() -> {
                            ivQrCode.setImageBitmap(bitmap);
                            ivQrCode.setVisibility(View.VISIBLE);
                            progressBar.setVisibility(View.GONE);
                        });
                    } else {
                        Log.e(TAG, "Failed to decode QR code bitmap");
                        runOnUiThread(() -> {
                            tvSubStatus.setText(R.string.weixin_qr_load_failed);
                        });
                    }
                } else {
                    Log.e(TAG, "QR code HTTP error: " + responseCode);
                    runOnUiThread(() -> {
                        tvSubStatus.setText(R.string.weixin_qr_load_failed);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load QR code image", e);
                runOnUiThread(() -> {
                    tvSubStatus.setText(R.string.weixin_qr_load_failed);
                });
            } finally {
                if (is != null) {
                    try { is.close(); } catch (Exception ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
    }
}

