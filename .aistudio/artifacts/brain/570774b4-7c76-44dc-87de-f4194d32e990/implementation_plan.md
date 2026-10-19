# Unified Floating Address Bar, Terminal Synchronization & Advanced Agent IDE

A cohesive architecture unifying the Floating Address Bar and Terminal, resolving the launch crash, enabling IDE-style project/directory scoping with autonomous multi-file generation & execution, and elevating Agent Mode with live step logs, approval gates, and interactive AI questioning.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following user preferences from Phase 1 have been integrated as the primary specifications:

- **Confirmed Decision 1 (Address Bar & Terminal Synchronization)**: Writing or typing in the Floating Address Bar directly synchronizes into the Terminal command input in real-time, providing bi-directional coordination between URL/prompt navigation and terminal commands.
- **Confirmed Decision 2 (Target Project / Directory Chip & IDE File Operations)**: Selecting a directory/project chip in the suggestion bar scopes all operations to that workspace path. The AI/Terminal can create new files (e.g. `.html`, `.kt`, `.js`), edit existing files, and execute/preview them directly like a native IDE.
- **Confirmed Decision 3 (Advanced Agent Mode & Interactive Approval Gates)**: When Agent Mode is active, natural language prompts (e.g., "create a responsive landing page .html") trigger autonomous multi-file generation with step-by-step inspection, live execution logs, bug determination, and approval gates.
- **Confirmed Decision 4 (Build Icon & Action/Command Panel)**: Tapping the "Build" icon expands an Action and Command Panel where AI questions, requirement clarifications, improvements, and approval gates are presented as interactive modals/cards.

---

## 1. Overview & Core Concept

### What It Does
1. **Crash-Free Terminal Launch**: Eliminates the crash triggered when tapping "Terminal" inside the Control Center / Address Bar by ensuring proper initialization of `TerminalRepository`, `ActiveSheet.Terminal`, safe coroutine lifecycle handling, and robust bottom sheet transitions.
2. **Synchronized Floating Address Bar & Terminal**:
   - The Floating Address Bar stays synchronized with the Terminal input stream: typing queries, shell commands, or URLs in the bar reflects immediately in the Terminal runner.
   - A synchronized status pill shows active execution status (Idle, Running, Awaiting Approval, Error).
3. **Project & Directory Selection Chip with IDE Workspace Scoping**:
   - Suggestion chips display target projects and folders (`/workspace`, `/src`, `/web`, custom project roots).
   - Once selected, the terminal shell and Agent executor bind their working directory to that target.
   - Users and AI can generate multiple files simultaneously, edit files, and launch previews (e.g., live preview of HTML/CSS in WebView or shell execution).
4. **Improvised Agent Mode with Step Inspection & Interactive Gates**:
   - Deep agent execution engine showing live step progression (Plan -> Generate -> Validate -> Test -> Fix).
   - Interactive Approval Gates: pauses before critical file writes or terminal shell commands, allowing user approval/rejection.
   - Interactive AI Clarification Panel: when the Agent detects ambiguous requirements or potential bugs, it presents structured questions with selectable chips directly within the expanded Build & Command Panel.

### Target Audience & Persona
Developers, technical creators, and power users who want an integrated mobile IDE, browser, and AI coding agent in a unified Android interface.

### Key Value
Seamless transition between browsing, terminal scripting, multi-file code authoring, and autonomous AI-assisted development without app instability or fragmented workflows.

---

## 2. User Experience & Visual Design

