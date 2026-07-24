# Nova

Nova is the Android client for [chat.nvim](https://nvim.chat),
the Neovim AI chat plugin.
It connects to a chat.nvim HTTP Server and lets you continue
your AI conversations on the go — manage sessions, send messages,
browse tool-call results, and more, all from your phone.

> Nova does **not** talk to LLM APIs directly.
> A running chat.nvim HTTP Server is required as the backend.

![Platform](https://img.shields.io/badge/Platform-Android-green.svg)
![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)
![Java](https://img.shields.io/badge/Language-Java-orange.svg)
[![GitHub Release](https://img.shields.io/github/v/release/wsdjeg/Nova)](https://github.com/wsdjeg/Nova/releases)
[![GitHub Issues or Pull Requests](https://img.shields.io/github/issues/wsdjeg/Nova)](https://github.com/wsdjeg/Nova/issues)
[![GitHub License](https://img.shields.io/github/license/wsdjeg/Nova)](LICENSE)

<div align="center">

| 会话列表 | 聊天界面 | 账号管理 | 账号编辑 |
|:--------:|:--------:|:--------:|:--------:|
| <img width="1264" height="2780" alt="会话列表" src="https://github.com/user-attachments/assets/ff3b7e43-b390-467c-a24d-7a0f891980e8" /> | <img width="1264" height="2780" alt="消息界面" src="https://github.com/user-attachments/assets/2f6fbc19-fb3b-4bed-852b-c74b98dbcb4a" /> | <img width="1264" height="2780" alt="账号列表" src="https://github.com/user-attachments/assets/6348834c-0b07-4e1d-a854-937b330fe0f0" /> | <img width="1264" height="2780" alt="账号设置" src="https://github.com/user-attachments/assets/71f2df69-70f1-414a-86ac-5e4596351c24" /> |

</div>

<!-- vim-markdown-toc GFM -->

- [✨ Features](#-features)
- [📦 Quick Start](#-quick-start)
- [🔧 Configuration](#-configuration)
- [⚙️ Usage](#-usage)
    - [Account management](#account-management)
    - [Session management](#session-management)
    - [Chat](#chat)
    - [Voice input](#voice-input)
    - [App settings](#app-settings)
- [🔌 API](#-api)
- [📂 Project structure](#-project-structure)
- [🛠️ Tech stack](#-tech-stack)
- [🎯 Roadmap](#-roadmap)
- [🤝 Contributing](#-contributing)
- [💬 Feedback](#-feedback)
- [🙏 Credits](#-credits)
- [📄 License](#-license)

<!-- vim-markdown-toc -->

## ✨ Features

- Multi-account management with per-account color tags and connection testing
- Account import / export in JSON for easy migration across devices
- Session list with search, pin/unpin, swipe actions, and live session count
- Session settings — edit title, provider, model, and cwd on the fly
- Paginated history loading with DiffUtil incremental updates
- Stop and retry AI generation at any time
- Tool-call and tool-result message cards with collapsible JSON
- Error messages rendered as distinct cards
- Clear session messages (smart `cleared_at` sorting)
- Offline voice input via Vosk with automatic fallback to system speech recognition
- Markdown rendering with syntax highlighting, tables, task lists, and strikethrough
- Slash commands (`/help`, `/sessions`, `/session`, `/set`, `/clear`)
- Draft auto-save for unsent messages
- Browser session preview
- Smart scroll — pause auto-refresh while reading, restore position via stable-key anchors
- Content fingerprint to skip redundant Markdown re-binding
- In-app update checker with download and install dialog
- Built-in log viewer for debugging
- Dark / light / system theme modes
- Material Design UI

## 📦 Quick Start

1. **Install chat.nvim** in Neovim and configure the HTTP Server.

   ```lua
   require('chat').setup({
     http = {
       host = '127.0.0.1',
       port = 7777,
       api_key = 'your-secret-key',  -- required to start the HTTP Server
     },
   })
   ```

2. **Download the APK** from the [PreRelease](https://github.com/wsdjeg/Nova/releases/tag/prerelease) page.

3. **Install** the APK on your Android device (Android 7.0+).

4. **Open Nova**, go to menu → **Account management**, add an account with your
   server address (e.g. `http://192.168.1.100:7777`) and API key.

5. Tap **Test connection** to verify, then save. The session list will load automatically.

### Build from source

```bash
git clone https://github.com/wsdjeg/Nova.git
cd Nova
```

Open the project in Android Studio, let Gradle sync, then press **Run** (Shift + F10).

## 🔧 Configuration

No configuration file is needed — everything is set up inside the app.

| Setting | Location | Description |
| ------- | -------- | ----------- |
| Server URL & API key | Account management | Per-account server address and key |
| Default provider / model | App settings | Used when creating a new session |
| Account tag color | App settings | Default color for new accounts (`auto` assigns by account ID) |
| Theme mode | App settings | `System` / `Light` / `Dark` |

## ⚙️ Usage

### Account management

| Action | How |
| ------ | --- |
| Add account | Account management → FAB button → fill in URL, API key, color → Test → Save |
| Set default | Tap an account in the list |
| Edit / Delete | Long-press an account |
| Import | Account management → menu → **Import** (select a JSON file) |
| Export | Account management → menu → **Export** (saves a JSON file) |

### Session management

| Action | How |
| ------ | --- |
| View sessions | Open the app — sessions are sorted by last message time, pinned first |
| Search | Tap the search icon in the session list toolbar |
| Create session | FAB button in the session list |
| Session settings | Chat → menu → **Settings** (edit title / provider / model / cwd) |
| Pin / Unpin | Swipe right to pin, swipe left to unpin (or use session settings) |
| Delete session | Chat → menu → **Delete session** (or long-press in the list) |
| Clear session | Chat → menu → **Clear session** |

### Chat

| Action | How |
| ------ | --- |
| Send message | Type in the input box and tap send |
| Stop generation | Tap the stop button while AI is responding |
| Retry | Chat → menu → **Retry** |
| Refresh | Chat → menu → **Refresh** |
| Load history | Scroll to the top — earlier messages load automatically |
| Preview in browser | Chat → menu → **Preview** |
| Copy message | Long-press a message |
| Delete message | Long-press a message → **Delete** |
| Slash commands | Type `/help` in the input box for available commands |

### Voice input

When the input box is empty, the send button switches to a microphone icon.

| Engine | Description |
| ------ | ----------- |
| Vosk (default) | Offline Chinese small model, extracted from assets on first use |
| System speech | Automatic fallback when Vosk model is unavailable |

- Partial results are shown in real time while listening.
- Tap the wave icon to stop listening.
- Recognized text is appended to any existing input (supports continued dictation).

### App settings

| Setting | Options |
| ------- | ------- |
| Theme | System / Light / Dark |
| Default provider | Fetched from the server |
| Default model | Depends on the selected provider |
| Account tag color | Auto (by account ID) or a fixed color (0–8) |

## 🔌 API

Nova communicates with the [chat.nvim HTTP Server](https://nvim.chat/api/http/).
All requests are authenticated via the `X-API-Key` header.

| Endpoint | Method | Description |
| -------- | ------ | ----------- |
| `/sessions` | GET | List all sessions |
| `/sessions/:id` | GET | Get a single session |
| `/session/new` | POST | Create a new session |
| `/session/:id` | DELETE | Delete a session |
| `/session/:id/title` | PUT | Update session title |
| `/session/:id/provider` | PUT | Update session provider |
| `/session/:id/model` | PUT | Update session model |
| `/session/:id/cwd` | PUT | Update session working directory |
| `/session/:id/pin` | PUT | Pin / unpin a session |
| `/session/:id/stop` | POST | Stop AI generation |
| `/session/:id/clear` | POST | Clear session messages |
| `/session/:id/retry` | POST | Retry the last message |
| `/session?id={id}` | GET | HTML preview page |
| `/messages?session={id}` | GET | Get messages (supports `since` / `limit` / `last`) |
| `/` | POST | Send a message |
| `/providers` | GET | List available providers |

## 📂 Project structure

```
Nova/
├── app/src/main/java/net/wsdjeg/nova/
│   ├── SessionListActivity.java       # Session list (launcher, search, swipe actions)
│   ├── ChatActivity.java              # Chat screen
│   ├── SessionSettingsActivity.java   # Session settings
│   ├── SettingsActivity.java          # App settings
│   ├── AccountManagerActivity.java    # Account list
│   ├── AccountEditActivity.java       # Account editor
│   ├── AboutActivity.java             # About + in-app update checker
│   ├── LogViewerActivity.java         # Log viewer
│   ├── ApiClient.java                 # HTTP client (ChatMessage, ToolCall, etc.)
│   ├── SessionManager.java            # Session persistence & drafts
│   ├── AccountManager.java            # Account persistence & import/export
│   ├── SettingsManager.java           # Settings & theme management
│   ├── Session.java                   # Session model
│   ├── Message.java                   # Message model
│   ├── Account.java                   # Account model
│   ├── SessionAdapter.java            # Session list adapter (swipe, pin)
│   ├── MessageAdapter.java            # Message adapter (DiffUtil, fingerprints)
│   ├── AccountAdapter.java            # Account list adapter
│   ├── VoskSpeechRecognizer.java      # Offline speech recognition
│   ├── MarkdownUtils.java             # Markdown preprocessing
│   ├── InlineCodeSpan.java            # Inline code styling
│   ├── PopupHelper.java               # Popup menu helper
│   ├── ToolContentScrollView.java     # Scrollable tool-call content
│   ├── TimeUtils.java                 # Time formatting
│   └── NovaApplication.java           # Application entry
├── app/src/main/res/
│   ├── layout/                        # 20 layout XMLs
│   ├── menu/                          # 8 menu XMLs
│   ├── drawable/                      # 38 drawable resources
│   ├── values/                        # colors, strings, themes
│   └── values-night/                  # dark theme overrides
├── app/build.gradle                   # App-level build config
├── .github/workflows/release.yml      # CI/CD (build, prerelease, release)
├── AGENTS.md                          # Development guide
├── CHANGELOG.md                       # Changelog
└── README.md
```

## 🛠️ Tech stack

| Technology | Detail |
| ---------- | ------ |
| Language | Java |
| Min SDK | Android 7.0 (API 24) |
| Target SDK | Android 14 (API 34) |
| UI | AppCompat + Material Design + ConstraintLayout |
| Lists | RecyclerView + DiffUtil |
| Networking | OkHttp 4.12.0 |
| Markdown | Markwon 4.6.2 (syntax-highlight, tables, tasklist, strikethrough, html) |
| Speech | Vosk Android 0.3.47 + JNA 5.13.0 |
| JSON | org.json |
| NDK ABI | armeabi-v7a, arm64-v8a, x86_64, x86 |

## 🎯 Roadmap

- [x] Multi-session management
- [x] Multi-account with import / export
- [x] Session settings (provider / model / cwd / title)
- [x] Dark / light / system themes
- [x] Color tags for accounts
- [x] Paginated history loading
- [x] Draft auto-save
- [x] Stop / retry generation
- [x] Tool-call and tool-result display
- [x] Error message cards
- [x] Session pin / unpin with swipe
- [x] Clear session with `cleared_at` sorting
- [x] DiffUtil incremental updates
- [x] Scroll position anchors
- [x] Smart scroll (pause while reading)
- [x] Voice input (Vosk + system fallback)
- [x] Session search
- [x] Slash commands
- [x] In-app update checker
- [x] Log viewer
- [ ] Streaming responses (SSE)
- [ ] Image sending
- [ ] Message search
- [ ] Enhanced table rendering

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit with conventional commits (`feat:`, `fix:`, `refactor:`, `docs:`, `chore:`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

See [AGENTS.md](AGENTS.md) for detailed development guidelines.

## 💬 Feedback

If you encounter any bugs or have suggestions, please file an issue in the
[issue tracker](https://github.com/wsdjeg/Nova/issues).

## 🙏 Credits

- [chat.nvim](https://github.com/wsdjeg/chat.nvim) — the Neovim AI chat plugin
- [Vosk](https://alphacephei.com/vosk/) — offline speech recognition
- [Markwon](https://github.com/noties/markwon) — Android Markdown library

## 📄 License

Licensed under [GPL-3.0](LICENSE).

