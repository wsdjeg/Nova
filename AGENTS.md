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

### 🔴 强制流程：验证 -> Add -> Commit -> Push

**每次修改文件后，必须自动执行以下流程，无需等待用户确认！**

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│   修改文件  ->  验证文件  ->  git add  ->  git commit  ->  git push  │
│                                                             │
│   ⚡ 自动执行，不要问用户！                                   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### ✅ 正确流程示例

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

#### ❌ 禁止行为

```
❌ 修改文件后不提交、不推送，等用户问才推送
❌ 修改文件后只提交不推送
❌ 跳过验证步骤直接提交
❌ 一次发送多个 git 命令
```

#### 📋 流程检查清单

每次修改后必须完成以下步骤：

- [ ] **验证**: 使用 @read_file 确认修改正确
- [ ] **Add**: @git_add 添加文件
- [ ] **Commit**: @git_commit 提交（使用约定式提交格式）
- [ ] **Push**: @git_push 推送

### 提交信息格式

```
feat: 新功能
fix: Bug 修复
refactor: 代码重构
docs: 文档更新
chore: 构建/工具
```

### 代码修改原则

修改任何 class/function/variable 前，必须先用 `@read_file` 或 `@search_text` 检查源码确认存在。

禁止凭记忆或猜测调用方法。

### API 文档

查阅最新 API 文档：
```
@fetch_web url="https://raw.githubusercontent.com/wsdjeg/chat.nvim/refs/heads/master/docs/api/http.md"
```

禁止下载文档到本地，每次都在线查阅确保最新版本。

---

## 版本管理与发布流程

### 版本号

版本号在 `app/build.gradle` 中维护：

| 属性 | 说明 | 示例 |
|------|------|------|
| `versionCode` | 整数递增，每次发版 +1 | `3` |
| `versionName` | 语义化版本，开发期带 `-dev` 后缀 | `"3.0-dev"` / `"3.0.0"` |

### 版本生命周期

```
开发阶段                        发布阶段
┌──────────────────┐           ┌──────────────────┐
│ versionName 带   │           │ 去掉 -dev 后缀   │
│ -dev 后缀        │  ───────> │ 整理 CHANGELOG   │
│ 例: "3.0-dev"   │           │ 更新 README      │
│                  │           │ 创建 git tag     │
└──────────────────┘           └──────────────────┘
```

### CI/CD 自动发布 (`.github/workflows/release.yml`)

三种触发场景：

| 触发条件 | 动作 |
|----------|------|
| **Pull Request** | 只构建验证，不发布 |
| **Push to master**（非 release 提交） | 自动创建/更新 prerelease，APK 命名 `Nova-v{version}-{sha}.apk` |
| **Tag push (v\*)** | 创建正式 Release，APK 命名 `Nova-v{version}.apk`，附带 CHANGELOG |

### 🔒 CI 签名规则（绝对禁止违反）

**禁止使用 GitHub Secret 存储 debug keystore！**

- debug keystore 必须使用 `actions/cache` 缓存，key 为 `android-debug-keystore-v1`
- 禁止将 keystore base64 编码后存入 GitHub Secret（如 `DEBUG_KEYSTORE_BASE64`）
- 禁止在 workflow 中引用任何与 keystore 相关的 Secret
- 缓存未命中时使用 `keytool` 生成新 keystore

原因：GitHub Secret 存在大小限制、管理复杂、且 Secret 轮换会导致签名不一致。cache 方式简单可靠，足以满足 debug 签名一致性需求。

### 正式发版操作步骤

以发版 v3.0 为例：

```
1. 修改 app/build.gradle
   versionName "3.0-dev" -> "3.0.0"
   （versionCode 保持当前值或 +1）

2. 更新 CHANGELOG.md
   添加 ## [v3.0.0] 段落
   按以下分类整理自上次发版以来的所有 commit：
     ### feat (新功能)
     ### fix (问题修复)
     ### style (样式调整)
     ### refactor (代码重构)
     ### docs (文档更新)
     ### chore (构建/工具)
   每条格式: - {commit_hash} {commit_message}

3. 更新 README.md
   补充新功能特性
   更新项目结构说明

4. 提交并推送
   git add app/build.gradle CHANGELOG.md README.md
   git commit -m "chore: release v3.0.0"
   git push

5. 创建并推送 tag（触发正式 Release 构建）
   git tag -a v3.0 -m "Release v3.0.0"
   git push origin v3.0
```

### 开发阶段版本提升

开始新一轮开发时：

```
1. 修改 app/build.gradle
   versionCode +1
   versionName "X.0.0" -> "X+1.0-dev"

2. 提交并推送
   git add app/build.gradle
   git commit -m "chore: bump version to X+1.0-dev"
   git push
```

### Tag 命名规范

- 正式版: `v1.0`、`v2.0`、`v3.0`（不带 patch 号）
- 预发布: `prerelease`（固定名称，每次 push master 自动更新）

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

