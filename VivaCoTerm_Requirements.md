# Viva-CoTerm — Feature Requirements Specification

**Plugin:** HDL + Vivado JetBrains Plugin  
**Target Version:** v0.3.0 (builds on v0.2.1)  
**Author:** Tanawin (MergePanicSociety)  
**Intended Reader:** Junie AI (JetBrains) — implementation target  
**Document Version:** 1.0

---

## Table of Contents

1. [Overview](#1-overview)
2. [Glossary](#2-glossary)
3. [Architecture Overview](#3-architecture-overview)
4. [Feature Requirements](#4-feature-requirements)
   - 4.1 Vivado Process Management
   - 4.2 TCL Bridge Service
   - 4.3 Viva-CoTerm Tool Window
   - 4.4 Predefined TCL Command Library
   - 4.5 MCP Server (AI Integration)
   - 4.6 Settings Additions
5. [User Stories](#5-user-stories)
6. [Non-Functional Requirements](#6-non-functional-requirements)
7. [Out of Scope for v0.3.0](#7-out-of-scope-for-v030)
8. [Suggested File & Class Structure](#8-suggested-file--class-structure)
9. [Priority Summary](#9-priority-summary)
10. [Instructions for Junie](#10-instructions-for-junie)

---

## 1. Overview

This document specifies requirements for **Viva-CoTerm**, a new subsystem to be added to the existing HDL + Vivado JetBrains Plugin (currently at v0.2.1). Viva-CoTerm provides a dedicated, bi-directional terminal tool window inside the JetBrains IDE that connects to a live Vivado GUI session, exposes a predefined TCL command library, and integrates with AI coding assistants (Claude Code and Junie) through the MCP (Model Context Protocol) standard.

### 1.1 What the Plugin Already Does (v0.2.1)

The v0.2.1 plugin already provides the following capabilities that Viva-CoTerm builds upon:

- **Verilog / SystemVerilog language support:** syntax highlighting, brace matching, auto-completion, commenter
- **TCL / XDC language support:** syntax highlighting, code folding, auto-completion
- **External linting** via Icarus Verilog and Verilator with real-time annotations
- **`VivadoUtils.launchVivado()`** — spawns a Vivado process in GUI or batch mode with optional TCL script
- **Right-click menu actions:** Build Project, Open Project, Run TCL Script, IP Composer, Open Vitis, Create HLS Kernel
- **Persistent settings (`VivadoSettingsState`):** `vivadoPath`, `vitisPath`, `board`, `part`, `ipRepoPath`
- **HDL Settings** tool window (right panel) and **Verilog Linter Debugger** tool window (bottom panel)

### 1.2 What Viva-CoTerm Adds

Viva-CoTerm adds three new capabilities on top of v0.2.1:

1. A **dedicated Vivado Console tool window** (bottom panel) with an embedded terminal that streams stdin/stdout to a long-running Vivado process running in GUI mode simultaneously.
2. A **predefined TCL command library** with named, parameterized operations (e.g., `buildProject`, `runSynthesis`) that can be invoked from the terminal UI or programmatically.
3. An **MCP server** embedded in the plugin that exposes the TCL command library as tools, enabling Claude Code and Junie AI to drive Vivado on the user's behalf.

---

## 2. Glossary

| Term | Definition |
|---|---|
| **Viva-CoTerm** | The new Vivado Console Terminal tool window and its supporting services described in this document. |
| **Vivado Process** | The OS-level process started by `VivadoUtils.launchVivado()` running in GUI mode. |
| **TCL Bridge** | The in-plugin component that pipes stdin/stdout between the JetBrains terminal widget and the Vivado process. |
| **Predefined Command** | A named, parameterised TCL operation defined in the plugin (e.g., `buildProject`). Compiled to raw TCL at runtime. |
| **MCP Server** | A local HTTP server embedded in the plugin that implements the Model Context Protocol, exposing predefined commands as callable tools. |
| **MCP Tool** | A single callable function exposed by the MCP server (maps 1:1 to a Predefined Command). |
| **AI Agent** | Claude Code or Junie AI acting as the MCP client that calls MCP tools to drive Vivado. |
| **XPR** | Xilinx Project file (`.xpr`) — the Vivado project descriptor already handled by `OpenProjectAction`. |
| **ZCU102** | Xilinx Zynq UltraScale+ MPSoC evaluation board (`xczu9eg-ffvb1156-2-e`), used as a representative target in examples. |

---

## 3. Architecture Overview

```
JetBrains IDE
  ├── Existing: VivadoUtils, Settings, Actions, Linter, TCL/Verilog language support
  ├── [NEW] VivadoProcessManager      — singleton service managing the Vivado OS process
  ├── [NEW] TclBridgeService          — streams stdin/stdout between IDE and process
  ├── [NEW] VivaCoTermToolWindow      — tool window (bottom panel)
  ├── [NEW] PredefinedCommandLibrary  — catalog of named TCL command templates
  └── [NEW] VivaMcpServer             — embedded HTTP MCP server on localhost

Vivado (GUI mode)    <──── TclBridgeService ──────────────────────────────────┐
Claude Code / Junie  ──── MCP (HTTP) ──► VivaMcpServer ──► PredefinedCommandLibrary
```

> **Note:** `VivadoProcessManager` wraps the existing `VivadoUtils.launchVivado()` call. No changes to `VivadoUtils` are required.

---

## 4. Feature Requirements

Priority levels:
- **MUST** — required for v0.3.0 release
- **SHOULD** — strongly recommended, implement if time allows
- **COULD** — nice-to-have, defer if time is short

---

### 4.1 Vivado Process Management

These requirements govern how Vivado is launched and kept alive as a long-running background process.

| ID | Requirement | Priority | Depends On |
|---|---|---|---|
| VPM-01 | The plugin SHALL expose a singleton `VivadoProcessManager` project service that owns exactly one Vivado OS process at a time. | MUST | |
| VPM-02 | `VivadoProcessManager` SHALL launch Vivado in GUI mode (`-mode gui`) using the existing `vivadoPath` from `VivadoSettingsState`. | MUST | |
| VPM-03 | `VivadoProcessManager` SHALL also open the Vivado graphical window (GUI) at launch so the user can interact with it manually. | MUST | VPM-02 |
| VPM-04 | `VivadoProcessManager` SHALL accept an optional initial TCL script path or TCL string to source on startup (reuses `VivadoUtils` logic). | SHOULD | VPM-02 |
| VPM-05 | If a Vivado process is already running when a launch is requested, `VivadoProcessManager` SHALL prompt the user to either reuse the existing session or terminate and relaunch. | MUST | |
| VPM-06 | `VivadoProcessManager` SHALL detect process termination (natural exit or crash) and update the tool window status indicator accordingly. | MUST | VPM-02 |
| VPM-07 | `VivadoProcessManager` SHALL expose a `shutdownVivado()` method that sends the TCL command `exit` and then force-kills the process after a 5-second timeout. | MUST | |
| VPM-08 | `VivadoProcessManager` SHALL expose a `restartVivado()` method equivalent to `shutdownVivado()` followed by a fresh launch. | SHOULD | VPM-07 |

---

### 4.2 TCL Bridge Service

These requirements govern the bidirectional pipe between the IDE and the running Vivado process.

| ID | Requirement | Priority | Depends On |
|---|---|---|---|
| TCL-01 | The plugin SHALL implement a `TclBridgeService` that continuously reads stdout and stderr from the Vivado process and publishes lines to subscribers. | MUST | VPM-02 |
| TCL-02 | `TclBridgeService` SHALL provide a `sendCommand(tcl: String): Future<String>` method that writes a TCL string to Vivado's stdin and returns the response lines up to the next prompt. | MUST | TCL-01 |
| TCL-03 | `TclBridgeService` SHALL detect the Vivado TCL prompt pattern (`Vivado%` or `vivado:>`) to determine when a command has completed. | MUST | TCL-02 |
| TCL-04 | `TclBridgeService` SHALL support sending multi-line TCL blocks as a single logical command. | SHOULD | TCL-02 |
| TCL-05 | `TclBridgeService` SHALL expose an `outputFlow: Flow<String>` (Kotlin coroutine flow) that the tool window subscribes to for live output display. | MUST | TCL-01 |
| TCL-06 | `TclBridgeService` SHALL queue commands when a previous command is still executing and process them sequentially. | MUST | TCL-02 |
| TCL-07 | `TclBridgeService` SHALL include a configurable per-command timeout (default 10 minutes) after which it marks the command as timed-out and returns an error string. | SHOULD | TCL-06 |

---

### 4.3 Viva-CoTerm Tool Window

These requirements define the dedicated Vivado console tool window inside JetBrains.

| ID | Requirement | Priority | Depends On |
|---|---|---|---|
| CTW-01 | The plugin SHALL register a new tool window with id `Vivado Console` anchored at the bottom of the IDE, alongside the existing `Verilog Linter Debugger` panel. | MUST | |
| CTW-02 | The tool window SHALL contain a terminal-style scrolling output area that renders all stdout/stderr lines from `TclBridgeService` with ANSI colour support. | MUST | TCL-05 |
| CTW-03 | The tool window SHALL contain a single-line input field at the bottom where the user can type raw TCL commands and send them by pressing Enter. | MUST | TCL-02 |
| CTW-04 | The input field SHALL maintain a command history (up/down arrow keys cycle through previous commands) persistent for the IDE session. | SHOULD | |
| CTW-05 | The tool window header SHALL display a coloured status badge: 🟢 Green = Vivado running, 🟡 Yellow = starting, 🔴 Red = stopped/crashed. | MUST | VPM-06 |
| CTW-06 | The tool window SHALL include a toolbar with buttons: **Launch Vivado**, **Restart Vivado**, **Stop Vivado**, **Clear Output**. | MUST | VPM-02, VPM-07, VPM-08 |
| CTW-07 | The **Launch Vivado** button SHALL open a small dialog letting the user optionally choose an XPR file to open and an optional initial TCL script to source. | SHOULD | CTW-06 |
| CTW-08 | The tool window SHALL display timestamps (`HH:mm:ss`) on each output line, toggleable via a toolbar checkbox. | COULD | |
| CTW-09 | Output lines containing `ERROR:` or `CRITICAL WARNING:` SHALL be highlighted in red; lines containing `WARNING:` in amber. | SHOULD | |
| CTW-10 | The tool window SHALL support copying selected output text to the clipboard. | MUST | |
| CTW-11 | The tool window SHALL NOT replace the Vivado GUI window — both the JetBrains terminal and the Vivado GUI window SHALL be visible at the same time. | MUST | VPM-03 |

---

### 4.4 Predefined TCL Command Library

These requirements define the catalog of named, parameterised operations that abstract raw TCL commands. They are the foundation for both user shortcuts and AI tool calls.

| ID | Requirement | Priority | Depends On |
|---|---|---|---|
| CMD-01 | The plugin SHALL implement a `PredefinedCommandLibrary` singleton that maintains a list of `PredefinedCommand` descriptors. | MUST | |
| CMD-02 | Each `PredefinedCommand` SHALL have: a unique camelCase `id`, a human-readable `name`, a `description`, a typed parameter schema, and a TCL template generator function. | MUST | CMD-01 |
| CMD-03 | The library SHALL include at minimum the commands listed in Section 4.4.1 at initial release. | MUST | CMD-01 |
| CMD-04 | Each command SHALL be invocable from the tool window via a **Run Command** dropdown/palette in the toolbar. | SHOULD | CTW-06, CMD-01 |
| CMD-05 | The library SHALL be extensible: additional commands can be added in future versions without changing the MCP server interface. | MUST | CMD-01 |
| CMD-06 | If a required parameter is missing, the command SHALL throw a descriptive error rather than sending malformed TCL. | MUST | CMD-02 |

#### 4.4.1 Mandatory Commands at v0.3.0

Parameters shown in `<angle brackets>` are required; `[square brackets]` are optional.

| Command ID | Parameters | Generated TCL Summary | Notes |
|---|---|---|---|
| `openProject` | `<xprPath>` | `open_project {xprPath}` | Wraps existing `OpenProjectAction` logic |
| `createProject` | `<name>, <path>, <part>, [board]` | `create_project` + `set_property board_part` + `add_files` | Wraps existing `genTclCreatePrjAndAddFilesCommand` |
| `addFiles` | `<fileOrFolder>` | `add_files -norecurse {paths}` | Reuses `collectHDLFiles` if a folder is given |
| `runSynthesis` | `[jobs]` | `launch_runs synth_1 -jobs N; wait_on_run synth_1` | `jobs` defaults to 4 |
| `runImplementation` | `[jobs]` | `launch_runs impl_1 -jobs N; wait_on_run impl_1` | Requires synthesis complete |
| `generateBitstream` | — | `launch_runs impl_1 -to_step write_bitstream; wait_on_run impl_1` | |
| `buildProject` | `<name>, <path>, <part>, [board], [jobs]` | Chains `createProject` + `addFiles` + `runSynthesis` + `runImplementation` + `generateBitstream` | Full flow; example: ZCU102 with DMA |
| `programDevice` | `[bitstreamPath]` | `open_hw_manager; connect_hw_server; open_hw_target; program_hw_devices` | If `bitstreamPath` omitted, uses last generated bitstream |
| `runTclScript` | `<scriptPath>` | `source {scriptPath}` | |
| `runTclRaw` | `<tclString>` | Sends the TCL string verbatim | For AI free-form TCL |
| `setTopModule` | `<moduleName>` | `set_property top moduleName [current_fileset]; update_compile_order -fileset sources_1` | |
| `addIpRepo` | `<repoPath>` | `set_property ip_repo_paths {repoPath} [current_project]; update_ip_catalog` | Reuses `ipRepoPath` setting |
| `getProjectStatus` | — | `report_compile_order; get_runs` | Returns JSON-parseable summary |

---

### 4.5 MCP Server (AI Integration)

These requirements define the embedded MCP server that lets Claude Code and Junie AI drive Vivado through the predefined command library.

| ID | Requirement | Priority | Depends On |
|---|---|---|---|
| MCP-01 | The plugin SHALL start an embedded HTTP server on `localhost` (default port `19999`, configurable in settings) implementing the Model Context Protocol specification. | MUST | |
| MCP-02 | The MCP server SHALL expose every command in `PredefinedCommandLibrary` as an MCP tool, with the command `id` as the tool name and the parameter schema as the JSON Schema input. | MUST | CMD-01, MCP-01 |
| MCP-03 | The MCP server SHALL expose a `tools/list` endpoint returning all available tool descriptors with `name`, `description`, and `inputSchema`. | MUST | MCP-02 |
| MCP-04 | The MCP server SHALL expose a `tools/call` endpoint that accepts `{ name, arguments }` and invokes the corresponding predefined command via `TclBridgeService`. | MUST | MCP-02, TCL-02 |
| MCP-05 | The `tools/call` response SHALL return the TCL output text as the result content, and set `isError=true` if Vivado reported an error. | MUST | MCP-04 |
| MCP-06 | If Vivado is not running when `tools/call` is received, the MCP server SHALL return an error message instructing the AI to ask the user to launch Vivado first. | MUST | VPM-06 |
| MCP-07 | The MCP server port and an on/off toggle SHALL be exposed in the HDL Settings panel. | MUST | MCP-01 |
| MCP-08 | The Viva-CoTerm tool window SHALL display the MCP server URL (e.g. `MCP: http://localhost:19999`) so the user can copy it into Claude Code or Junie settings. | MUST | CTW-06, MCP-01 |
| MCP-09 | The MCP server SHOULD support SSE (Server-Sent Events) streaming for long-running commands so the AI client receives incremental output. | SHOULD | MCP-04 |
| MCP-10 | All tool calls received by the MCP server SHALL be echoed to the Viva-CoTerm output area prefixed with `[AI]` so the user sees what the AI is doing. | MUST | CTW-02 |

---

### 4.6 Settings Additions

New settings to add to the existing HDL Settings panel (`HdlSettingsPanel` / `VivadoSettingsState`):

| ID | Requirement | Priority |
|---|---|---|
| SET-01 | Add a **MCP Server Port** integer field (default `19999`, range `1024–65535`). | MUST |
| SET-02 | Add a **MCP Server Enabled** boolean toggle (default `true`). | MUST |
| SET-03 | Add a **Default Build Jobs** integer field (default `4`) used by `runSynthesis`, `runImplementation`, `buildProject`. | SHOULD |
| SET-04 | Add a **Command Timeout (min)** integer field (default `10`) used by `TclBridgeService`. | SHOULD |

---

## 5. User Stories

### 5.1 Manual Use

**Story A — Opening Vivado Console:**

1. User right-clicks a folder of Verilog files. They see **Vivado > Launch Vivado Console**.
2. The Vivado Console tool window opens at the bottom of the IDE.
3. A dialog asks: open an existing XPR, or start fresh. User picks their XPR.
4. Vivado GUI window appears on screen. The JetBrains console shows the startup log.
5. User types `get_runs` in the console input. The output appears immediately in the scrolling area.

**Story B — Running a Predefined Command from the UI:**

1. User clicks the **Run Command** palette in the Vivado Console toolbar.
2. User selects `generateBitstream`. A dialog shows required and optional parameters.
3. User clicks OK. The tool window shows the TCL being sent and incremental output from Vivado.
4. On success, a balloon notification appears: "Bitstream generation complete".

### 5.2 AI-Driven Use (Claude Code / Junie)

**Story C — AI builds the project on ZCU102 with DMA support:**

1. User has Vivado Console open with Vivado running.
2. User tells Claude Code: *"Build the project with DMA support for ZCU102"*.
3. Claude Code calls the MCP `tools/list` endpoint and discovers `buildProject`.
4. Claude Code calls `tools/call` with:
   ```json
   {
     "name": "buildProject",
     "arguments": {
       "name": "dma_zcu102",
       "path": "/home/user/fpga/dma_zcu102",
       "part": "xczu9eg-ffvb1156-2-e",
       "board": "xilinx.com:zcu102:part0:3.4",
       "jobs": 8
     }
   }
   ```
5. The Vivado Console output area displays each step prefixed with `[AI]`, showing synthesis and implementation progress.
6. Claude Code receives the final output and reports back to the user with a summary.

> **Note:** The AI determines the correct part and board string for ZCU102 from its own knowledge or by querying the user. The plugin does not need a board database.

**Story D — User wants manual control mid-flow:**

1. AI has run synthesis. User wants to manually inspect the timing report in the Vivado GUI.
2. User switches focus to the Vivado GUI window (which has been open the entire time).
3. User opens the timing report visually in Vivado, then tells the AI to continue with implementation.
4. AI calls `tools/call` with `{ "name": "runImplementation", "arguments": { "jobs": 4 } }`.

---

## 6. Non-Functional Requirements

| ID | Requirement | Priority |
|---|---|---|
| NF-01 | All new Kotlin code SHALL use coroutines (`kotlinx.coroutines`) for I/O — no blocking calls on the EDT. | MUST |
| NF-02 | The MCP HTTP server SHALL respond to `tools/list` within 200 ms. | SHOULD |
| NF-03 | The plugin SHALL compile against IntelliJ Platform SDK 2024.1+ and be compatible with IntelliJ IDEA, CLion, and GoLand. | MUST |
| NF-04 | No new external runtime dependencies SHALL be introduced beyond what is already in `build.gradle`. The HTTP server SHALL use the built-in Java `HttpServer` or Ktor (already common in IntelliJ plugins). | SHOULD |
| NF-05 | The embedded MCP server SHALL only bind to `127.0.0.1` (loopback) — never to `0.0.0.0`. | MUST |
| NF-06 | When the IDE project is closed, `VivadoProcessManager` SHALL call `shutdownVivado()` automatically. | MUST |

---

## 7. Out of Scope for v0.3.0

- **Simulation support (xsim).** Commands that drive simulation are deferred to v0.4.0.
- **Remote Vivado sessions (SSH).** The MCP server connects only to a local Vivado process.
- **Board database or part number lookup inside the plugin.** The AI or user supplies the part/board string.
- **Vitis / Vitis HLS integration via MCP.** Existing Vitis actions remain as right-click menu items only.
- **Authentication or TLS on the MCP server.** Loopback-only binding provides sufficient isolation.
- **Automatic inference of DMA or other IP from source files.** The AI decides what TCL to call.

---

## 8. Suggested File & Class Structure

The following new Kotlin files should be created under `src/main/kotlin/com/hdl/`:

```
vivado/
  VivadoProcessManager.kt        ← singleton project service  [NEW]
  TclBridgeService.kt            ← stdin/stdout pipe + coroutine flow  [NEW]
  PredefinedCommand.kt           ← data class / sealed class hierarchy  [NEW]
  PredefinedCommandLibrary.kt    ← catalog of all commands  [NEW]

coterm/
  VivaCoTermToolWindowFactory.kt ← registers tool window with JetBrains  [NEW]
  VivaCoTermPanel.kt             ← UI: output area + input field + toolbar  [NEW]

mcp/
  VivaMcpServer.kt               ← embedded HTTP MCP server  [NEW]
  McpToolDescriptor.kt           ← data class for tools/list response  [NEW]

settings/
  HdlSettingsPanel.kt            ← EDIT: add MCP port, toggle, timeouts
  VivadoSettingsState.kt         ← EDIT: add mcpPort, mcpEnabled, defaultJobs, cmdTimeoutMin

resources/
  META-INF/plugin.xml            ← EDIT: add toolWindow + projectService registrations
```

> **Do NOT modify `VivadoUtils.kt`.** Wrap it inside `VivadoProcessManager` instead.

---

## 9. Priority Summary

| Priority | Requirement IDs | Count |
|---|---|---|
| **MUST** | VPM-01–07, TCL-01–03, TCL-05–06, CTW-01–03, CTW-05–06, CTW-10–11, CMD-01–03, CMD-05–06, MCP-01–08, MCP-10, SET-01–02, NF-01, NF-03, NF-05–06 | 30 |
| **SHOULD** | VPM-08, TCL-04, TCL-07, CTW-04, CTW-07, CTW-09, CMD-04, MCP-09, SET-03–04, NF-02, NF-04 | 11 |
| **COULD** | CTW-08 | 1 |

---

## 10. Instructions for Junie

Paste the prompt below to Junie to start implementation:

```
You are implementing the Viva-CoTerm feature for the HDL + Vivado JetBrains plugin.
The attached requirements document (VivaCoTerm_Requirements.md) is the source of truth.

Implement in this order:
1. VivadoProcessManager.kt        — wraps existing VivadoUtils.launchVivado()
2. TclBridgeService.kt            — coroutine-based stdin/stdout bridge
3. PredefinedCommand.kt + PredefinedCommandLibrary.kt
4. VivaCoTermPanel.kt + VivaCoTermToolWindowFactory.kt
5. VivaMcpServer.kt + McpToolDescriptor.kt
6. Edit HdlSettingsPanel.kt and VivadoSettingsState.kt
7. Edit plugin.xml to register the new tool window and project services

Rules:
- Use Kotlin coroutines for all I/O. Never block the EDT.
- The MCP server must only bind to 127.0.0.1.
- Do NOT modify VivadoUtils.kt — wrap it inside VivadoProcessManager.
- All new classes go under com.hdl.vivado, com.hdl.coterm, or com.hdl.mcp as shown in Section 8.
- Follow IntelliJ Platform SDK 2024.1+ APIs.
```
