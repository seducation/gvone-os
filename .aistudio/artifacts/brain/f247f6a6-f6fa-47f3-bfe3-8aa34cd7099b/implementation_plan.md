# GVONE Code Studio: Tabs Navigation Integration, Extensions Manager & Grok-Style Multi-Bot System

Integrates GVONE Code Studio directly into the bottom-left navigation tabs overview so tapping files launches the IDE, adds an in-IDE Extensions Manager (Prettier, ESLint, GitLens, Live Preview, Language Tools), and builds a Grok-style Multi-Bot AI system that interacts with open tabs, project files, and chat history.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following user preferences have been aligned and confirmed for this phase:

- **Direct IDE Launch from Bottom-Left Tabs Navigation**:
  - In the Tab Overview (accessed via the bottom-left tabs button on the floating bar), tapping any file in the "Files & Workspace" view or inside tab groups opens it directly in Code Studio IDE.
  - A prominent "Open Code Studio" launcher button with badge is placed in the Tab Overview header and Files section for instant IDE access.
- **IDE Extensions Manager**:
  - Built-in Extensions view in the IDE Activity Bar allowing users to browse, toggle, and configure extensions:
    1. **Prettier Code Formatter**: Auto-formats Kotlin, Python, JS, HTML, CSS, JSON with clean indentation.
    2. **ESLint & Code Linter**: Real-time syntax issue, warning, and TODO/FIXME detection.
    3. **GitLens**: Working tree diff inspector, commit history, and branch status.
    4. **Live Preview Web Server**: One-tap launch to preview HTML/JS web files directly inside a new browser tab.
    5. **Language Tools**: Autocomplete snippets and syntax validation for Kotlin, Python, and JavaScript.
- **Grok-Style Multi-Bot AI Coding System**:
  - A multi-persona AI bot selector embedded into the IDE with access to open tabs, project files, and chats:
    1. **Grok Coder**: Witty, direct, hyper-intelligent algorithmic optimizer and deep coder.
    2. **Code Architect**: Structural planner focusing on system design, clean architecture, and modularity.
    3. **Bug Hunter & Security Auditor**: Strict auditor scanning for edge cases, nullability, and security risks.
    4. **Test & Refactor Specialist**: Generates unit tests and modernizes legacy code.
  - Features file context attachment (`@file`), tab context attachment (`@tab`), and one-click **Apply to Editor** or **Create New File**.

---

## 1. Overview & Core Concept

- **What It Does**: Bridges the browser's navigation panel directly into the IDE workflow. Users tapping files in their tab groups or workspace view transition directly into Code Studio with the file open. Inside the IDE, users can install/toggle extensions and converse with Grok-style AI bots that understand the browser's active tabs, chats, and files.
- **Target Audience / Persona**: Developers who want a unified mobile dev studio where files, browsing tabs, terminal sessions, and AI bots work together seamlessly.
- **Key Value**: Closes the discovery gap by making Code Studio instantly accessible from the primary bottom-left tabs button, adding real IDE tooling (extensions), and elevating AI assistance with multiple specialized bots.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Opening IDE from Tabs Navigation**:
   - User taps the bottom-left tabs icon on the Floating Address Bar, opening `TabOverviewScreen`.
   - In the "Files" tab (or inside a tab group containing files), tapping any file card immediately launches `GVONEIdeScreen` with that file loaded into the editor.
   - Alternatively, tapping the "Code Studio IDE" banner card in Tab Overview opens the IDE with the workspace.
2. **Managing Extensions**:
   - In the IDE's left Activity Bar, tapping the **Extensions** icon opens the Extensions Manager.
   - Shows active and recommended extensions (Prettier, ESLint, GitLens, Live Preview, Language Tools).
   - Tapping "Enable/Disable" toggles extension behavior; tapping "Run Prettier" formats the document; tapping "Live Preview" opens the current HTML file in a new browser tab.
3. **Conversing with Grok-Style Multi-Bots**:
   - Tapping the **AI Bot** icon in the Activity Bar or bottom panel displays the Bot Switcher.
   - User selects their desired bot persona:
     - *Grok Coder*: Bold, unfiltered, high-iq algorithmic programmer.
     - *Code Architect*: Senior engineer specializing in clean patterns and structure.
     - *Bug Hunter*: Paranoid security auditor finding edge cases.
     - *Test Specialist*: Unit test and regression coverage specialist.
   - The user asks questions or selects quick action chips.
   - The bot responds with code snippets, diffs, and explanations, accompanied by an "Apply to Editor" button that updates the file in place.

### Visual Identity & Theme

