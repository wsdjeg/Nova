package net.wsdjeg.nova;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用量统计模型
 *
 * 对应 GET /usage-stats 响应:
 * { "window": "total", "stats": { "<provider>": { "<model>": record } } }
 */
public class UsageStats {

    private final String window;
    private final Map<String, Map<String, UsageRecord>> stats;

    public UsageStats(String window, Map<String, Map<String, UsageRecord>> stats) {
        this.window = window != null ? window : "";
        this.stats = stats != null ? stats : new LinkedHashMap<>();
    }

    /** 窗口标识，如 "total" / "hours=24" / "days=7" */
    public String getWindow() {
        return window;
    }

    /** provider -> (model -> record) 只读视图 */
    public Map<String, Map<String, UsageRecord>> getStats() {
        return Collections.unmodifiableMap(stats);
    }

    /** 是否没有任何统计数据 */
    public boolean isEmpty() {
        return stats.isEmpty();
    }
}