### Key User Flows

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. ADDRESS BAR / TERMINAL SYNC                                              │
│ [ ⚡ Terminal /workspace/web | create index.html and style.css          ] │
│   │                                                                         │
│   ▼ Tapping / Typing syncs directly to Terminal Input & active directory    │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 2. PROJECT / DIRECTORY TARGET CHIP BAR                                     │
│ [ 📁 MyProject ] [ 📂 /src ] [ 📂 /web ] [ ➕ New Target ] [ 🚀 Run Preview ]│
│   │                                                                         │
│   ▼ Selecting chip scopes file system & AI workspace                        │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 3. AGENT MODE EXECUTION WITH STEP INSPECTOR                                 │
│ [ 🤖 Agent Mode ON ]  Task: "Build modern dashboard with html/css/js"       │
│  ├─ ✅ Step 1: Scaffolding project structure (/web/index.html, app.js)      │
│  ├─ ⚙️ Step 2: Generating responsive layout and styles                      │
│  ├─ 🛑 Approval Gate: "Write 3 files and run preview?" [ Approve | Deny ]  │
│  └─ 💬 AI Question Card: "Include dark mode toggle?" [ Yes (Rec) | No ]     │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Aesthetic Direction & Visual Identity
- **Theme**: High-precision Developer Dark Mode with Material Design 3 surface tokens.
- **Color Tokens**:
  - `Surface`: `#121316` (Deep Obsidian)
  - `Surface Container High`: `#1E1F24` (Subtle dark elevation)
  - `Primary / Accent`: `#64B5F6` (Electric Cyan/Blue for active focus & terminal prompts)
  - `Agent Accent`: `#B388FF` (Cosmic Violet for Agent Mode, planning, and autonomous steps)
  - `Approval Gate / Warning`: `#FFB74D` (Amber warning for user confirmation gates)
  - `Success / Logs`: `#81C784` (Terminal emerald green for completed steps and outputs)
  - `Error / Bug Detection`: `#E57373` (Soft crimson for bug alerts)
- **Typography**:
  - Code & Terminal: Monospace font family (`FontFamily.Monospace`) for commands, logs, file paths, and diffs.
  - UI Labels & Headers: Clean sans-serif with distinct weight hierarchies (Medium 14sp for chips, Bold 16sp for card headers).
- **Layout & Density**:
  - Spacing adhering strictly to 8dp grid with minimum touch target size of 48dp.
  - Floating Address Bar with smooth elevation, embedded directory pill, and synchronized action triggers.
  - Build & Command Panel expandable via the hammer/build icon (`Icons.Default.Build` or `BuildCircle`).

---

## 3. Key Product Decisions & Trade-Offs

### Decision 1: Root Cause & Resolution of the Terminal Launch Crash
- **Chosen Approach**: Wrap terminal sheet invocation in safe state verification:
  1. Guard `ActiveSheet.Terminal` transitions against uninitialized `TerminalRepository` state.
  2. Verify coroutine scope in `TerminalScreen` when collecting command flows.
  3. Ensure `ModalBottomSheet` or docked panel state does not encounter `IllegalStateException` on empty backstack or null initial directory.
- **Why**: Prevents runtime crashes when clicking Terminal in the Control Center while allowing graceful fallback to default workspace directory `/workspace`.
- **Alternatives Considered**: Disabling terminal launching from the control sheet was rejected because the user explicitly wants direct access.

### Decision 2: Real-Time Address Bar to Terminal Synchronization
- **Chosen Approach**: Two-way reactive synchronization between `BrowserViewModel.addressBarText` and `TerminalViewModel.commandInput`. When Terminal is active, typing in the floating address bar immediately updates the terminal draft command, and terminal execution updates the address bar status indicator.
- **Why**: Fulfills the user's explicit request ("writing in address bar will send in terminal") without forcing users to open full keyboard in two separate places.
- **Alternatives Considered**: One-way push button was rejected as sluggish and disjointed.

### Decision 3: IDE Workspace Scoping with Multi-File Generation
- **Chosen Approach**: An IDE Target Chip Bar above the terminal/address bar that lets users select projects or directories (`TargetWorkspace`). Selecting a chip:
  1. Changes terminal `pwd` (`cd <path>`).
  2. Directs the AI Agent to generate/edit files inside that specific folder.
  3. Emits structured multi-file creation operations (`FileWriteOperation(path, content)`).
  4. Provides a "Run / Preview" button to render generated `.html` in WebView or run scripts in the terminal shell.
- **Why**: Delivers an intuitive IDE experience on mobile without overwhelming complex menus.

### Decision 4: Agent Mode with Approval Gates & Interactive AI Questions
- **Chosen Approach**: An autonomous multi-step agent loop with:
  1. **Step Inspection**: Real-time step progress (`StepType.PLAN`, `StepType.FILE_CREATE`, `StepType.VALIDATE`, `StepType.EXECUTE`).
  2. **Approval Gates**: Critical actions (e.g. overwriting files, running destructive shell commands) pause and display an interactive approval card with diff preview and `Approve / Reject` buttons.
  3. **Interactive AI Questions**: When the Agent needs clarification (e.g., choice between CSS frameworks or HTML templates), it renders interactive question chips directly in the expanded Build Panel.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                           UI LAYER (Jetpack Compose)                          │
