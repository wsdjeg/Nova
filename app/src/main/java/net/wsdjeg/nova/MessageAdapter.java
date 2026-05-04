package net.wsdjeg.nova;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.html.HtmlPlugin;
import java.util.ArrayList;
import java.util.List;

/**
 * 消息列表适配器
 * 
 * 核心逻辑：
 * - messages 列表包含所有消息（包括 tool 类型、error 类型和空 content）
 * - 显示时过滤掉不可显示的消息（tool 类型或空 content）
 * - 使用 visibleMessages 缓存可见消息列表
 * - 错误消息使用特殊样式显示（现代卡片样式）
 * 
 * 消息类型：
 * - TYPE_USER: 用户消息（蓝色背景，右侧）
 * - TYPE_BOT: AI 消息（灰色背景，左侧）
 * - TYPE_ERROR: 错误消息（浅红色背景，左侧强调线）
 * 
 * 位置恢复机制：
 * - 提供 getVisibleMessageAt() 获取指定可见位置的消息
 * - 提供 findVisiblePositionByCreated() 根据时间戳找到可见位置
 * - 用于下拉加载更多后的精确位置恢复
 */
public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    private List<Message> messages;
    private List<Message> visibleMessages;  // 过滤后的可见消息
    private Markwon markwon;
    private Context context;
    private static final int TYPE_USER = 1;
    private static final int TYPE_BOT = 2;
    private static final int TYPE_ERROR = 3;

    public MessageAdapter(List<Message> messages, Context context) {
        this.messages = messages;
        this.visibleMessages = new ArrayList<>();
        this.context = context;
        this.markwon = Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .build();
        updateVisibleMessages();
    }
    
    /**
     * 更新可见消息列表
     * 过滤掉 tool 类型和空 content 的消息
     */
    private void updateVisibleMessages() {
        visibleMessages.clear();
        for (Message msg : messages) {
            if (msg.shouldDisplay()) {
                visibleMessages.add(msg);
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        Message msg = visibleMessages.get(position);
        if (msg.isError()) {
            return TYPE_ERROR;
        }
        return msg.isUser() ? TYPE_USER : TYPE_BOT;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout;
        if (viewType == TYPE_ERROR) {
            layout = R.layout.item_message_error;
        } else if (viewType == TYPE_USER) {
            layout = R.layout.item_message_user;
        } else {
            layout = R.layout.item_message_bot;
        }
        View view = LayoutInflater.from(parent.getContext())
            .inflate(layout, parent, false);
        return new MessageViewHolder(view, viewType == TYPE_ERROR);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = visibleMessages.get(position);
        
        // 错误消息使用特殊样式
        if (message.isError()) {
            bindErrorMessage(holder, message);
        } else {
            // 使用 Markwon 渲染 Markdown
            markwon.setMarkdown(holder.messageText, message.getContent());
        }
        
        // 使用统一的时间格式化工具
        String time = TimeUtils.formatTime(message.getTimestamp());
        holder.timeText.setText(time);
        
        // 设置长按复制功能
        final String copyText = message.isError() ? message.getError() : message.getContent();
        holder.messageText.setOnLongClickListener(v -> {
            copyToClipboard(copyText);
            return true;
        });
        
        // 整个消息区域也可以长按复制
        holder.itemView.setOnLongClickListener(v -> {
            copyToClipboard(copyText);
            return true;
        });
    }
    
    /**
     * 绑定错误消息
     * 解析错误信息并设置标题和详情
     */
    private void bindErrorMessage(MessageViewHolder holder, Message message) {
        String error = message.getError();
        
        // 解析错误消息：提取标题和详情
        // 格式示例: "API Error (throttling): usage allocated quota exceeded..."
        String title = "";
        String detail = "";
        
        if (error != null && !error.isEmpty()) {
            // 查找冒号分隔符
            int colonIndex = error.indexOf(':');
            if (colonIndex > 0 && colonIndex < error.length() - 1) {
                title = error.substring(0, colonIndex).trim();
                detail = error.substring(colonIndex + 1).trim();
            } else {
                // 没有冒号，整条作为标题
                title = error;
                detail = "";
            }
        }
        
        // 设置标题（如果有）
        if (holder.errorTitle != null) {
            holder.errorTitle.setText(title);
            // 如果没有详情，隐藏详情文本
            if (detail.isEmpty()) {
                holder.messageText.setVisibility(View.GONE);
            } else {
                holder.messageText.setVisibility(View.VISIBLE);
                holder.messageText.setText(detail);
            }
        } else {
            // 兼容旧布局：没有 errorTitle，直接显示完整错误
            holder.messageText.setText(error);
        }
    }

    @Override
    public int getItemCount() {
        return visibleMessages.size();
    }
    
    /**
     * 获取可见消息数量
     */
    public int getVisibleMessageCount() {
        return visibleMessages.size();
    }
    
    /**
     * 刷新数据（更新可见消息列表并通知适配器）
     * 注意：不能重写 notifyDataSetChanged() 因为它是 final 方法
     */
    public void refreshData() {
        updateVisibleMessages();
        super.notifyDataSetChanged();
    }
    
    /**
     * 通知消息插入
     */
    public void notifyMessageInserted() {
        updateVisibleMessages();
        super.notifyDataSetChanged();
    }
    
    /**
     * 通知消息范围插入
     */
    public void notifyMessagesRangeInserted(int positionStart, int itemCount) {
        updateVisibleMessages();
        super.notifyDataSetChanged();
    }
    
    /**
     * 获取最后一条可见消息
     */
    public Message getLastVisibleMessage() {
        if (visibleMessages.isEmpty()) return null;
        return visibleMessages.get(visibleMessages.size() - 1);
    }
    
    /**
     * 获取指定可见位置的消息
     * 
     * 用于位置保存：记录第一条可见消息的时间戳作为锚点
     * 
     * @param position 可见消息列表中的位置（RecyclerView 显示的位置）
     * @return 该位置的消息对象，如果位置无效则返回 null
     */
    public Message getVisibleMessageAt(int position) {
        if (position >= 0 && position < visibleMessages.size()) {
            return visibleMessages.get(position);
        }
        return null;
    }
    
    /**
     * 根据消息时间戳找到在可见消息列表中的位置
     * 
     * 用于位置恢复：加载更多消息后，找到锚点消息的新位置
     * 
     * @param created 消息的服务端时间戳（秒）
     * @return 可见消息列表中的位置，如果未找到则返回 -1
     */
    public int findVisiblePositionByCreated(long created) {
        for (int i = 0; i < visibleMessages.size(); i++) {
            if (visibleMessages.get(i).getCreated() == created) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * 复制文本到剪贴板
     */
    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("消息内容", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        }
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        TextView timeText;
        TextView errorTitle;  // 错误消息标题（仅错误消息布局有）
        TextView errorIcon;   // 错误图标（仅错误消息布局有）

        MessageViewHolder(View itemView) {
            this(itemView, false);
        }
        
        MessageViewHolder(View itemView, boolean isError) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            timeText = itemView.findViewById(R.id.timeText);
            
            if (isError) {
                errorTitle = itemView.findViewById(R.id.errorTitle);
                errorIcon = itemView.findViewById(R.id.errorIcon);
            } else {
                errorTitle = null;
                errorIcon = null;
            }
        }
    }
}
