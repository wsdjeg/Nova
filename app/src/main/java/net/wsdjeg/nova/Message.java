package net.wsdjeg.nova;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息数据模型
 * 存储完整的消息信息，包括原始 role 类型
 * 
 * 支持的消息类型：
 * - user: 用户消息
 * - assistant: AI 消息（可能包含 tool_calls）
 * - tool: 工具执行结果
 * - error: 错误消息
 * - notice: UI-only 通知消息（服务端提示，如 API key 轮换，不发送给模型）
 * 
 * 显示过滤规则：
 * - 只显示 content 不为空的消息，或 tool_calls 不为空
 * - tool 消息单独显示
 * - 错误消息特殊显示
 * - 通知消息居中提示样式显示
 */
public class Message {
    private String content;
    private boolean isUser;
    private long timestamp;
    private String role;          // 原始 role: user, assistant, tool, system, notice 等
    private long created;         // 服务器时间戳（秒）
    private boolean isPending;    // 是否是待确认的消息
    private String error;         // 错误信息
    private String notice;        // UI-only 通知文本（role=notice）
    private int serverIndex = -1; // 服务端 1-indexed 索引（来自 /messages 数组的位置）
    
    // Tool calls 支持
    private List<ToolCall> toolCalls;  // assistant 消息中的工具调用请求
    private String toolCallId;                   // tool 消息关联的工具调用 ID
    private String toolName;                     // tool 消息的工具名称
    private String toolError;                    // tool 消息的错误信息

    // 消息级 token 用量（assistant 消息，来自消息自带 usage 或 on_complete 标记归属）
    private MessageUsage usage;

    // 使用指定时间戳的构造方法
    public Message(String content, boolean isUser, long timestamp) {
        this.content = content;
        this.isUser = isUser;
        this.timestamp = timestamp;
        this.role = isUser ? "user" : "assistant";
        this.created = timestamp / 1000;
        this.isPending = false;
        this.error = null;
        this.notice = null;
        this.toolCalls = null;
        this.toolCallId = null;
        this.toolName = null;
        this.toolError = null;
        this.usage = null;
    }

    // 完整构造方法（包含 role 和 created）
    public Message(String content, String role, long created) {
        this.content = content != null ? content : "";
        this.role = role != null ? role : "assistant";
        this.isUser = "user".equals(this.role);
        this.created = created;
        this.timestamp = created * 1000;
        this.isPending = false;
        this.error = null;
        this.notice = null;
        this.toolCalls = null;
        this.toolCallId = null;
        this.toolName = null;
        this.toolError = null;
        this.usage = null;
    }

    // 错误消息构造方法
    public Message(String error, long created) {
        this.content = "";
        this.role = "error";
        this.isUser = false;
        this.error = error;
        this.created = created;
        this.timestamp = created * 1000;
        this.isPending = false;
        this.notice = null;
        this.toolCalls = null;
        this.toolCallId = null;
        this.toolName = null;
        this.toolError = null;
        this.usage = null;
    }

    // 使用当前时间的构造方法（用于用户刚发送的消息）
    public Message(String content, boolean isUser) {
        this.content = content;
        this.isUser = isUser;
        this.timestamp = System.currentTimeMillis();
        this.role = isUser ? "user" : "assistant";
        this.created = this.timestamp / 1000;
        this.isPending = false;
        this.error = null;
        this.notice = null;
        this.toolCalls = null;
        this.toolCallId = null;
        this.toolName = null;
        this.toolError = null;
        this.usage = null;
    }

    // 创建 pending 消息的静态方法
    public static Message createPending(String content, boolean isUser) {
        Message msg = new Message(content, isUser);
        msg.isPending = true;
        return msg;
    }

    /**
     * 创建 UI-only 通知消息（服务端提示，如 API key 轮换，不发送给模型）
     */
    public static Message createNotice(String notice, long created) {
        Message msg = new Message("", "notice", created);
        msg.notice = notice;
        return msg;
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

    public boolean isPending() {
        return isPending;
    }

    public void setPending(boolean pending) {
        isPending = pending;
    }

    public String getError() {
        return error;
    }

    public boolean isError() {
        return error != null && !error.isEmpty();
    }

    public String getNotice() {
        return notice;
    }

    /**
     * 是否是 UI-only 通知消息
     */
    public boolean isNotice() {
        return "notice".equals(role) && notice != null && !notice.isEmpty();
    }

    public int getServerIndex() {
        return serverIndex;
    }
    
    public void setServerIndex(int serverIndex) {
        this.serverIndex = serverIndex;
    }
    
    /**
     * 是否是 assistant 消息
     */
    public boolean isAssistant() {
        return "assistant".equals(role);
    }
    
    /**
     * 是否是 tool 消息（工具执行结果）
     */
    public boolean isToolMessage() {
        return "tool".equals(role);
    }
    
    /**
     * 获取 tool_calls
     */
    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }
    
    /**
     * 设置 tool_calls
     */
    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }
    
    /**
     * 是否有 tool_calls
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
    
    /**
     * 获取工具调用 ID（tool 消息）
     */
    public String getToolCallId() {
        return toolCallId;
    }
    
    /**
     * 设置工具调用 ID
     */
    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }
    
    /**
     * 获取工具名称（tool 消息）
     */
    public String getToolName() {
        return toolName;
    }
    
    /**
     * 设置工具名称
     */
    public void setToolName(String toolName) {
        this.toolName = toolName;
    }
    
    /**
     * 获取工具错误信息（tool 消息）
     */
    public String getToolError() {
        return toolError;
    }
    
    /**
     * 设置工具错误信息
     */
    public void setToolError(String toolError) {
        this.toolError = toolError;
    }
    
    /**
     * 是否有工具执行错误
     */
    public boolean hasToolError() {
        return toolError != null && !toolError.isEmpty();
    }

    /**
     * 获取消息级 token 用量
     */
    public MessageUsage getUsage() {
        return usage;
    }

    /**
     * 设置消息级 token 用量
     */
    public void setUsage(MessageUsage usage) {
        this.usage = usage;
    }

    /**
     * 创建当前消息的浅拷贝并设置 token 用量
     *
     * 用于 on_complete 标记延迟归属 usage 后替换原消息对象：
     * MessageAdapter 的 DiffUtil 通过对象内容比较刷新视图，
     * 原地修改无法触发内容变化检测，必须替换为新对象。
     */
    public Message copyWithUsage(MessageUsage usage) {
        Message copy = new Message(this.content, this.role, this.created);
        copy.timestamp = this.timestamp;
        copy.isPending = this.isPending;
        copy.error = this.error;
        copy.notice = this.notice;
        copy.serverIndex = this.serverIndex;
        copy.toolCalls = this.toolCalls;
        copy.toolCallId = this.toolCallId;
        copy.toolName = this.toolName;
        copy.toolError = this.toolError;
        copy.usage = usage;
        return copy;
    }

    /**
     * 是否应该显示在列表中
     * - content 不为空（正常消息）
     * - 或者是错误消息
     * - 或者是 UI-only 通知消息
     * - 或者是 tool 消息
     * - 或者有 tool_calls（assistant 消息）
     */
    public boolean shouldDisplay() {
        // 错误消息需要显示
        if (isError()) {
            return true;
        }
        
        // UI-only 通知消息需要显示
        if (isNotice()) {
            return true;
        }
        
        // tool 消息需要显示
        if (isToolMessage()) {
            return true;
        }
        
        // assistant 消息：有 tool_calls 就显示
        if (isAssistant() && hasToolCalls()) {
            return true;
        }
        
        // 正常消息：content 不为空
        return content != null && !content.isEmpty();
    }
}

