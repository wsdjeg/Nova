package net.wsdjeg.nova;

public class Message {
    private String content;
    private boolean isUser;
    private long timestamp;

    // 使用指定时间戳的构造方法
    public Message(String content, boolean isUser, long timestamp) {
        this.content = content;
        this.isUser = isUser;
        this.timestamp = timestamp;
    }

    // 使用当前时间的构造方法（用于用户刚发送的消息）
    public Message(String content, boolean isUser) {
        this.content = content;
        this.isUser = isUser;
        this.timestamp = System.currentTimeMillis();
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
}
