# Unified File Exploration & File Opening Across GVONE Browser

Unifies the file exploration and file opening experience between the bottom-right three-dots menu ("Files & Storage" / `GVONEFileBrowserSheet`), the Inspector Panel (`SafariActionsSheet` / `XcodeInspectorPanel`), the IDE primary sidebar Explorer (`IdeSidebar.kt`), the Xcode Workspace Editor (`XcodeWorkspaceEditor.kt` / `GVONEIdeScreen.kt`), and the Tab Overview ("Files & Workspace" / `TabOverviewScreen.kt`).

---

## Completed Architecture & Behaviors

### 1. Folder Interaction & Subfolder Drilling
- **Before**: Clicking a folder in the IDE Explorer or Tab Overview attempted to open the directory path as a file in an editor tab, causing errors or doing nothing.
- **Unified Functionality**:
  - Clicking any directory in `UnifiedFileExplorerView`, `IdeSidebar`, or `TabOverviewScreen` navigates directly into the subfolder (`currentFolder = file.path`).
  - Displays a clean breadcrumb navigation bar showing the current directory path (e.g. `Projects/KotlinCalc`).
  - Includes a Back button (`← Up to parent`) and a Root button (`Workspace Root`) to quickly navigate up the directory hierarchy.
  - Folder rows and cards are highlighted with amber folder icons (`#F59E0B`), "FOLDER" badge, and a right chevron (`›`) indicating navigation.

### 2. File Opening & Unified Image Experience
- **Before**: Clicking an image file in the IDE or Tab Overview tried to decode binary bytes into a UTF-8 text string buffer in `BasicTextField`, resulting in garbled text or crashes. Meanwhile, the three-dots menu opened images in a browser tab (`gvone-file://${file.path}`) using `GVONEFileViewerScreen` and `ImageFileViewer`.
- **Unified Functionality**:
  - In `UnifiedFileExplorerView` (hosted in the Inspector Panel and action sheets), clicking a file opens it via `onOpenFileInTab(file, inNewTab = false)`, matching the exact `gvone-file://${file.path}` tab experience with full path header and `ImageFileViewer`.
  - In `XcodeWorkspaceEditor` and `GVONEIdeScreen`, opening an image tab detects `FileType.IMAGE` (or `png`, `jpg`, `jpeg`, `webp`, `gif`, `svg`, `bmp`) and renders `ImageFileViewer` with bitmap zoom, path metadata, and dimensions instead of raw text bytes.
  - In `TabOverviewScreen`, `TabFileCard` now decodes and displays an image thumbnail preview, displays an "IMG" badge in pink (`#EC4899`), and clicking the card launches the file in a browser tab with `gvone-file://${file.path}`.
  - In `FilePreviewDialog`, images are rendered with full bitmap preview, resolution dimensions (`W × H px`), formatted size, and direct buttons to open in a tab or in a new tab.

---

## Modified & Created Files
- `com.example.ui.screens.files.UnifiedFileExplorerView.kt`: Reusable file & subfolder browser with breadcrumbs, storage pills, and quick previews.
- `com.example.ui.screens.ide.IdeSidebar.kt`: ExplorerView updated with `currentFolder` state, breadcrumbs bar, back navigation, and folder-drill clicks.
- `com.example.ui.screens.ide.GVONEIdeScreen.kt`: Binary-safe file loading for images, and `ImageFileViewer` integration in the active tab workspace pane.
- `com.example.ui.screens.ide.XcodeWorkspaceEditor.kt`: Image tab detection rendering `ImageFileViewer`.
- `com.example.ui.screens.ide.XcodeInspectorPanel.kt` & `XcodeLayoutModels.kt`: Added `XcodeInspectorTab.FILES` tab powered by `UnifiedFileExplorerView`.
- `com.example.ui.screens.SafariActionsSheet.kt`: Added `SafariActionTab.FILES` tab powered by `UnifiedFileExplorerView`.
- `com.example.ui.screens.CombinedActionsInspectorSheet.kt`: Provided `fileSystem` and `onOpenFileInTab` to the Inspector.
- `com.example.ui.screens.TabOverviewScreen.kt`: Subfolder breadcrumb bar, folder drilling in search, groups, and workspace files, image thumbnail card rendering, and image preview dialog.
