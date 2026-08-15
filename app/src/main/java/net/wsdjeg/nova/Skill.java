package net.wsdjeg.nova;

/**
 * Skill 数据模型
 * 对应服务端 GET /skills API 返回的 slash 命令
 * 
 * 字段：
 * - name: skill 名称（如 "clear"），输入 /name 触发
 * - description: skill 描述
 * - builtin: 是否为内置 skill
 */
public class Skill {
    public final String name;
    public final String description;
    public final boolean builtin;
    
    public Skill(String name, String description, boolean builtin) {
        this.name = name != null ? name : "";
        this.description = description != null ? description : "";
        this.builtin = builtin;
    }
    
    /**
     * 获取完整的命令文本（含 / 前缀）
     */
    public String getCommand() {
        return "/" + name;
    }
    
    /**
     * 名称或描述是否包含关键词（忽略大小写），用于输入过滤
     */
    public boolean matches(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return true;
        }
        String k = keyword.toLowerCase();
        return name.toLowerCase().contains(k)
                || description.toLowerCase().contains(k);
    }
    
    @Override
    public String toString() {
        return getCommand() + (description.isEmpty() ? "" : " - " + description);
    }
}

