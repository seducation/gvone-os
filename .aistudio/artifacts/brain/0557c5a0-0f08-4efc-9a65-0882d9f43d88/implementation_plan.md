# Unified Xcode-Style IDE & Browser Window Architecture

Unify GVONE Browser and GVONE Code Studio into a single cohesive, high-performance developer browser window modeled on Xcode. The application replaces separate overlay screens and standalone activity bars with an integrated workspace consisting of a top Development Bar, a bottom-left multi-tab Navigator Panel (Files, Tabs, Chats, Grok Bots, Extensions), a central Web & Code Workspace, an Xcode-style bottom Debugger/Terminal area, and a bottom-right Inspector Panel.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions have been synthesized from your answers and are ready for implementation:

- **Confirmed Decision 1 (Unified Central Stage)**: The separate IDE overlay is merged directly into the main browser window. The central workspace dynamically displays either the full web browsing engine (GVONE WebView / Environment Canvas) or the Code Editor depending on active tab selection and developer actions, governed by the Xcode-style Development Bar.
- **Confirmed Decision 2 (Bottom-Left Navigator Integration)**: The isolated VS Code activity bar is completely removed. Its capabilities are consolidated into a comprehensive Navigator Panel accessible from the bottom-left tabs button and toolbar. The panel features dedicated tabs for **Files**, **Tabs** (open browser tabs and tab groups), **Chats** (agent conversations), **Bots** (Grok-style multi-bot assistants), and **Extensions**.
- **Confirmed Decision 3 (Grok-Style Specialized Bots)**: The Navigator's Bots tab features specialized personas with distinct tasks: **Grok Coder** (algorithmic coding), **Web Scraper** (DOM and content extraction), **Bug Hunter** (security auditing), **Code Architect** (system design), and **Test Specialist** (TDD and assertions).
- **Confirmed Decision 4 (Xcode-Style Terminal & Inspector)**: The bottom terminal is enhanced with Xcode console semantics (output filtering, target process selection, clear, and interactive shell execution), paired with a bottom-right Inspector panel for page/DOM attributes, security headers, and safety rules.

---

## 1. Overview & Core Concept

GVONE transforms from a web browser with a secondary launcher IDE into a **Developer Browser**: an integrated development environment where web navigation, DOM inspection, code editing, command execution, and AI bot workflows occur inside a unified Xcode-style window.

### Key Capabilities
- **Top Development Bar**: Xcode-inspired toolbar displaying target scheme (active URL or project file), Run/Reload and Stop buttons, Tor proxy indicator, quick location capsule, and pane toggle controls.
- **Navigator Panel (Bottom-Left)**: Replaces the standalone activity bar with 5 vertical tabs:
  - *Files*: Workspace directory tree, file creation, editing, and Git status badges.
  - *Tabs*: Real-time list of all active browser tabs, private containers, and tab groups.
  - *Chats*: Multi-turn autonomous agent conversations and CLI execution history.
  - *Bots*: Grok-style bots tailored for specialized tasks (coding, scraping, debugging, architecture, testing).
  - *Extensions*: Extension marketplace and active toggles (Prettier, ESLint, GitLens, Data Saver, Live Preview).
- **Central Workspace**: Hosts live WebView pages, Environment Start Canvas, or the Monospace Code Editor with line numbers and syntax highlighting without jarring screen transitions.
- **Xcode-Style Debug Console (Bottom Area)**: Dockable terminal with filter pills (All, Target, Errors, Warnings), execution status, and shell command executor.
- **Inspector Panel (Bottom-Right)**: Inspects live DOM elements, HTTP security headers, file metadata, and CNS safety firewall rules.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Browsing with Developer Bar**:
   - The user browses the web. The top Development Bar shows the active domain, HTTPS lock, Tor routing state, and Run (Reload) button.
   - Tapping the Inspector icon reveals the right-side Inspector displaying element stats, headers, and cookies.
2. **Switching to Code & Project Files**:
   - Tapping the bottom-left Navigator button opens the Navigator Panel. The user selects a file from the *Files* tab.
   - The central workspace seamlessly transitions to the Code Editor tab while preserving active browser tabs in background memory.
3. **Task Automation with Grok Bots**:
   - In the Navigator's *Bots* tab, the user chooses between Grok Coder, Web Scraper, or Bug Hunter.
   - Tapping "Extract Page DOM" prompts Web Scraper to analyze the active WebView tab and extract clean HTML/JSON into a new project file.
4. **Xcode Terminal Execution**:
   - Tapping the Debug icon slides up the Xcode-style console with filtered stdout/stderr, command prompt, and run status.

