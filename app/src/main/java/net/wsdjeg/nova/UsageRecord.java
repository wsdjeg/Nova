package net.wsdjeg.nova;

/**
 * 单个 provider/model 的用量记录
 *
 * 对应 GET /usage-stats 响应中 stats.<provider>.<model> 对象:
 * { "requests", "total_tokens", "prompt_tokens", "completion_tokens", "cached_tokens" }
 *
 * 注意: total_tokens = prompt_tokens + completion_tokens
 * cached_tokens 是 prompt tokens 中由 provider 前缀缓存服务的部分
 */
public class UsageRecord {

    private final long requests;
    private final long totalTokens;
    private final long promptTokens;
    private final long completionTokens;
    private final long cachedTokens;

    public UsageRecord(long requests, long totalTokens, long promptTokens,
                       long completionTokens, long cachedTokens) {
        this.requests = requests;
        this.totalTokens = totalTokens;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.cachedTokens = cachedTokens;
    }

    /** 请求次数 */
    public long getRequests() {
        return requests;
    }

    /** 总 tokens（prompt + completion） */
    public long getTotalTokens() {
        return totalTokens;
    }

    /** 输入（prompt）tokens */
    public long getPromptTokens() {
        return promptTokens;
    }

    /** 输出（completion）tokens */
    public long getCompletionTokens() {
        return completionTokens;
    }

    /** 缓存命中 tokens（prompt 的子集） */
    public long getCachedTokens() {
        return cachedTokens;
    }
}

