# 项目指南 - Nova Android Chat App

## 项目概述

Nova 是一个 Android AI 聊天助手应用，作为 [chat.nvim](https://nvim.chat) 的移动端客户端。

### 技术栈
- **语言**: Java
- **最低 SDK**: 24 (Android 7.0)
- **目标 SDK**: 34 (Android 14)
- **构建工具**: Gradle 8.0
- **依赖库**: 
  - AndroidX AppCompat 1.6.1
  - Material Design 1.11.0
  - ConstraintLayout 2.1.4
  - OkHttp 4.12.0 (网络请求)

## 目录结构

```
├── app/                              # 主应用模块
│   ├── build.gradle                  # 应用级构建配置
│   ├── proguard-rules.pro            # ProGuard 规则
│   └── src/main/
│       ├── AndroidManifest.xml       # 应用清单
│       ├── java/net/wsdjeg/nova/
│       │   ├── ChatActivity.java          # 聊天界面
│       │   ├── SessionListActivity.java   # 会话列表界面
│       │   ├── SessionSettingsActivity.java # 会话设置界面
│       │   ├── SettingsActivity.java      # 设置界面
│       │   ├── AccountManagerActivity.java # 账户管理界面
│       │   ├── AccountEditActivity.java   # 账户编辑界面
│       │   ├── AboutActivity.java         # 关于界面
│       │   ├── ApiClient.java             # API 客户端
│       │   ├── SettingsManager.java       # 设置管理
│       │   ├── SessionManager.java        # 会话管理
│       │   ├── AccountManager.java        # 账户管理
│       │   ├── Message.java               # 消息模型
│       │   ├── MessageAdapter.java        # 消息列表适配器
│       │   ├── Session.java               # 会话模型
│       │   ├── SessionAdapter.java        # 会话列表适配器
│       │   ├── Account.java               # 账户模型
│       │   ├── AccountAdapter.java        # 账户列表适配器
│       │   ├── TimeUtils.java             # 时间工具类
│       │   └── NovaApplication.java       # Application 类
│       └── res/
│           ├── layout/               # 布局文件
│           │   ├── activity_chat.xml
│           │   ├── activity_session_list.xml
│           │   ├── activity_session_settings.xml
│           │   ├── activity_settings.xml
│           │   ├── activity_account_manager.xml
│           │   ├── activity_account_edit.xml
│           │   ├── activity_about.xml
│           │   ├── item_message.xml
│           │   ├── item_message_user.xml
│           │   ├── item_message_bot.xml
│           │   ├── item_session.xml
│           │   ├── item_account.xml
│           │   └── dialog_account.xml
│           ├── drawable/              # drawable 资源
│           │   ├── ai_message_bg.xml
│           │   ├── user_message_bg.xml
│           │   ├── send_button_bg.xml
│           │   ├── btn_stop_bg.xml
│           │   ├── session_icon_bg.xml
│           │   ├── session_count_bg.xml
│           │   ├── account_tag_bg.xml
│           │   ├── provider_tag_bg.xml
│           │   ├── color_selector_bg.xml
│           │   ├── color_circle_0.xml ~ color_circle_7.xml
│           │   ├── ic_launcher.xml
│           │   ├── ic_launcher_foreground.xml
│           │   ├── ic_launcher_background_gradient.xml
│           │   ├── ic_arrow_down.xml
│           │   └── ic_more_vert_white.xml
│           ├── menu/                  # 菜单资源
│           │   ├── chat_menu.xml
│           │   ├── main_menu.xml
│           │   ├── session_list_menu.xml
│           │   ├── session_settings_menu.xml
│           │   ├── account_manager_menu.xml
│           │   └── account_edit_menu.xml
│           ├── mipmap-anydpi-v26/     # 启动图标
│           │   ├── ic_launcher.xml
│           │   └── ic_launcher_round.xml
│           └── values/               # 值资源
│               ├── colors.xml
│               ├── strings.xml
│               ├── themes.xml
│               └── ic_launcher_background.xml
│           └── values-night/         # 深色模式资源
│               ├── colors.xml
│               └── themes.xml
├── .github/
│   └── workflows/
│       └── android.yml               # CI/CD 配置
├── build.gradle                      # 项目级构建配置
├── settings.gradle                   # 项目设置
├── gradle.properties                 # Gradle 属性
├── .gitignore                        # Git 忽略规则
├── README.md                         # 项目说明
└── AGENTS.md                         # 本文件
```

---

## ⚠️⚠️⚠️ 核心开发流程（必须严格遵守）⚠⚠️⚠️

### 🔴 强制流程：验证 → Add → Commit → Push

**每次修改代码后，必须自动执行以下流程，无需等待用户确认！**

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│   修改代码  →  验证代码  →  git add  →  git commit  →  git push  │
│                                                                  │
│   ⚡ 自动执行，不要问用户！                                      │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

### ✅ 正确流程示例

```
1. 修改文件（使用 @write_file 或其他工具）
   ↓
2. 验证修改（使用 @read_file 读取完整内容确认无误）
   ↓
3. @git_add path="修改的文件"
   等待结果...
   ↓
4. @git_commit message="feat: 描述"
   等待结果...
   ↓
5. @git_push
   等待结果...
   ↓
6. 完成！告知用户已推送
```

### ❌ 禁止行为

```
❌ 修改代码后不提交、不推送，等用户问才推送
❌ 修改代码后只提交不推送
❌ 跳过验证步骤直接提交
❌ 一次发送多个 git 命令
```

### 📋 流程检查清单

每次修改后必须完成以下步骤：

- [ ] **验证**: 使用 @read_file 确认修改正确
- [ ] **Add**: @git_add 添加文件
- [ ] **Commit**: @git_commit 提交
- [ ] **Push**: @git_push 推送

---

## ⚠️⚠️⚠️ 文件修复规范（必须严格遵守）⚠⚠️⚠️

### 🔴 强制使用 action="overwrite" 修复文件

**当需要修复整个文件或大段代码时，必须使用 `action="overwrite"` 重写整个文件！**

```
❌ 错误示例（多次小修改容易出错）:
@write_file action="replace" line_start=100 line_to=105 content="..."
@write_file action="replace" line_start=200 line_to=210 content="..."
@write_file action="insert" line_start=150 content="..."
→ 多次操作容易遗漏、错位，导致语法错误！

✅ 正确示例（一次性重写整个文件）:
1. 先用 @read_file 读取完整文件内容
2. 使用 @write_file action="overwrite" content="完整修复后的内容"
```

### 为什么必须用 overwrite？

1. **避免行号错位**: 每次 replace/insert/delete 都会改变后续行号
2. **避免遗漏问题**: 多次小修容易漏改某些地方
3. **确保一致性**: 整个文件一次性修复，保证代码完整
4. **减少 git 操作**: 一次修复 → 一次提交 → 一次推送

### 修复文件的标准流程

```
1. @read_file filepath="问题文件"                    # 读取完整内容
   ↓
2. 在回复中分析问题并准备完整修复内容
   ↓
3. @write_file 
     filepath="问题文件" 
     action="overwrite" 
     content="完整修复后的文件内容"
   ↓
4. @read_file filepath="问题文件"                    # 验证修复结果
   ↓
5. @git_add → @git_commit → @git_push                # 提交推送
```

### ❌ 禁止的修复方式

```
❌ 使用 action="replace" 多次修复同一文件
❌ 使用 action="insert" 和 action="delete" 交替操作
❌ 在没有完整读取文件的情况下盲目修改
❌ 修复后不验证就直接提交
```

---

## 开发规范

### 代码修改规范

**⚠️ 极其重要：修改前必须验证存在性！**

在修改任何 class、函数、变量之前，**必须先检查它们是否存在**：

```
❌ 错误示例（不要瞎猜）:
// 直接调用不存在的方法
String ip = settingsManager.getIpAddress();  // 方法不存在！

✅ 正确示例（先验证再使用）:
1. 先用 @search_text 或 @read_file 检查源码
2. 确认 SettingsManager 类中有 getUrl() 方法
3. 然后使用: String ip = settingsManager.getUrl();
```

**验证方法：**
1. 使用 `@find_files` 找到目标文件
2. 使用 `@read_file` 或 `@search_text` 查看实际代码
3. 确认 class/function/variable 确实存在
4. 然后才能在代码中引用

**禁止行为：**
- 禁止凭记忆或猜测调用方法
- 禁止假设某个类有某个方法
- 禁止不看源码就写代码

### API 实现规范

Nova 作为 chat.nvim 的移动端客户端，需要实现 HTTP API 与服务端通信。

**⚠️ 禁止下载 API 文档到本地！**

```
❌ 禁止行为:
- 禁止使用 @fetch_web 的 output 参数保存文档到文件
- 禁止创建 api_doc.md 或任何本地 API 文档文件
- 禁止将 API 文档内容写入项目目录

✅ 正确做法:
- 使用 @fetch_web 直接在线查阅
- 查阅后直接根据文档内容实现代码
- 不保留任何文档副本
```

**获取最新 API 文档：**

当需要实现或修改 API 相关功能时，使用 `@fetch_web` 直接查阅在线文档：

```
@fetch_web url="https://raw.githubusercontent.com/wsdjeg/chat.nvim/refs/heads/master/docs/api/http.md"
```

该文档包含：
- API 端点定义
- 请求/响应格式
- 认证方式
- 错误处理
- 代码示例

**为什么不保存文档？**
- API 文档会持续更新，本地文档容易过时
- 在线查阅确保每次都获取最新版本
- 减少项目文件维护负担
- 避免"离线文档与实际 API 不一致"的问题

**注意：** 每次修改 API 相关代码时都应先查阅最新文档确认。

### Git 工作流

**⚠️ 重要：Git 工具必须逐个执行！**

使用 git 相关工具时，必须一个一个发送执行，**严禁**一次发送多个 git 工具调用！

```
❌ 错误示例（不要这样）:
@git_add path="file1.java"
@git_commit message="update"
@git_push

✅ 正确示例（必须这样）:
第一步: @git_add path="file1.java"
等待结果...
第二步: @git_commit message="update"
等待结果...
第三步: @git_push
等待结果...
```

### 提交信息规范

使用约定式提交：
- `feat:` 新功能
- `fix:` Bug 修复
- `refactor:` 代码重构
- `docs:` 文档更新
- `style:` 代码格式
- `test:` 测试相关
- `chore:` 构建/工具

### 分支策略

- `master`: 主分支，保持稳定
- 功能开发: 从 master 创建 feature 分支
- 修复: 从 master 创建 fix 分支

---

## 构建与运行

### CI/CD 流程

GitHub Actions 自动构建：
- 触发: push 到 master
- 输出: PreRelease APK
- 命名: `Nova-v{version}.apk`

## 功能模块

### 核心功能模块

1. **会话管理模块**
   - `SessionListActivity` - 会话列表主界面
   - `SessionSettingsActivity` - 会话设置界面
   - `SessionManager` - 会话数据管理
   - `Session` - 会话数据模型
   - `SessionAdapter` - 会话列表适配器

2. **聊天模块**
   - `ChatActivity` - 聊天主界面
   - `Message` - 消息数据模型
   - `MessageAdapter` - 消息列表适配器

3. **账户管理模块**
   - `AccountManagerActivity` - 账户列表管理界面
   - `AccountEditActivity` - 账户编辑界面
   - `AccountManager` - 账户数据管理
   - `Account` - 账户数据模型
   - `AccountAdapter` - 账户列表适配器

4. **设置模块**
   - `SettingsActivity` - 应用设置界面
   - `SettingsManager` - 设置数据管理

5. **API 通信模块**
   - `ApiClient` - HTTP API 客户端（使用 OkHttp）

6. **工具模块**
   - `TimeUtils` - 时间格式化工具
   - `NovaApplication` - Application 入口类

7. **关于模块**
   - `AboutActivity` - 关于页面

### 资源文件

**布局文件 (layout/)**:
- `activity_chat.xml` - 聊天界面布局
- `activity_session_list.xml` - 会话列表布局
- `activity_session_settings.xml` - 会话设置布局
- `activity_settings.xml` - 设置界面布局
- `activity_account_manager.xml` - 账户管理布局
- `activity_account_edit.xml` - 账户编辑布局
- `activity_about.xml` - 关于页面布局
- `item_message.xml`, `item_message_user.xml`, `item_message_bot.xml` - 消息项布局
- `item_session.xml` - 会话项布局
- `item_account.xml` - 账户项布局
- `dialog_account.xml` - 账户对话框布局

**菜单文件 (menu/)**:
- `chat_menu.xml` - 聊天界面菜单
- `main_menu.xml` - 主菜单
- `session_list_menu.xml` - 会话列表菜单
- `session_settings_menu.xml` - 会话设置菜单
- `account_manager_menu.xml` - 账户管理菜单
- `account_edit_menu.xml` - 账户编辑菜单

**Drawable 资源**:
- 消息背景: `ai_message_bg.xml`, `user_message_bg.xml`
- 按钮背景: `send_button_bg.xml`, `btn_stop_bg.xml`
- 图标背景: `session_icon_bg.xml`, `session_count_bg.xml`
- 标签背景: `account_tag_bg.xml`, `provider_tag_bg.xml`
- 颜色选择器: `color_selector_bg.xml`, `color_circle_0~7.xml`
- 图标: `ic_launcher*.xml`, `ic_arrow_down.xml`, `ic_more_vert_white.xml`

**主题与样式**:
- `themes.xml` - 应用主题（支持深色模式）
- `colors.xml` - 颜色定义（支持深色模式）
- `strings.xml` - 字符串资源

## 注意事项

1. **Git 操作**: 必须逐个执行，不要批量发送
2. **版本管理**: versionCode 和 versionName 在 app/build.gradle
3. **网络请求**: 使用 OkHttp，需要 INTERNET 权限
4. **构建环境**: 需要 JDK 11+
5. **包名**: net.wsdjeg.nova
