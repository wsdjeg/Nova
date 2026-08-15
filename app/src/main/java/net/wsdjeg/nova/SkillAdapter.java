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
 */
public class SkillAdapter extends RecyclerView.Adapter<SkillAdapter.SkillViewHolder> {
    private final List<Skill> allSkills = new ArrayList<>();
    private final List<Skill> filteredSkills = new ArrayList<>();
    private OnSkillClickListener listener;

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
    public SkillViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_skill, parent, false);
        return new SkillViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SkillViewHolder holder, int position) {
        Skill skill = filteredSkills.get(position);
        holder.textName.setText(skill.getCommand());
        holder.textDesc.setText(skill.description);
        holder.textDesc.setVisibility(skill.description.isEmpty() ? View.GONE : View.VISIBLE);
        holder.textBuiltin.setVisibility(skill.builtin ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSkillClick(skill);
            }
        });
    }

    @Override
    public int getItemCount() {
        return filteredSkills.size();
    }

    /**
     * 设置完整 skill 列表（来自服务端 GET /skills）
     * 并按当前关键词重新过滤
     */
    public void setSkills(List<Skill> skills) {
        allSkills.clear();
        if (skills != null) {
            allSkills.addAll(skills);
        }
        // notifyDataSetChanged() 前必须先更新过滤结果，避免位置错乱
        performFilter(lastKeyword);
    }

    private String lastKeyword = "";

    /**
     * 按关键词过滤（name/description 匹配，忽略大小写）
     */
    public void filter(String keyword) {
        lastKeyword = keyword != null ? keyword : "";
        performFilter(lastKeyword);
    }

    private void performFilter(String keyword) {
        filteredSkills.clear();
        for (Skill skill : allSkills) {
            if (skill.matches(keyword)) {
                filteredSkills.add(skill);
            }
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
}

