package net.wsdjeg.nova;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

/**
 * 会话列表适配器
 * 用于在 RecyclerView 中显示会话列表
 * 支持多账号聚合显示
 * 
 * 布局：
 * 第一行：账号标签 + 标题
 * 第二行：provider | model
 * 第三行：cwd
 * 右侧：spinner 或 时间（垂直居中）
 */
public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionViewHolder> {
    private List<Session> sessions;
    private OnSessionClickListener listener;
    private AccountManager accountManager;
    private SettingsManager settingsManager;
    
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
    
    /**
     * 设置设置管理器（用于获取全局颜色设置）
     */
    public void setSettingsManager(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
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
        
        // 第一行：账号标签
        String accountId = session.getAccountId();
        if (accountId != null && !accountId.isEmpty() && accountManager != null) {
            Account account = accountManager.getAccount(accountId);
            if (account != null) {
                holder.textAccount.setVisibility(View.VISIBLE);
                holder.textAccount.setText(account.getDisplayName());
                setAccountBadgeColor(holder.textAccount, account);
            } else {
                holder.textAccount.setVisibility(View.GONE);
            }
        } else {
            holder.textAccount.setVisibility(View.GONE);
        }
        
        // 第一行：标题
        holder.textTitle.setText(session.getTitle());
        
        // 第二行：provider | model
        String provider = session.getProvider();
        String model = session.getModel();
        if (provider != null && !provider.isEmpty() && model != null && !model.isEmpty()) {
            holder.textProviderModel.setVisibility(View.VISIBLE);
            holder.textProviderModel.setText(provider + " | " + model);
        } else if (provider != null && !provider.isEmpty()) {
            holder.textProviderModel.setVisibility(View.VISIBLE);
            holder.textProviderModel.setText(provider);
        } else if (model != null && !model.isEmpty()) {
            holder.textProviderModel.setVisibility(View.VISIBLE);
            holder.textProviderModel.setText(model);
        } else {
            holder.textProviderModel.setVisibility(View.GONE);
        }
        
        // 第三行：cwd
        String cwd = session.getCwd();
        if (cwd != null && !cwd.isEmpty()) {
            holder.textCwd.setVisibility(View.VISIBLE);
            holder.textCwd.setText(cwd);
        } else {
            holder.textCwd.setVisibility(View.GONE);
        }
        
        // 右侧区域：spinner 或 时间（互斥）
        if (session.isInProgress()) {
            holder.progressSpinner.setVisibility(View.VISIBLE);
            holder.textTime.setVisibility(View.GONE);
        } else {
            holder.progressSpinner.setVisibility(View.GONE);
            holder.textTime.setVisibility(View.VISIBLE);
            holder.textTime.setText(session.getFormattedTime());
        }
        
        // 未读数量（右上角徽章）
        int unreadCount = session.getUnreadCount();
        if (unreadCount > 0) {
            holder.textCount.setVisibility(View.VISIBLE);
            holder.textCount.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));
        } else {
            holder.textCount.setVisibility(View.GONE);
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
     * 为账号标签设置颜色
     * 优先级：账号自定义颜色 > 全局设置
     */
    private void setAccountBadgeColor(TextView textView, Account account) {
        if (settingsManager == null) {
            settingsManager = new SettingsManager(textView.getContext());
        }
        
        String colorHex = AccountManager.getAccountColor(account, settingsManager);
        int color = Color.parseColor(colorHex);
        
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
        TextView textTime;
        TextView textCount;
        TextView textAccount;
        TextView textProviderModel;
        TextView textCwd;
        ProgressBar progressSpinner;
        FrameLayout rightArea;
        
        SessionViewHolder(View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.textSessionTitle);
            textTime = itemView.findViewById(R.id.textSessionTime);
            textCount = itemView.findViewById(R.id.textSessionCount);
            textAccount = itemView.findViewById(R.id.textAccount);
            textProviderModel = itemView.findViewById(R.id.textProviderModel);
            textCwd = itemView.findViewById(R.id.textSessionCwd);
            progressSpinner = itemView.findViewById(R.id.progressSpinner);
            rightArea = itemView.findViewById(R.id.rightArea);
        }
    }
}
