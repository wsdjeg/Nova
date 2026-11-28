package net.wsdjeg.nova;

/**
 * 回收站条目模型
 *
 * 对应 chat.nvim HTTP API 中 GET /trash 返回数组的一项：
 * {
 *   "id": "2024-01-15-10-30-00",
 *   "file": "2024-01-15-10-30-00~1.json",
 *   "deleted_at": 1730000000,
 *   "title": "...",
 *   "preview": "...",
 *   "message_count": 12
 * }
 *
 * 字段说明：
 * - id：被删除会话的 ID（形如 YYYY-MM-DD-HH-MM-SS）；
 * - file：该快照在服务端回收站目录中的文件名。服务端刻意只返回文件名，
 *   不返回宿主机绝对路径，客户端也不应该拼路径；
 * - deleted_at：删除时间，Unix 时间戳（秒）；
 * - title / preview / message_count：删除时快照里的会话信息。
 *
 * accountId 不是服务端字段：回收站页面聚合多个账号的数据，
 * 由客户端在合并结果时标注归属，用于显示账号标签并决定恢复 / 删除
 * 该走哪一台服务器。
 *
 * 为什么 file 比 id 更重要：
 * 同一会话被删除多次时会留下多份快照（xxx.json、xxx~1.json …），
 * 这些条目的 id 完全相同，只有 file 能唯一寻址。因此恢复 / 永久删除
 * 统一使用 {@link #getTarget()} 返回的 file，而不是 id。
 */
public class TrashEntry {
    private final String id;
    private String file;
    private long deletedAt;
    private String title;
    private String preview;
    private int messageCount;
    private String accountId;

    public TrashEntry(String id) {
        this.id = id != null ? id : "";
        this.file = "";
        this.deletedAt = 0;
        this.title = "";
        this.preview = "";
        this.messageCount = 0;
        this.accountId = "";
    }

    public String getId() {
        return id;
    }

    /**
     * 归属账号 ID（客户端标注，非服务端字段）
     */
    public String getAccountId() {
        return accountId != null ? accountId : "";
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId != null ? accountId : "";
    }

    public String getFile() {
        return file != null ? file : "";
    }

    public void setFile(String file) {
        this.file = file != null ? file : "";
    }

    /**
     * 删除时间（Unix 秒，服务端原始值）
     */
    public long getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(long deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getTitle() {
        return title != null ? title : "";
    }

    public void setTitle(String title) {
        this.title = title != null ? title : "";
    }

    public String getPreview() {
        return preview != null ? preview : "";
    }

    public void setPreview(String preview) {
        this.preview = preview != null ? preview : "";
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    /**
     * 恢复 / 永久删除时使用的寻址参数。
     *
     * 优先使用服务端返回的文件名：同一 id 存在多份快照时只有文件名能区分。
     * file 缺失时退回会话 ID（服务端同样支持按 id 寻址）。
     */
    public String getTarget() {
        String name = getFile();
        return name.isEmpty() ? id : name;
    }

    /**
     * 列表展示标题：优先服务端返回的标题，缺失时退回会话 ID
     */
    public String getDisplayTitle() {
        String name = getTitle().trim();
        return name.isEmpty() ? id : name;
    }

    /**
     * 列表展示用的预览文本：把换行压成空格，避免挤占多行
     */
    public String getDisplayPreview() {
        return getPreview().replace("\r", " ").replace("\n", " ").trim();
    }

    /**
     * 删除时间的毫秒时间戳（统一供 TimeUtils 使用）
     *
     * 服务端字段是 Unix 秒；这里同时兼容毫秒输入，
     * 避免把 13 位时间戳再乘 1000 溢出成荒谬日期。
     */
    public long getDeletedAtMillis() {
        if (deletedAt <= 0) {
            return 0;
        }
        return deletedAt > 100000000000L ? deletedAt : deletedAt * 1000L;
    }

    /**
     * 重复快照的区分符（"~1"、"~2" …）。
     *
     * 同一会话被删除多次时，服务端文件名依次为 id.json、id~1.json、id~2.json。
     * 首份快照不需要额外标记，返回空字符串。这里刻意返回文件名里的原始后缀
     * （带波浪号），而不是解析成序号：它与 detail 弹窗里显示的完整文件名
     * 一一对应，用户能直接把界面上的标记和文件名对上，不会出现
     * 「第 1 份」到底指第一份还是第二份的歧义。
     *
     * 命名与预期不符（例如服务端改了后缀规则）时同样返回空字符串，
     * 完整文件名在详情弹窗里始终可见。
     */
    public String getSnapshotLabel() {
        String name = getFile();
        if (name.isEmpty() || !name.startsWith(id)) {
            return "";
        }
        if (name.equals(id + ".json")) {
            return "";
        }
        String suffix = name.substring(id.length());
        if (!suffix.endsWith(".json")) {
            return "";
        }
        suffix = suffix.substring(0, suffix.length() - ".json".length());
        if (!suffix.startsWith("~") || suffix.length() < 2) {
            return "";
        }
        for (int i = 1; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) {
                return "";
            }
        }
        return suffix;
    }
}

