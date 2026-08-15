package net.wsdjeg.nova;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志列表适配器
 *
 * - 每条日志独立一行，正文 TextView 开启 setTextIsSelectable，
 *   支持长按后拖动选择部分文本复制（局部复制）
 * - 点击条目弹出详情弹窗（由 Activity 处理）
 */
public class LogAdapter extends RecyclerView.Adapter<LogAdapter.ViewHolder> {

    public interface OnEntryClickListener {
        void onEntryClick(NovaLog.Entry entry);
    }

    private static final int COLOR_V = Color.parseColor("#9E9E9E");
    private static final int COLOR_D = Color.parseColor("#64B5F6");
    private static final int COLOR_I = Color.parseColor("#81C784");
    private static final int COLOR_W = Color.parseColor("#FFB74D");
    private static final int COLOR_E = Color.parseColor("#E57373");
    private static final int COLOR_BODY = Color.parseColor("#D0D0D0");

    private final List<NovaLog.Entry> items = new ArrayList<>();
    private final OnEntryClickListener clickListener;

    public LogAdapter(OnEntryClickListener clickListener) {
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final NovaLog.Entry entry = items.get(position);

        String header = NovaLog.formatShort(entry.time)
                + " " + NovaLog.levelLetter(entry.level)
                + "/" + entry.tag;
        holder.tvHeader.setText(header);
        holder.tvHeader.setTextColor(levelColor(entry.level));

        String body = entry.message;
        if (entry.stack != null && !entry.stack.isEmpty()) {
            body = body + "\n" + entry.stack;
        }
        holder.tvBody.setText(body);
        holder.tvBody.setTextColor(COLOR_BODY);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onEntryClick(entry);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** 当前展示的数据（只读引用，勿修改） */
    public List<NovaLog.Entry> getItems() {
        return items;
    }

    public NovaLog.Entry get(int position) {
        return items.get(position);
    }

    /** 全量替换并刷新 */
    public void setData(List<NovaLog.Entry> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    /** 追加一条（用于实时更新，避免整表刷新破坏文本选择状态） */
    public void append(NovaLog.Entry entry) {
        items.add(entry);
        notifyItemInserted(items.size() - 1);
    }

    private static int levelColor(int level) {
        switch (level) {
            case NovaLog.VERBOSE:
                return COLOR_V;
            case NovaLog.DEBUG:
                return COLOR_D;
            case NovaLog.INFO:
                return COLOR_I;
            case NovaLog.WARN:
                return COLOR_W;
            case NovaLog.ERROR:
            default:
                return COLOR_E;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvHeader;
        final TextView tvBody;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tv_log_header);
            tvBody = itemView.findViewById(R.id.tv_log_body);
            // 关键：允许行内长按选择文本，实现局部复制
            tvBody.setTextIsSelectable(true);
        }
    }
}

