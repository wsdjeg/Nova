package net.wsdjeg.nova;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 用量统计列表适配器
 *
 * 数据源为 UsageStats.stats 中被筛选（provider/model）后的记录列表
 * 每个条目显示:
 * - 标题: 模型名（或 provider/model，取决于筛选条件）
 * - 请求次数
 * - token 细分: 总量 / 输入 / 输出
 * - 缓存命中的 tokens（> 0 时显示）
 */
public class UsageStatsAdapter extends RecyclerView.Adapter<UsageStatsAdapter.ViewHolder> {

    /** 单条筛选后的记录: 标题 + provider/model 原始键 + 数值 */
    public static class Entry {
        public final String title;
        public final String provider;
        public final String model;
        public final UsageRecord record;

        public Entry(String title, String provider, String model, UsageRecord record) {
            this.title = title;
            this.provider = provider;
            this.model = model;
            this.record = record;
        }
    }

    private final List<Entry> items = new ArrayList<>();

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_usage_record, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Entry entry = items.get(position);
        UsageRecord record = entry.record;

        holder.tvTitle.setText(entry.title);

        holder.tvRequests.setText(
                holder.itemView.getContext().getString(R.string.usage_requests_format, record.getRequests()));

        String total = Session.formatTokens(record.getTotalTokens());
        String prompt = Session.formatTokens(record.getPromptTokens());
        String completion = Session.formatTokens(record.getCompletionTokens());
        holder.tvTokens.setText(
                holder.itemView.getContext().getString(R.string.usage_tokens_format, total, prompt, completion));

        if (record.getCachedTokens() > 0) {
            String cached = Session.formatTokens(record.getCachedTokens());
            String cachePct = cacheHitPercent(record);
            holder.tvCached.setText(
                    holder.itemView.getContext().getString(R.string.usage_cached_format, cached, cachePct));
            holder.tvCached.setVisibility(View.VISIBLE);
        } else {
            holder.tvCached.setVisibility(View.GONE);
        }
    }

    /** 缓存命中率 = cached_tokens / prompt_tokens * 100（prompt 为 0 时返回 0） */
    private static String cacheHitPercent(UsageRecord record) {
        if (record.getPromptTokens() <= 0) {
            return "0%";
        }
        long pct = record.getCachedTokens() * 100 / record.getPromptTokens();
        return pct + "%";
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** 全量替换并刷新 */
    public void setData(List<Entry> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvRequests;
        final TextView tvTokens;
        final TextView tvCached;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_usage_title);
            tvRequests = itemView.findViewById(R.id.tv_usage_requests);
            tvTokens = itemView.findViewById(R.id.tv_usage_tokens);
            tvCached = itemView.findViewById(R.id.tv_usage_cached);
        }
    }
}

