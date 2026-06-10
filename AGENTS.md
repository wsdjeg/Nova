# Nova Android Chat App - 项目指南

## 技术栈

| 项目 | 说明 |
|------|------|
| 语言 | Java |
| 最低 SDK | 24 (Android 7.0) |
| 目标 SDK | 34 (Android 14) |
| 构建工具 | Gradle 8.0 |
| 网络库 | OkHttp 4.12.0 |
| 包名 | net.wsdjeg.nova |

## 核心模块

```
app/src/main/java/net/wsdjeg/nova/
├── ChatActivity.java          # 聊天界面
├── SessionListActivity.java   # 会话列表
├── SessionSettingsActivity.java
├── SettingsActivity.java
├── AccountManagerActivity.java
├── AccountEditActivity.java
├── AboutActivity.java
├── ApiClient.java             # HTTP API 客户端
├── SettingsManager.java       # 设置管理
├── SessionManager.java        # 会话管理
├── AccountManager.java        # 账户管理
├── Message/Session/Account    # 数据模型
├── *Adapter.java              # RecyclerView 适配器
├── TimeUtils.java
└── NovaApplication.java
```

---

## 开发规范

### 文件修改

**必须使用 `action="overwrite"` 重写整个文件**

禁止操作：replace、insert、delete（会导致行号错位、代码损坏）

### 修改流程

```
修改 → 验证(读取完整文件) → git_add → git_commit → git_push
```

- 验证必须读取完整文件，不能只读部分
- Git 操作必须逐个执行，不能批量发送
- **修改后必须立即提交并推送到远程仓库，严禁只修改不推送！**
  - `git_add` 暂存变更
  - `git_commit` 提交变更
  - `git_push` 推送到远程
  - 以上三步必须全部执行完成，缺一不可
  - 无需等待用户确认，自动完成

### 代码修改原则

修改任何 class/function/variable 前，必须先用 `@read_file` 或 `@search_text` 检查源码确认存在。

禁止凭记忆或猜测调用方法。

### API 文档

查阅最新 API 文档：
```
@fetch_web url="https://raw.githubusercontent.com/wsdjeg/chat.nvim/refs/heads/master/docs/api/http.md"
```

禁止下载文档到本地，每次都在线查阅确保最新版本。

### 提交信息格式

```
feat: 新功能
fix: Bug 修复
refactor: 代码重构
docs: 文档更新
chore: 构建/工具
```

---

## 资源文件

### 布局 (res/layout/)
- activity_chat.xml、activity_session_list.xml、activity_session_settings.xml
- activity_settings.xml、activity_account_manager.xml、activity_account_edit.xml
- activity_about.xml、item_*.xml

### 菜单 (res/menu/)
- chat_menu.xml、main_menu.xml、session_list_menu.xml
- session_settings_menu.xml、account_manager_menu.xml

### Drawable
- ai_message_bg.xml、user_message_bg.xml、send_button_bg.xml
- ic_launcher*.xml、ic_arrow_down.xml、ic_more_vert_white.xml
- color_circle_0~7.xml

---

## 注意事项

1. versionCode/versionName 在 app/build.gradle
2. 网络请求需要 INTERNET 权限
3. 构建需要 JDK 11+
4. 支持深色模式（values-night/）

