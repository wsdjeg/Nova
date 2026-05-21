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
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.html.HtmlPlugin;
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
 * 
 * 并发安全修复：
 * - 使用临时列表构建新数据，避免清空后逐步添加导致的中间状态问题
 * - 所有数据访问方法都有边界检查
 * - post() 操作使用 itemView.post() 更安全
 */
public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = "MessageAdapter";
    private static final int COLLAPSED_LINES = 3;   // 折叠时显示行数
    private static final int EXPANDED_LINES = 10;   // 展开时显示行数
    private static final float LINE_HEIGHT_SP = 14f; // 每行高度(sp)
    
    private final List<Message> messages;
    private final List<Object> visibleItems;  // 可以是 Message 或 ToolCallItem
    private final Markwon markwon;
    private final Context context;
    private final int linkColor;
    
    // 记录展开状态的项
    private final Set<Integer> expandedToolCalls = new HashSet<>();
    private final Set<Integer> expandedToolResults = new HashSet<>();
    
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
        
        /**
         * 安全获取工具名称
         * 如果 function 为 null，返回 "<unknown>"
         */
        public String getToolName() {
            if (toolCall != null && toolCall.function != null && toolCall.function.name != null) {
                return toolCall.function.name;
            }
            return "<unknown>";
        }
        
        /**
         * 安全获取工具参数
         * 如果 function 为 null，返回 ""
         */
        public String getArguments() {
            if (toolCall != null && toolCall.function != null && toolCall.function.arguments != null) {
                return toolCall.function.arguments;
            }
            return "";
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
     * 更新可见项列表 - 使用临时列表避免并发问题
     */
    private void updateVisibleItems() {
        List<Object> newVisibleItems = new ArrayList<>();
        Log.d(TAG, "=== updateVisibleItems: " + messages.size() + " messages ===");
        
        for (int msgIdx = 0; msgIdx < messages.size(); msgIdx++) {
            Message msg = messages.get(msgIdx);
            
            Log.d(TAG, "  MSG[" + msgIdx + "]: role=" + msg.getRole() 
                + ", hasToolCalls=" + msg.hasToolCalls()
                + ", isTool=" + msg.isToolMessage()
                + ", contentLen=" + (msg.getContent() == null ? "null" : msg.getContent().length())
                + ", shouldDisplay=" + msg.shouldDisplay());
            
            if (!msg.shouldDisplay()) {
                Log.d(TAG, "    → SKIP: shouldDisplay=false");
                continue;
            }
            
            // tool 消息：直接添加
            if (msg.isToolMessage()) {
                Log.d(TAG, "    → ADD tool message, toolName=" + msg.getToolName());
                newVisibleItems.add(msg);
                continue;
            }
            
            // assistant 消息：检查是否有 tool_calls
            if (msg.isAssistant()) {
                if (msg.hasToolCalls()) {
                    Log.d(TAG, "    → assistant with tool_calls[" + msg.getToolCalls().size() + "]");
                    // 先显示 content（如果有且不为空）
                    if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
                        Log.d(TAG, "      → ADD content item");
                        newVisibleItems.add(msg);
                    }
                    
                    // 拆分 tool_calls 为单独的显示项
                    List<ApiClient.ToolCall> toolCalls = msg.getToolCalls();
                    for (int i = 0; i < toolCalls.size(); i++) {
                        ApiClient.ToolCall tc = toolCalls.get(i);
                        if (tc != null && tc.function != null) {
                            ToolCallItem item = new ToolCallItem(msg, tc, i);
                            String toolName = tc.function.name != null ? tc.function.name : "<unknown>";
                            Log.d(TAG, "      → ADD ToolCallItem: " + toolName);
                            newVisibleItems.add(item);
                        } else {
                            Log.d(TAG, "      → SKIP invalid ToolCall (function=null)");
                        }
                    }
                } else {
                    Log.d(TAG, "    → ADD assistant message (no tool_calls)");
                    newVisibleItems.add(msg);
                }
                continue;
            }
            
            // 其他消息：直接添加
            Log.d(TAG, "    → ADD " + (msg.isUser() ? "user" : "other") + " message");
            newVisibleItems.add(msg);
        }
        
        Log.d(TAG, "=== updateVisibleItems result: " + newVisibleItems.size() + " visible items ===");
        
        // 一次性替换，避免中间状态
        visibleItems.clear();
        visibleItems.addAll(newVisibleItems);
    }
    
    @Override
    public int getItemViewType(int position) {
        if (position < 0 || position >= visibleItems.size()) {
            Log.w(TAG, "getItemViewType: invalid position=" + position + ", size=" + visibleItems.size());
            return TYPE_BOT;
        }
        
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
        if (position < 0 || position >= visibleItems.size()) {
            Log.w(TAG, "onBindViewHolder: invalid position=" + position + ", size=" + visibleItems.size());
            return;
        }
        
        Object item = visibleItems.get(position);
        
        try {
            if (holder instanceof ToolCallViewHolder && item instanceof ToolCallItem) {
                bindToolCallViewHolder((ToolCallViewHolder) holder, (ToolCallItem) item);
            } else if (holder instanceof ToolResultViewHolder && item instanceof Message) {
                bindToolResultViewHolder((ToolResultViewHolder) holder, (Message) item);
            } else if (holder instanceof MessageViewHolder && item instanceof Message) {
                bindMessageViewHolder((MessageViewHolder) holder, (Message) item);
            }
        } catch (Exception e) {
            Log.e(TAG, "onBindViewHolder error at position " + position + ": " + e.getMessage(), e);
        }
    }
    
    private void bindMessageViewHolder(MessageViewHolder holder, Message message) {
        if (message == null || holder.messageText == null) return;
        
        try {
            if (message.isError()) {
                String error = message.getError();
                holder.messageText.setText(error != null ? error : "");
            } else {
                String content = message.getContent();
                if (content != null) {
                    markwon.setMarkdown(holder.messageText, content);
                    holder.messageText.setMovementMethod(LinkMovementMethod.getInstance());
                    holder.messageText.setLinkTextColor(linkColor);
                }
            }
            
            if (holder.timeText != null) {
                String time = TimeUtils.formatTime(message.getTimestamp());
                holder.timeText.setText(time);
            }
            
            final String copyText = message.isError() ? message.getError() : message.getContent();
            if (copyText != null) {
                holder.messageText.setOnLongClickListener(v -> {
                    copyToClipboard(copyText);
                    return true;
                });
                holder.itemView.setOnLongClickListener(v -> {
                    copyToClipboard(copyText);
                    return true;
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "bindMessageViewHolder error: " + e.getMessage(), e);
        }
    }
    
    /**
     * 计算内容区域的高度（dp）
     */
    private int calculateHeightPx(int lines) {
        float density = context.getResources().getDisplayMetrics().density;
        float lineSp = LINE_HEIGHT_SP + 4f;
        return (int) (lineSp * lines * density);
    }
    
    /**
     * 安全设置视图高度
     */
    private void safeSetHeight(NestedScrollView scrollView, int heightPx) {
        if (scrollView == null) return;
        ViewGroup.LayoutParams params = scrollView.getLayoutParams();
        if (params != null) {
            params.height = heightPx;
            scrollView.requestLayout();
        }
    }
    
    private void bindToolCallViewHolder(ToolCallViewHolder holder, ToolCallItem item) {
        if (item == null || holder.statusIcon == null) return;
        
        try {
            // 状态图标
            holder.statusIcon.setText("🔧");
            
            // 工具名称
            final String toolName = item.getToolName();
            if (holder.toolNameText != null) {
                holder.toolNameText.setText(toolName);
            }
            
            // 工具参数
            final String args = item.getArguments();
            if (holder.contentText != null) {
                if (args != null && !args.isEmpty()) {
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(args);
                        String formatted = formatJson(json);
                        holder.contentText.setText(formatted);
                    } catch (Exception e) {
                        holder.contentText.setText(args);
                    }
                    holder.contentText.setVisibility(View.VISIBLE);
                    
                    // 设置初始高度
                    final int collapsedHeightPx = calculateHeightPx(COLLAPSED_LINES);
                    safeSetHeight(holder.contentScrollV, collapsedHeightPx);
                    
                    // 使用 itemView.post() 更安全，避免 ViewHolder 被回收后 post 失败
                    holder.itemView.post(() -> {
                        try {
                            // 检查 ViewHolder 是否仍然有效
                            int currentPos = holder.getAdapterPosition();
                            if (currentPos == RecyclerView.NO_POSITION) return;
                            
                            // 再次检查 contentScrollV 和 contentText 是否存在
                            if (holder.contentScrollV == null || holder.contentText == null) return;
                            
                            int lineCount = holder.contentText.getLineCount();
                            if (lineCount > COLLAPSED_LINES && holder.expandHint != null) {
                                holder.expandHint.setVisibility(View.VISIBLE);
                                boolean isExpanded = expandedToolCalls.contains(currentPos);
                                int heightPx = isExpanded ? calculateHeightPx(EXPANDED_LINES) : collapsedHeightPx;
                                safeSetHeight(holder.contentScrollV, heightPx);
                                holder.expandHint.setText(isExpanded ? "收起 ▲" : "展开 ▼");
                            } else if (holder.expandHint != null) {
                                holder.expandHint.setVisibility(View.GONE);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "post runnable error: " + e.getMessage());
                        }
                    });
                } else {
                    holder.contentText.setVisibility(View.GONE);
                    if (holder.expandHint != null) {
                        holder.expandHint.setVisibility(View.GONE);
                    }
                }
            }
            
            // 时间戳
            if (holder.timeText != null) {
                String time = TimeUtils.formatTime(item.getCreated() * 1000);
                holder.timeText.setText(time);
            }
            
            // 点击展开/折叠
            if (holder.expandHint != null) {
                holder.expandHint.setOnClickListener(v -> {
                    int currentPos = holder.getAdapterPosition();
                    if (currentPos == RecyclerView.NO_POSITION) return;
                    
                    boolean isExpanded = expandedToolCalls.contains(currentPos);
                    if (isExpanded) {
                        expandedToolCalls.remove(currentPos);
                        safeSetHeight(holder.contentScrollV, calculateHeightPx(COLLAPSED_LINES));
                        holder.expandHint.setText("展开 ▼");
                    } else {
                        expandedToolCalls.add(currentPos);
                        safeSetHeight(holder.contentScrollV, calculateHeightPx(EXPANDED_LINES));
                        holder.expandHint.setText("收起 ▲");
                    }
                });
            }
            
            // 长按复制
            holder.itemView.setOnLongClickListener(v -> {
                String copyText = toolName + "\n" + (args != null ? args : "");
                copyToClipboard(copyText);
                return true;
            });
        } catch (Exception e) {
            Log.e(TAG, "bindToolCallViewHolder error: " + e.getMessage(), e);
        }
    }
    
    private void bindToolResultViewHolder(ToolResultViewHolder holder, Message message) {
        if (message == null || holder.statusIcon == null) return;
        
        try {
            String toolName = message.getToolName();
            if (toolName == null) toolName = "<unknown>";
            
            // 状态图标
            if (message.hasToolError()) {
                holder.statusIcon.setText("❌");
                if (holder.toolNameText != null) {
                    holder.toolNameText.setText(toolName + " (error)");
                    holder.toolNameText.setTextColor(ContextCompat.getColor(context, R.color.error));
                }
            } else {
                holder.statusIcon.setText("✅");
                if (holder.toolNameText != null) {
                    holder.toolNameText.setText(toolName);
                    holder.toolNameText.setTextColor(0xFF2196F3);
                }
            }
            
            // 内容
            final String content = message.getContent();
            if (holder.contentText != null) {
                if (content != null && !content.isEmpty()) {
                    holder.contentText.setText(content);
                    holder.contentText.setVisibility(View.VISIBLE);
                    
                    final int collapsedHeightPx = calculateHeightPx(COLLAPSED_LINES);
                    safeSetHeight(holder.contentScrollV, collapsedHeightPx);
                    
                    // 使用 itemView.post() 更安全
                    holder.itemView.post(() -> {
                        try {
                            int currentPos = holder.getAdapterPosition();
                            if (currentPos == RecyclerView.NO_POSITION) return;
                            
                            if (holder.contentScrollV == null || holder.contentText == null) return;
                            
                            int lineCount = holder.contentText.getLineCount();
                            if (lineCount > COLLAPSED_LINES && holder.expandHint != null) {
                                holder.expandHint.setVisibility(View.VISIBLE);
                                boolean isExpanded = expandedToolResults.contains(currentPos);
                                int heightPx = isExpanded ? calculateHeightPx(EXPANDED_LINES) : collapsedHeightPx;
                                safeSetHeight(holder.contentScrollV, heightPx);
                                holder.expandHint.setText(isExpanded ? "收起 ▲" : "展开 ▼");
                            } else if (holder.expandHint != null) {
                                holder.expandHint.setVisibility(View.GONE);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "post runnable error: " + e.getMessage());
                        }
                    });
                } else {
                    holder.contentText.setVisibility(View.GONE);
                    if (holder.expandHint != null) {
                        holder.expandHint.setVisibility(View.GONE);
                    }
                }
            }
            
            // 时间戳
            if (holder.timeText != null) {
                String time = TimeUtils.formatTime(message.getTimestamp());
                holder.timeText.setText(time);
            }
            
            // 点击展开/折叠
            if (holder.expandHint != null) {
                holder.expandHint.setOnClickListener(v -> {
                    int currentPos = holder.getAdapterPosition();
                    if (currentPos == RecyclerView.NO_POSITION) return;
                    
                    boolean isExpanded = expandedToolResults.contains(currentPos);
                    if (isExpanded) {
                        expandedToolResults.remove(currentPos);
                        safeSetHeight(holder.contentScrollV, calculateHeightPx(COLLAPSED_LINES));
                        holder.expandHint.setText("展开 ▼");
                    } else {
                        expandedToolResults.add(currentPos);
                        safeSetHeight(holder.contentScrollV, calculateHeightPx(EXPANDED_LINES));
                        holder.expandHint.setText("收起 ▲");
                    }
                });
            }
            
            // 长按复制
            holder.itemView.setOnLongClickListener(v -> {
                if (content != null && !content.isEmpty()) {
                    copyToClipboard(content);
                }
                return true;
            });
        } catch (Exception e) {
            Log.e(TAG, "bindToolResultViewHolder error: " + e.getMessage(), e);
        }
    }
    
    private String formatJson(org.json.JSONObject json) {
        StringBuilder sb = new StringBuilder();
        formatJsonRecursive(json, sb, 0);
        return sb.toString();
    }
    
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
    
    public void updateMessages(List<Message> newMessages) {
        this.messages.clear();
        this.messages.addAll(newMessages);
        updateVisibleItems();
        notifyDataSetChanged();
    }
    
    public void notifyDataSetChangedWithUpdate() {
        updateVisibleItems();
        notifyDataSetChanged();
    }
    
    public Message getLastVisibleMessage() {
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
        Log.w(TAG, "getVisibleItemAt: invalid position=" + position + ", size=" + visibleItems.size());
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
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && text != null) {
                ClipData clip = ClipData.newPlainText("消息内容", text);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "copyToClipboard error: " + e.getMessage(), e);
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
    
    static class ToolCallViewHolder extends RecyclerView.ViewHolder {
        TextView statusIcon;
        TextView toolNameText;
        TextView contentText;
        TextView timeText;
        TextView expandHint;
        NestedScrollView contentScrollV;

        ToolCallViewHolder(View itemView) {
            super(itemView);
            statusIcon = itemView.findViewById(R.id.statusIcon);
            toolNameText = itemView.findViewById(R.id.toolNameText);
            contentText = itemView.findViewById(R.id.argsText);
            timeText = itemView.findViewById(R.id.timeText);
            expandHint = itemView.findViewById(R.id.expandHint);
            contentScrollV = itemView.findViewById(R.id.contentScrollV);
        }
    }
    
    static class ToolResultViewHolder extends RecyclerView.ViewHolder {
        TextView statusIcon;
        TextView toolNameText;
        TextView timeText;
        TextView contentText;
        TextView expandHint;
        NestedScrollView contentScrollV;

        ToolResultViewHolder(View itemView) {
            super(itemView);
            statusIcon = itemView.findViewById(R.id.statusIcon);
            toolNameText = itemView.findViewById(R.id.toolNameText);
            timeText = itemView.findViewById(R.id.timeText);
            contentText = itemView.findViewById(R.id.contentText);
            expandHint = itemView.findViewById(R.id.expandHint);
            contentScrollV = itemView.findViewById(R.id.contentScrollV);
        }
    }
}
