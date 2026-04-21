package net.wsdjeg.nova;

import java.util.Date;

/**
 * 会话数据模型
 * 用于在会话列表中显示每个会话的信息
 */
public class Session {
    private String sessionId;
    private String firstMessage;  // 第一个消息（用于显示标题）
    private String lastMessage;
    private long lastMessageTime;
    private int messageCount;
    private String preview;
    private int unreadCount;  // 未读消息数量
    
    public Session(String sessionId) {
        this.sessionId = sessionId;
        this.firstMessage = "";
        this.lastMessage = "";
        this.lastMessageTime = System.currentTimeMillis();
        this.messageCount = 0;
        this.preview = "";
        this.unreadCount = 0;
    }
    
    public Session(String sessionId, String firstMessage, String lastMessage, long lastMessageTime, int messageCount) {
        this.sessionId = sessionId;
        this.firstMessage = firstMessage != null ? firstMessage : "";
        this.lastMessage = lastMessage;
        this.lastMessageTime = lastMessageTime;
        this.messageCount = messageCount;
        this.preview = generatePreview(lastMessage);
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
    
    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
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
