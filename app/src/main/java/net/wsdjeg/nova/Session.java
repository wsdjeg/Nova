package net.wsdjeg.nova;

import java.util.Date;

/**
 * 会话数据模型
 * 用于在会话列表中显示每个会话的信息
 * 支持多账号聚合
 */
public class Session {
    private String sessionId;
    private String accountId;     // 关联的账号ID（支持多账号）
    private String firstMessage;  // 第一个消息（用于显示标题）
    private String lastMessage;
    private long lastMessageTime;
    private int messageCount;
    private String preview;
    private int unreadCount;  // 未读消息数量
    
    // 新增字段：来自 API /sessions
    private String provider;  // AI provider
    private String model;     // Model name
    private String cwd;       // Working directory
    
    public Session(String sessionId) {
        this.sessionId = sessionId;
        this.accountId = "";  // 默认空，表示本地会话或当前账号
        this.firstMessage = "";
        this.lastMessage = "";
        this.lastMessageTime = System.currentTimeMillis();
        this.messageCount = 0;
        this.preview = "";
        this.unreadCount = 0;
        this.provider = "";
        this.model = "";
        this.cwd = "";
    }
    
    public Session(String sessionId, String firstMessage, String lastMessage, long lastMessageTime, int messageCount) {
        this.sessionId = sessionId;
        this.accountId = "";
        this.firstMessage = firstMessage != null ? firstMessage : "";
        this.lastMessage = lastMessage;
        this.lastMessageTime = lastMessageTime;
        this.messageCount = messageCount;
        this.preview = generatePreview(lastMessage);
        this.unreadCount = 0;
        this.provider = "";
        this.model = "";
        this.cwd = "";
    }
    
    /**
     * 新构造函数：包含 accountId, provider, model, cwd
     */
    public Session(String sessionId, String accountId, String cwd, String provider, String model) {
        this.sessionId = sessionId;
        this.accountId = accountId != null ? accountId : "";
        this.cwd = cwd != null ? cwd : "";
        this.provider = provider != null ? provider : "";
        this.model = model != null ? model : "";
        this.firstMessage = "";
        this.lastMessage = "";
        this.lastMessageTime = System.currentTimeMillis();
        this.messageCount = 0;
        this.preview = "";
        this.unreadCount = 0;
    }
    
    // 兼容旧版本的构造函数
    public Session(String sessionId, String lastMessage, long lastMessageTime, int messageCount) {
        this(sessionId, "", lastMessage, lastMessageTime, messageCount);
    }
    
    /**
     * 生成消息预览（截取前50个字符）
     */
    private String generatePreview(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        // 去除换行符，显示单行预览
        String singleLine = message.replace("\n", " ").trim();
        if (singleLine.length() > 50) {
            return singleLine.substring(0, 50) + "...";
        }
        return singleLine;
    }
    
    /**
     * 获取格式化的时间显示
     * 今天：HH:mm
     * 非今天但今年：MM-dd HH:mm
     * 非今年：yyyy-MM-dd HH:mm
     */
    public String getFormattedTime() {
        return TimeUtils.formatTime(lastMessageTime);
    }
    
    /**
     * 获取会话信息显示
     * 格式：provider / model
     */
    public String getSessionInfo() {
        if (provider != null && !provider.isEmpty() && model != null && !model.isEmpty()) {
            return provider + " / " + model;
        }
        return "Unknown Provider";
    }
    
    /**
     * 获取路径显示
     * 如果路径太长，只显示最后部分
     */
    public String getShortPath() {
        if (cwd == null || cwd.isEmpty()) {
            return "";
        }
        // 只显示最后 30 个字符
        if (cwd.length() > 30) {
            return "..." + cwd.substring(cwd.length() - 30);
        }
        return cwd;
    }
    
    /**
     * 更新会话信息（从消息列表获取）
     * @param lastMessageContent 最后一条消息内容
     * @param count 消息数量
     * @param time 最后消息时间
     */
    public void updateSessionInfo(String lastMessageContent, int count, long time) {
        this.lastMessage = lastMessageContent;
        this.preview = generatePreview(lastMessageContent);
        this.messageCount = count;
        this.lastMessageTime = time;
    }
    
    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getAccountId() {
        return accountId;
    }
    
    public void setAccountId(String accountId) {
        this.accountId = accountId != null ? accountId : "";
    }
    
    public String getFirstMessage() {
        return firstMessage;
    }
    
    public void setFirstMessage(String firstMessage) {
        this.firstMessage = firstMessage != null ? firstMessage : "";
    }
    
    public String getLastMessage() {
        return lastMessage;
    }
    
    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
        this.preview = generatePreview(lastMessage);
    }
    
    public long getLastMessageTime() {
        return lastMessageTime;
    }
    
    public void setLastMessageTime(long lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }
    
    public int getMessageCount() {
        return messageCount;
    }
    
    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }
    
    public String getPreview() {
        return preview;
    }
    
    public int getUnreadCount() {
        return unreadCount;
    }
    
    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }
    
    public String getProvider() {
        return provider;
    }
    
    public void setProvider(String provider) {
        this.provider = provider != null ? provider : "";
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model != null ? model : "";
    }
    
    public String getCwd() {
        return cwd;
    }
    
    public void setCwd(String cwd) {
        this.cwd = cwd != null ? cwd : "";
    }
    
    /**
     * 获取显示标题
     * 显示第一个消息的第一行
     * 如果没有消息，显示 "新会话"
     */
    public String getTitle() {
        if (firstMessage != null && !firstMessage.isEmpty()) {
            // 获取第一行
            String firstLine = firstMessage.split("\n")[0].trim();
            if (firstLine.length() > 30) {
                return firstLine.substring(0, 30) + "...";
            }
            return firstLine;
        }
        return "新会话";
    }
}
