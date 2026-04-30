# Nova - chat.nvim Android Client

<div align="center">

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)
![Java](https://img.shields.io/badge/Language-Java-orange.svg)

**Mobile client for [chat.nvim](https://nvim.chat) - Neovim AI chat plugin**

[Download APK](https://github.com/wsdjeg/Nova/releases/tag/prerelease) | [Report Bug](https://github.com/wsdjeg/Nova/issues) | [Request Feature](https://github.com/wsdjeg/Nova/issues)

</div>

## 📱 项目简介

> ⚠️ **注意**: 本应用不直接连接大模型 API，而是通过 chat.nvim HTTP Server 作为中间层。

## 📸 应用截图

<div align="center">

| 会话列表 | 聊天界面 | 设置界面 |
|:--------:|:--------:|:--------:|
| <img src="https://github.com/user-attachments/assets/e6c4910b-28f6-4493-b16b-1b099decc983" width="250"/> | <img src="https://github.com/user-attachments/assets/016ebdda-13ae-4468-b865-b8aaed9a3abb" width="250"/> | <img src="https://github.com/user-attachments/assets/fc3d35da-f85c-436c-9c66-6f35a7243661" width="250"/> |

</div>

### ✨ 功能特性

- 👥 **多账号管理** - 支持添加、编辑、删除、设置默认账号，每个账号可独立配置颜色标签
- 🔄 **会话管理** - 查看、切换、创建、删除不同的对话会话
- 📱 **移动端访问** - 在手机上继续你的 Neovim AI 对话
- ⚙️ **服务器配置** - 配置 chat.nvim HTTP Server 地址即可连接
- 💬 **消息列表** - 流畅的对话消息展示，区分用户和 AI 消息
- 🎨 **Material Design** - 现代化的 Material 设计风格
- 🌙 **深色模式** - 支持系统自动、浅色、深色三种主题模式
- 🔒 **本地存储** - 设置信息和会话列表保存在本地
- 📝 **Markdown 渲染** - 支持代码高亮、表格、任务列表等
- 🎯 **会话设置** - 支持修改会话的 provider 和 model
- 🔍 **连接测试** - 支持测试服务器连接状态

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
2. 下载最新的 `Nova-v{version}.apk`
3. 在 Android 设备上安装

## 💬 使用指南

### 账号管理

1. **添加账号**
   - 打开应用，点击右上角菜单 → 「账号管理」
   - 点击右下角 FAB 按钮添加新账号
   - 填写服务器地址、端口、API Key（可选）
   - 选择颜色标签（可选）
   - 点击「测试连接」验证配置
   - 保存账号

2. **切换默认账号**
   - 在账号列表中点击要设为默认的账号
   - 或长按账号 → 「设为默认账号」

3. **编辑/删除账号**
   - 长按账号 → 选择「编辑」或「删除」

### 会话管理

1. **查看会话列表**
   - 打开应用显示所有会话列表
   - 会话按最后消息时间排序
   - 显示会话标题、预览、消息数量

2. **创建新会话**
   - 点击右下角 FAB 按钮创建新会话
   - 可选填 provider 和 model

3. **会话设置**
   - 在聊天界面点击右上角菜单 → 「会话设置」
   - 查看/修改当前会话的 provider 和 model
   - 从服务器获取可用的 provider/model 列表

4. **删除会话**
   - 长按会话项，选择删除

### 聊天交互

1. **发送消息** - 在输入框输入消息，点击发送按钮
2. **查看消息** - 消息列表自动滚动到最新消息
3. **停止响应** - AI 响应过程中可点击停止按钮中断
4. **重试** - 支持重试失败的请求

### 应用设置

1. **主题设置** - 支持系统自动、浅色、深色三种模式
2. **颜色标签** - 设置账号标签颜色（自动分配或手动选择）
3. **默认配置** - 设置默认的 provider 和 model

## 📂 项目结构

```
Nova/
├── app/
│   ├── src/main/
│   │   ├── java/net/wsdjeg/nova/
│   │   │   ├── Activities/
│   │   │   │   ├── SessionListActivity.java    # 会话列表界面（启动入口）
│   │   │   │   ├── ChatActivity.java           # 聊天界面
│   │   │   │   ├── SettingsActivity.java       # 应用设置界面
│   │   │   │   ├── SessionSettingsActivity.java # 会话设置界面
│   │   │   │   ├── AccountManagerActivity.java  # 账号管理界面
│   │   │   │   ├── AccountEditActivity.java     # 账号编辑界面
│   │   │   │   └── AboutActivity.java           # 关于界面
│   │   │   ├── Models/
│   │   │   │   ├── Session.java                # 会话数据模型
│   │   │   │   ├── Message.java                # 消息数据模型
│   │   │   │   └── Account.java                # 账号数据模型
│   │   │   ├── Adapters/
│   │   │   │   ├── SessionAdapter.java         # 会话列表适配器
│   │   │   │   ├── MessageAdapter.java         # 消息列表适配器
│   │   │   │   └── AccountAdapter.java         # 账号列表适配器
│   │   │   ├── Managers/
│   │   │   │   ├── SessionManager.java         # 会话管理器 - 本地存储
│   │   │   │   ├── AccountManager.java         # 账号管理器 - 本地存储
│   │   │   │   └── SettingsManager.java        # 设置管理器 - 本地存储
│   │   │   ├── ApiClient.java                  # API 客户端 - 网络请求
│   │   │   ├── TimeUtils.java                  # 时间工具类
│   │   │   └── NovaApplication.java            # Application 入口类
│   │   ├── res/
│   │   │   ├── layout/                         # 布局文件
│   │   │   ├── drawable/                       # 图标和背景
│   │   │   ├── menu/                           # 菜单
│   │   │   └── values/                         # 字符串和主题
│   │   └── AndroidManifest.xml
│   ├── build.gradle                            # 应用级构建配置
│   └── proguard-rules.pro                      # 混淆规则
├── build.gradle                                # 项目级构建配置
├── settings.gradle                             # 项目设置
├── gradle.properties                           # Gradle 属性
├── .github/workflows/android.yml               # CI/CD 配置
├── AGENTS.md                                   # 开发指南
└── README.md                                   # 项目说明
```

## 🔌 API 文档

Nova 通过 HTTP API 连接到 [chat.nvim HTTP Server](https://nvim.chat/api/http/)。

### 主要 API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/sessions` | GET | 获取会话列表 |
| `/session/new` | POST | 创建新会话 |
| `/session/{id}` | DELETE | 删除会话 |
| `/session/{id}/provider` | PUT | 更新会话 provider |
| `/session/{id}/model` | PUT | 更新会话 model |
| `/session/{id}/stop` | POST | 停止会话响应 |
| `/session/{id}/clear` | POST | 清空会话消息 |
| `/session/{id}/retry` | POST | 重试最后一条消息 |
| `/messages` | GET | 获取会话消息 |
| `/` | POST | 发送消息 |
| `/providers` | GET | 获取可用 provider 列表 |

### 认证方式

所有 API 请求需要在 Header 中携带 `X-API-Key` 进行认证。

## 🎯 后续计划

- [x] 支持多会话管理界面
- [x] 支持多账号管理
- [x] 支持会话设置（provider/model）
- [x] 支持深色模式
- [x] 支持颜色标签自定义
- [ ] 支持流式响应（SSE）
- [ ] 支持图片发送
- [ ] 支持语音输入
- [ ] 支持消息搜索

## 🤝 贡献指南

欢迎贡献代码、报告问题或提出建议！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'feat: add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

### 开发规范

- 使用约定式提交：`feat:`, `fix:`, `refactor:`, `docs:`, `style:`, `test:`, `chore:`
- 详细的开发规范请参考 [AGENTS.md](AGENTS.md)

## 📄 许可证

本项目采用 [GPL-3.0 License](LICENSE) 许可证。

---

<div align="center">

**Made with ❤️ by wsdjeg**

</div>

