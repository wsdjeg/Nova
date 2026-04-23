package net.wsdjeg.nova;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Random;

/**
 * 会话列表适配器
 * 用于在 RecyclerView 中显示会话列表
 * 支持多账号聚合显示
 */
public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionViewHolder> {
    private List<Session> sessions;
    private OnSessionClickListener listener;
    private AccountManager accountManager;
    
    /**
     * 会话点击监听器接口
     */
    public interface OnSessionClickListener {
        void onSessionClick(Session session);
        void onSessionLongClick(Session session);
    }
    
    public SessionAdapter(List<Session> sessions, OnSessionClickListener listener) {
        this.sessions = sessions;
        this.listener = listener;
    }
    
    /**
     * 设置账号管理器（用于获取账号信息）
     */
    public void setAccountManager(AccountManager accountManager) {
        this.accountManager = accountManager;
    }
    
    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_session, parent, false);
        return new SessionViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        Session session = sessions.get(position);
        
        // 设置会话标题
        holder.textTitle.setText(session.getTitle());
        
        // 设置最后消息预览
        holder.textPreview.setText(session.getPreview());
        
        // 设置时间（使用 TimeUtils 统一格式化）
        holder.textTime.setText(session.getFormattedTime());
        
        // 设置未读消息数量
        int unreadCount = session.getUnreadCount();
        if (unreadCount > 0) {
            holder.textCount.setVisibility(View.VISIBLE);
            holder.textCount.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));
        } else {
            holder.textCount.setVisibility(View.GONE);
        }
        
        // 显示账号标签（如果有账号信息）
        String accountId = session.getAccountId();
        if (accountId != null && !accountId.isEmpty() && accountManager != null) {
            Account account = accountManager.getAccount(accountId);
            if (account != null) {
                holder.textAccount.setVisibility(View.VISIBLE);
                holder.textAccount.setText(account.getName());
                // 设置账号标签背景色
                setAccountBadgeColor(holder.textAccount, accountId);
            } else {
                holder.textAccount.setVisibility(View.GONE);
            }
        } else {
            holder.textAccount.setVisibility(View.GONE);
        }
        
        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSessionClick(session);
            }
        });
        
        // 长按事件
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onSessionLongClick(session);
                return true;
            }
            return false;
        });
    }
    
    /**
     * 为账号标签设置不同颜色
     */
    private void setAccountBadgeColor(TextView textView, String accountId) {
        // 根据 accountId 生成固定颜色
        int color = generateColorFromString(accountId);
        
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(12f);
        drawable.setColor(color);
        
        textView.setBackground(drawable);
    }
    
    /**
     * 根据字符串生成固定颜色
     */
    private int generateColorFromString(String str) {
        // 预定义的颜色列表
        int[] colors = {
            Color.parseColor("#FF6B6B"),  // 红色
            Color.parseColor("#4ECDC4"),  // 青色
            Color.parseColor("#45B7D1"),   // 蓝色
            Color.parseColor("#96CEB4"),   // 绿色
            Color.parseColor("#FFEAA7"),   // 黄色
            Color.parseColor("#DDA0DD"),   // 紫色
            Color.parseColor("#98D8C8"),   // 薄荷绿
            Color.parseColor("#F7DC6F"),   // 金色
        };
        
        // 根据字符串 hash 选择颜色
        int index = Math.abs(str.hashCode()) % colors.length;
        return colors[index];
    }
    
    @Override
    public int getItemCount() {
        return sessions.size();
    }
    
    /**
     * 更新会话列表
     */
    public void updateSessions(List<Session> newSessions) {
        this.sessions = newSessions;
        notifyDataSetChanged();
    }
    
    /**
     * 添加会话到列表开头
     */
    public void addSession(Session session) {
        sessions.add(0, session);
        notifyItemInserted(0);
    }
    
    /**
     * 更新指定会话
     */
    public void updateSession(int position, Session session) {
        if (position >= 0 && position < sessions.size()) {
            sessions.set(position, session);
            notifyItemChanged(position);
        }
    }
    
    /**
     * 移除会话
     */
    public void removeSession(int position) {
        if (position >= 0 && position < sessions.size()) {
            sessions.remove(position);
            notifyItemRemoved(position);
        }
    }
    
    static class SessionViewHolder extends RecyclerView.ViewHolder {
        TextView textTitle;
        TextView textPreview;
        TextView textTime;
        TextView textCount;
        TextView textAccount;  // 账号标签
        
        SessionViewHolder(View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.textSessionTitle);
            textPreview = itemView.findViewById(R.id.textSessionPreview);
            textTime = itemView.findViewById(R.id.textSessionTime);
            textCount = itemView.findViewById(R.id.textSessionCount);
            textAccount = itemView.findViewById(R.id.textSessionAccount);
        }
    }
}
