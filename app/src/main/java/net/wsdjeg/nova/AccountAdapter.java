package net.wsdjeg.nova;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 账号列表适配器
 * 用于 AccountManageActivity 显示账号列表
 */
public class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.ViewHolder> {

    private List<Account> accounts;
    private OnAccountActionListener listener;

    public interface OnAccountActionListener {
        void onAccountClick(Account account, int position);
        void onAccountEdit(Account account, int position);
        void onAccountDelete(Account account, int position);
        void onAccountSetActive(Account account, int position);
    }

    public AccountAdapter(List<Account> accounts) {
        this.accounts = accounts;
    }

    public void setOnAccountActionListener(OnAccountActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_account, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Account account = accounts.get(position);
        
        // 显示名称和URL
        holder.textName.setText(account.getDisplayName());
        holder.textUrl.setText(account.getUrl());
        
        // 激活状态
        if (account.isActive()) {
            holder.iconActive.setVisibility(View.VISIBLE);
            holder.textStatus.setText("当前激活");
            holder.textStatus.setTextColor(0xFF4CAF50);  // 绿色
            holder.itemView.setBackgroundColor(0xFFF5F5F5);  // 浅灰背景
        } else {
            holder.iconActive.setVisibility(View.GONE);
            // 显示最后使用时间
            long lastUsed = account.getLastUsedAt();
            long now = System.currentTimeMillis();
            long diff = now - lastUsed;
            
            if (diff < 60000) {  // 1分钟内
                holder.textStatus.setText("刚刚使用");
            } else if (diff < 3600000) {  // 1小时内
                holder.textStatus.setText((diff / 60000) + "分钟前");
            } else if (diff < 86400000) {  // 24小时内
                holder.textStatus.setText((diff / 3600000) + "小时前");
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault());
                holder.textStatus.setText("最后使用: " + sdf.format(new Date(lastUsed)));
            }
            holder.textStatus.setTextColor(0xFF9E9E9E);  // 灰色
            holder.itemView.setBackgroundColor(0xFFFFFFFF);  // 白色背景
        }
        
        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAccountClick(account, holder.getAdapterPosition());
            }
        });
        
        // 编辑按钮
        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAccountEdit(account, holder.getAdapterPosition());
            }
        });
        
        // 删除按钮
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAccountDelete(account, holder.getAdapterPosition());
            }
        });
        
        // 长按激活
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null && !account.isActive()) {
                listener.onAccountSetActive(account, holder.getAdapterPosition());
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return accounts.size();
    }

    /**
     * 更新数据
     */
    public void updateData(List<Account> newAccounts) {
        this.accounts = newAccounts;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView iconActive;
        TextView textName;
        TextView textUrl;
        TextView textStatus;
        ImageButton btnEdit;
        ImageButton btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            iconActive = itemView.findViewById(R.id.iconActive);
            textName = itemView.findViewById(R.id.textAccountName);
            textUrl = itemView.findViewById(R.id.textAccountUrl);
            textStatus = itemView.findViewById(R.id.textAccountStatus);
            btnEdit = itemView.findViewById(R.id.btnEditAccount);
            btnDelete = itemView.findViewById(R.id.btnDeleteAccount);
        }
    }
}
