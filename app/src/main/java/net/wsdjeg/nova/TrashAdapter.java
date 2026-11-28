package net.wsdjeg.nova;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 回收站列表适配器
 *
 * 每一行展示一个被删除的会话快照：
 * - 第一行：徽标组（账号标签 + 快照标记）+ 标题 + 恢复 / 永久删除按钮
 * - 第二行：消息预览（最多两行，空快照时隐藏）
 * - 第三行：删除时间 + 消息条数
 *
 * 徽标组容器（trashBadges）不是装饰：账号标签与快照标记都可能各自隐藏，
 * 而标题的起始约束锚定在该容器的 end。容器本身也 GONE 时，
 * ConstraintLayout 才会启用 goneMarginStart=0，让标题贴左；
 * 只要有一个徽标可见，容器就 VISIBLE，标题自然排在徽标之后。
 * 若把标题直接锚在某个可隐藏的 TextView 上，两个徽标一隐一显时
 * 标题就会压到仍可见的那个徽标上。
 *
 * 账号标签的取色逻辑与会话列表保持一致（账号自定义颜色优先，否则用全局设置）。
 */
public class TrashAdapter extends RecyclerView.Adapter<TrashAdapter.TrashViewHolder> {

    /**
     * 回收站条目操作回调
     */
    public interface OnTrashActionListener {
        /** 点击整行：展示条目详情与操作菜单 */
        void onEntryClick(TrashEntry entry);

        /** 恢复该快照 */
        void onRestore(TrashEntry entry);

        /** 永久删除该快照 */
        void onPurge(TrashEntry entry);
    }

    private final List<TrashEntry> entries = new ArrayList<>();
    private final OnTrashActionListener listener;
    private AccountManager accountManager;
    private SettingsManager settingsManager;

    public TrashAdapter(OnTrashActionListener listener) {
        this.listener = listener;
    }

    /**
     * 设置账号管理器（用于解析账号标签与颜色）
     */
    public void setAccountManager(AccountManager accountManager) {
        this.accountManager = accountManager;
    }

    /**
     * 设置设置管理器（用于读取全局标签颜色）
     */
    public void setSettingsManager(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    @NonNull
    @Override
    public TrashViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trash, parent, false);
        return new TrashViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrashViewHolder holder, int position) {
        TrashEntry entry = entries.get(position);

        // 账号标签：跨账号聚合时用于区分归属
        boolean accountVisible = false;
        String accountId = entry.getAccountId();
        if (!accountId.isEmpty() && accountManager != null) {
            Account account = accountManager.getAccount(accountId);
            if (account != null) {
                holder.textAccount.setText(account.getDisplayName());
                setAccountBadgeColor(holder.textAccount, account);
                accountVisible = true;
            }
        }
        holder.textAccount.setVisibility(accountVisible ? View.VISIBLE : View.GONE);

        // 快照标记：同一会话被删除多次时，区分 id.json / id~1.json
        // 直接显示文件名里的原始后缀（~1），与详情弹窗中的完整文件名对得上
        String snapshotLabel = entry.getSnapshotLabel();
        boolean snapshotVisible = !snapshotLabel.isEmpty();
        if (snapshotVisible) {
            holder.textSnapshot.setText(snapshotLabel);
        }
        holder.textSnapshot.setVisibility(snapshotVisible ? View.VISIBLE : View.GONE);

        // 徽标组：任一徽标可见才显示容器，否则整组 GONE，让标题贴左
        holder.badges.setVisibility(
                (accountVisible || snapshotVisible) ? View.VISIBLE : View.GONE);

        holder.textTitle.setText(entry.getDisplayTitle());

        // 预览：空快照（如从未发过消息的会话）不占位
        String preview = entry.getDisplayPreview();
        if (preview.isEmpty()) {
            holder.textPreview.setVisibility(View.GONE);
        } else {
            holder.textPreview.setVisibility(View.VISIBLE);
            holder.textPreview.setText(preview);
        }

        // 元信息：删除时间 + 消息条数
        String time = TimeUtils.formatTime(entry.getDeletedAtMillis());
        String meta;
        if (time.isEmpty()) {
            meta = holder.itemView.getContext()
                    .getString(R.string.trash_message_count, entry.getMessageCount());
        } else {
            meta = holder.itemView.getContext().getString(
                    R.string.trash_meta_format, time, entry.getMessageCount());
        }
        holder.textMeta.setText(meta);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (listener != null && pos >= 0 && pos < entries.size()) {
                listener.onEntryClick(entries.get(pos));
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (listener != null && pos >= 0 && pos < entries.size()) {
                listener.onEntryClick(entries.get(pos));
                return true;
            }
            return false;
        });

        holder.btnRestore.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (listener != null && pos >= 0 && pos < entries.size()) {
                listener.onRestore(entries.get(pos));
            }
        });

        holder.btnPurge.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (listener != null && pos >= 0 && pos < entries.size()) {
                listener.onPurge(entries.get(pos));
            }
        });
    }

    /**
     * 全量替换数据源
     */
    public void setData(List<TrashEntry> data) {
        entries.clear();
        if (data != null) {
            entries.addAll(data);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    /**
     * 为账号标签设置颜色
     * 优先级：账号自定义颜色 > 全局设置
     */
    private void setAccountBadgeColor(TextView textView, Account account) {
        if (settingsManager == null) {
            settingsManager = new SettingsManager(textView.getContext());
        }

        String colorHex = AccountManager.getAccountColor(account, settingsManager);
        int color;
        try {
            color = Color.parseColor(colorHex);
        } catch (Exception e) {
            color = Color.parseColor("#2196F3");
        }

        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(12f);
        drawable.setColor(color);

        textView.setBackground(drawable);
        textView.setTextColor(getContrastColor(color));
    }

    /**
     * 根据背景色获取对比色（黑色或白色）
     */
    private int getContrastColor(int backgroundColor) {
        double luminance = (0.299 * Color.red(backgroundColor)
                + 0.587 * Color.green(backgroundColor)
                + 0.114 * Color.blue(backgroundColor)) / 255;

        return luminance > 0.5 ? Color.parseColor("#212121") : Color.parseColor("#FFFFFF");
    }

    static class TrashViewHolder extends RecyclerView.ViewHolder {
        final View badges;
        final TextView textAccount;
        final TextView textSnapshot;
        final TextView textTitle;
        final TextView textPreview;
        final TextView textMeta;
        final ImageButton btnRestore;
        final ImageButton btnPurge;

        TrashViewHolder(@NonNull View itemView) {
            super(itemView);
            badges = itemView.findViewById(R.id.trashBadges);
            textAccount = itemView.findViewById(R.id.textTrashAccount);
            textSnapshot = itemView.findViewById(R.id.textTrashSnapshot);
            textTitle = itemView.findViewById(R.id.textTrashTitle);
            textPreview = itemView.findViewById(R.id.textTrashPreview);
            textMeta = itemView.findViewById(R.id.textTrashMeta);
            btnRestore = itemView.findViewById(R.id.btnTrashRestore);
            btnPurge = itemView.findViewById(R.id.btnTrashPurge);
        }
    }
}

