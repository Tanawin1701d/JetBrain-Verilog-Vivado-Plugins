# Changelog

The top section of this file is rendered into the Marketplace change notes by
`build.gradle.kts` — keep the newest release first, and keep entries as `- ` bullets.

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
