# Nova Android Chat App - Project Guide

## Tech Stack

| Item | Detail |
|------|--------|
| Language | Java |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 34 (Android 14) |
| Build Tool | Gradle 8.0 |
| Network Library | OkHttp 4.12.0 |
| Package Name | net.wsdjeg.nova |

## Core Modules

```
app/src/main/java/net/wsdjeg/nova/
├── ChatActivity.java          # Chat screen
├── SessionListActivity.java   # Session list
├── SessionSettingsActivity.java
├── SettingsActivity.java
├── AccountManagerActivity.java
├── AccountEditActivity.java
├── AboutActivity.java
├── ApiClient.java             # HTTP API client
├── SettingsManager.java       # Settings management
├── SessionManager.java        # Session management
├── AccountManager.java        # Account management
├── Message/Session/Account    # Data models
├── *Adapter.java              # RecyclerView adapters
├── TimeUtils.java
└── NovaApplication.java
```

---

## Development Rules

### File Modification

**Must use `action="overwrite"` to rewrite the entire file.**

Forbidden operations: replace, insert, delete (cause line number misalignment and code corruption).

### CHANGELOG.md Protection Rule (Strictly Enforced)

**CHANGELOG.md may only be modified during an official release!**

- Do NOT create, modify, or update CHANGELOG.md during development.
- Do NOT touch CHANGELOG.md in feat/fix/refactor/docs or any daily commits.
- CHANGELOG.md may only be modified when executing the "Official Release Steps".
- During a release, use `git log` to compile commit history and write entries by category.

Violating this rule leads to:
1. CHANGELOG content out of sync with actual releases.
2. Redundant rework during release preparation.
3. Development-phase entries polluting the official release record.

### Mandatory Workflow: Verify -> Add -> Commit -> Push

**After every file modification, the following workflow must be executed automatically without waiting for user confirmation.**

```
Modify File -> Verify -> git add -> git commit -> git push
```

#### Correct Flow

```
1. Modify file (using @write_file or other tools)
2. Verify modification (use @read_file to confirm correctness)
3. @git_add path="modified_file" — wait for result
4. @git_commit message="feat: description" — wait for result
5. @git_push — wait for result
6. Done! Inform the user that changes have been pushed.
```

#### Forbidden Behaviors

- Modifying a file without committing or pushing, waiting for the user to ask.
- Committing without pushing.
- Skipping the verification step before committing.
- Sending multiple git commands in a single batch.
- Modifying CHANGELOG.md during development.

#### Checklist

After each modification, confirm:

- [ ] **Verify**: Use @read_file to confirm the change is correct.
- [ ] **Add**: @git_add to stage the file.
- [ ] **Commit**: @git_commit with conventional commit format.
- [ ] **Push**: @git_push to push to remote.

### Commit Message Format

```
feat: new feature
fix: bug fix
refactor: code refactoring
docs: documentation update
chore: build/tooling
```

### Code Modification Principle

Before modifying any class/function/variable, always use `@read_file` or `@search_text` to inspect the source code and confirm its existence.

Never call methods from memory or guess.

### API Documentation

Fetch the latest API documentation online each time:

```
@fetch_web url="https://raw.githubusercontent.com/wsdjeg/chat.nvim/refs/heads/master/docs/api/http.md"
```

Do NOT download the documentation to local storage. Always fetch online to ensure the latest version.

---

## Version Management & Release Process

### Version Numbers

Version numbers are maintained in `app/build.gradle`:

| Property | Description | Example |
|----------|-------------|---------|
| `versionCode` | Integer, incremented each release | `3` |
| `versionName` | Semantic version, `-dev` suffix during development | `"3.0-dev"` / `"3.0.0"` |

### Version Lifecycle

```
Development Phase                        Release Phase
┌────────────────────────┐              ┌────────────────────────┐
│ versionName has -dev   │              │ Remove -dev suffix     │
│ suffix                 │  ─────────>  │ Compile CHANGELOG      │
│ e.g. "3.0-dev"         │              │ Update README          │
│                        │              │ Commit chore: release   │
└────────────────────────┘              └────────────────────────┘
```

