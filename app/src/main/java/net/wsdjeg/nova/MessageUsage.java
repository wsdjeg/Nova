package net.wsdjeg.nova;

import org.json.JSONObject;

/**
 * 消息级 Token 用量
 * 对应消息对象中的 usage 字段（保持 provider 原始形状），缓存命中字段归一化，
 * 与服务端 chat.usage 模块的规则一致：
 *
 * - 通用:               usage.cached_tokens
 * - DeepSeek:           usage.prompt_cache_hit_tokens
 * - Anthropic:          usage.cache_read_input_tokens
 * - OpenAI / DashScope: usage.prompt_tokens_details.cached_tokens
 *
 * total_tokens 缺失时回退为 prompt + completion。
 */
public class MessageUsage {
    public long totalTokens;
    public long promptTokens;
    public long completionTokens;
    public long cachedTokens;

    /**
     * 是否携带有效用量（全 0 视为无用量，不显示徽标）
     */
    public boolean hasUsage() {
        return totalTokens > 0 || promptTokens > 0 || completionTokens > 0 || cachedTokens > 0;
    }

    /**
     * 缓存命中率（cached / prompt，0~100 整数），prompt 为 0 时返回 0
     */
    public int cacheHitPercent() {
        return Session.cacheHitPercent(promptTokens, cachedTokens);
    }

    /**
     * 与另一用量对象逐字段比较（DiffUtil 内容比较用）
     */
    public boolean sameAs(MessageUsage other) {
        if (other == null) {
            return false;
        }
        return totalTokens == other.totalTokens
            && promptTokens == other.promptTokens
            && completionTokens == other.completionTokens
            && cachedTokens == other.cachedTokens;
    }

    /**
     * 从 provider 原始 usage JSON 解析并归一化
     * @param obj usage JSON 对象，为 null 或无有效数值时返回 null
     */
    public static MessageUsage fromJson(JSONObject obj) {
        if (obj == null) {
            return null;
        }
        MessageUsage u = new MessageUsage();
        u.promptTokens = obj.optLong("prompt_tokens", 0);
        u.completionTokens = obj.optLong("completion_tokens", 0);
        u.totalTokens = obj.optLong("total_tokens", 0);
        u.cachedTokens = cachedTokensFrom(obj);
        if (u.totalTokens <= 0) {
            u.totalTokens = u.promptTokens + u.completionTokens;
        }
        return u.hasUsage() ? u : null;
    }

    /**
     * 归一化 provider 专属缓存命中字段
     */
    private static long cachedTokensFrom(JSONObject obj) {
        long n = obj.optLong("cached_tokens", 0);
        if (n > 0) {
            return n;
        }
        // deepseek
        n = obj.optLong("prompt_cache_hit_tokens", 0);
        if (n > 0) {
            return n;
        }
        // anthropic
        n = obj.optLong("cache_read_input_tokens", 0);
        if (n > 0) {
            return n;
        }
        // openai / dashscope
        JSONObject details = obj.optJSONObject("prompt_tokens_details");
        if (details != null) {
            n = details.optLong("cached_tokens", 0);
        }
        return n;
    }
}

