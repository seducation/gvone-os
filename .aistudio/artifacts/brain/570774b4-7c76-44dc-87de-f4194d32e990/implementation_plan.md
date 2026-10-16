# Implementation Plan: Restoring Main Actions Panel & Unifying Bottom-Left Navigator

Align the Xcode-style workspace with the user's exact specifications: remove top horizontal tabs from the main canvas, consolidate all open tabs and project file management exclusively into the bottom-left Navigator, and restore the complete previous Main actions panel (settings, bookmarks, share, desktop mode, etc.) within a unified bottom-right sheet featuring Xcode Inspector Attribute tabs.

---

## 1. User Requirements & Structural Changes

1. **Remove Canvas Horizontal Tabs**:
   - Eliminate the top horizontal tabs strip from `XcodeWorkspaceEditor` and the browser workspace.
   - Keep the main workspace area clean, focused, and edge-to-edge.
   - The active tab or file is represented cleanly in the top Xcode status capsule (`UniversalXcodeBar`), while full tab switching occurs via the Navigator.

2. **Consolidate Tabs into Bottom-Left Navigator**:
   - Tapping the bottom-left tabs button in `FloatingAddressBar` opens `TabOverviewScreen`.
   - `TabOverviewScreen` serves as the single home for both:
     - **Web Browser Tabs & Tab Groups**: Creating new tabs, closing tabs, private browsing, and switching active tabs.
     - **Project Files & Documents**: Creating, renaming, deleting, and selecting project files for editing.
   - Selecting a file switches to the Code Editor; selecting a web page switches to Web Preview.

3. **Combined Bottom-Right Sheet (Main Actions + Inspector Attributes)**:
   - Tapping the bottom-right three-dot icon opens a combined sheet.
   - **Tab 1: "Main"**: Restores the complete previous `SafariActionsSheet` UI:
     - Share, Add Bookmark, Add to Reading List, Copy Link
     - Find on Page, Request Desktop Site, Reader Mode
     - Downloads, History, Bookmarks, and Full Settings
     - Clear browsing data and Tor circuit control
   - **Tab 2: "Attributes / Inspector"**: Xcode-style inspector with:
     - File Identity, Target, Syntax Language, Prettier formatter, line metrics (Code Editor mode)
     - DOM inspection, SSL certificate, response headers, page performance (Browser mode)
   - **Tab 3: "Dev Tools / Console"**: Quick access to terminal logs, web app bridge toggles, and developer settings.

---

## 2. Proposed Changes

### Top Bar & Canvas
#### `app/src/main/java/com/example/ui/screens/ide/XcodeWorkspaceEditor.kt`
- Remove the horizontal tabs strip composable (`LazyRow` of open files) from the top of the editor canvas.
- Display the editor content full-height with line numbers and syntax highlighting.
- Rely on the active file state updated via the bottom-left Navigator.

#### `app/src/main/java/com/example/ui/components/UniversalXcodeBar.kt`
- Keep the clean Xcode-style top bar with Run (▶), Stop (⏹), and the active Target / Scheme capsule (showing current file or current URL).
- Add a direct shortcut button on the left to toggle the bottom-left Navigator.

---

### Navigator Panel
#### `app/src/main/java/com/example/ui/screens/TabOverviewScreen.kt`
- Ensure the Navigator panel cleanly displays:
  - Tab Groups, open Web Tabs with previews and close buttons.
  - Project Files section with file tree, quick-add file button, and file delete/rename options.
  - Instant transition to either Web Preview or Code Editor upon selection.

---

### Inspector & Main Actions Sheet
#### `app/src/main/java/com/example/ui/screens/ide/XcodeInspectorPanel.kt` & `SafariActionsSheet.kt`
- Build a unified `UnifiedActionsInspectorSheet` (or enhance `SafariActionsSheet`) with a top segmented tab control:
  - **Main**: Complete existing Safari actions panel (Settings, Share, Bookmarks, History, Downloads, Desktop Site, Find on Page, Clear Data, etc.).
  - **Attributes**: Xcode inspector with File Identity, DOM elements, text attributes, Prettier format action, and security details.
  - **Console / Network**: Quick logs and developer toggles.
- Preserve all existing handlers for bookmarks, history, settings, downloads, and desktop mode.

#### `app/src/main/java/com/example/MainActivity.kt`
- Wire the bottom-right three-dot button directly to the unified sheet containing both the restored Main panel and Inspector Attribute tabs.
- Ensure state persistence between Main actions and Inspector tabs.

---

## 3. Verification Plan

### Automated Verification
- Run `compile_applet` to ensure successful compilation and strict type safety across all Compose components.
- Run `gradle :app:testDebugUnitTest` via `run_command` to verify existing unit tests pass.

### User Flow Verification
1. **Clean Workspace without Horizontal Tabs**: Verify both browser and code editor occupy the full canvas without redundant horizontal tabs.
2. **Bottom-Left Navigator**: Tap the bottom-left icon, verify tab overview and project files are displayed, and selecting an item switches the active workspace.
3. **Bottom-Right Combined Sheet**: Tap the three-dot icon, verify the "Main" tab shows all previous settings, bookmarks, share, and desktop site actions, and switching to "Attributes" displays the Xcode inspector.
