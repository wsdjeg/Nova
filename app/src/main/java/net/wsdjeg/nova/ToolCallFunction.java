package net.wsdjeg.nova;

/**
 * 工具调用参数
 */
public class ToolCallFunction {
    public String name;
    public String arguments; // JSON 字符串

    public ToolCallFunction(String name, String arguments) {
        this.name = name;
        this.arguments = arguments;
    }
}

