package net.wsdjeg.nova;

import java.util.UUID;

/**
 * 账号模型类
 * 支持多服务器账号管理
 */
public class Account {
    private String id;          // 唯一标识
    private String name;        // 显示名称
    private String url;         // 服务器地址
    private String apiKey;      // API密钥（可选）
    private boolean isActive;   // 是否当前激活
    private long createdAt;     // 创建时间
    private long lastUsedAt;    // 最后使用时间

    public Account() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.lastUsedAt = System.currentTimeMillis();
    }

    public Account(String name, String url) {
        this();
        this.name = name;
        this.url = url;
    }

    public Account(String name, String url, String apiKey) {
        this(name, url);
        this.apiKey = apiKey;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getApiKey() {
        return apiKey;
    }

    public boolean isActive() {
        return isActive;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastUsedAt() {
        return lastUsedAt;
    }

    // Setters
    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public void setLastUsedAt(long lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    /**
     * 更新最后使用时间
     */
    public void updateLastUsed() {
        this.lastUsedAt = System.currentTimeMillis();
    }

    /**
     * 获取显示名称（如果为空则返回URL）
     */
    public String getDisplayName() {
        return name != null && !name.isEmpty() ? name : url;
    }

    @Override
    public String toString() {
        return "Account{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", isActive=" + isActive +
                '}';
    }
}
