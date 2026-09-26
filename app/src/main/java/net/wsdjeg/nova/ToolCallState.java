package net.wsdjeg.nova;

/**
 * 工具调用状态（用于工具结果消息）
 */
public class ToolCallState {
    public String name;
    public String error;

    public ToolCallState(String name, String error) {
        this.name = name;
        this.error = error;
    }
}

