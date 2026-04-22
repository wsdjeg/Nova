# Nova - Android AI Chat Assistant

<div align="center">

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)
![Java](https://img.shields.io/badge/Language-Java-orange.svg)

**A simple and elegant AI chat assistant for Android**

[Download APK](https://github.com/wsdjeg/Nova/releases/tag/prerelease) | [Report Bug](https://github.com/wsdjeg/Nova/issues) | [Request Feature](https://github.com/wsdjeg/Nova/issues)

</div>

## 📱 项目简介

Nova 是一个轻量级的 Android AI 聊天助手应用，作为 [chat.nvim](https://nvim.chat) 的移动端客户端，让你随时随地与 AI 助手进行对话。

### ✨ 功能特性

- 🤖 **AI 对话** - 与 AI 助手进行自然流畅的对话
- ⚙️ **灵活配置** - 支持自定义服务器地址、端口和 API Key
- 💬 **消息列表** - 流畅的对话消息展示，区分用户和 AI 消息
- 🎨 **Material Design** - 现代化的 Material 设计风格
- 🔒 **本地存储** - 设置信息安全保存在本地
- 📋 **命令支持** - 支持斜杠命令管理会话
- 🔄 **会话管理** - 查看和切换不同的对话会话
- 📝 **Markdown 渲染** - 支持代码高亮、表格、任务列表等

## 🛠️ 技术栈

| 技术 | 说明 |
|------|------|
| **语言** | Java |
| **最低 SDK** | Android 7.0 (API 24) |
| **目标 SDK** | Android 14 (API 34) |
| **UI 框架** | AppCompat + Material Design + ConstraintLayout |
| **列表组件** | RecyclerView |
| **网络请求** | OkHttp 4.12.0 |
| **Markdown** | Markwon 4.6.2 (含代码高亮、表格、任务列表等扩展) |
| **JSON 解析** | org.json |

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

### 直接安装

如果你只想体验应用，可以直接下载 APK：

1. 访问 [PreRelease](https://github.com/wsdjeg/Nova/releases/tag/prerelease) 页面
2. 下载最新的 `ChatApp.apk`
3. 在 Android 设备上安装

## 💬 使用指南

### 首次配置

1. 打开应用，点击右上角「Settings」图标
2. 配置服务器地址、端口和 API Key
3. 点击保存，返回主界面

### 界面交互

1. **会话列表** - 打开应用显示所有会话列表
2. **进入对话** - 点击会话进入对应的聊天界面
3. **新建会话** - 点击右下角按钮创建新会话
4. **删除会话** - 长按会话项，选择删除

## 📂 项目结构

```
Nova/
├── app/
│   ├── src/main/
│   │   ├── java/net/wsdjeg/nova/
│   │   │   ├── SessionListActivity.java  # 会话列表界面（启动入口）
│   │   │   ├── ChatActivity.java         # 聊天界面
│   │   │   ├── SettingsActivity.java     # 设置界面 - API 配置
│   │   │   ├── Session.java              # 会话数据模型
│   │   │   ├── SessionAdapter.java       # 会话列表适配器
│   │   │   ├── SessionManager.java       # 会话管理器 - 本地存储
│   │   │   ├── Message.java              # 消息数据模型
│   │   │   ├── MessageAdapter.java       # 消息列表适配器
│   │   │   ├── ApiClient.java            # API 客户端 - 网络请求
│   │   │   └── SettingsManager.java      # 设置管理器 - 本地存储
│   │   ├── res/
│   │   │   ├── layout/                   # 布局文件
│   │   │   ├── drawable/                 # 图标和背景
│   │   │   ├── menu/                     # 菜单
│   │   │   └── values/                   # 字符串和主题
│   │   └── AndroidManifest.xml
│   ├── build.gradle                      # 应用级构建配置
│   └── proguard-rules.pro                # 混淆规则
├── build.gradle                          # 项目级构建配置
├── settings.gradle                       # 项目设置
├── .github/workflows/android.yml         # CI/CD 配置
└── README.md
```

## 🔌 API 文档

Nova 连接到 [chat.nvim HTTP Server](https://nvim.chat/api/http/)。

## 🎯 后续计划

- [x] 支持多会话管理界面
- [ ] 支持流式响应（SSE）
- [ ] 支持图片发送
- [ ] 支持语音输入

## 🤝 贡献指南

欢迎贡献代码、报告问题或提出建议！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'feat: add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

## 📄 许可证

本项目采用 [GPL-3.0 License](LICENSE) 许可证。

---

<div align="center">

**Made with ❤️ by wsdjeg**

</div>