### CI/CD Auto-Release (`.github/workflows/release.yml`)

Three trigger scenarios:

| Trigger | Action |
|---------|--------|
| **Pull Request** | Build validation only, no release. |
| **Push to master** (non-release commit) | Auto-create/update prerelease. APK named `Nova-v{version}-{sha}.apk`. |
| **Push to master** (commit message contains `chore: release`) | Auto-create official Release + tag. APK named `Nova-v{version}.apk`. CHANGELOG attached. |

> **Note**: No manual tag creation or push is needed. CI creates tags automatically via `gh release create --target`.
> Tags created with `GITHUB_TOKEN` do not trigger a new workflow run, avoiding duplicate builds.

### CI Signing Rule (Strictly Enforced)

**Do NOT use GitHub Secret to store the debug keystore!**

- The debug keystore must be cached using `actions/cache` with key `android-debug-keystore-v1`.
- Do NOT base64-encode the keystore into a GitHub Secret (e.g. `DEBUG_KEYSTORE_BASE64`).
- Do NOT reference any keystore-related Secret in the workflow.
- When cache miss occurs, generate a new keystore using `keytool`.

Rationale: GitHub Secrets have size limits, are complex to manage, and Secret rotation causes signing inconsistency. The cache approach is simpler, more reliable, and sufficient for debug signing consistency.

### Official Release Steps

Example: releasing v3.0:

```
1. Update app/build.gradle
   versionName "3.0-dev" -> "3.0.0"
   (versionCode: keep current or +1)

2. Update CHANGELOG.md
   Add a ## [v3.0.0] section
   Categorize all commits since the last release:
     ### feat (New Features)
     ### fix (Bug Fixes)
     ### style (Style Adjustments)
     ### refactor (Code Refactoring)
     ### docs (Documentation Updates)
     ### chore (Build/Tooling)
   Format: - {commit_hash} {commit_message}

3. Update README.md
   Add new feature descriptions
   Update project structure section

4. Commit and push (CI auto-creates tag + Release)
   git add app/build.gradle CHANGELOG.md README.md
   git commit -m "chore: release v3.0.0"
   git push

   No manual tag creation or push needed!
   CI detects the "chore: release" commit and automatically:
   - Builds the APK
   - Creates the v3.0.0 tag (via gh release create --target)
   - Creates an official GitHub Release with CHANGELOG attached
```

### Development Phase Version Bump

When starting a new development cycle:

```
1. Update app/build.gradle
   versionCode +1
   versionName "X.0.0" -> "X+1.0-dev"

2. Commit and push
   git add app/build.gradle
   git commit -m "chore: bump version to X+1.0-dev"
   git push
```

### Tag Naming Convention

- Official release: `v1.0`, `v2.0`, `v3.0` (no patch number)
- Prerelease: `prerelease` (fixed name, auto-updated on each push to master)
- All tags are created automatically by CI; no manual operation required.

---

## Resource Files

### Layouts (res/layout/)
- activity_chat.xml, activity_session_list.xml, activity_session_settings.xml
- activity_settings.xml, activity_account_manager.xml, activity_account_edit.xml
- activity_about.xml, item_*.xml

### Menus (res/menu/)
- chat_menu.xml, main_menu.xml, session_list_menu.xml
- session_settings_menu.xml, account_manager_menu.xml

### Drawables
- ai_message_bg.xml, user_message_bg.xml, send_button_bg.xml
- ic_launcher*.xml, ic_arrow_down.xml, ic_more_vert_white.xml
- color_circle_0~7.xml

---

## Notes

1. versionCode/versionName are in `app/build.gradle`.
2. INTERNET permission is required for network requests.
3. JDK 11+ is required for building.
4. Dark mode is supported (values-night/).

