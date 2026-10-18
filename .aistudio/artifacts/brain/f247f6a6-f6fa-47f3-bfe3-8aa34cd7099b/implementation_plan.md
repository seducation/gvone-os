# Implementation Plan: Google Drive Integration for Projects, Git Sync & Extension Code

## Overview
This plan transitions the project's source control and repository indicators to a **Google Drive Cloud Sync** model. The Git icons are replaced with modern Drive/Cloud Drive icons across the Navigator Panel, IDE sidebar, and development bars. In addition, projects can package and send their Git commits, modified files, saved tabs, and extension code directly into a structured **Drive File Folder** (`GVONE_Drive/<ProjectName>/`).

---

## User Review Required

> [!IMPORTANT]
> The Drive folder will organize project assets into clear subfolders (`commits/`, `files/`, `saved_tabs/`, `extensions/`, and `changes/`) stored persistently in `GVONEFileSystem` (`StorageLocation.CLOUD`), enabling offline/online browsing and cloud export.

---

## Proposed Changes

### 1. Visual Icon & Identity Updates (`IdeSidebar.kt`, `GVONEIdeScreen.kt`, `TabOverviewScreen.kt`, `QuickMenuCard.kt`)
- **Replace Git Icons with Drive Icon**:
  - Replace `Icons.Rounded.Source`, `Icons.Rounded.AltRoute`, and `Icons.Rounded.AccountTree` with Google Drive / Cloud Drive icons (`Icons.Rounded.CloudSync`, `Icons.Rounded.CloudDone`, `Icons.Rounded.FolderShared`, or Drive icon badge) across the Xcode IDE sidebar, Navigator panel, and bottom development bar.
  - Rename "Source Control" tab to **"Drive & Cloud Control"** (with Drive icon badge).
  - Update status bar indicator: from "git: main" to **"Drive: <Project>/main"** or cloud sync status.

### 2. Drive Project Packaging & Storage (`GVONEDriveManager.kt`, `GVONEFileSystem.kt`)
- Create a dedicated `GVONEDriveManager` helper:
  - **`commits/`**: Saves commit snapshots, commit log (`commit_<hash>.json`), commit messages, and author info as Drive files.
  - **`saved_tabs/`**: Saves open/saved project tabs (`tabs.json` and bookmarks) into the Drive project folder.
  - **`extensions/`**: Exports installed extension code, extension manifests, and settings (Prettier, ESLint, GitLens configs, language snippets) into `extensions/<extension_id>/`.
  - **`files/` & `changes/`**: Mirrors project files and stages modified diffs into the Drive directory.
- Integrate with `StorageLocation.CLOUD` (`/Cloud/GVONE_Drive/<ProjectName>/`) so users can browse them directly in the GVONE File Browser.

### 3. "Send to Drive" / "Sync with Drive" Actions (`TabOverviewScreen.kt`, `QuickMenuCard.kt`, `IdeSidebar.kt`)
- In Project Group view & Quick Menu card:
  - Add **"Send to Drive"** (Drive icon) button to instantly sync tabs, commits, extension code, and file changes.
  - Display Drive sync status badge (e.g. `☁️ Synced to Drive: 5 files, 4 tabs, 3 extensions`).
- In the Drive & Cloud Control sidebar:
  - Provide interactive buttons to:
    - "Commit & Push to Drive"
    - "Export Extension Code to Drive"
    - "Browse in Drive Folder" (opens the Drive directory in Files viewer).

---

## Verification Plan

### Automated Build Verification:
- Run `gradle compileDebugKotlin` to verify no compile or icon errors.
- Run `compile_applet` to verify platform build integrity.

### Functional Verification:
1. Open the Navigator Panel and check that the Drive icon replaces the Git icon.
2. In the Project overview, click **"Send to Drive"**; verify that commits, saved tabs, extension code, and file changes are packaged into the Drive folder.
3. Open the Files / Drive section to verify that `commits/`, `saved_tabs/`, `extensions/`, and `files/` folders are created and readable.
4. Verify that running `.html` continues to work alongside Drive sync.
