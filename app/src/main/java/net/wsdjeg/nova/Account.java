package net.wsdjeg.nova;

import java.util.UUID;

/**
 * 账号模型类
 * 支持多服务器账号管理
 */
public class Account {
    private String id;          // 唯一标识
    private String name;        // 显示名称
    private String host;        // 服务器地址（不含端口）
    private int port;           // 服务器端口
    private String apiKey;      // API密钥（可选）
    private boolean isDefault;  // 是否为默认账号
    private long createdAt;     // 创建时间
    private long lastUsedAt;    // 最后使用时间
    private int colorIndex;     // 颜色索引，-1表示使用全局设置

    public Account() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.lastUsedAt = System.currentTimeMillis();
        this.port = 8080;  // 默认端口
        this.colorIndex = -1;  // 默认使用全局设置
    }

    public Account(String name, String host, int port) {
        this();
        this.name = name;
        this.host = host;
        this.port = port;
    }

    public Account(String name, String host, int port, String apiKey) {
        this(name, host, port);
        this.apiKey = apiKey;
    }

    // 兼容旧代码的构造函数
    public Account(String name, String url) {
        this();
        this.name = name;
        // 解析 URL 提取 host 和 port
        parseUrl(url);
    }

    public Account(String name, String url, String apiKey) {
        this(name, url);
        this.apiKey = apiKey;
    }

    /**
     * 解析 URL 提取 host 和 port
     */
    private void parseUrl(String url) {
        if (url == null || url.isEmpty()) {
            this.host = "";
            this.port = 8080;
            return;
        }
        
        try {
            // 移除协议前缀
            String temp = url;
            if (temp.startsWith("http://")) {
                temp = temp.substring(7);
            } else if (temp.startsWith("https://")) {
                temp = temp.substring(8);
            }
            
            // 移除路径部分
            int slashIndex = temp.indexOf('/');
            if (slashIndex > 0) {
                temp = temp.substring(0, slashIndex);
            }
            
            // 解析端口
            int colonIndex = temp.lastIndexOf(':');
            if (colonIndex > 0) {
                this.host = temp.substring(0, colonIndex);
                this.port = Integer.parseInt(temp.substring(colonIndex + 1));
            } else {
                this.host = temp;
                this.port = url.startsWith("https://") ? 443 : 80;
            }
        } catch (Exception e) {
            this.host = url;
            this.port = 8080;
        }
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    /**
     * 获取完整URL（兼容旧代码）
     */
    public String getUrl() {
        if (host == null || host.isEmpty()) {
            return "";
        }
        String protocol = (port == 443) ? "https://" : "http://";
        if (port == 80 || port == 443) {
            return protocol + host;
        }
        return protocol + host + ":" + port;
    }

    public String getApiKey() {
        return apiKey;
    }

    /**
     * 是否为默认账号
     */
    public boolean isDefault() {
        return isDefault;
    }
    
    /**
     * 兼旧代码的别名方法
     */
    public boolean isActive() {
        return isDefault();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastUsedAt() {
        return lastUsedAt;
    }

    /**
     * 获取颜色索引
     * @return 颜色索引，-1表示使用全局设置
     */
    public int getColorIndex() {
        return colorIndex;
    }

    /**
     * 是否使用自定义颜色
     */
    public boolean hasCustomColor() {
        return colorIndex >= 0 && colorIndex < SettingsManager.ACCOUNT_TAG_COLORS.length;
    }

    // Setters
    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    /**
     * 设置URL（兼容旧代码）
     */
    public void setUrl(String url) {
        parseUrl(url);
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 设置是否为默认账号
     */
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
    
    /**
     * 兼旧代码的别名方法
     */
    public void setActive(boolean active) {
        setDefault(active);
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public void setLastUsedAt(long lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    /**
     * 设置颜色索引
     * @param colorIndex 颜色索引，-1表示使用全局设置
     */
    public void setColorIndex(int colorIndex) {
        this.colorIndex = colorIndex;
    }

    /**
     * 更新最后使用时间
     */
    public void updateLastUsed() {
        this.lastUsedAt = System.currentTimeMillis();
    }

    /**
     * 获取显示名称（如果为空则返回 host:port）
     */
    public String getDisplayName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        if (port == 80 || port == 443) {
            return host;
        }
        return host + ":" + port;
    }

    @Override
    public String toString() {
        return "Account{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", colorIndex=" + colorIndex +
                ", isDefault=" + isDefault +
                '}';
    }
}
