package net.wsdjeg.nova;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

/**
 * 会话列表适配器
 * 用于在 RecyclerView 中显示会话列表
 */
public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionViewHolder> {
    private List<Session> sessions;
    private OnSessionClickListener listener;
    
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
        
        SessionViewHolder(View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.textSessionTitle);
            textPreview = itemView.findViewById(R.id.textSessionPreview);
            textTime = itemView.findViewById(R.id.textSessionTime);
            textCount = itemView.findViewById(R.id.textSessionCount);
        }
    }
}
