# GVONE OS Agent Operational Guidelines & Invariants

## Autonomous Full-Cycle Code Delivery Workflow

Whenever the user or terminal input tasks the assistant with writing or implementing code (e.g., "write an HTML calculator", "build a Kotlin screen", "create a script"):

1. **Write Complete, Standalone Code**:
   - Provide complete, self-contained, working code without missing parts or placeholders (e.g. no `<!-- TODO: add buttons -->`).
   - Include all necessary HTML, CSS, JavaScript, or Kotlin definitions.

2. **Autonomous File Generation**:
   - Persist the generated code directly to the target project directory (defaulting to `Projects/<filename>` or the active workspace folder).
   - Never stop at simply printing markdown code blocks if file creation is implied or requested.

3. **Automated Bug Hunting & Quality Audit**:
   - Inspect the code immediately for:
     - Syntax, parsing, and bracket matching errors.
     - Critical edge cases (e.g., division by zero, null inputs, infinite loops, missing event listeners).
     - Responsive layout, styling flaws, and touch/mouse interaction handling.

4. **Autonomous Correction & Patching**:
   - If bugs, missing edge cases, or styling flaws are detected during the audit, patch and correct the file immediately.

5. **Verification & Preview Readiness**:
   - Clearly report the file path created, the bug audit results, and confirm that the artifact is ready to be previewed or executed in the IDE / Web Browser.

## Unified Multi-Mode Developer Tooling (DevBar)

- Always maintain and extend `DevBar.kt` (`UnifiedDeveloperBar`) as the single, universal developer bar across all workspace modes (Browser, Code Editor, Bot Chat, PDF, Image, Media, Markdown, Data Inspector).
- Whenever adding new file types or workspace viewers, adapt `DevBar.kt` to provide context-specific toolbar actions and expandable drawer tabs instead of building fragmented standalone bars.

