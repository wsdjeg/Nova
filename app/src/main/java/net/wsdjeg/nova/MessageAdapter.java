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
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    private List<Message> messages;
    private Markwon markwon;
    private Context context;
    private static final int TYPE_USER = 1;
    private static final int TYPE_BOT = 2;

    public MessageAdapter(List<Message> messages, Context context) {
        this.messages = messages;
        this.context = context;
        this.markwon = Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .build();
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isUser() ? TYPE_USER : TYPE_BOT;
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
        Message message = messages.get(position);
        
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
        return messages.size();
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
