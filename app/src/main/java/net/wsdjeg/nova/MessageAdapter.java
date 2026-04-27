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
 * - messages 列表包含所有消息（包括 tool 类型和空 content）
 * - 显示时过滤掉不可显示的消息（tool 类型或空 content）
 * - 使用 visibleMessages 缓存可见消息列表
 */
public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    private List<Message> messages;
    private List<Message> visibleMessages;  // 过滤后的可见消息
    private Markwon markwon;
    private Context context;
    private static final int TYPE_USER = 1;
    private static final int TYPE_BOT = 2;

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
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return visibleMessages.get(position).isUser() ? TYPE_USER : TYPE_BOT;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_USER ? 
            R.layout.item_message_user : R.layout.item_message_bot;
        View view = LayoutInflater.from(parent.getContext())
            .inflate(layout, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = visibleMessages.get(position);
        
        // 使用 Markwon 渲染 Markdown
        markwon.setMarkdown(holder.messageText, message.getContent());
        
        // 使用统一的时间格式化工具
        String time = TimeUtils.formatTime(message.getTimestamp());
        holder.timeText.setText(time);
        
        // 设置长按复制功能
        holder.messageText.setOnLongClickListener(v -> {
            copyToClipboard(message.getContent());
            return true;
        });
        
        // 整个消息区域也可以长按复制
        holder.itemView.setOnLongClickListener(v -> {
            copyToClipboard(message.getContent());
            return true;
        });
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
     * 通知数据变化（重写以更新可见消息列表）
     */
    @Override
    public void notifyDataSetChanged() {
        updateVisibleMessages();
        super.notifyDataSetChanged();
    }
    
    /**
     * 通知项插入
     */
    @Override
    public void notifyItemRangeInserted(int positionStart, int itemCount) {
        updateVisibleMessages();
        super.notifyDataSetChanged();  // 简化处理，重新计算可见列表
    }
    
    /**
     * 通知项插入（单个）
     */
    @Override
    public void notifyItemInserted(int position) {
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

        MessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            timeText = itemView.findViewById(R.id.timeText);
        }
    }
}