├───────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────┐       ┌────────────────────────────────────┐ │
│  │     FloatingAddressBar      │◄─────►│        TerminalScreen / Sheet      │ │
│  │   (Synced Input & Status)   │       │   (Shell, Agent Mode, Step Logs)   │ │
│  └──────────────┬──────────────┘       └──────────────────┬─────────────────┘ │
│                 │                                         │                   │
│                 ▼                                         ▼                   │
│  ┌─────────────────────────────┐       ┌────────────────────────────────────┐ │
│  │   Target Directory Chips    │       │     Build & Command Panel          │ │
│  │ (Projects, Folders, Scopes) │       │ (Approval Gates, AI Question Cards)│ │
│  └──────────────┬──────────────┘       └──────────────────┬─────────────────┘ │
└─────────────────┼─────────────────────────────────────────┼───────────────────┘
                  │                                         │
                  ▼                                         ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                         VIEWMODEL & ORCHESTRATION LAYER                       │
├───────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────┐       ┌────────────────────────────────────┐ │
│  │     BrowserViewModel        │◄─────►│        TerminalRepository          │ │
│  │   (ActiveSheet, SyncedText) │       │ (Sessions, Shell Commands, Output) │ │
│  └──────────────┬──────────────┘       └──────────────────┬─────────────────┘ │
│                 │                                         │                   │
│                 ▼                                         ▼                   │
│  ┌──────────────────────────────────────────────────────────────────────────┐ │
│  │                       AgentOrchestrator / AgentRuntime                   │ │
│  │  (Nodal Planner, Multi-File Generation, Step Logger, Approval Gates)     │ │
│  └──────────────────────────────────────┬───────────────────────────────────┘ │
└─────────────────────────────────────────┼─────────────────────────────────────┘
                                          │
                                          ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                         DATA & EXECUTION LAYER                                │
├───────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────┐       ┌────────────────────────────────────┐ │
│  │     GVONEFileSystem         │       │     TerminalShellEngine            │ │
│  │ (Multi-File Create, Edit)   │       │ (Command Execution, Shell Output)  │ │
│  └─────────────────────────────┘       └────────────────────────────────────┘ │
│  ┌─────────────────────────────┐       ┌────────────────────────────────────┐ │
│  │    GVONEAIService (Gemini)  │       │     WebView / Browser Preview      │ │
│  │ (Prompt Reasoning, Q&A)     │       │ (HTML/CSS/JS Live Render Bridge)   │ │
│  └─────────────────────────────┘       └────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────────────────┘
```

### Core Entities & State Models

```kotlin
// Target workspace scoping
data class TargetDirectory(
    val id: String,
    val name: String,
    val absolutePath: String,
    val isProjectRoot: Boolean = false,
    val fileCount: Int = 0
)

// Agent Step with inspection & approval gates
data class AgentTaskStep(
    val id: String,
    val title: String,
    val description: String,
    val type: AgentStepType, // PLAN, CREATE_FILE, EDIT_FILE, RUN_COMMAND, BUG_CHECK
    val status: AgentStepStatus, // PENDING, RUNNING, AWAITING_APPROVAL, COMPLETED, FAILED
    val targetFile: String? = null,
    val codeDiff: String? = null,
    val shellCommand: String? = null,
    val approvalRequired: Boolean = false
)

// Interactive AI Question in the Build/Command panel
data class AgentQuestionPrompt(
    val id: String,
    val question: String,
    val options: List<String>,
    val recommendedIndex: Int = 0,
    val isMultiSelect: Boolean = false,
    val onAnswerSelected: (String) -> Unit
)
```

### Verification & Testing Plan
1. **Crash Fix Verification**: Verify tapping "Terminal" from the Control Action Sheet and Address Bar opens smoothly without unhandled exceptions or state desync.
2. **Synchronized Address Bar Input**: Verify typing in Floating Address Bar reflects immediately in the Terminal command line and vice-versa.
3. **Directory Target Selection**: Switch between target directory chips (`/workspace`, `/web`, custom projects) and verify terminal prompt path and file system scoping update immediately.
4. **IDE Multi-File Creation**: Issue prompt (e.g., "create a portfolio website with index.html, style.css, and script.js"), verify multiple files are generated in the targeted folder, and preview them in the browser WebView.
5. **Agent Mode Step Inspection & Approval Gates**: Verify that running Agent Mode displays live step indicators, bug checks, interactive approval modals, and that the Build icon expands the Command/Question Panel.
6. **Compilation**: Execute `compile_applet` to ensure full build pass with zero compile warnings or missing symbols.
