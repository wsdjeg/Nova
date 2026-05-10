package net.wsdjeg.nova;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.core.MarkwonTheme;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 消息列表适配器
 * 
 * 消息类型：
 * - TYPE_USER: 用户消息（蓝色背景，右侧）
 * - TYPE_BOT: AI 消息（灰色背景，左侧）
 * - TYPE_ERROR: 错误消息（浅红色背景，居中，红色文字）
 * - TYPE_TOOL_CALL: 工具调用请求（assistant 消息中的 tool_calls）
 * - TYPE_TOOL_RESULT: 工具执行结果（role=tool 的消息，简洁显示）
 */
public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = "MessageAdapter";
    private static final int MAX_COLLAPSED_LINES = 3;
    
    private List<Message> messages;
    private List<Object> visibleItems;  // 可以是 Message 或 ToolCallItem
    private Markwon markwon;
    private Context context;
    private int linkColor;
    
    // 记录展开状态的项
    private Set<Integer> expandedToolCalls = new HashSet<>();
    private Set<Integer> expandedToolResults = new HashSet<>();
    
    private static final int TYPE_USER = 1;
    private static final int TYPE_BOT = 2;
    private static final int TYPE_ERROR = 3;
    private static final int TYPE_TOOL_CALL = 4;
    private static final int TYPE_TOOL_RESULT = 5;
    
    /**
     * 工具调用的显示项
     * 用于拆分 assistant 消息中的多个 tool_calls
     */
    public static class ToolCallItem {
        public Message parentMessage;
        public ApiClient.ToolCall toolCall;
        public int index;
        
        public ToolCallItem(Message parentMessage, ApiClient.ToolCall toolCall, int index) {
            this.parentMessage = parentMessage;
            this.toolCall = toolCall;
            this.index = index;
        }
        
        public long getCreated() {
            return parentMessage.getCreated();
        }
        
        public String getToolName() {
            return toolCall.function.name;
        }
        
        public String getArguments() {
            return toolCall.function.arguments;
        }
    }

    public MessageAdapter(List<Message> messages, Context context) {
        this.messages = messages;
        this.visibleItems = new ArrayList<>();
        this.context = context;
        this.linkColor = ContextCompat.getColor(context, R.color.primary);
        
        // 使用简化配置的 Markwon
        this.markwon = Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .build();
        updateVisibleItems();
    }
    
    /**
     * 更新可见项列表
     * 将消息拆分为多个显示项：
     * - 普通消息直接添加
     * - tool 消息直接添加（简洁显示）
     * - assistant 消息如果有 tool_calls，拆分为多个 ToolCallItem
     * - assistant 消息如果有 content，单独显示 content
     */
    private void updateVisibleItems() {
        visibleItems.clear();
        
        for (Message msg : messages) {
            if (!msg.shouldDisplay()) {
                continue;
            }
            
            // tool 消息：直接添加
            if (msg.isToolMessage()) {
                visibleItems.add(msg);
                continue;
            }
            
            // assistant 消息：检查是否有 tool_calls
            if (msg.isAssistant() && msg.hasToolCalls()) {
                // 先显示 content（如果有且不为空）
                if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
                    visibleItems.add(msg);
                }
                
                // 拆分 tool_calls 为单独的显示项
                List<ApiClient.ToolCall> toolCalls = msg.getToolCalls();
                for (int i = 0; i < toolCalls.size(); i++) {
                    ToolCallItem item = new ToolCallItem(msg, toolCalls.get(i), i);
                    visibleItems.add(item);
                }
                continue;
            }
            
            // 其他消息：直接添加
            visibleItems.add(msg);
        }
        
        Log.d(TAG, "updateVisibleItems: " + visibleItems.size() + " items from " + messages.size() + " messages");
    }

    @Override
    public int getItemViewType(int position) {
        Object item = visibleItems.get(position);
        
        if (item instanceof ToolCallItem) {
            return TYPE_TOOL_CALL;
        }
        
        if (item instanceof Message) {
            Message msg = (Message) item;
            if (msg.isError()) {
                return TYPE_ERROR;
            }
            if (msg.isToolMessage()) {
                return TYPE_TOOL_RESULT;
            }
            return msg.isUser() ? TYPE_USER : TYPE_BOT;
        }
        
        return TYPE_BOT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        
        switch (viewType) {
            case TYPE_ERROR:
                view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_error, parent, false);
                return new MessageViewHolder(view);
                
            case TYPE_USER:
                view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_user, parent, false);
                return new MessageViewHolder(view);
                
            case TYPE_TOOL_CALL:
                view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_tool_call, parent, false);
                return new ToolCallViewHolder(view);
                
            case TYPE_TOOL_RESULT:
                view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_tool_result, parent, false);
                return new ToolResultViewHolder(view);
                
            default: // TYPE_BOT
                view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_bot, parent, false);
                return new MessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = visibleItems.get(position);
        
        if (holder instanceof ToolCallViewHolder) {
            ToolCallItem toolCallItem = (ToolCallItem) item;
            bindToolCallViewHolder((ToolCallViewHolder) holder, toolCallItem, position);
        } else if (holder instanceof ToolResultViewHolder) {
            Message msg = (Message) item;
            bindToolResultViewHolder((ToolResultViewHolder) holder, msg, position);
        } else if (holder instanceof MessageViewHolder) {
            Message message = (Message) item;
            bindMessageViewHolder((MessageViewHolder) holder, message);
        }
    }
    
    private void bindMessageViewHolder(MessageViewHolder holder, Message message) {
        if (message.isError()) {
            holder.messageText.setText(message.getError());
        } else {
            markwon.setMarkdown(holder.messageText, message.getContent());
            holder.messageText.setMovementMethod(LinkMovementMethod.getInstance());
            holder.messageText.setLinkTextColor(linkColor);
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
    
    private void bindToolCallViewHolder(ToolCallViewHolder holder, ToolCallItem item, int position) {
        // 状态图标
        holder.statusIcon.setText("🔧");
        
        // 工具名称
        holder.toolNameText.setText(item.getToolName());
        // 工具参数
        String args = item.getArguments();
        if (args != null && !args.isEmpty()) {
            try {
                // 尝试格式化 JSON
                org.json.JSONObject json = new org.json.JSONObject(args);
                String formatted = formatJson(json);
                holder.argsText.setText(formatted);
            } catch (Exception e) {
                // 不是有效 JSON，直接显示原始文本
                holder.argsText.setText(args);
            }
            holder.argsText.setVisibility(View.VISIBLE);
            
            // 检查内容是否超过3行
            holder.argsText.post(() -> {
                int lineCount = holder.argsText.getLineCount();
                if (lineCount > MAX_COLLAPSED_LINES) {
                    holder.expandHint.setVisibility(View.VISIBLE);
                    // 检查展开状态
                    boolean isExpanded = expandedToolCalls.contains(position);
                    holder.argsText.setMaxLines(isExpanded ? Integer.MAX_VALUE : MAX_COLLAPSED_LINES);
                    holder.expandHint.setText(isExpanded ? "点击收起" : "点击展开");
                } else {
                    holder.expandHint.setVisibility(View.GONE);
                }
            });
        } else {
            holder.argsText.setVisibility(View.GONE);
            holder.expandHint.setVisibility(View.GONE);
        }
        
        // 时间戳
        String time = TimeUtils.formatTime(item.getCreated() * 1000);
        holder.timeText.setText(time);
        
        // 点击展开/折叠
        View.OnClickListener toggleListener = v -> {
            boolean isExpanded = expandedToolCalls.contains(position);
            if (isExpanded) {
                expandedToolCalls.remove(position);
                holder.argsText.setMaxLines(MAX_COLLAPSED_LINES);
                holder.expandHint.setText("点击展开");
            } else {
                expandedToolCalls.add(position);
                holder.argsText.setMaxLines(Integer.MAX_VALUE);
                holder.expandHint.setText("点击收起");
            }
        };
        holder.argsText.setOnClickListener(toggleListener);
        holder.expandHint.setOnClickListener(toggleListener);
        
        // 长按复制参数
        holder.itemView.setOnLongClickListener(v -> {
            copyToClipboard(item.getToolName() + "\n" + args);
            return true;
        });
    }
    
    /**
     * 工具结果：简洁显示，默认只显示3行
     */
    private void bindToolResultViewHolder(ToolResultViewHolder holder, Message message, int position) {
        String toolName = message.getToolName();
        
        // 检查是否有错误
        if (message.hasToolError()) {
            holder.statusIcon.setText("❌");
            holder.toolNameText.setText(toolName + " (error)");
            holder.toolNameText.setTextColor(ContextCompat.getColor(context, R.color.error));
        } else {
            holder.statusIcon.setText("✅");
            holder.toolNameText.setText(toolName);
            holder.toolNameText.setTextColor(ContextCompat.getColor(context, R.color.success));
        }
        
        // 显示内容，默认3行
        String content = message.getContent();
        if (content != null && !content.isEmpty()) {
            holder.contentText.setText(content);
            holder.contentText.setVisibility(View.VISIBLE);
            
            // 检查内容是否超过3行
            holder.contentText.post(() -> {
                int lineCount = holder.contentText.getLineCount();
                if (lineCount > MAX_COLLAPSED_LINES) {
                    holder.expandHint.setVisibility(View.VISIBLE);
                    boolean isExpanded = expandedToolResults.contains(position);
                    holder.contentText.setMaxLines(isExpanded ? Integer.MAX_VALUE : MAX_COLLAPSED_LINES);
                    holder.expandHint.setText(isExpanded ? "点击收起" : "点击展开");
                } else {
                    holder.expandHint.setVisibility(View.GONE);
                }
            });
        } else {
            holder.contentText.setVisibility(View.GONE);
            holder.expandHint.setVisibility(View.GONE);
        }
        
        // 时间戳
        String time = TimeUtils.formatTime(message.getTimestamp());
        holder.timeText.setText(time);
        
        // 点击展开/折叠
        View.OnClickListener toggleListener = v -> {
            boolean isExpanded = expandedToolResults.contains(position);
            if (isExpanded) {
                expandedToolResults.remove(position);
                holder.contentText.setMaxLines(MAX_COLLAPSED_LINES);
                holder.expandHint.setText("点击展开");
            } else {
                expandedToolResults.add(position);
                holder.contentText.setMaxLines(Integer.MAX_VALUE);
                holder.expandHint.setText("点击收起");
            }
        };
        holder.contentText.setOnClickListener(toggleListener);
        holder.expandHint.setOnClickListener(toggleListener);
        
        // 长按复制结果内容
        holder.itemView.setOnLongClickListener(v -> {
            if (content != null && !content.isEmpty()) {
                copyToClipboard(content);
            }
            return true;
        });
    }
    
    /**
     * 格式化 JSON 对象为可读字符串
     */
    private String formatJson(org.json.JSONObject json) {
        StringBuilder sb = new StringBuilder();
        formatJsonRecursive(json, sb, 0);
        return sb.toString();
    }
    
    /**
     * 递归格式化 JSON
     */
    private void formatJsonRecursive(org.json.JSONObject json, StringBuilder sb, int indent) {
        String prefix = "";
        for (int i = 0; i < indent; i++) {
            prefix += "  ";
        }
        
        sb.append("{\n");
        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            sb.append(prefix).append("  ").append(key).append(": ");
            try {
                Object value = json.get(key);
                if (value instanceof org.json.JSONObject) {
                    formatJsonRecursive((org.json.JSONObject) value, sb, indent + 1);
                } else if (value instanceof org.json.JSONArray) {
                    sb.append(formatJsonArray((org.json.JSONArray) value));
                } else if (value instanceof String) {
                    sb.append("\"").append(value).append("\"");
                } else {
                    sb.append(value.toString());
                }
            } catch (Exception e) {
                sb.append("?");
            }
            if (keys.hasNext()) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(prefix).append("}");
    }
    
    /**
     * 格式化 JSON 数组为可读字符串
     */
    private String formatJsonArray(org.json.JSONArray array) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < array.length(); i++) {
            sb.append("  ");
            try {
                Object item = array.get(i);
                if (item instanceof org.json.JSONObject) {
                    formatJsonRecursive((org.json.JSONObject) item, sb, 1);
                } else if (item instanceof org.json.JSONArray) {
                    sb.append(formatJsonArray((org.json.JSONArray) item));
                } else {
                    sb.append(item.toString());
                }
            } catch (Exception e) {
                sb.append("?");
            }
            if (i < array.length() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public int getItemCount() {
        return visibleItems.size();
    }
    
    public int getVisibleMessageCount() {
        return visibleItems.size();
    }
    
    public void refreshData() {
        updateVisibleItems();
        super.notifyDataSetChanged();
    }
    
    public void notifyMessageInserted() {
        updateVisibleItems();
        super.notifyDataSetChanged();
    }
    
    public void notifyMessagesRangeInserted(int positionStart, int itemCount) {
        updateVisibleItems();
        super.notifyDataSetChanged();
    }
    
    public Message getLastVisibleMessage() {
        // 从 messages 列表获取最后一条消息
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).shouldDisplay()) {
                return messages.get(i);
            }
        }
        return null;
    }
    
    public Object getVisibleItemAt(int position) {
        if (position >= 0 && position < visibleItems.size()) {
            return visibleItems.get(position);
        }
        return null;
    }
    
    public int findVisiblePositionByCreated(long created) {
        for (int i = 0; i < visibleItems.size(); i++) {
            Object item = visibleItems.get(i);
            if (item instanceof Message) {
                if (((Message) item).getCreated() == created) {
                    return i;
                }
            } else if (item instanceof ToolCallItem) {
                if (((ToolCallItem) item).getCreated() == created) {
                    return i;
                }
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

    /**
     * 普通消息 ViewHolder
     */
    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        TextView timeText;

        MessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            timeText = itemView.findViewById(R.id.timeText);
        }
    }
    
    /**
    /**
     * 工具调用 ViewHolder
     */
    static class ToolCallViewHolder extends RecyclerView.ViewHolder {
        TextView statusIcon;
        TextView toolNameText;
        TextView argsText;
        TextView timeText;
        TextView expandHint;

        ToolCallViewHolder(View itemView) {
            super(itemView);
            statusIcon = itemView.findViewById(R.id.statusIcon);
            toolNameText = itemView.findViewById(R.id.toolNameText);
            argsText = itemView.findViewById(R.id.argsText);
            timeText = itemView.findViewById(R.id.timeText);
            expandHint = itemView.findViewById(R.id.expandHint);
        }
    }
    
    /**
     * 工具结果 ViewHolder - 支持折叠显示
     */
    static class ToolResultViewHolder extends RecyclerView.ViewHolder {
        TextView statusIcon;
        TextView toolNameText;
        TextView timeText;
        TextView contentText;
        TextView expandHint;

        ToolResultViewHolder(View itemView) {
            super(itemView);
            statusIcon = itemView.findViewById(R.id.statusIcon);
            toolNameText = itemView.findViewById(R.id.toolNameText);
            timeText = itemView.findViewById(R.id.timeText);
            contentText = itemView.findViewById(R.id.contentText);
            expandHint = itemView.findViewById(R.id.expandHint);
        }
    }
}
