package net.wsdjeg.nova;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class AboutActivity extends AppCompatActivity {

    // GitHub Releases 页面
    private static final String RELEASES_URL = "https://github.com/wsdjeg/Nova/releases";
    // GitHub 仓库
    private static final String GITHUB_URL = "https://github.com/wsdjeg/Nova";
    // chat.nvim 官网
    private static final String CHAT_NVIM_URL = "https://nvim.chat";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        // 设置 Toolbar 作为 ActionBar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("关于");

        // 设置版本号
        setVersionText();

        // 设置链接点击事件
        setupLinks();

        // 设置检查更新按钮
        setupCheckUpdate();
    }

    private void setVersionText() {
        TextView tvVersion = findViewById(R.id.tv_version);
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            tvVersion.setText("Version " + version);
        } catch (PackageManager.NameNotFoundException e) {
            tvVersion.setText("Version 1.0.0");
        }
    }

    private void setupLinks() {
        // GitHub 仓库链接
        TextView tvGithub = findViewById(R.id.tv_github);
        tvGithub.setOnClickListener(v -> openUrl(GITHUB_URL));

        // chat.nvim 官网链接
        TextView tvChatNvim = findViewById(R.id.tv_chat_nvim);
        tvChatNvim.setOnClickListener(v -> openUrl(CHAT_NVIM_URL));
    }

    private void setupCheckUpdate() {
        TextView tvCheckUpdate = findViewById(R.id.tv_check_update);
        tvCheckUpdate.setOnClickListener(v -> openUrl(RELEASES_URL));
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
