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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.core.MarkwonTheme;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * 稳定标识 (StableKey):
 * 每个 visibleItem 有一个全局唯一稳定的字符串 key，用于：
 * 1. 在刷新后定位同一项（保持滚动位置）
 * 2. 配合 setHasStableIds(true) 让 RecyclerView 识别同一 ViewHolder
 *
 * Key 设计:
 * - tool_call:  "tc:" + toolCall.id
 * - tool 消息:  "tr:" + toolCallId（首选）或 "tr_idx:" + serverIndex
 * - pending:    "pending:" + created + ":" + contentHash
 * - error:      "err:" + serverIndex + ":" + created
 * - 普通消息:    "msg:" + serverIndex + (":content" 如果是 assistant 且有 tool_calls 拆分)
 */
public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = "MessageAdapter";
    private static final int COLLAPSED_LINES = 3;   // 折叠时显示行数
    private static final int EXPANDED_LINES = 10;   // 展开时显示行数
    private static final float LINE_HEIGHT_SP = 14f; // 每行高度(sp)
    
    private List<Message> messages;
    private List<Object> visibleItems;  // 可以是 Message 或 ToolCallItem
    private List<String> visibleKeys;   // 与 visibleItems 一一对应的 stableKey
    private Map<String, Integer> keyToPosition;  // O(1) 查找位置
    private Markwon markwon;
    private Context context;
    private int linkColor;
    
    // 记录展开状态的项 - 改用 stableKey 避免 position 变化时状态错乱
    private Set<String> expandedToolCalls = new HashSet<>();
    private Set<String> expandedToolResults = new HashSet<>();
    
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
        this.visibleKeys = new ArrayList<>();
        this.keyToPosition = new HashMap<>();
        this.context = context;
        this.linkColor = ContextCompat.getColor(context, R.color.primary);
        
        // 启用 stableIds，配合 getItemId() 减少不必要的 ViewHolder 重建
        setHasStableIds(true);
        
        // 配置 Markdown 标题大小：通过 AbstractMarkwonPlugin 配置主题
        this.markwon = Markwon.builder(context)
            .usePlugin(new AbstractMarkwonPlugin() {
                @Override
                public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                    builder.headingTextSizeMultipliers(new float[]{
                        1.6f,  // H1
                        1.4f,  // H2
                        1.25f, // H3
                        1.1f,  // H4
                        1.0f,  // H5
                        0.9f   // H6
                    });
                }
            })
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .build();
        updateVisibleItems();
    }

    /**
     * 计算 visibleItem 的稳定唯一 key
     *
     * @param item visibleItem (Message 或 ToolCallItem)
     * @param hasContentBeforeToolCalls 当 assistant 消息既有 content 又有 tool_calls 时，
     *                                   content 部分加 ":content" 后缀以与 toolCall 区分
     */
    private String computeStableKey(Object item) {
        if (item instanceof ToolCallItem) {
            ToolCallItem tc = (ToolCallItem) item;
            String tcId = tc.toolCall != null ? tc.toolCall.id : null;
            if (tcId != null && !tcId.isEmpty()) {
                return "tc:" + tcId;
            }
            // 兜底：用父消息的 serverIndex + 子序号
            int parentIdx = tc.parentMessage.getServerIndex();
            return "tc_idx:" + parentIdx + ":" + tc.index;
        }
        
        Message msg = (Message) item;
        
        if (msg.isPending()) {
            int hash = msg.getContent() != null ? msg.getContent().hashCode() : 0;
            return "pending:" + msg.getCreated() + ":" + hash;
        }
        
        if (msg.isError()) {
            return "err:" + msg.getServerIndex() + ":" + msg.getCreated();
        }
        
        if (msg.isToolMessage()) {
            String tcId = msg.getToolCallId();
            if (tcId != null && !tcId.isEmpty()) {
                return "tr:" + tcId;
            }
            return "tr_idx:" + msg.getServerIndex();
        }
        
        // 普通消息（user / assistant content / system）
        // 如果是 assistant 且有 tool_calls，content 部分加 :content 后缀区分
        if (msg.isAssistant() && msg.hasToolCalls()) {
            return "msg:" + msg.getServerIndex() + ":content";
        }
        return "msg:" + msg.getServerIndex();
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
        visibleKeys.clear();
        keyToPosition.clear();
        Log.d(TAG, "=== updateVisibleItems: " + messages.size() + " messages ===");
        
        for (int msgIdx = 0; msgIdx < messages.size(); msgIdx++) {
            Message msg = messages.get(msgIdx);
            if (!msg.shouldDisplay()) {
                Log.d(TAG, "  MSG[" + msgIdx + "]: SKIP shouldDisplay=false, role=" + msg.getRole());
                continue;
            }
            
            // tool 消息：直接添加
            if (msg.isToolMessage()) {
                Log.d(TAG, "  MSG[" + msgIdx + "]: ADD tool message, toolName=" + msg.getToolName());
                addVisibleItem(msg);
                continue;
            }
            
            // assistant 消息：检查是否有 tool_calls
            if (msg.isAssistant() && msg.hasToolCalls()) {
                Log.d(TAG, "  MSG[" + msgIdx + "]: assistant with tool_calls[" + msg.getToolCalls().size() + "]");
                // 先显示 content（如果有且不为空）
                if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
                    Log.d(TAG, "    → ADD content item");
                    addVisibleItem(msg);
                }
                
                // 拆分 tool_calls 为单独的显示项
                List<ApiClient.ToolCall> toolCalls = msg.getToolCalls();
                for (int i = 0; i < toolCalls.size(); i++) {
                    ToolCallItem item = new ToolCallItem(msg, toolCalls.get(i), i);
                    Log.d(TAG, "    → ADD ToolCallItem: " + toolCalls.get(i).function.name);
                    addVisibleItem(item);
                }
                continue;
            }
            
            // 其他消息：直接添加
            Log.d(TAG, "  MSG[" + msgIdx + "]: ADD " + (msg.isUser() ? "user" : "bot") + " message");
            addVisibleItem(msg);
        }
        
        Log.d(TAG, "=== updateVisibleItems result: " + visibleItems.size() + " visible items ===");
    }
    
    private void addVisibleItem(Object item) {
        int pos = visibleItems.size();
        String key = computeStableKey(item);
        // 处理极端情况：key 重复（理论上不应发生），追加 :pos 保唯一
        if (keyToPosition.containsKey(key)) {
            Log.w(TAG, "Duplicate stableKey detected: " + key + ", appending position");
            key = key + ":dup" + pos;
        }
        visibleItems.add(item);
        visibleKeys.add(key);
        keyToPosition.put(key, pos);
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

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= visibleKeys.size()) {
            return RecyclerView.NO_ID;
        }
        // 用 stableKey 的 hashCode 作为稳定 ID（hashCode 对 String 是确定性的）
        return visibleKeys.get(position).hashCode();
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
        String key = visibleKeys.get(position);
        
        if (holder instanceof ToolCallViewHolder) {
            ToolCallItem toolCallItem = (ToolCallItem) item;
            bindToolCallViewHolder((ToolCallViewHolder) holder, toolCallItem, key);
        } else if (holder instanceof ToolResultViewHolder) {
            Message msg = (Message) item;
            bindToolResultViewHolder((ToolResultViewHolder) holder, msg, key);
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
    
    /**
     * 计算内容区域的高度（dp）
     */
    private int calculateHeightPx(int lines) {
        // 每行高度(像素) = sp值 * density
        float density = context.getResources().getDisplayMetrics().density;
        float lineSp = LINE_HEIGHT_SP + 4f; // 11sp文字 + 内边距
        return (int) (lineSp * lines * density);
    }
    
    private void bindToolCallViewHolder(ToolCallViewHolder holder, ToolCallItem item, String stableKey) {
        // 状态图标
        holder.statusIcon.setText("🔧");
        
        // 工具名称 - 蓝色粗体
        holder.toolNameText.setText(item.getToolName());
        
        // 工具参数
        String args = item.getArguments();
        if (args != null && !args.isEmpty()) {
            try {
                // 尝试格式化 JSON
                org.json.JSONObject json = new org.json.JSONObject(args);
                String formatted = formatJson(json);
                holder.contentText.setText(formatted);
            } catch (Exception e) {
                // 不是有效 JSON，直接显示原始文本
                holder.contentText.setText(args);
            }
            holder.contentText.setVisibility(View.VISIBLE);
            
            // 检查内容是否超过折叠行数
            holder.contentText.post(() -> {
                int lineCount = holder.contentText.getLineCount();
                if (lineCount > COLLAPSED_LINES) {
                    holder.expandHint.setVisibility(View.VISIBLE);
                    // 检查展开状态
                    boolean isExpanded = expandedToolCalls.contains(stableKey);
                    updateContentHeight(holder, isExpanded);
                    holder.expandHint.setText(isExpanded ? "收起 ▲" : "展开 ▼");
                } else {
                    holder.expandHint.setVisibility(View.GONE);
                    // 内容不足折叠行数，设置实际高度
                    holder.contentScrollV.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                }
            });
        } else {
            holder.contentText.setVisibility(View.GONE);
            holder.expandHint.setVisibility(View.GONE);
        }
        
        // 时间戳
        String time = TimeUtils.formatTime(item.getCreated() * 1000);
        holder.timeText.setText(time);
        
        // 点击展开/折叠
        View.OnClickListener toggleListener = v -> {
            boolean isExpanded = expandedToolCalls.contains(stableKey);
            if (isExpanded) {
                expandedToolCalls.remove(stableKey);
                updateContentHeight(holder, false);
                holder.expandHint.setText("展开 ▼");
            } else {
                expandedToolCalls.add(stableKey);
                updateContentHeight(holder, true);
                holder.expandHint.setText("收起 ▲");
            }
        };
        holder.expandHint.setOnClickListener(toggleListener);
        
        // 长按复制参数
        holder.itemView.setOnLongClickListener(v -> {
            copyToClipboard(item.getToolName() + "\n" + args);
            return true;
        });
    }
    
    /**
     * 更新内容区域高度
     */
    private void updateContentHeight(ToolCallViewHolder holder, boolean isExpanded) {
        int heightPx = calculateHeightPx(isExpanded ? EXPANDED_LINES : COLLAPSED_LINES);
        holder.contentScrollV.getLayoutParams().height = heightPx;
        holder.contentScrollV.requestLayout();
    }
    
    /**
     * 更新内容区域高度 (ToolResult)
     */
    private void updateResultContentHeight(ToolResultViewHolder holder, boolean isExpanded) {
        int heightPx = calculateHeightPx(isExpanded ? EXPANDED_LINES : COLLAPSED_LINES);
        holder.contentScrollV.getLayoutParams().height = heightPx;
        holder.contentScrollV.requestLayout();
    }
    
    /**
     * 工具结果：简洁显示，默认只显示3行
     */
    private void bindToolResultViewHolder(ToolResultViewHolder holder, Message message, String stableKey) {
        String toolName = message.getToolName();
        
        // 检查是否有错误
        if (message.hasToolError()) {
            holder.statusIcon.setText("❌");
            holder.toolNameText.setText(toolName + " (error)");
            holder.toolNameText.setTextColor(ContextCompat.getColor(context, R.color.error));
        } else {
            holder.statusIcon.setText("✅");
            holder.toolNameText.setText(toolName);
            holder.toolNameText.setTextColor(0xFF2196F3); // 蓝色
        }
        
        // 显示内容，默认3行
        String content = message.getContent();
        if (content != null && !content.isEmpty()) {
            holder.contentText.setText(content);
            holder.contentText.setVisibility(View.VISIBLE);
            
            // 检查内容是否超过折叠行数
            holder.contentText.post(() -> {
                int lineCount = holder.contentText.getLineCount();
                if (lineCount > COLLAPSED_LINES) {
                    holder.expandHint.setVisibility(View.VISIBLE);
                    boolean isExpanded = expandedToolResults.contains(stableKey);
                    updateResultContentHeight(holder, isExpanded);
                    holder.expandHint.setText(isExpanded ? "收起 ▲" : "展开 ▼");
                } else {
                    holder.expandHint.setVisibility(View.GONE);
                    // 内容不足折叠行数，设置实际高度
                    holder.contentScrollV.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
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
            boolean isExpanded = expandedToolResults.contains(stableKey);
            if (isExpanded) {
                expandedToolResults.remove(stableKey);
                updateResultContentHeight(holder, false);
                holder.expandHint.setText("展开 ▼");
            } else {
                expandedToolResults.add(stableKey);
                updateResultContentHeight(holder, true);
                holder.expandHint.setText("收起 ▲");
            }
        };
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
    
    public void updateMessages(List<Message> newMessages) {
        this.messages.clear();
        this.messages.addAll(newMessages);
        updateVisibleItems();
        super.notifyDataSetChanged();
    }
    
    public void notifyDataSetChangedWithUpdate() {
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
    
    /**
     * 获取指定位置的 stableKey
     */
    public String getStableKeyAt(int position) {
        if (position >= 0 && position < visibleKeys.size()) {
            return visibleKeys.get(position);
        }
        return null;
    }
    
    /**
     * 通过 stableKey 查找位置（O(1)）
     * @return position 或 -1 如果未找到
     */
    public int findVisiblePositionByKey(String stableKey) {
        if (stableKey == null) return -1;
        Integer pos = keyToPosition.get(stableKey);
        return pos != null ? pos : -1;
    }
    
    /**
     * @deprecated 使用 findVisiblePositionByKey 代替。
     *             created 不唯一（同一 assistant 消息拆分为多个 visibleItem 共享一个 created）。
     */
    @Deprecated
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
     * 工具调用 ViewHolder
     */
    static class ToolCallViewHolder extends RecyclerView.ViewHolder {
        TextView statusIcon;
        TextView toolNameText;
        TextView contentText;
        TextView timeText;
        TextView expandHint;
        HorizontalScrollView contentScrollH;
        ScrollView contentScrollV;

        ToolCallViewHolder(View itemView) {
            super(itemView);
            statusIcon = itemView.findViewById(R.id.statusIcon);
            toolNameText = itemView.findViewById(R.id.toolNameText);
            contentText = itemView.findViewById(R.id.argsText);
            timeText = itemView.findViewById(R.id.timeText);
            expandHint = itemView.findViewById(R.id.expandHint);
            contentScrollH = itemView.findViewById(R.id.contentScrollH);
            contentScrollV = itemView.findViewById(R.id.contentScrollV);
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
        HorizontalScrollView contentScrollH;
        ScrollView contentScrollV;

        ToolResultViewHolder(View itemView) {
            super(itemView);
            statusIcon = itemView.findViewById(R.id.statusIcon);
            toolNameText = itemView.findViewById(R.id.toolNameText);
            timeText = itemView.findViewById(R.id.timeText);
            contentText = itemView.findViewById(R.id.contentText);
            expandHint = itemView.findViewById(R.id.expandHint);
            contentScrollH = itemView.findViewById(R.id.contentScrollH);
            contentScrollV = itemView.findViewById(R.id.contentScrollV);
        }
    }
}