- **Design System**: Seamless integration with GVONE Browser's signature dark glassmorphic cyan/emerald aesthetic (`#00E5FF`, `#10B981`, `#0F172A`).
- **Activity Bar & Sidebar**: Monospace typography for file paths and code; high-contrast category badges; glowing active tab pills.
- **Extensions Cards**: Crisp cards with extension icons, publisher tags, status pills, and toggle switches.
- **Bot Cards**: Distinct avatar badges, persona descriptions, and styled response bubbles with monospace syntax blocks.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Direct File Click Routing to IDE**
  - *Chosen Approach*: Update `TabOverviewScreen` so clicking any file item forwards to `onOpenFileInIde`, closing the tab overview and opening `GVONEIdeScreen` with that file active.
  - *Why*: Directly aligns with user requirement ("On clicking the file to open ide") and prevents confusion over where the IDE is located.
- **Decision 2: Built-in Modular Extensions Engine**
  - *Chosen Approach*: Implement an extensible registry of built-in extensions (Prettier, ESLint, GitLens, Live Preview, Language Tools) with reactive state and action handlers.
  - *Why*: Provides genuine IDE utility (formatting, linting, git diffing, web previewing) without requiring external heavy runtimes.
- **Decision 3: Grok-Style Multi-Bot Personas with Context**
  - *Chosen Approach*: Build a persona switcher that dynamically injects specialized system prompts (Grok Coder, Architect, Bug Hunter, Test Specialist) and context from active tabs, chats, and workspace files into `GVONEAIService`.
  - *Why*: Matches the user's explicit preference for "multiple bot like grok bots" while leveraging existing Gemini API infrastructure.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│      Bottom-Left Navigation Panel (TabOverviewScreen / Tabs Group)     │
│       ├── Tabs Grid                                                    │
│       ├── Chats Grid                                                   │
│       └── Files & Workspace Grid ────[Tap File / IDE Banner]───────────┐
└────────────────────────────────────────────────────────────────────────┘
                                                                         │
                                                                         ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        GVONE Code Studio (IDE)                         │
├────────────────────────────────────────────────────────────────────────┤
│ Top Bar: Breadcrumbs | Format | Save | Live Preview | Close            │
├──────────┬─────────────────────────────────────────────────────────────┤
│ Activity │ Primary Sidebar (Switchable Views):                         │
│   Bar    │   ├── Explorer (Project tree, New file/folder)              │
│  [📁]    │   ├── Search (Global text search)                           │
│  [🔍]    │   ├── Source Control (GitLens, staging, commits)            │
│  [🧩]    │   ├── Extensions Manager (Prettier, ESLint, GitLens, Live)  │
│  [🤖]    │   └── Grok Multi-Bot Copilot (Grok, Architect, Bug Hunter)  │
│          ├─────────────────────────────────────────────────────────────┤
│          │ Editor Group (Multi-tab buffer, line numbers, syntax)       │
│          ├─────────────────────────────────────────────────────────────┤
│          │ Dockable Bottom Panel (Terminal, Output, Problems, Bot Chat)│
├──────────┴─────────────────────────────────────────────────────────────┤
│ Status Bar: Git Branch | Ln, Col | Encoding | Language | Active Bot    │
└────────────────────────────────────────────────────────────────────────┘
                                    │
               ┌────────────────────┴────────────────────┐
               ▼                                         ▼
┌───────────────────────────────┐         ┌──────────────────────────────┐
│ GVONEFileSystem & GitManager  │         │ GVONEAIService (Gemini)      │
│ (Disk persistence & changes)  │         │ (Multi-persona Grok bots)    │
└───────────────────────────────┘         └──────────────────────────────┘
```

### Data Model & State

- **`IdeExtension`**: Represents an IDE extension with `id`, `name`, `description`, `icon`, `isEnabled`, `category`, and executable action (e.g. Format Document, Run Linter, Preview Webpage).
- **`GrokBotPersona`**: Defines an AI bot persona (`GROK_CODER`, `CODE_ARCHITECT`, `BUG_HUNTER`, `TEST_SPECIALIST`) with system prompts, avatar, tone, and capabilities.
- **`BotChatMessage`**: Chat message with sender, timestamp, code blocks, and one-tap action to apply to editor.
- **`IdeTab`**: Represents open files with dirty state, cursor, and language mode.

### Interactive Component & State Mapping

- **Tabs Overview File Card**: Tapping a file invokes `onOpenFileInIde(file)` -> sets active tab in IDE and switches `activeSheet` to `ActiveSheet.Ide`.
- **Extensions Panel**: Toggling switches dynamically enables/disables formatting, linting, and git features; tapping "Run Prettier" formats the buffer; tapping "Live Preview" creates a browser tab loading the active HTML/JS file.
- **Grok Bot Switcher**: Switching bots alters the prompt personality and response style; bot responses with code include an "Apply Code to File" button that updates the editor state in real time.
