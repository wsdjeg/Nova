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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 服务端日志列表适配器
 *
 * 服务端日志行格式（logger.nvim）:
 * [ HH:MM:SS:mmm ] [ Level ] [ name ] message
 *
 * - 解析行内级别字段并着色（Error 红 / Warn 橙 / Info 绿 / Debug 蓝）
 * - 点击条目弹出详情（由 Activity 处理）
 */
public class ServerLogAdapter extends RecyclerView.Adapter<ServerLogAdapter.ViewHolder> {

    public interface OnLogClickListener {
        void onLogClick(String line);
    }

    private static final int COLOR_DEBUG = Color.parseColor("#64B5F6");
    private static final int COLOR_INFO = Color.parseColor("#81C784");
    private static final int COLOR_WARN = Color.parseColor("#FFB74D");
    private static final int COLOR_ERROR = Color.parseColor("#E57373");
    private static final int COLOR_DEFAULT = Color.parseColor("#D0D0D0");

    /** 匹配 "[ Info  ]" / "[ Error ]" 等级别字段 */
    private static final Pattern LEVEL_PATTERN =
            Pattern.compile("\\[\\s*(Trace|Debug|Info|Warn|Error|Fatal)\\s*\\]");

    private final List<String> items = new ArrayList<>();
    private final OnLogClickListener clickListener;

    public ServerLogAdapter(OnLogClickListener clickListener) {
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_server_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final String line = items.get(position);
        holder.tvLine.setText(line);
        holder.tvLine.setTextColor(levelColor(line));

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onLogClick(line);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * 全量替换并刷新
     *
     * @return 内容是否发生变化
     */
    public boolean setData(List<String> data) {
        if (sameContent(data)) {
            return false;
        }
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
        return true;
    }

    /** 拼接全部日志行（用于复制） */
    public String getAllText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    /** 简单比较首尾行与数量，避免自动刷新时无谓的整表刷新 */
    private boolean sameContent(List<String> data) {
        int n = data == null ? 0 : data.size();
        if (n != items.size()) {
            return false;
        }
        if (n == 0) {
            return true;
        }
        return items.get(0).equals(data.get(0))
                && items.get(n - 1).equals(data.get(n - 1));
    }

    /** 根据行内级别字段着色 */
    private static int levelColor(String line) {
        if (line == null) {
            return COLOR_DEFAULT;
        }
        Matcher m = LEVEL_PATTERN.matcher(line);
        if (m.find()) {
            String level = m.group(1);
            if ("Error".equals(level) || "Fatal".equals(level)) {
                return COLOR_ERROR;
            }
            if ("Warn".equals(level)) {
                return COLOR_WARN;
            }
            if ("Info".equals(level)) {
                return COLOR_INFO;
            }
            if ("Debug".equals(level) || "Trace".equals(level)) {
                return COLOR_DEBUG;
            }
        }
        return COLOR_DEFAULT;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvLine;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLine = itemView.findViewById(R.id.tv_server_log_line);
        }
    }
}

