package net.wsdjeg.nova;

/**
 * 工具调用参数
 */
class ToolCallFunction {
    public String name;
    public String arguments; // JSON 字符串
    
    public ToolCallFunction(String name, String arguments) {
        this.name = name;
        this.arguments = arguments;
    }
}

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

/**
 * 工具调用状态（用于工具结果消息）
 */
class ToolCallState {
    public String name;
    public String error;
    
    public ToolCallState(String name, String error) {
        this.name = name;
        this.error = error;
    }
}

