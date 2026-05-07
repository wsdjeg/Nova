package net.wsdjeg.nova;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import io.noties.markwon.Markwon;
import io.noties.markwon.core.CorePlugin;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.html.HtmlPlugin;
import java.util.ArrayList;
import java.util.List;

/**
 * 消息列表适配器
 * 
 * 消息类型：
 * - TYPE_USER: 用户消息（蓝色背景，右侧）
 * - TYPE_BOT: AI 消息（灰色背景，左侧）
 * - TYPE_ERROR: 错误消息（浅红色背景，居中，红色文字）
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
        
        // 配置 Markwon，自定义链接颜色
        this.markwon = Markwon.builder(context)
            .usePlugin(CorePlugin.create(plugin -> {
                plugin.defaultTheme(MarkwonTheme.builder(context)
                    .linkColor(ContextCompat.getColor(context, R.color.primary))
                    .build());
            }))
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .build();
        updateVisibleMessages();
    }
    
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
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = visibleMessages.get(position);
        
        // 错误消息直接显示文本，不使用 Markdown 渲染
        if (message.isError()) {
            holder.messageText.setText(message.getError());
        } else {
            markwon.setMarkdown(holder.messageText, message.getContent());
        }
        
        String time = TimeUtils.formatTime(message.getTimestamp());
        holder.timeText.setText(time);
        
        final String copyText = message.isError() ? message.getError() : message.getContent();
        holder.messageText.setOnLongClickListener(v -> {
            copyToClipboard(copyText);
            return true;
        });
        
        holder.itemView.setOnLongClickListener(v -> {
            copyToClipboard(copyText);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return visibleMessages.size();
    }
    
    public int getVisibleMessageCount() {
        return visibleMessages.size();
    }
    
    public void refreshData() {
        updateVisibleMessages();
        super.notifyDataSetChanged();
    }
    
    public void notifyMessageInserted() {
        updateVisibleMessages();
        super.notifyDataSetChanged();
    }
    
    public void notifyMessagesRangeInserted(int positionStart, int itemCount) {
        updateVisibleMessages();
        super.notifyDataSetChanged();
    }
    
    public Message getLastVisibleMessage() {
        if (visibleMessages.isEmpty()) return null;
        return visibleMessages.get(visibleMessages.size() - 1);
    }
    
    public Message getVisibleMessageAt(int position) {
        if (position >= 0 && position < visibleMessages.size()) {
            return visibleMessages.get(position);
        }
        return null;
    }
    
    public int findVisiblePositionByCreated(long created) {
        for (int i = 0; i < visibleMessages.size(); i++) {
            if (visibleMessages.get(i).getCreated() == created) {
                return i;
            }
        }
        return -1;
    }
    
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

