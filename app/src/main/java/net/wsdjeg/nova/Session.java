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
    private String title;         // 服务器返回的会话标题（优先显示）
    private String firstMessage;  // 第一个消息（用于显示标题，备用）
    private String lastMessage;
    private String lastMessageRole;  // 最后一条消息的角色（user/assistant）
    private long lastMessageTime;
    private int messageCount;
    private String preview;
    private int unreadCount;  // 未读消息数量
    
    // 新增字段：来自 API /sessions
    private String provider;     // AI provider
    private String model;        // Model name
    private String cwd;          // Working directory
    private boolean inProgress;  // 会话是否正在进行中
    // 分页加载相关：当前已加载的最旧消息索引（索引从 1 开始）
    // firstMessageIndex = 1 表示已加载到第一条消息
    // firstMessageIndex = 0 表示尚未初始化
    private int firstMessageIndex = 0;
    
    public Session(String sessionId) {
        this.sessionId = sessionId;
        this.accountId = "";  // 默认空，表示本地会话或当前账号
        this.title = "";
        this.firstMessage = "";
        this.lastMessage = "";
        this.lastMessageRole = "";
        this.lastMessageTime = System.currentTimeMillis();
        this.messageCount = 0;
        this.preview = "";
        this.unreadCount = 0;
        this.provider = "";
        this.model = "";
        this.cwd = "";
        this.inProgress = false;
        this.firstMessageIndex = 0;
    }
    
    public Session(String sessionId, String firstMessage, String lastMessage, long lastMessageTime, int messageCount) {
        this.sessionId = sessionId;
        this.accountId = "";
        this.title = "";
        this.firstMessage = firstMessage != null ? firstMessage : "";
        this.lastMessage = lastMessage;
        this.lastMessageRole = "";
        this.lastMessageTime = lastMessageTime;
        this.messageCount = messageCount;
        this.preview = generatePreview(lastMessage);
        this.unreadCount = 0;
        this.provider = "";
        this.model = "";
        this.cwd = "";
        this.inProgress = false;
        this.firstMessageIndex = 0;
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
        this.title = "";
        this.firstMessage = "";
        this.lastMessage = "";
        this.lastMessageRole = "";
        this.lastMessageTime = System.currentTimeMillis();
        this.messageCount = 0;
        this.preview = "";
        this.unreadCount = 0;
        this.inProgress = false;
        this.firstMessageIndex = 0;
    }
    
    // 兼容旧版本的构造函数
    public Session(String sessionId, String lastMessage, long lastMessageTime, int messageCount) {
        this(sessionId, "", lastMessage, lastMessageTime, messageCount);
    }
    
    /**
     * 生成消息预览（截取前50个字符，带角色图标）
     * assistant: 🤖 + 内容
     * user: 👤 + 内容
     */
    private String generatePreview(String message) {
        return generatePreview(message, lastMessageRole);
    }
    
    /**
     * 生成消息预览（带角色图标）
     */
    private String generatePreview(String message, String role) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        // 去除换行符，显示单行预览
        String singleLine = message.replace("\n", " ").trim();
        if (singleLine.length() > 50) {
            singleLine = singleLine.substring(0, 50) + "...";
        }
        // 添加角色图标
        if (role != null && role.equals("assistant")) {
            return "🤖 " + singleLine;
        } else if (role != null && role.equals("user")) {
            return "👤 " + singleLine;
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
    
    /**
     * 计算首次加载时的 since 值
     * @param pageSize 每页消息数（默认 20）
     * @return since 值（从第几条消息开始加载）
     */
    public int calculateInitialSince(int pageSize) {
        if (messageCount <= 0) {
            return 1;
        }
        if (messageCount <= pageSize) {
            return 1;
        }
        // 加载最后 pageSize 条消息
        return messageCount - pageSize + 1;
    }
    
    /**
     * 初始化 firstMessageIndex
     * @param pageSize 每页消息数
     */
    public void initializeFirstMessageIndex(int pageSize) {
        if (firstMessageIndex == 0) {
            firstMessageIndex = calculateInitialSince(pageSize);
        }
    }
    
    /**
     * 是否还有更早的消息可加载
     * @return true 如果 firstMessageIndex > 1
     */
    public boolean hasOlderMessages() {
        return firstMessageIndex > 1;
    }
    
    /**
     * 计算加载更早消息的 since 值
     * @param pageSize 每页消息数
     * @return 新的 since 值，如果已到第一条则返回当前值
     */
    public int calculateOlderSince(int pageSize) {
        if (firstMessageIndex <= 1) {
            return 1;
        }
        int newSince = firstMessageIndex - pageSize;
        if (newSince < 1) {
            newSince = 1;
        }
        return newSince;
    }
    
    /**
     * 更新 firstMessageIndex（加载更早消息后）
     * @param newSince 新的 since 值
     */
    public void updateFirstMessageIndex(int newSince) {
        firstMessageIndex = newSince;
    }
    
    /**
     * 是否已加载到第一条消息
     * @return true 如果 firstMessageIndex == 1
     */
    public boolean isAtFirstMessage() {
        return firstMessageIndex == 1;
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
    
    public String getTitle() {
        // 优先使用服务器返回的 title
        if (title != null && !title.isEmpty()) {
            // 如果标题太长，截取前30个字符
            if (title.length() > 30) {
                return title.substring(0, 30) + "...";
            }
            return title;
        }
        // 备用：使用第一个消息
        if (firstMessage != null && !firstMessage.isEmpty()) {
            String firstLine = firstMessage.split("\n")[0].trim();
            if (firstLine.length() > 30) {
                return firstLine.substring(0, 30) + "...";
            }
            return firstLine;
        }
        return "新会话";
    }
    
    public void setTitle(String title) {
        this.title = title != null ? title : "";
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
        this.preview = generatePreview(lastMessage, this.lastMessageRole);
    }
    
    /**
     * 设置最后消息内容和角色
     */
    public void setLastMessageWithRole(String lastMessage, String role) {
        this.lastMessage = lastMessage;
        this.lastMessageRole = role;
        this.preview = generatePreview(lastMessage, role);
    }
    
    /**
     * 获取最后消息角色
     */
    public String getLastMessageRole() {
        return lastMessageRole;
    }
    
    /**
     * 设置最后消息角色
     */
    public void setLastMessageRole(String role) {
        this.lastMessageRole = role;
        this.preview = generatePreview(this.lastMessage, role);
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
    
    public boolean isInProgress() {
        return inProgress;
    }
    
    public void setInProgress(boolean inProgress) {
        this.inProgress = inProgress;
    }
    
    public int getFirstMessageIndex() {
        return firstMessageIndex;
    }
    
    public void setFirstMessageIndex(int firstMessageIndex) {
        this.firstMessageIndex = firstMessageIndex;
    }
}
