package net.wsdjeg.nova;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
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
    private Button btnOpenQrBrowser;

    private ApiClient apiClient;
    private AccountManager accountManager;
    private Handler pollHandler;
    private final AtomicBoolean isPolling = new AtomicBoolean(false);
    private int pollCount = 0;
    private String currentQrUrl = "";

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
        btnOpenQrBrowser = findViewById(R.id.btn_open_qr_browser);

        btnStartLogin.setOnClickListener(v -> {
            if (isPolling.get()) {
                stopPolling();
                btnStartLogin.setText(R.string.weixin_start_login);
            } else {
                startLoginFlow();
            }
        });

        btnOpenQrBrowser.setOnClickListener(v -> {
            if (!currentQrUrl.isEmpty()) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(currentQrUrl));
                startActivity(browserIntent);
            }
        });
    }

    /**
     * 启动微信扫码登录流程
     */
    private void startLoginFlow() {
        if (apiClient == null) {
            Toast.makeText(this, R.string.please_add_account, Toast.LENGTH_SHORT).show();
            return;
        }

        pollCount = 0;
        currentQrUrl = "";
        isPolling.set(true);
        btnStartLogin.setText(R.string.weixin_stop_polling);
        progressBar.setVisibility(View.VISIBLE);
        ivQrCode.setVisibility(View.GONE);
        btnOpenQrBrowser.setVisibility(View.GONE);
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
        btnStartLogin.setText(R.string.weixin_start_login);
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
        if (result.isInit()) {
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
                btnOpenQrBrowser.setVisibility(View.VISIBLE);
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
            progressBar.setVisibility(View.GONE);
            btnStartLogin.setText(R.string.weixin_start_login);
            tvStatus.setText(R.string.weixin_login_success);
            tvSubStatus.setText(result.message);

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
            btnOpenQrBrowser.setVisibility(View.GONE);
            pollHandler.postDelayed(() -> pollLoginStatus(), POLL_INTERVAL_MS);

        } else {
            // 未知状态
            tvStatus.setText(getString(R.string.weixin_status_unknown, result.status));
            pollHandler.postDelayed(() -> pollLoginStatus(), POLL_INTERVAL_MS);
        }
    }

    /**
     * 异步加载二维码图片
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
                            btnOpenQrBrowser.setVisibility(View.VISIBLE);
                        });
                    }
                } else {
                    Log.e(TAG, "QR code HTTP error: " + responseCode);
                    runOnUiThread(() -> {
                        btnOpenQrBrowser.setVisibility(View.VISIBLE);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load QR code image", e);
                runOnUiThread(() -> {
                    btnOpenQrBrowser.setVisibility(View.VISIBLE);
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

