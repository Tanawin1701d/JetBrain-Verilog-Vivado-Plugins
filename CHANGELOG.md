# Changelog

The top section of this file is rendered into the Marketplace change notes by
`build.gradle.kts` — keep the newest release first, and keep entries as `- ` bullets.

## 1.0.1 — The whole Vivado Tcl reference, and console session recording

**The Vivado command catalogue**

- New: every command in the *Vivado Design Suite Tcl Command Reference* (UG835) — 771 of them, with syntax, argument tables and examples — is now reachable from an AI assistant and from the console
- New: `searchVivadoCommands` searches the reference by keyword, category or command name
- New: `describeVivadoCommand` returns the full reference entry for one command
- New: `runVivadoCommand` executes any documented command, with the name checked against the reference and the arguments validated
- New: `runVivadoCommand` stays available with raw Tcl disabled — it is not raw Tcl: no `;`, no line breaks, no `$` or backslashes, and `[...]` substitution is limited to read-only queries
- New: **Run Command ▼ → Browse Vivado Commands…** opens a searchable browser over the same catalogue and drops the command you pick into the console input
- New: search results are tiered so flow commands rank ahead of device and GUI accessors; the tiers are plain text files under `src/main/resources/vivado/` that you can edit
- New: `tools/extract_ug835.py` regenerates the catalogue from a newer UG835 without disturbing the curated tiers
- Changed: the tool list stays at 30 entries — the 771 commands are shipped as data, because rendering them as tools would cost an assistant roughly 37k tokens on a single `tools/list` call
- Changed: catalogue lookups are answered inside the plugin, so an assistant can browse the reference before Vivado is launched
- Changed: the connection dialog counts tools from the library instead of quoting a fixed number

**Session recording**

- New: Record button in the Vivado Console toolbar, writing everything sent to and received from Vivado into a log file
- New: the button carries a blinking red dot while a recording is running, with the file name and a live record count in the console status bar
- New: you pick the file each time you start, with an append option and an overwrite prompt; the folder is remembered for the next recording
- New: `Console Log Folder` setting under Viva-CoTerm / MCP Server, for where the Record button starts from
- New: recordings cover the commands an MCP client issues as well as the ones you type, because the tap sits on the Tcl bridge itself
- New: the console's own notices are recorded too — `[AI] Executing: ...`, MCP start/stop, parameter errors and connection warnings all land in the log alongside the traffic
- New: logs are line-oriented and marked by direction — sent, received, or plugin notice — with a header, a full date-and-time stamp to the millisecond on every record, and a closing count
- New: records are stamped at the moment the data crosses the bridge, not when it reaches the disk, so the log keeps the real timing even while output arrives in bursts
- New: recording is owned by the project, so closing the Vivado Console tool window no longer cuts a session log short
- New: the MCP session token is deliberately kept out of the log, which records only that one was issued
- Changed: plugin status lines are published separately from Vivado output, so a recording no longer files our own notices as session output
- Changed: published console lines are delivered in order; one coroutine per line guaranteed nothing about ordering, in the console or in the log
- Changed: severity now outranks the source prefix when colouring the console, so a `[VivaCo-Term] ERROR: ...` notice reads as an error rather than as ordinary chatter

**Quality**

- Added: 18 unit tests covering the log format, multi-line commands, append vs overwrite, folder creation, a write racing a stop, and the bridge taps that carry notices into the log

## 1.0.0 — Vivado Console and AI assistant support

The Viva-CoTerm work that was developed as 0.3.0 never shipped, so it arrives here
as part of 1.0.0 rather than as a separate release.

**AI assistant support (MCP)**

- New: embedded MCP server (default port 19999) so Claude Code, Junie, Codex or any other MCP client can drive a live Vivado session
- New: the server starts only when you ask, behind a safety agreement that gates the arbitrary-Tcl tools
- New: access control — loopback binding, no CORS headers, browser origins and rebound host names refused, and a per-session bearer token minted on every start
- New: connection guide shown after starting the server, with the URL, token and a copyable client config
- New: blocked/live icon on the Start MCP button so server state is readable at a glance
- New: MCP settings — port, default job count, command timeout
- New: every command an assistant issues is echoed to the console before it runs

**Vivado Console (Viva-CoTerm)**

- New: Vivado Console tool window with a live, bidirectional Tcl terminal
- New: socket-based Tcl bridge that keeps working after `start_gui` — the Vivado GUI stays usable while you type
- New: 27 predefined Tcl commands covering projects, synthesis, implementation, bitstreams and block design
- New: Run Command palette in the toolbar, with a parameter dialog per command
- New: command history (up/down arrow) in the Tcl input field
- New: Vivado &gt; Launch Vivado Console right-click action

**Language support**

- New: Verilog structural keyword pairs (module/endmodule, begin/end, case/endcase, function, task, generate) for brace matching

**Fixed**

- Build Project and IP Composer no longer delete a previous run's Vivado project without asking, and report a failed deletion instead of building on half-deleted state
- Working directories are now scoped per source folder, so building one folder can no longer destroy another folder's project
- JSON unescaping corrupted any MCP argument containing a backslash — Windows paths, Tcl regexes, escaped braces
- Brace counting in MCP arguments miscounted braces inside Vivado property strings, truncating the parsed request

**Quality**

- Added: 86 unit tests covering MCP access control, the JSON layer, tool schemas, all 27 commands, Verilog tokenisation, and launcher paths
- Changed: sinceBuild is 251, matching the platform actually built and verified against
- Removed: dead template and scratch files, and an unused Tcl generator that emitted `create_project -force`
- Docs: AI-assistance disclosure, AMD/JetBrains trademark notice, and rewritten README and Marketplace description

## 0.2.1

- Added Vitis support: open a Vitis workspace from a folder
- Added Vitis HLS support: create an HLS kernel from a folder
