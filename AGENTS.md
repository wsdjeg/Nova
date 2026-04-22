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
## API 实现

Nova 作为 chat.nvim 的移动端客户端，需要实现 HTTP API 与服务端通信。

### API 文档

> **参考文档**: [HTTP API](https://raw.githubusercontent.com/wsdjeg/chat.nvim/refs/heads/master/docs/api/http.md)
> 
> 该文档会持续更新，实现时请以最新文档为准。

### API 端点

| Endpoint        | Method | Description                                              |
| --------------- | ------ | -------------------------------------------------------- |
| `/`             | POST   | 发送消息到指定会话                                        |
| `/sessions`     | GET    | 获取所有会话列表                                          |
| `/session/new`  | POST   | 创建新会话                                               |
| `/session/:id`  | DELETE | 删除指定会话                                              |
| `/session`      | GET    | 获取会话 HTML 预览 (需要 `id` 参数)                       |
| `/messages`     | GET    | 获取会话消息列表 (需要 `session` 参数)                     |

**Base URL**: `http://{host}:{port}/` (默认: `127.0.0.1:7777`)

### 认证

所有请求（除 GET /session 外）都需要 `X-API-Key` 请求头：

```java
Request request = new Request.Builder()
    .url(url)
    .addHeader("X-API-Key", apiKey)
    .build();
```

### 请求示例

#### 发送消息 (POST /)

```java
// 请求体
{
  "session": "session-id",
  "content": "消息内容"
}

// OkHttp 实现
String json = String.format("{\"session\":\"%s\",\"content\":\"%s\"}", 
    sessionId, content);
RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
Request request = new Request.Builder()
    .url(baseUrl + "/")
    .addHeader("X-API-Key", apiKey)
    .post(body)
    .build();
```

#### 获取会话列表 (GET /sessions)

```java
Request request = new Request.Builder()
    .url(baseUrl + "/sessions")
    .addHeader("X-API-Key", apiKey)
    .build();

// 响应示例
[
  {
    "id": "2024-01-15-10-30-00",
    "cwd": "/home/user/project",
    "provider": "openai",
    "model": "gpt-4o"
  }
]
```

#### 创建新会话 (POST /session/new)

```java
// 请求体 (可选)
{
  "cwd": "/path/to/project",
  "provider": "openai",
  "model": "gpt-4o"
}

// 响应 201
{
  "id": "2024-01-15-10-30-00"
}
```

#### 删除会话 (DELETE /session/:id)

```java
Request request = new Request.Builder()
    .url(baseUrl + "/session/" + sessionId)
    .addHeader("X-API-Key", apiKey)
    .delete()
    .build();

// 响应: 204 成功, 404 会话不存在, 409 会话进行中
```

#### 获取消息列表 (GET /messages)

```java
Request request = new Request.Builder()
    .url(baseUrl + "/messages?session=" + sessionId)
    .addHeader("X-API-Key", apiKey)
    .build();

// 响应示例
[
  {
    "role": "user",
    "content": "Hello!"
  },
  {
    "role": "assistant", 
    "content": "Hi there!"
  }
]
```

### 响应状态码

| 状态码 | 说明 |
| ------ | ---- |
| 200    | 成功 |
| 201    | 创建成功 |
| 204    | 成功 (无内容) |
| 400    | 请求格式错误 |
| 401    | 认证失败 (无效或缺失 API Key) |
| 404    | 资源不存在 |
| 409    | 冲突 (会话正在处理中) |

### 消息队列机制

> **重要**: 服务端使用消息队列系统处理请求：
> 1. 消息接收后立即进入队列
> 2. 队列每 5 秒检查一次
> 3. 当会话空闲时才处理消息
> 4. 如果会话忙碌，消息保留在队列中等待

这意味着发送消息后不会立即收到 AI 回复，客户端需要：
- 定期轮询 `/messages` 获取新消息
- 或者实现流式响应 (如支持)

## 常见问题

### Q: 如何添加新功能？
A: 创建 feature 分支 → 开发 → 测试 → PR → 合并

### Q: 如何更新版本号？
A: 修改 app/build.gradle 中的 versionCode 和 versionName

### Q: CI 构建失败怎么办？
A: 检查 Actions 日志，通常是依赖或配置问题
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
