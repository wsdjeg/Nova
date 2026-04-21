# Nova - Android AI Chat Assistant

<div align="center">

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)
![Java](https://img.shields.io/badge/Language-Java-orange.svg)

**A simple and elegant AI chat assistant for Android**

</div>

## 📱 项目简介

Nova 是一个轻量级的 Android AI 聊天助手应用，支持自定义 API 接口，可以连接各种 AI 服务（如 OpenAI、Claude 等）。

### ✨ 功能特性

- 🤖 **AI 对话** - 与 AI 助手进行自然对话
- ⚙️ **自定义配置** - 支持配置 API URL、端口和 API Key
- 💬 **消息列表** - 流畅的对话消息展示
- 🎨 **Material Design** - 现代化的 Material 设计风格
- 🔒 **本地存储** - 设置信息安全保存在本地
- 📋 **命令支持** - 支持斜杠命令管理会话

## 🛠️ 技术栈

| 技术 | 说明 |
|------|------|
| **语言** | Java |
| **最低 SDK** | Android 7.0 (API 24) |
| **目标 SDK** | Android 14 (API 34) |
| **UI 框架** | AppCompat + Material Design + ConstraintLayout |
| **列表组件** | RecyclerView |
| **网络请求** | HttpURLConnection |
| **JSON 解析** | org.json |

## 📦 依赖库

```gradle
// AndroidX
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'androidx.constraintlayout:constraintlayout:2.1.4'

// Material Design
implementation 'com.google.android.material:material:1.11.0'

// Network (可选)
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
```

## 🚀 快速开始

### 环境要求

- Android Studio Hedgehog 或更高版本
- JDK 8 或更高版本
- Android SDK (API 34)
- Gradle 8.0+

### 安装步骤

1. **克隆项目**
   ```bash
   git clone https://github.com/wsdjeg/Nova.git
   cd Nova
   ```

2. **在 Android Studio 中打开**
   - File → Open → 选择项目目录
   - 等待 Gradle 同步完成

3. **运行应用**
   - 连接 Android 设备或启动模拟器
   - 点击 Run 按钮 (Shift + F10)

### 配置 API

首次使用需要配置 AI 服务 API：

1. 打开应用，点击右上角「Settings」
2. 填写以下信息：
   - **URL**: chat.nvim HTTP 服务器地址（如 `127.0.0.1`）
   - **Port**: HTTP 端口（如 `7777`）
   - **API Key**: 认证密钥
   - **Session ID**: 会话 ID（格式：`YYYY-MM-DD-HH-MM-SS`）
3. 点击「Save」保存设置

## 💬 命令功能

Nova 支持在消息输入框中使用命令，以 `/` 开头：

| 命令 | 说明 | 示例 |
|------|------|------|
| `/help` | 显示帮助信息 | `/help` |
| `/sessions` | 列出所有活动会话 | `/sessions` |
| `/session <id>` | 获取会话预览 | `/session 2024-01-15-10-30-00` |
| `/set <id>` | 设置当前会话 ID | `/set 2024-01-15-10-30-00` |
| `/clear` | 清空消息列表 | `/clear` |

**使用示例：**

```
/help                           # 查看帮助
/sessions                       # 列出所有会话
/session 2024-01-15-10-30-00    # 查看会话详情
/set 2024-01-15-10-30-00        # 切换到该会话
/clear                          # 清空当前对话
```

> **提示**：命令不区分大小写，`/HELP` 和 `/help` 效果相同。

## 📂 项目结构

```
Nova/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/myandroidapp/
│   │   │   ├── MainActivity.java        # 主界面
│   │   │   ├── SettingsActivity.java    # 设置界面
│   │   │   ├── Message.java            # 消息数据模型
│   │   │   ├── MessageAdapter.java     # 消息列表适配器
│   │   │   ├── ApiClient.java          # API 客户端
│   │   │   └── SettingsManager.java    # 设置管理器
│   │   ├── res/
│   │   │   ├── layout/                 # 布局文件
│   │   │   ├── drawable/               # 图标和背景
│   │   │   ├── menu/                   # 菜单
│   │   │   └── values/                # 字符串和主题
│   │   └── AndroidManifest.xml
│   ├── build.gradle                   # 应用级构建配置
│   └── proguard-rules.pro             # 混淆规则
├── build.gradle                       # 项目级构建配置
├── settings.gradle                    # 项目设置
└── README.md
```

## 🔌 API 接口格式

Nova 连接到 [chat.nvim HTTP Server](https://nvim.chat/api/http/)。

### 配置说明

在设置界面配置以下信息：

| 字段 | 说明 | 示例 |
|------|------|------|
| **URL** | chat.nvim HTTP 服务器地址 | `127.0.0.1` |
| **Port** | HTTP 端口 | `7777` |
| **API Key** | 认证密钥 | `your-secret-key` |
| **Session ID** | 会话 ID | `2024-01-15-10-30-00` |

### 可用端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/` | POST | 发送消息到会话 |
| `/sessions` | GET | 获取所有活动会话 ID |
| `/session?id=...` | GET | 获取会话 HTML 预览 |

### 请求格式

**POST /** - 发送消息到指定会话

```json
{
  "session": "2024-01-15-10-30-00",
  "content": "你好，Nova！"
}
```

**请求头：**

```
Content-Type: application/json
X-API-Key: your-secret-key
```

### 响应格式

| 状态码 | 说明 |
|--------|------|
| **204** | 成功 - 消息已排队 |
| **401** | 未授权 - API Key 无效 |
| **400** | 请求错误 - 缺少必填字段 |
| **404** | 未找到 - 错误的端点或会话 |

> **提示**：Session ID 格式为 `YYYY-MM-DD-HH-MM-SS`，在 chat.nvim 中创建会话时自动生成。

## 🎯 后续计划

- [ ] 支持流式响应（SSE）
- [ ] 支持多会话管理
- [ ] 支持图片发送
- [ ] 支持语音输入
- [ ] 支持深色模式
- [ ] 支持消息复制和删除
- [ ] 支持对话导出

## 🤝 贡献指南

欢迎贡献代码、报告问题或提出建议！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'feat: add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

## 📄 许可证

本项目采用 [MIT License](LICENSE) 许可证。

## 📮 联系方式

如有问题或建议，欢迎：
- 提交 [Issue](https://github.com/wsdjeg/Nova/issues)
- 查看 [Wiki](https://github.com/wsdjeg/Nova/wiki)

---

<div align="center">

**Made with ❤️ by wsdjeg**

</div>
