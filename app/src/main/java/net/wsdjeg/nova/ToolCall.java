package net.wsdjeg.nova;

/**
 * 工具调用
 */
public class ToolCall {
    public String id;
    public String type;
    public ToolCallFunction function;

    public ToolCall(String id, String type, ToolCallFunction function) {
        this.id = id;
        this.type = type;
        this.function = function;
    }
}

