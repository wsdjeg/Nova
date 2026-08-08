package net.wsdjeg.nova;

/**
 * 微信登录状态结果
 * 对应 GET /weixin/login/status 返回的 JSON
 */
public class WeChatLoginResult {
    /** 状态：init, wait, scaned, confirmed, expired */
    public String status = "";
    /** 消息 */
    public String message = "";
    /** 二维码 URL */
    public String qrcodeUrl = "";
    /** 会话 key */
    public String sessionKey = "";
    /** 是否为最新的二维码 */
    public boolean isFresh = false;
    /** Bot Token（confirmed 时返回） */
    public String botToken = "";
    /** Account ID（confirmed 时返回） */
    public String accountId = "";
    /** Base URL（confirmed 时返回） */
    public String baseUrl = "";
    /** User ID（confirmed 时返回） */
    public String userId = "";

    public boolean isConfirmed() {
        return "confirmed".equals(status);
    }

    public boolean isWaiting() {
        return "wait".equals(status);
    }

    public boolean isScanned() {
        return "scaned".equals(status);
    }

    public boolean isExpired() {
        return "expired".equals(status);
    }

    public boolean isInit() {
        return "init".equals(status);
    }
}

