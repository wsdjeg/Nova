# Nova - chat.nvim Android Client

<div align="center">

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)
![Java](https://img.shields.io/badge/Language-Java-orange.svg)

**Mobile client for [chat.nvim](https://nvim.chat) - Neovim AI chat plugin**

[Download APK](https://github.com/wsdjeg/Nova/releases/tag/prerelease) | [Report Bug](https://github.com/wsdjeg/Nova/issues) | [Request Feature](https://github.com/wsdjeg/Nova/issues)

</div>

## 📱 项目简介

Nova 是 [chat.nvim](https://nvim.chat) 的 Android 移动端客户端，需要配合 Neovim + chat.nvim HTTP Server 使用。

> ⚠️ **注意**: 本应用不直接连接大模型 API，而是通过 chat.nvim HTTP Server 作为中间层。

### ✨ 功能特性

- 📱 **移动端访问** - 在手机上继续你的 Neovim AI 对话
- ⚙️ **服务器配置** - 配置 chat.nvim HTTP Server 地址即可连接
- 💬 **消息列表** - 流畅的对话消息展示，区分用户和 AI 消息
- 🎨 **Material Design** - 现代化的 Material 设计风格
- 🔒 **本地存储** - 设置信息和会话列表保存在本地
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

### 前置条件

1. **安装 Neovim + chat.nvim** - 安装 [chat.nvim](https://nvim.chat) Neovim 插件
2. **启动 HTTP Server** - 在 Neovim 中运行 HTTP Server（默认端口 8000）
3. **配置连接** - 在 Nova 中填写服务器地址即可使用

### 环境要求（开发）

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

### 直接安装 APK

1. 访问 [PreRelease](https://github.com/wsdjeg/Nova/releases/tag/prerelease) 页面
2. 下载最新的 `ChatApp.apk`
3. 在 Android 设备上安装

## 💬 使用指南

### 首次配置

1. 打开应用，点击右上角「Settings」图标
2. 配置服务器信息：
   - **服务器地址**: 运行 chat.nvim HTTP Server 的电脑 IP 地址
   - **端口**: HTTP Server 端口（默认 8000）
   - **API Key**: 你的 API Key（如果 chat.nvim 配置了验证）
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

Nova 通过 HTTP API 连接到 [chat.nvim HTTP Server](https://nvim.chat/api/http/)。

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