### Visual Identity & Theme
- **Color Palette**: Dark developer slate (`#0B0E14` base, `#141C2B` panels, `#1E293B` borders), Xcode blue accent (`#0A84FF`), Tor purple (`#C084FC`), and success emerald (`#34D399`).
- **Typography**: JetBrains Monospace for code gutters, debug logs, and path breadcrumbs; System Sans-Serif with semibold weights for navigation tabs and controls.
- **Elevation & Layout**: Crisp 1dp borders, segmented toggle pills, and animated dockable panels adhering to Material 3 responsive guidelines.

---

## 3. Key Product Decisions & Trade-Offs

- **Consolidated Window vs. Modal Screens**:
  - *Chosen*: A single unified Compose container managing layout panes (Development Bar, Navigator, Center Workspace, Debug Console, Inspector).
  - *Why*: Eliminates the disjointed feeling of switching between `ActiveSheet.Ide` and `MainActivity`, allowing simultaneous visibility of browser state and code.
- **Navigator Tab Segments vs. Floating Rails**:
  - *Chosen*: Integrated segmented control inside the bottom-left Navigator panel replacing the VS Code left rail.
  - *Why*: Maximizes horizontal real estate on mobile and tablet screens while organizing Files, Tabs, Chats, Bots, and Extensions under one intuitive navigation roof.
- **Pre-configured Grok Bot Personas**:
  - *Chosen*: Dedicated Grok bots with tailored system prompts and direct actions (e.g. Scrape Page, Scan Vulnerabilities, Generate Unit Tests).
  - *Why*: Provides immediate, actionable utility rather than a generic text prompt.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        XCODE DEVELOPMENT BAR                           │
│  [▶ Run/Reload] [■ Stop] [ Target: kotlin-calc / web ] [⊞ Nav|Debug|Insp] │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
      ┌──────────────────────────────┼──────────────────────────────┐
      │                              │                              │
┌─────▼────────────────────────┐ ┌───▼──────────────────────┐ ┌─────▼────────────────────────┐
│    BOTTOM-LEFT NAVIGATOR     │ │    CENTRAL WORKSPACE     │ │    BOTTOM-RIGHT INSPECTOR    │
│  ┌────────────────────────┐  │ │                          │ │  ┌────────────────────────┐  │
│  │Files|Tabs|Chat|Bots|Ext│  │ │  ┌────────────────────┐  │ │  │Attributes | DOM | Net  │  │
│  └────────────────────────┘  │ │  │ Active WebView     │  │ │  └────────────────────────┘  │
│  • Project File Tree         │ │  │   OR               │  │ │  • Element Tag & Box Model │
│  • Open Browser Tabs         │ │  │ Monospace Editor   │  │ │  • Headers, Cookies, Tor   │
│  • Agent Dialogues           │ │  │ (Tabs strip above) │  │ │  • CNS Rules & Permissions │
│  • Grok Bots (5 personas)    │ │  └────────────────────┘  │ └──────────────────────────────┘
│  • Developer Extensions      │ └───────────┬──────────────┘
└──────────────────────────────┘             │
                                 ┌───────────▼──────────────┐
                                 │  XCODE DEBUG CONSOLE     │
                                 │  [All|Target|Err|Warn]   │
                                 │  > Shell & Stdout Logs   │
                                 └──────────────────────────┘
```

### Components to Update and Unify
1. **`MainActivity.kt`**:
   - Embed the unified Xcode window layout at the root level.
   - Replace separate `ActiveSheet.Ide` modal logic with the integrated central workspace mode.
   - Connect the Development Bar, Navigator button, Terminal button, and Inspector button.
2. **`XcodeDevelopmentBar.kt`** (in `ui/components`):
   - Xcode-style top development bar: Run/Reload, Stop, Target scheme / URL capsule, Tor status, and three toggle buttons for Navigator, Debugger, and Inspector.
3. **`XcodeNavigatorPanel.kt`** (in `ui/screens/ide`):
   - Bottom-left sliding/dockable panel containing the 5 tabs: **Files**, **Tabs**, **Chats**, **Bots**, and **Extensions**, eliminating the standalone activity bar.
4. **`XcodeInspectorPanel.kt`** (in `ui/screens/ide`):
   - Bottom-right collapsible panel providing live web and file inspection (DOM elements, page stats, security, CNS rules).
5. **`XcodeDebugConsole.kt`** (in `ui/screens/ide`):
   - Xcode-style debug console with log level filters, process info, clear actions, and shell execution.
6. **`GrokBotsEngine.kt`**:
   - Specialized bot definitions (Grok Coder, Web Scraper, Bug Hunter, Code Architect, Test Specialist) with automated workflows.
