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
 * Skills 自动补全列表适配器
 * 用于输入框输入 / 时弹出的 skill 列表
 * 
 * 布局：
 * /name [内置]  description
 * 
 * 状态行机制：
 * - 数据未加载/加载中/加载失败/无匹配时显示单条状态（status != null）
 * - 保证弹窗在任何情况下都有可见内容，避免空列表高度为 0
 */
public class SkillAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_SKILL = 0;
    private static final int TYPE_STATUS = 1;

    private final List<Skill> allSkills = new ArrayList<>();
    private final List<Skill> filteredSkills = new ArrayList<>();
    private OnSkillClickListener listener;

    /** 状态行文本；非 null 时列表只显示这一条 */
    private String status = null;

    /** 当前过滤关键词（/ 之后的部分） */
    private String lastKeyword = "";

    /**
     * Skill 点击监听器接口
     */
    public interface OnSkillClickListener {
        void onSkillClick(Skill skill);
    }

    public SkillAdapter(OnSkillClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_STATUS) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_skill_status, parent, false);
            return new StatusViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_skill, parent, false);
        return new SkillViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_STATUS) {
            ((StatusViewHolder) holder).textStatus.setText(status != null ? status : "");
            return;
        }
        Skill skill = filteredSkills.get(position);
        SkillViewHolder h = (SkillViewHolder) holder;
        h.textName.setText(skill.getCommand());
        h.textDesc.setText(skill.description);
        h.textDesc.setVisibility(skill.description.isEmpty() ? View.GONE : View.VISIBLE);
        h.textBuiltin.setVisibility(skill.builtin ? View.VISIBLE : View.GONE);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSkillClick(skill);
            }
        });
    }

    @Override
    public int getItemViewType(int position) {
        return status != null ? TYPE_STATUS : TYPE_SKILL;
    }

    @Override
    public int getItemCount() {
        return status != null ? 1 : filteredSkills.size();
    }

    /**
     * 设置完整 skill 列表（来自服务端 GET /skills）
     * 保留当前关键词重新过滤；是否显示状态行由外部 updateStatus 决定
     */
    public void setSkills(List<Skill> skills) {
        allSkills.clear();
        if (skills != null) {
            allSkills.addAll(skills);
        }
        refreshFilter();
    }

    /**
     * 按关键词过滤（name/description 匹配，忽略大小写）
     */
    public void filter(String keyword) {
        lastKeyword = keyword != null ? keyword : "";
        refreshFilter();
    }

    /**
     * 是否已有服务端数据（区分"未加载"和"加载了但为空"）
     */
    public boolean hasSkills() {
        return !allSkills.isEmpty();
    }

    /**
     * 当前过滤结果是否非空
     */
    public boolean hasMatch() {
        return !filteredSkills.isEmpty();
    }

    /**
     * 显示状态行（加载中/失败/无匹配）
     */
    public void setStatus(String message) {
        status = message;
        notifyDataSetChanged();
    }

    /**
     * 清除状态行，恢复显示列表
     */
    public void clearStatus() {
        if (status != null) {
            status = null;
            notifyDataSetChanged();
        }
    }

    private void refreshFilter() {
        filteredSkills.clear();
        for (Skill skill : allSkills) {
            if (skill.matches(lastKeyword)) {
                filteredSkills.add(skill);
            }
        }
        // 有匹配结果时清除状态行；为空时保留现有状态（由外部决定显示什么）
        if (!filteredSkills.isEmpty()) {
            status = null;
        }
        notifyDataSetChanged();
    }

    static class SkillViewHolder extends RecyclerView.ViewHolder {
        TextView textName;
        TextView textDesc;
        TextView textBuiltin;

        SkillViewHolder(View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.textSkillName);
            textDesc = itemView.findViewById(R.id.textSkillDesc);
            textBuiltin = itemView.findViewById(R.id.textSkillBuiltin);
        }
    }

    static class StatusViewHolder extends RecyclerView.ViewHolder {
        TextView textStatus;

        StatusViewHolder(View itemView) {
            super(itemView);
            textStatus = itemView.findViewById(R.id.textSkillStatus);
        }
    }
}

