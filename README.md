# Nova - chat.nvim Android Client

<div align="center">

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)
![Java](https://img.shields.io/badge/Language-Java-orange.svg)

**Mobile client for [chat.nvim](https://nvim.chat) - Neovim AI chat plugin**

[Download APK](https://github.com/wsdjeg/Nova/releases/tag/prerelease) | [Report Bug](https://github.com/wsdjeg/Nova/issues) | [Request Feature](https://github.com/wsdjeg/Nova/issues)

</div>

## 📱 项目简介

Nova 是 [chat.nvim](https://nvim.chat) 的 Android 移动端客户端，让你在手机上继续 Neovim 的 AI 对话。

> **注意**: 本应用不直接连接大模型 API，而是通过 chat.nvim HTTP Server 作为中间层。你需要先在 Neovim 中启动 HTTP Server。

## 📸 应用截图

<div align="center">

| 会话列表 | 消息界面 | 账号列表 | 账号设置 |
|:--------:|:--------:|:--------:|:--------:|
| <img width="1264" height="2780" alt="会话列表" src="https://github.com/user-attachments/assets/ff3b7e43-b390-467c-a24d-7a0f891980e8" /> | <img width="1264" height="2780" alt="消息界面" src="https://github.com/user-attachments/assets/2f6fbc19-fb3b-4bed-852b-c74b98dbcb4a" /> | <img width="1264" height="2780" alt="账号列表" src="https://github.com/user-attachments/assets/6348834c-0b07-4e1d-a854-937b330fe0f0" /> | <img width="1264" height="2780" alt="账号设置" src="https://github.com/user-attachments/assets/71f2df69-70f1-414a-86ac-5e4596351c24" /> |

</div>

## ✨ 功能特性

- **多账号管理** - 支持添加、编辑、删除、设置默认账号，每个账号可独立配置颜色标签
- **账号导入/导出** - 支持以 JSON 格式导入导出账号配置，方便多设备迁移
- **会话管理** - 查看、切换、创建、删除不同的对话会话，支持修改 provider/model/cwd
- **会话搜索** - 在会话列表中快速搜索会话标题和预览内容，顶部显示会话总数
- **移动端访问** - 在手机上继续你的 Neovim AI 对话
- **服务器配置** - 配置 chat.nvim HTTP Server 地址即可连接
- **消息列表** - 流畅的对话消息展示，区分用户和 AI 消息，支持分页加载历史消息
- **停止/重试生成** - AI 响应过程中可中断，也可重试最后一条消息
- **工具调用展示** - 支持显示 AI 的 tool_calls 和 tool_result 消息，JSON 参数和结果可折叠
- **错误消息展示** - AI 返回错误时以卡片样式展示，清晰区分正常消息
- **会话标题设置** - 支持在会话设置中自定义会话标题
- **会话置顶** - 支持将会话置顶，方便快速访问重要会话
- **滑动操作** - 会话列表支持左滑取消置顶、右滑置顶，操作更便捷
- **清空会话** - 一键清空会话消息记录，基于 cleared_at 字段智能排序
- **语音输入** - 基于 Vosk 的离线语音识别，无需联网即可语音输入消息；Vosk 不可用时自动回退到 Android 系统在线语音识别
- **Material Design** - 现代化的 Material 设计风格
- **深色模式** - 支持系统自动、浅色、深色三种主题模式
- **本地存储** - 设置信息和会话列表保存在本地
- **Markdown 渲染** - 支持代码高亮、表格、任务列表等，统一 14sp 字体，支持斜杠命令
- **连接测试** - 支持测试服务器连接状态
- **会话预览** - 在浏览器中打开会话的 HTML 预览页面
- **草稿保存** - 未发送的消息自动保存为草稿
- **增量刷新** - 智能增量刷新消息，减少网络请求
- **DiffUtil 增量更新** - 使用 DiffUtil 算法高效计算消息差异，避免全量刷新
- **StableKey 锚点** - 基于稳定键的消息锚点定位，刷新后精准恢复滚动位置
- **智能滚动** - 用户滚动时暂停自动刷新，避免消息跳动干扰阅读
- **内容指纹** - 基于 ViewHolder 内容指纹跳过相同的 Markdown 重绑定，提升滚动流畅度
- **日志查看器** - 内置日志查看功能，方便调试排错

## 🛠️ 技术栈

| 技术 | 说明 |
|------|------|
| **语言** | Java |
| **最低 SDK** | Android 7.0 (API 24) |
| **目标 SDK** | Android 14 (API 34) |
| **UI 框架** | AppCompat + Material Design + ConstraintLayout |
| **列表组件** | RecyclerView + DiffUtil |
| **网络请求** | OkHttp 4.12.0 |
| **Markdown** | Markwon 4.6.2 (含代码高亮、表格、任务列表、删除线、HTML 等扩展) |
| **语音识别** | Vosk Android 0.3.47 (离线语音识别) + JNA 5.13.0 |
| **JSON 解析** | org.json |
| **NDK ABI** | armeabi-v7a, arm64-v8a, x86_64, x86 |

## 🚀 快速开始

### 前置条件

1. **安装 Neovim + chat.nvim** - 安装 [chat.nvim](https://nvim.chat) Neovim 插件
2. **配置 HTTP Server** - 在 chat.nvim 配置中设置 `http.api_key`
3. **启动 HTTP Server** - HTTP Server 会在设置 API key 后自动启动（默认端口 7777）

```lua
require('chat').setup({
  http = {
    host = '127.0.0.1',    -- 默认值
    port = 7777,           -- 默认值
    api_key = 'your-secret-key',  -- 必须设置以启用 HTTP Server
  },
})
```

### 安装 APK

1. 访问 [PreRelease](https://github.com/wsdjeg/Nova/releases/tag/prerelease) 页面
2. 下载最新的 `Nova-v{version}.apk`
3. 在 Android 设备上安装

### 从源码构建

1. **克隆项目**
   ```bash
   git clone https://github.com/wsdjeg/Nova.git
   cd Nova
   ```

2. **在 Android Studio 中打开**
   - File -> Open -> 选择项目目录
   - 等待 Gradle 同步完成

3. **运行应用**
   - 连接 Android 设备或启动模拟器
   - 点击 Run 按钮 (Shift + F10)

## 💬 使用指南

### 账号管理

1. **添加账号**
   - 打开应用，点击右上角菜单 -> 「账号管理」
   - 点击右下角 FAB 按钮添加新账号
   - 填写服务器地址（如 `http://192.168.1.100:7777`）和 API Key
   - 选择颜色标签（可选）
   - 点击「测试连接」验证配置
   - 保存账号

2. **切换默认账号**
   - 在账号列表中点击要设为默认的账号

3. **编辑/删除账号**
   - 长按账号 -> 选择「编辑」或「删除」

4. **导入/导出账号**
   - 在账号管理页面点击菜单 -> 「导出账号」生成 JSON 文件
   - 点击菜单 -> 「导入账号」从 JSON 文件恢复账号配置

### 会话管理

1. **查看会话列表**
   - 打开应用显示所有账号的会话列表
   - 会话按最后消息时间降序排序，置顶会话优先显示
   - 顶部显示会话总数

2. **搜索会话**
   - 点击会话列表顶部搜索图标，输入关键词即可按标题和预览内容过滤

3. **创建新会话**
   - 点击右下角 FAB 按钮创建新会话

4. **会话设置**
   - 在聊天界面点击右上角菜单 -> 「会话设置」
   - 查看/修改当前会话的标题、provider、model、cwd

5. **置顶/取消置顶**
   - 在会话列表中右滑会话项可置顶，左滑可取消置顶
   - 也可通过会话设置页面操作

6. **删除会话**
   - 在聊天界面点击菜单 -> 「删除会话」
   - 或在会话列表长按会话项，选择删除

7. **清空会话**
   - 在聊天界面点击菜单 -> 「清空会话」

### 聊天交互

1. **发送消息** - 在输入框输入消息，点击发送按钮
2. **语音输入** - 输入框为空时，发送按钮自动切换为麦克风图标，点击即可语音输入
   - **Vosk 离线识别（优先）**: 内置中文小模型，首次使用时自动从 assets 解压到外部存储，完全离线运行
   - **系统语音识别（回退）**: 当 Vosk 模型不可用时，自动回退到 Android 系统在线语音识别
   - 识别过程中实时显示部分结果，支持追加识别（在已有文本后继续语音输入）
   - 聆听状态下发送按钮变为声波图标，点击可停止聆听
3. **停止响应** - AI 响应过程中可点击「停止」按钮中断
4. **重试消息** - 点击右上角菜单中的重试按钮
5. **加载历史** - 滚动到顶部自动触发分页加载更早的消息
6. **预览会话** - 点击菜单 -> 「预览」在浏览器中打开 HTML 预览
7. **复制消息** - 长按消息内容可复制
8. **斜杠命令** - 支持斜杠命令快捷操作

### 应用设置

1. **主题设置** - 支持系统自动、浅色、深色三种模式
2. **默认 Provider/Model** - 可设置新建会话的默认 provider 和 model
3. **账号标签颜色** - 可设置新账号的默认颜色标签（含自动分配选项）
4. **日志查看器** - 在会话列表页面通过菜单进入日志查看器，方便调试

## 📂 项目结构

```
Nova/
├── app/
│   ├── src/main/
│   │   ├── java/net/wsdjeg/nova/
│   │   │   ├── SessionListActivity.java      # 会话列表界面（启动入口，含搜索、滑动操作）
│   │   │   ├── ChatActivity.java             # 聊天界面
│   │   │   ├── SettingsActivity.java         # 应用设置界面
│   │   │   ├── SessionSettingsActivity.java  # 会话设置界面
│   │   │   ├── AccountManagerActivity.java   # 账号管理界面
│   │   │   ├── AccountEditActivity.java      # 账号编辑界面
│   │   │   ├── AboutActivity.java            # 关于界面
│   │   │   ├── LogViewerActivity.java        # 日志查看器
│   │   │   ├── Session.java                 # 会话数据模型（含 cleared_at 字段）
│   │   │   ├── Message.java                 # 消息数据模型（本地存储用）
│   │   │   ├── Account.java                 # 账号数据模型
│   │   │   ├── SessionAdapter.java          # 会话列表适配器（含滑动操作）
│   │   │   ├── MessageAdapter.java          # 消息列表适配器（含 DiffUtil、ViewHolder 指纹）
│   │   │   ├── AccountAdapter.java          # 账号列表适配器
│   │   │   ├── SessionManager.java          # 会话管理器
│   │   │   ├── AccountManager.java          # 账户管理器（含导入/导出）
│   │   │   ├── SettingsManager.java         # 设置管理器
│   │   │   ├── ApiClient.java               # API 客户端（含 ChatMessage 等内部类）
│   │   │   ├── VoskSpeechRecognizer.java    # Vosk 离线语音识别器
│   │   │   ├── TimeUtils.java               # 时间工具类
│   │   │   └── NovaApplication.java         # Application 入口类
│   │   ├── res/
│   │   │   ├── layout/                      # 布局文件
│   │   │   │   ├── activity_chat.xml         #   聊天界面
│   │   │   │   ├── activity_session_list.xml #   会话列表（含搜索栏）
│   │   │   │   ├── activity_session_settings.xml
│   │   │   │   ├── activity_settings.xml
│   │   │   │   ├── activity_account_manager.xml
│   │   │   │   ├── activity_account_edit.xml
│   │   │   │   ├── activity_about.xml
│   │   │   │   ├── activity_log_viewer.xml
│   │   │   │   ├── dialog_account.xml
│   │   │   │   ├── dialog_voice_input.xml    #   语音输入对话框
│   │   │   │   ├── item_message.xml          #   消息项（通用）
│   │   │   │   ├── item_message_bot.xml      #   AI 消息
│   │   │   │   ├── item_message_user.xml     #   用户消息
│   │   │   │   ├── item_message_error.xml    #   错误消息
│   │   │   │   ├── item_tool_call.xml        #   工具调用
│   │   │   │   ├── item_tool_result.xml      #   工具结果
│   │   │   │   ├── item_session.xml          #   会话列表项（含滑动操作）
│   │   │   │   └── item_account.xml          #   账号列表项
│   │   │   ├── drawable/                    # 图标、背景和样式资源
│   │   │   ├── mipmap-anydpi-v26/           # 自适应图标
│   │   │   ├── menu/                        # 菜单文件
│   │   │   └── values/                      # 字符串、颜色和主题
│   │   └── AndroidManifest.xml
│   ├── build.gradle                         # 应用级构建配置
│   └── proguard-rules.pro                   # 混淆规则
├── build.gradle                             # 项目级构建配置
├── settings.gradle                          # 项目设置
├── gradle.properties                        # Gradle 属性
├── .github/workflows/release.yml            # CI/CD 配置（构建、预发布、正式发布）
├── AGENTS.md                                # 开发指南
├── CHANGELOG.md                             # 更新日志
└── README.md                                # 项目说明
```

## 🔌 API 文档

Nova 通过 HTTP API 连接到 [chat.nvim HTTP Server](https://nvim.chat/api/http/)。

### 主要 API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/sessions` | GET | 获取会话列表 |
| `/sessions/:id` | GET | 获取单个会话详情 |
| `/session/new` | POST | 创建新会话 |
| `/session/:id` | DELETE | 删除会话 |
| `/session/:id/title` | PUT | 更新会话标题 |
| `/session/:id/provider` | PUT | 更新会话 provider |
| `/session/:id/model` | PUT | 更新会话 model |
| `/session/:id/cwd` | PUT | 更新会话工作目录 |
| `/session/:id/pin` | PUT | 置顶/取消置顶会话 |
| `/session/:id/stop` | POST | 停止会话响应 |
| `/session/:id/clear` | POST | 清空会话消息 |
| `/session/:id/retry` | POST | 重试最后一条消息 |
| `/session?id={id}` | GET | HTML 预览页面（浏览器打开） |
| `/messages?session={id}` | GET | 获取会话消息（支持 since/limit/last 参数） |
| `/` | POST | 发送消息 |
| `/providers` | GET | 获取可用 provider 列表 |

### 认证方式

所有 API 请求需要在 Header 中携带 `X-API-Key` 进行认证。

## 🎯 后续计划

- [x] 支持多会话管理界面
- [x] 支持多账号管理
- [x] 支持会话设置（provider/model/cwd）
- [x] 支持深色模式
- [x] 支持颜色标签自定义
- [x] 支持分页加载历史消息
- [x] 支持草稿保存
- [x] 支持停止/重试生成
- [x] 支持会话标题设置
- [x] 支持工具调用（tool_calls）消息展示
- [x] 支持日志查看器
- [x] 支持会话置顶
- [x] 支持清空会话
- [x] 支持错误消息展示
- [x] 支持账号导入/导出
- [x] 支持 DiffUtil 增量消息更新
- [x] 支持滚动位置锚点记忆
- [x] 支持用户滚动时暂停自动刷新
- [x] 支持语音输入（Vosk 离线识别 + 系统语音识别回退）
- [x] 支持会话搜索
- [x] 支持滑动置顶/取消置顶
- [x] 支持 cleared_at 字段会话排序优化
- [ ] 支持流式响应（SSE）
- [ ] 支持图片发送
- [ ] 支持消息搜索
- [ ] 支持消息表格渲染增强

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

## 📝 更新日志

查看完整更新日志请访问 [CHANGELOG.md](CHANGELOG.md)。

## 📄 许可证

本项目采用 [GPL-3.0 License](LICENSE) 许可证。

---

<div align="center">

**Made by wsdjeg**

</div>

