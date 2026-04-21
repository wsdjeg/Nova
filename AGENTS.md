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

## 开发规范

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

### Git 操作顺序

1. **添加文件**: `@git_add path="file.java"`
2. **确认暂存**: `@git_status` 确认文件已暂存
3. **提交变更**: `@git_commit message="feat: 描述"`
4. **推送到远程**: `@git_push branch="master"`

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
