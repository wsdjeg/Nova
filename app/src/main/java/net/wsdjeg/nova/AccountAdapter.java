package net.wsdjeg.nova;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 账号列表适配器
 * 用于 AccountManagerActivity 显示账号列表
 */
public class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.ViewHolder> {

    private Context context;
    private List<Account> accounts;
    private OnAccountClickListener listener;

    public interface OnAccountClickListener {
        void onAccountClick(Account account);
        void onAccountLongClick(Account account);
        void onEditClick(Account account);
        void onDeleteClick(Account account);
    }

    public AccountAdapter(Context context) {
        this.context = context;
    }

    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts;
        notifyDataSetChanged();
    }

    public void setOnAccountClickListener(OnAccountClickListener listener) {
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
        
        // 默认状态
        if (account.isActive()) {
            holder.statusDot.setBackgroundResource(R.drawable.session_icon_bg);
            holder.textStatusLabel.setText("默认账号");
            holder.textStatusLabel.setTextColor(0xFF4CAF50);  // 绿色
            holder.textStatusLabel.setVisibility(View.VISIBLE);
            holder.itemView.setBackgroundColor(0xFFF5F5F5);  // 浅灰背景
        } else {
            // 显示最后使用时间
            long lastUsed = account.getLastUsedAt();
            long now = System.currentTimeMillis();
            long diff = now - lastUsed;
            
            if (diff < 60000) {  // 1分钟内
                holder.textLastUsed.setText("刚刚使用");
            } else if (diff < 3600000) {  // 1小时内
                holder.textLastUsed.setText((diff / 60000) + "分钟前");
            } else if (diff < 86400000) {  // 24小时内
                holder.textLastUsed.setText((diff / 3600000) + "小时前");
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault());
                holder.textLastUsed.setText("最后使用: " + sdf.format(new Date(lastUsed)));
            }
            holder.textLastUsed.setTextColor(0xFF9E9E9E);  // 灰色
            holder.textStatusLabel.setVisibility(View.GONE);
            holder.itemView.setBackgroundColor(0xFFFFFFFF);  // 白色背景
        }
        
        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAccountClick(account);
            }
        });
        
        // 长按事件
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onAccountLongClick(account);
                return true;
            }
            return false;
        });
        
        // 编辑按钮
        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditClick(account);
            }
        });
        
        // 删除按钮
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(account);
            }
        });
    }

    @Override
    public int getItemCount() {
        return accounts == null ? 0 : accounts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View statusDot;
        TextView textName;
        TextView textUrl;
        TextView textLastUsed;
        TextView textStatusLabel;
        ImageButton btnDefault;
        ImageButton btnEdit;
        ImageButton btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            statusDot = itemView.findViewById(R.id.account_status_dot);
            textName = itemView.findViewById(R.id.account_name);
            textUrl = itemView.findViewById(R.id.account_url);
            textLastUsed = itemView.findViewById(R.id.account_last_used);
            textStatusLabel = itemView.findViewById(R.id.account_status_label);
            btnDefault = itemView.findViewById(R.id.account_default_button);
            btnEdit = itemView.findViewById(R.id.account_edit_button);
            btnDelete = itemView.findViewById(R.id.account_delete_button);
        }
    }
}
