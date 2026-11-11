# fileMatrix

**fileMatrix** is an Android-native desktop-first file manager engineered specifically for **Googlebooks** (Android Desktop windowing and Chromebook freeform environments). It unifies all 5 core Googlebook file ecosystems into a high-density **Material 3 Expressive** desktop interface.

---

## 🌟 Key Features

### 1. Unified 5-Location Storage Matrix
- **Googlebook Local**: Fast NIO access (`java.nio.file`) across `/storage/emulated/0`, Downloads, Documents, Media.
- **Linux VM (Crostini / Virtualization)**: Seamless SAF tree integration + high-speed SSH/SFTP socket client.
- **Antigravity**: Direct Antigravity workspace integration with live path mapping.
- **Google Drive**: Cloud storage and virtual document streaming.
- **Mobile Files**: Connected cross-device tethering and external storage devices.
- **1-Click Mount Hub**: Guided onboarding cards in the sidebar with persistable SAF grants.

### 2. Desktop-First Browsing & Windowing
- **Material 3 Expressive Desktop Theme**: High-density typography (JetBrains Mono / monospace for paths, sizes, hashes), custom dark surfaces (`surfaceContainerLowest` to `surfaceContainerHighest`), and pointer hover states.
- **Multi-Pane & Tabbed Navigation**:
  - Resizable Dual-Pane Split container (`Ctrl+\` toggle).
  - Per-pane tab strip (`Ctrl+T` new tab, `Ctrl+W` close tab, `Ctrl+Tab` cycle).
- **3 Switchable View Modes**:
  - **Dense Table (`Ctrl+1`)**: Sortable columns (Name, Size, Modified, Permissions/POSIX), multi-select, and desktop context menus.
  - **Miller Columns (`Ctrl+2`)**: Cascading horizontal hierarchy columns with auto-scroll and an Inspector / Quick Preview panel for leaf files.
  - **Adaptive Grid (`Ctrl+3`)**: Responsive thumbnail and icon grid.
- **Interactive Breadcrumb Bar**: Path segments with 1-click jump + direct text edit mode (`Ctrl+L`).

### 3. Desktop Power Tools
- **Command Palette (`Ctrl+K` / `Ctrl+P`)**:
  - Instant workspace actions via `>` prefix (e.g. `> Toggle Dual-Pane`, `> Mount Linux VM`).
  - Federated file search querying the local Room FTS4 index and live SAF search concurrently.
- **Integrated Terminal Drawer (`` Ctrl+` ``)**:
  - Collapsible bottom dock with SSH & local shell sessions.
  - TrueColor & ANSI 16/256-color rendering.
  - **"Follow Folder" Sync**: Automatically sends `cd "<translated_posix_path>"` when navigating directories.
- **Checksum & Metadata Inspector (`Alt+Enter`)**:
  - Real-time cryptographic hashing (MD5, SHA-1, SHA-256).
  - One-click copy and instant clipboard verification with Match/Mismatch badge.
- **Batch Rename Utility (`Shift+F2`)**:
  - Regex find & replace, numbering templates (`{name}_{counter:03d}`), and case transforms with live side-by-side preview and collision detection.
- **Non-Blocking Transfer Queue**:
  - Background foreground service for large transfers between any storage roots (`Local <-> SAF`, `SAF <-> SAF`).
  - Real-time speed (MB/s), ETA calculation, Pause/Resume, and conflict resolution (Overwrite, Skip, Keep Both).

---

## 🏗️ Architecture & Modules

```
fileMatrix/
├── app/                  # Application bootstrap, MainActivity (freeform desktop window), DI
├── core/
│   ├── model/            # VfsNode, StorageRoot, TransferJob, SshProfile, Checksum models
│   ├── designsystem/     # Material 3 Expressive desktop theme, desktop tokens, context menus
│   ├── database/         # Room Database, FTS4 file index, persisted roots, SSH profiles
│   ├── vfs/              # Hybrid VFS router, LocalFileSystemProvider, SafDocumentProvider,
│   │                     # PathTranslationRegistry, TransferQueueManager & Foreground Service
│   └── terminal/         # Embedded SSH/SFTP & local shell client, ANSI parser, TerminalDrawer
└── feature/
    ├── workspace/        # Sidebar, DualPaneContainer, TabStrip, BreadcrumbBar, Table/Miller/Grid views
    └── powertools/       # Command Palette (Ctrl+K), Batch Rename (Shift+F2), Checksum Inspector
```

---

## 🚀 Building & Testing

### Prerequisites
- JDK 17
- Android SDK 35 (`platforms;android-35`, `build-tools;35.0.0`)
- Gradle 8.11.1+ (included via `./gradlew`)

### Build Commands
```bash
# Compile and generate Debug APK
./gradlew assembleDebug

# Run unit test suite
./gradlew testDebugUnitTest
```

The compiled APK will be at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📦 CI/CD & Automated GitHub Releases

Automated builds and APK releases are managed by [GitHub Actions](.github/workflows/release.yml):

- **Version Releases (`v*`)**: Pushing a tag (e.g. `git tag v1.0.0 && git push origin v1.0.0`) automatically builds, tests, and publishes a new GitHub Release with the APK (`fileMatrix-v1.0.0.apk`) and its SHA-256 checksum.
- **Rolling Debug Builds (`latest-debug`)**: Every push to `main` updates the `latest-debug` pre-release so the newest build is always accessible.
- **Manual Trigger (`workflow_dispatch`)**: Trigger a build anytime directly from the **Actions** tab on GitHub with custom tag names, draft status, and pre-release flags.

Releases and APK binaries can be downloaded at:
[https://github.com/Salmonllama/fileMatrix/releases](https://github.com/Salmonllama/fileMatrix/releases)
