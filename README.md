# Nova

Nova is the Android client for [chat.nvim](https://nvim.chat),
the Neovim AI chat plugin.
It connects to a chat.nvim HTTP Server and lets you continue
your AI conversations on the go - manage sessions, send messages,
browse tool-call results, upload images, share content, and more,
all from your phone.

> Nova does **not** talk to LLM APIs directly.
> A running chat.nvim HTTP Server is required as the backend.

![Platform](https://img.shields.io/badge/Platform-Android-green.svg)
![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)
![Java](https://img.shields.io/badge/Language-Java-orange.svg)
[![Build & Release](https://github.com/wsdjeg/Nova/actions/workflows/release.yml/badge.svg)](https://github.com/wsdjeg/Nova/actions/workflows/release.yml)
[![GitHub Release](https://img.shields.io/github/v/release/wsdjeg/Nova)](https://github.com/wsdjeg/Nova/releases)
[![GitHub Issues or Pull Requests](https://img.shields.io/github/issues/wsdjeg/Nova)](https://github.com/wsdjeg/Nova/issues)
[![GitHub License](https://img.shields.io/github/license/wsdjeg/Nova)](LICENSE)

<div align="center">

<table align="center">
  <tr>
    <th width="20%">Session List</th>
    <th width="20%">Chat</th>
    <th width="20%">Account List</th>
    <th width="20%">Edit Account</th>
    <th width="20%">About</th>
  </tr>
  <tr>
    <td width="20%" align="center"><img alt="session list" src="https://github.com/user-attachments/assets/71b50d8a-8d85-498e-a966-b324c5bd0b80" /></td>
    <td width="20%" align="center"><img alt="chat" src="https://github.com/user-attachments/assets/b05b7ddd-434c-4d7e-9c5f-4b1bb8e96f90" /></td>
    <td width="20%" align="center"><img alt="account list" src="https://github.com/user-attachments/assets/102dd9a0-ae93-45c5-88d6-32d716dc35ff" /></td>
    <td width="20%" align="center"><img alt="edit account" src="https://github.com/user-attachments/assets/a4f10477-b6eb-40c2-9ced-e2c298b8e69d" /></td>
    <td width="20%" align="center"><img alt="about" src="https://github.com/user-attachments/assets/422d42b7-6ed5-4f51-86f3-bb612a04999a" /></td>
  </tr>
</table>

</div>

<!-- vim-markdown-toc GFM -->

- [Features](#features)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Usage](#usage)
    - [Account management](#account-management)
    - [Session management](#session-management)
    - [Chat](#chat)
    - [Image upload](#image-upload)
    - [Share](#share)
    - [Voice input](#voice-input)
    - [WeChat login](#wechat-login)
    - [App settings](#app-settings)
- [API](#api)
- [Project structure](#project-structure)
- [Tech stack](#tech-stack)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [Feedback](#feedback)
- [Credits](#credits)
- [License](#license)

<!-- vim-markdown-toc -->

## Features

- Multi-account management with per-account color tags and connection testing
- Account import / export in JSON for easy migration across devices
- Session list with search, pin/unpin, swipe actions, and live session count
- Session settings - edit title, provider, model, cwd, and bridge integrations on the fly
- Paginated history loading with DiffUtil incremental updates
- Stop and retry AI generation at any time
- Tool-call and tool-result message cards with collapsible JSON
- Error messages rendered as distinct cards
- Clear session messages (smart `cleared_at` sorting)
- Offline voice input via Vosk with automatic fallback to system speech recognition
- Image upload to session working directory with multi-image batch selection and progress dialog
- Share content from other apps (images / text) with upload confirmation
- WeChat login - scan QR code in-app to connect your WeChat account
- Bridge (integration) settings per session
- Markdown rendering with syntax highlighting, tables, task lists, and strikethrough
- Separate Markwon instances for user (blue-tinted code) and AI messages
- Slash commands (`/help`, `/sessions`, `/session`, `/set`, `/clear`, `/title`)
- Skills autocomplete - type `/` in the input box to browse and pick server-side skills, with live filtering by name / description and 5-minute list caching
- Long-press message for copy / delete with popup at touch point
- Text selection dialog with Markdown rendering for long-press content
- Draft auto-save for unsent messages
- Browser session preview
- Smart scroll - pause auto-refresh while reading, restore position via stable-key anchors
- Content fingerprint to skip redundant Markdown re-binding
- In-app update checker with download and install dialog (supports dev builds via commit hash)
- Multi-language support (Chinese / English / System)
- Built-in log viewer for debugging
- Dark / light / system theme modes
- Material Design UI

## Quick Start

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

4. **Open Nova**, go to menu -> **Account management**, add an account with your
   server address (e.g. `http://192.168.1.100:7777`) and API key.

5. Tap **Test connection** to verify, then save. The session list will load automatically.

### Build from source

```bash
git clone https://github.com/wsdjeg/Nova.git
cd Nova
```

Open the project in Android Studio, let Gradle sync, then press **Run** (Shift + F10).

## Configuration

No configuration file is needed - everything is set up inside the app.

| Setting | Location | Description |
| ------- | -------- | ----------- |
| Server URL & API key | Account management | Per-account server address and key |
| Default provider / model | App settings | Used when creating a new session |
| Account tag color | App settings | Default color for new accounts (`auto` assigns by account ID) |
| Theme mode | App settings | `System` / `Light` / `Dark` |
| Language | App settings | `System` / `English` / `Chinese` |

## Usage

### Account management

| Action | How |
| ------ | --- |
| Add account | Account management -> FAB button -> fill in URL, API key, color -> Test -> Save |
| Set default | Tap an account in the list |
| Edit / Delete | Long-press an account |
| Import | Account management -> menu -> **Import** (select a JSON file) |
| Export | Account management -> menu -> **Export** (saves a JSON file) |

### Session management

| Action | How |
| ------ | --- |
| View sessions | Open the app - sessions are sorted by last message time, pinned first |
| Search | Tap the search icon in the session list toolbar |
| Create session | FAB button in the session list |
| Session settings | Chat -> menu -> **Settings** (edit title / provider / model / cwd / bridge) |
| Pin / Unpin | Swipe right to pin, swipe left to unpin (or use session settings) |
| Delete session | Chat -> menu -> **Delete session** (or long-press in the list) |
| Clear session | Chat -> menu -> **Clear session** |

### Chat

| Action | How |
| ------ | --- |
| Send message | Type in the input box and tap send |
| Stop generation | Tap the stop button while AI is responding |
| Retry | Chat -> menu -> **Retry** |
| Refresh | Chat -> menu -> **Refresh** |
| Load history | Scroll to the top - earlier messages load automatically |
| Preview in browser | Chat -> menu -> **Preview** |
| Copy message | Long-press a message -> **Copy** |
| Delete message | Long-press a message -> **Delete** |
| Select text | Long-press a message -> **Select text** (renders Markdown) |
| Slash commands | Type `/help` in the input box for available commands |
| Autocomplete skills | Type `/` at the start of the input box - a popup lists available skills; keep typing to filter by name / description, tap a row to insert `/skill_name ` |

The skills popup appears when the input starts with `/` and contains no space.
Full-width slash variants (`／`, `⁄`, `∕`) are normalized automatically, so
Chinese IME input works with slash commands and skill names too.
The skill list is fetched from the server (5-minute cache) and shows
loading / error / empty states inline.

### Image upload

| Action | How |
| ------ | --- |
| Upload image | Chat -> attach button -> select images -> confirm upload path -> upload |
| Batch upload | Select multiple images in the picker, all are uploaded with a progress dialog |
| Set upload directory | Session settings -> **Upload directory** (defaults to session cwd) |

Images are uploaded to the session's working directory on the server. The upload path can be
configured per session in session settings.

### Share

Nova registers as a share target. From other apps:

| Action | How |
| ------ | --- |
| Share image(s) | Gallery / Files -> Share -> select Nova -> confirm upload |
| Share text | Any app -> Share -> select Nova -> text is sent as a chat message |

The share screen shows a preview and confirmation dialog before sending.

### Voice input

When the input box is empty, the send button switches to a microphone icon.

| Engine | Description |
| ------ | ----------- |
| Vosk (default) | Offline Chinese small model, extracted from assets on first use |
| System speech | Automatic fallback when Vosk model is unavailable |

- Partial results are shown in real time while listening.
- Tap the wave icon to stop listening.
- Recognized text is appended to any existing input (supports continued dictation).

### WeChat login

Nova supports WeChat integration via in-app QR code login.

| Action | How |
| ------ | --- |
| Start WeChat login | App settings -> **WeChat login** |
| Scan QR code | The QR code is generated locally (ZXing) and displayed in-app |
| Check status | The app polls the server for login status automatically |
| Re-login | App settings -> **WeChat login** -> **Disconnect** to clear credentials, then reconnect |

### App settings

| Setting | Options |
| ------- | ------- |
| Theme | System / Light / Dark |
| Language | System / English / Chinese |
| Default provider | Fetched from the server |
| Default model | Depends on the selected provider |
| Account tag color | Auto (by account ID) or a fixed color (0-8) |
| WeChat login | Connect / disconnect WeChat account |

## API

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
| `/session?id=glm-5.3_common` | GET | HTML preview page |
| `/messages?session=glm-5.3_common` | GET | Get messages (supports `since` / `limit` / `last`) |
| `/` | POST | Send a message |
| `/providers` | GET | List available providers |
| `/skills` | GET | List available skills (used for slash autocomplete) |
| `/upload?session=glm-5.3_common&path={dir}` | POST | Upload image to session working directory |
| `/session/:id/bridges` | GET / PUT | Get / set bridge (integration) settings for a session |
| `/weixin/credentials` | GET | Get WeChat login QR code and status |
| `/weixin/credentials` | DELETE | Disconnect WeChat (clear credentials) |

## Project structure

```
Nova/
├── app/src/main/java/net/wsdjeg/nova/
│   ├── SessionListActivity.java       # Session list (launcher, search, swipe actions)
│   ├── ChatActivity.java              # Chat screen
│   ├── ChatUploadHelper.java          # Image upload helper (batch upload, progress)
│   ├── ChatVoiceHelper.java           # Voice recognition helper (Vosk + system fallback)
│   ├── SessionSettingsActivity.java   # Session settings (title / provider / model / cwd / bridge)
│   ├── SettingsActivity.java          # App settings (theme / language / WeChat login)
│   ├── AccountManagerActivity.java    # Account list
│   ├── AccountEditActivity.java       # Account editor
│   ├── AboutActivity.java             # About + in-app update checker
│   ├── LogViewerActivity.java         # Log viewer
│   ├── ShareActivity.java             # Share target (image upload / text send)
│   ├── WeChatLoginActivity.java       # WeChat QR code login screen
│   ├── ApiClient.java                 # HTTP client (all API endpoints)
│   ├── SessionManager.java            # Session persistence & drafts
│   ├── AccountManager.java            # Account persistence & import/export
│   ├── SettingsManager.java           # Settings & theme management
│   ├── Session.java                   # Session model
│   ├── Message.java                   # Message model
│   ├── ChatMessage.java               # Chat message DTO (top-level)
│   ├── ToolCall.java                  # Tool call DTO (top-level)
│   ├── Provider.java                  # Provider DTO (top-level)
│   ├── Skill.java                     # Skill DTO (top-level)
│   ├── Account.java                   # Account model
│   ├── WeChatLoginResult.java         # WeChat login result model
│   ├── QRCodeUtils.java               # QR code generation (ZXing)
│   ├── SessionAdapter.java            # Session list adapter (swipe, pin)
│   ├── MessageAdapter.java            # Message adapter (DiffUtil, fingerprints)
│   ├── AccountAdapter.java            # Account list adapter
│   ├── SkillAdapter.java              # Skills autocomplete popup adapter
│   ├── VoskSpeechRecognizer.java      # Offline speech recognition
│   ├── MarkdownUtils.java             # Markdown preprocessing
│   ├── InlineCodeSpan.java            # Inline code styling
│   ├── PopupHelper.java               # Popup menu helper
│   ├── ToolContentScrollView.java     # Scrollable tool-call content
│   ├── TimeUtils.java                 # Time formatting
│   └── NovaApplication.java           # Application entry
├── app/src/main/res/
│   ├── layout/                        # 24 layout XMLs
│   ├── menu/                          # 8 menu XMLs
│   ├── drawable/                      # 38 drawable resources
│   ├── values/                        # colors, strings, themes
│   ├── values-en/                     # English string resources
│   ├── values-night/                  # dark theme overrides
│   └── xml/                           # backup rules, file paths
├── app/build.gradle                   # App-level build config
├── .github/workflows/release.yml      # CI/CD (build, prerelease, release)
├── AGENTS.md                          # Development guide
├── CHANGELOG.md                       # Changelog
└── README.md
```

## Tech stack

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
| QR Code | ZXing Core 3.5.3 (local QR code generation) |
| JSON | org.json |
| NDK ABI | armeabi-v7a, arm64-v8a, x86_64, x86 |

## Roadmap

- [x] Multi-session management
- [x] Multi-account with import / export
- [x] Session settings (provider / model / cwd / title / bridge)
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
- [x] Skills autocomplete (`/` popup with live filtering)
- [x] Long-press message for copy / delete
- [x] Text selection dialog with Markdown rendering
- [x] Image upload to session working directory
- [x] Share from other apps (images / text)
- [x] WeChat login (in-app QR code)
- [x] Bridge (integration) settings
- [x] In-app update checker
- [x] Multi-language support (Chinese / English)
- [x] Log viewer
- [ ] Streaming responses (SSE)
- [ ] Message search
- [ ] Enhanced table rendering

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit with conventional commits (`feat:`, `fix:`, `refactor:`, `docs:`, `chore:`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

See [AGENTS.md](AGENTS.md) for detailed development guidelines.

## Feedback

If you encounter any bugs or have suggestions, please file an issue in the
[issue tracker](https://github.com/wsdjeg/Nova/issues).

## Credits

- [chat.nvim](https://github.com/wsdjeg/chat.nvim) - the Neovim AI chat plugin
- [Vosk](https://alphacephei.com/vosk/) - offline speech recognition
- [Markwon](https://github.com/noties/markwon) - Android Markdown library
- [ZXing](https://github.com/zxing/zxing) - QR code generation

## License

Licensed under [GPL-3.0](LICENSE).

