# Changelog

The top section of this file is rendered into the Marketplace change notes by
`build.gradle.kts` — keep the newest release first, and keep entries as `- ` bullets.

## 0.3.0 — Viva-CoTerm

- New: Vivado Console tool window with a live, bidirectional Tcl terminal
- New: socket-based Tcl bridge that keeps working after `start_gui` — the GUI stays usable
- New: 27 predefined Tcl commands covering projects, synthesis, implementation, bitstreams and block design
- New: Run Command palette in the toolbar for parameterised commands
- New: embedded MCP server (default port 19999) so an AI assistant such as Claude Code or Junie can drive Vivado
- New: MCP access control — per-session bearer token, browser origins refused, no CORS headers
- New: MCP server starts only on request, behind a safety agreement that gates arbitrary-Tcl tools
- New: connection guide shown after starting the MCP server, with a copyable client config
- New: command history (up/down arrow) in the Tcl input field
- New: Vivado &gt; Launch Vivado Console right-click action
- New: MCP settings — port, default job count, command timeout
- New: Verilog structural keyword pairs (module/endmodule, begin/end, case/endcase, …) for brace matching
- Fixed: Build Project and IP Composer no longer delete a previous run's Vivado project without asking
- Fixed: per-source-folder working directories, so building one folder cannot destroy another's project
- Fixed: JSON unescaping corrupted any MCP argument containing a backslash
- Fixed: brace counting in MCP arguments miscounted braces inside Vivado property strings
- Added: unit test suite covering the MCP layer, the command library, and Verilog tokenisation

## 0.2.1

- Added Vitis support: open a Vitis workspace from a folder
- Added Vitis HLS support: create an HLS kernel from a folder
