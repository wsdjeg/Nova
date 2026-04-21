package net.wsdjeg.nova;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.html.HtmlPlugin;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    private List<Message> messages;
    private Markwon markwon;
    private static final int TYPE_USER = 1;
    private static final int TYPE_BOT = 2;

    public MessageAdapter(List<Message> messages, android.content.Context context) {
        this.messages = messages;
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
        
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String time = sdf.format(new Date(message.getTimestamp()));
        holder.timeText.setText(time);
    }

    @Override
    public int getItemCount() {
        return messages.size();
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
