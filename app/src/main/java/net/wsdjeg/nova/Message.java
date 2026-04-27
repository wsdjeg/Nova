package net.wsdjeg.nova;

/**
 * 消息数据模型
 * 存储完整的消息信息，包括原始 role 类型
 * 
 * 显示过滤规则：
 * - 只显示 content 不为空的消息
 * - 只显示 role 不是 tool 的消息
 */
public class Message {
    private String content;
    private boolean isUser;
    private long timestamp;
    private String role;      // 原始 role: user, assistant, tool, system 等
    private long created;      // 服务器时间戳（秒）

    // 使用指定时间戳的构造方法
    public Message(String content, boolean isUser, long timestamp) {
        this.content = content;
        this.isUser = isUser;
        this.timestamp = timestamp;
        this.role = isUser ? "user" : "assistant";
        this.created = timestamp / 1000;
    }

    // 完整构造方法（包含 role 和 created）
    public Message(String content, String role, long created) {
        this.content = content != null ? content : "";
        this.role = role != null ? role : "assistant";
        this.isUser = "user".equals(this.role);
        this.created = created;
        this.timestamp = created * 1000;
    }

    // 使用当前时间的构造方法（用于用户刚发送的消息）
    public Message(String content, boolean isUser) {
        this.content = content;
        this.isUser = isUser;
        this.timestamp = System.currentTimeMillis();
        this.role = isUser ? "user" : "assistant";
        this.created = this.timestamp / 1000;
    }

    public String getContent() {
        return content;
    }

    public boolean isUser() {
        return isUser;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getRole() {
        return role;
    }

    public long getCreated() {
        return created;
    }

    /**
     * 是否应该显示在列表中
     * - content 不为空
     * - role 不是 tool
     */
    public boolean shouldDisplay() {
        return content != null && !content.isEmpty() && !"tool".equals(role);
    }
}
