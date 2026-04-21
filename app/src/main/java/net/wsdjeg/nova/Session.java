package net.wsdjeg.nova;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 会话数据模型
 * 用于在会话列表中显示每个会话的信息
 */
public class Session {
    private String sessionId;
    private String lastMessage;
    private long lastMessageTime;
    private int messageCount;
    private String preview;
    
    public Session(String sessionId) {
        this.sessionId = sessionId;
        this.lastMessage = "";
        this.lastMessageTime = System.currentTimeMillis();
        this.messageCount = 0;
        this.preview = "";
    }
    
    public Session(String sessionId, String lastMessage, long lastMessageTime, int messageCount) {
        this.sessionId = sessionId;
        this.lastMessage = lastMessage;
        this.lastMessageTime = lastMessageTime;
        this.messageCount = messageCount;
        this.preview = generatePreview(lastMessage);
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
     * 昨天：昨天 HH:mm
     * 更早：MM-dd HH:mm
     */
    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat sdfDate = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        
        Date now = new Date();
        Date messageDate = new Date(lastMessageTime);
        
        // 判断是否是今天
        SimpleDateFormat sdfDay = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String nowDay = sdfDay.format(now);
        String messageDay = sdfDay.format(messageDate);
        
        if (nowDay.equals(messageDay)) {
            return sdf.format(messageDate);
        }
        
        // 判断是否是昨天
        long oneDayMs = 24 * 60 * 60 * 1000;
        Date yesterday = new Date(now.getTime() - oneDayMs);
        String yesterdayDay = sdfDay.format(yesterday);
        
        if (yesterdayDay.equals(messageDay)) {
            return "昨天 " + sdf.format(messageDate);
        }
        
        return sdfDate.format(messageDate);
    }
    
    // Getters and Setters
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
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
    
    /**
     * 获取显示标题
     * 格式：Session {sessionId前8位}
     */
    public String getTitle() {
        if (sessionId != null && sessionId.length() >= 8) {
            return "Session " + sessionId.substring(0, 8);
        }
        return "Session " + (sessionId != null ? sessionId : "Unknown");
    }
}
