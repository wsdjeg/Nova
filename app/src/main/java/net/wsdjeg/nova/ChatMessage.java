package net.wsdjeg.nova;

import java.util.List;

/**
 * 聊天消息数据模型
 * 包含 role、content、error、notice、created、tool_calls 和 tool_call_state 字段
 * 
 * 消息类型：
 * - 正常消息：有 role 和 content
 * - 错误消息：有 error 字段（无 role 或 role 为空）
 * - 通知消息：有 notice 字段（UI-only，无 role，如 API key 轮换提示）
 * - 工具调用消息：有 tool_calls（role=assistant）
 * - 工具结果消息：role=tool，有 tool_call_state 和 content
 */
public class ChatMessage {
    public String role;
    public String content;
    public String error;    // 错误消息字段
    public String notice;   // UI-only 通知字段（无 role，不发送给模型）
    public long created;
    public List<ToolCall> toolCalls;  // 工具调用（AI 调用工具时）
    public ToolCallState toolCallState;  // 工具状态（工具结果消息）
    public String toolCallId;  // tool 消息引用的 tool_call.id（顶层 tool_call_id）
    public int rawIndex = -1;  // 在 API 原始响应中的位置（0-based），用于计算正确的 serverIndex
    
    public ChatMessage(String role, String content, long created) {
        this.role = role;
        this.content = content;
        this.error = null;
        this.notice = null;
        this.created = created;
        this.toolCalls = null;
        this.toolCallState = null;
        this.toolCallId = null;
    }
    
    /**
     * 创建错误消息
     */
    public ChatMessage(String error, long created) {
        this.role = null;
        this.content = null;
        this.error = error;
        this.notice = null;
        this.created = created;
        this.toolCalls = null;
        this.toolCallState = null;
        this.toolCallId = null;
    }
    
    /**
     * 创建带工具调用的消息
     */
    public ChatMessage(String role, String content, long created, List<ToolCall> toolCalls) {
        this.role = role;
        this.content = content;
        this.error = null;
        this.notice = null;
        this.created = created;
        this.toolCalls = toolCalls;
        this.toolCallState = null;
        this.toolCallId = null;
    }
    
    /**
     * 创建工具结果消息
     */
    public static ChatMessage createToolResult(String content, long created, ToolCallState state) {
        ChatMessage msg = new ChatMessage("tool", content, created);
        msg.toolCallState = state;
        return msg;
    }
    
    /**
     * 创建工具结果消息（带 tool_call_id）
     */
    public static ChatMessage createToolResult(String content, long created, ToolCallState state, String toolCallId) {
        ChatMessage msg = new ChatMessage("tool", content, created);
        msg.toolCallState = state;
        msg.toolCallId = toolCallId;
        return msg;
    }
    
    /**
     * 创建 UI-only 通知消息（无 role，如 API key 轮换提示）
     */
    public static ChatMessage createNotice(String notice, long created) {
        ChatMessage msg = new ChatMessage(null, null, created);
        msg.notice = notice;
        return msg;
    }
    
    /**
     * 是否是错误消息
     */
    public boolean isError() {
        return error != null && !error.isEmpty();
    }
    
    /**
     * 是否是 UI-only 通知消息
     */
    public boolean hasNotice() {
        return notice != null && !notice.isEmpty();
    }
    
    /**
     * 是否有工具调用
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
    
    /**
     * 是否是工具结果消息
     */
    public boolean isToolResult() {
        return "tool".equals(role) && toolCallState != null;
    }
    
    /**
     * 是否是工具错误消息
     */
    public boolean isToolError() {
        return "tool".equals(role) && toolCallState != null 
            && toolCallState.error != null && !toolCallState.error.isEmpty();
    }
    
    /**
     * 是否有可显示的内容（content、error 或 notice）
     */
    public boolean hasDisplayableContent() {
        return (content != null && !content.isEmpty()) || isError() || hasNotice();
    }
}

