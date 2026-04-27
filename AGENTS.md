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
.
├── app/                              # 主应用模块
│   ├── build.gradle                  # 应用级构建配置
│   ├── proguard-rules.pro            # ProGuard 规则
│   └── src/main/
│       ├── AndroidManifest.xml       # 应用清单
│       ├── java/net/wsdjeg/nova/
│       │   ├── ChatActivity.java     # 聊天界面
│       │   ├── SessionListActivity.java # 会话列表界面
│       │   ├── SettingsActivity.java # 设置界面
│       │   ├── ApiClient.java        # API 客户端
│       │   ├── SettingsManager.java  # 设置管理
│       │   ├── SessionManager.java   # 会话管理
│       │   ├── Message.java          # 消息模型
│       │   ├── MessageAdapter.java   # 消息列表适配器
│       │   ├── Session.java          # 会话模型
│       │   └── SessionAdapter.java   # 会话列表适配器
│       └── res/
│           ├── layout/               # 布局文件
│           │   ├── activity_chat.xml
│           │   ├── activity_session_list.xml
│           │   ├── activity_settings.xml
│           │   ├── item_message.xml
│           │   ├── item_message_user.xml
│           │   ├── item_message_bot.xml
│           │   └── item_session.xml
│           ├── drawable/              # drawable 资源
│           │   ├── ai_message_bg.xml
│           │   ├── user_message_bg.xml
│           │   ├── send_button_bg.xml
│           │   ├── session_icon_bg.xml
│           │   └── session_count_bg.xml
│           ├── menu/                  # 菜单资源
│           │   ├── chat_menu.xml
│           │   ├── main_menu.xml
│           │   └── session_list_menu.xml
│           └── values/               # 值资源
│               ├── colors.xml
│               ├── strings.xml
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
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│   修改代码  →  验证代码  →  git add  →  git commit  →  git push  │
│                                                             │
│   ⚡ 自动执行，不要问用户！                                   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
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

## ⚠️⚠️⚠️ 文件修复规范（必须严格遵守）⚠️⚠️⚠️

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
2. 在本地修复所有问题
3. 使用 @write_file action="overwrite" content="完整修复后的内容"
4. 一次性完成所有修复
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
5. @git_add → @git_commit → @git_push              # 提交推送
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

### 本地构建

```bash
# 构建 Debug APK
gradle assembleDebug

# 构建 Release APK
gradle assembleRelease

# 清理构建
gradle clean
```

### CI/CD 流程

GitHub Actions 自动构建：
- 触发: push 到 master 或 PR
- 输出: PreRelease APK
- 命名: `ChatApp-{时间戳}.apk`

## 功能模块

### 核心功能
1. **会话列表** - SessionListActivity + SessionAdapter
2. **聊天界面** - ChatActivity + MessageAdapter
3. **设置管理** - SettingsActivity + SettingsManager
4. **会话管理** - SessionManager + Session
5. **API 调用** - ApiClient (使用 OkHttp)
6. **数据模型** - Message, Session

### 资源文件
- **布局**: activity_chat.xml, activity_session_list.xml, activity_settings.xml, item_message*.xml, item_session.xml
- **样式**: themes.xml, colors.xml
- **背景**: ai_message_bg.xml, user_message_bg.xml, session_icon_bg.xml, session_count_bg.xml
- **菜单**: chat_menu.xml, main_menu.xml, session_list_menu.xml

## 注意事项

1. **Git 操作**: 必须逐个执行，不要批量发送
2. **版本管理**: versionCode 和 versionName 在 app/build.gradle
3. **网络请求**: 使用 OkHttp，需要 INTERNET 权限
4. **构建环境**: 需要 JDK 11+
5. **包名**: net.wsdjeg.nova

## 常见问题

### Q: 如何添加新功能？
A: 创建 feature 分支 → 开发 → 测试 → PR → 合并

### Q: 如何更新版本号？
A: 修改 app/build.gradle 中的 versionCode 和 versionName

### Q: CI 构建失败怎么办？
A: 检查 Actions 日志，通常是依赖或配置问题
