# HDL and Vivado Support

An IntelliJ IDEA plugin for FPGA work: Verilog and SystemVerilog language support,
cross-module linting, and a live Tcl terminal wired into a running Xilinx Vivado
session — with optional MCP access so an AI assistant can drive it too.

**Version 1.0.0** · IntelliJ IDEA 2025.1+ · [Changelog](CHANGELOG.md)

---

## What it does

| | |
|---|---|
| **Verilog / SystemVerilog** | Highlighting, completion, commenting, and brace matching for `module`/`endmodule`, `begin`/`end`, `case`/`endcase`, `function`, `task`, `generate`. Files: `.v .vh .sv .svh` |
| **Tcl / XDC** | Highlighting, folding for `proc` bodies and braced blocks, `${var}` handling, Vivado command completion. Files: `.tcl .xdc` |
| **Linting** | Icarus Verilog or Verilator, inline. Set a **Top Folder** and modules are analysed together, so cross-file instantiations resolve. |
| **Vivado** | Build a project from a folder, open an existing `.xpr`, run Tcl/XDC scripts (`Ctrl+Alt+R`), package IP through IP Composer. |
| **Vitis** | Open a Vitis workspace, create a Vitis HLS kernel. |
| **Vivado Console** | A bidirectional Tcl terminal on a live session. Runs over a socket rather than stdin, so the Vivado GUI stays usable while you type. 27 commands in a toolbar palette. |
| **AI assistant (MCP)** | Optional, off by default — see below. |

## Install

**From the JetBrains Marketplace** — Settings → Plugins → Marketplace → search *HDL and Vivado Support*.

**From source:**

```bash
./gradlew buildPlugin
# then: Settings → Plugins → ⚙ → Install Plugin from Disk
# → build/distributions/*.zip
```

**Configure** — Settings → Tools → **HDL Settings**: Vivado executable, Vitis executable,
FPGA part or board, linter binaries, IP repository path.

## AI assistant support (MCP)

The plugin can expose your live Vivado session to **Claude Code**, **Junie**, **Codex**, or any
other [Model Context Protocol](https://modelcontextprotocol.io) client. The assistant gets the same
27 parameter-checked commands the Run Command palette offers — projects, sources, block design,
synthesis, implementation, bitstream, programming.

It is **off by default**. Five things bound it:

| Control | Effect |
|---|---|
| **Manual start** | Never starts on its own. You start it from the Vivado Console toolbar and accept a safety agreement each time. |
| **Raw Tcl opt-in** | `runTclRaw` and `runTclScript` are hidden from the tool list and refused unless enabled for that session. |
| **Three access locks** | `Host` must be loopback (blocks DNS rebinding), `Origin` must be absent (blocks browsers), and a fresh per-session bearer token is required. No CORS headers are sent. |
| **Echoed** | Every command appears in the console as an `[AI]` line *before* it runs. |
| **Vivado must be RUNNING** | No live session, no tools. |

### Connecting

Start the server (**Start MCP**); the dialog gives you the URL, the token, and a copyable config.

```bash
claude mcp add --transport http viva-coterm http://127.0.0.1:19999 \
  --header "Authorization: Bearer <token from the panel>"
```

Any other client takes the same shape:

```json
{
  "mcpServers": {
    "viva-coterm": {
      "type": "http",
      "url": "http://127.0.0.1:19999",
      "headers": { "Authorization": "Bearer <token from the panel>" }
    }
  }
}
```

> **The assistant runs commands with your privileges.** These locks decide *who* may issue a
> command, not whether the command is a good idea. Watch the console and keep backups.

## Building and testing

```bash
./gradlew test          # unit tests — no IDE, no Vivado, no linter required
./gradlew verifyPlugin  # IntelliJ Plugin Verifier
./gradlew runIde        # sandbox IDE with the plugin installed
./gradlew buildPlugin   # installable zip in build/distributions/
```

The suite covers the MCP access gate, the JSON layer, tool-schema generation, all 27 commands,
Verilog tokenisation, and the launcher's working-directory layout. Anything needing a real IDE,
linter, or Vivado is a manual checklist in [`test_files/README.md`](test_files/README.md);
the AI path has its own in [`test_files/mcp/README.md`](test_files/mcp/README.md).

## Layout

```
src/main/kotlin/com/hdl/
├── verilog/     Language support + the linter framework
├── tcl/         Tcl and XDC language support
├── vivado/      Process launching, settings, the Tcl bridge, the command library
├── coterm/      Vivado Console tool window
├── mcp/         MCP server, access control, JSON, tool schemas
└── settings/    HDL Settings UI and tutorial
```

The three subsystems — language support, linting, Viva-CoTerm — are independent and share only
the settings layer. Note there are **two separate paths into Vivado**: the right-click actions
shell out one-shot via `VivadoUtils`, while the console holds a persistent socket into one
long-lived session. They do not share state.

### Adding a linter

Implement `VerilogLinter`, parse the tool's output into `List<LintResult>`, and register it in
`VerilogExternalAnnotator`.

```kotlin
class MyLinter : VerilogLinter {
    override val name = "mylinter"
    override fun isAvailable(toolPath: String?): Boolean = TODO()
    override fun verifyTool(toolPath: String): Pair<Boolean, String> = TODO()
    override fun lint(
        project: Project, toolPath: String?, file: VirtualFile,
        content: String, topFolder: VirtualFile?, excludePaths: Set<String>
    ): LinterOutput = TODO()
}
```

## Compatibility

Built and verified against IntelliJ IDEA 2025.1 (build 251) and Vivado 2023.2.
`sinceBuild` is `251` because that is what is actually tested — it will be widened once older
builds have been through `./gradlew verifyPlugin`, rather than claimed in advance.

## How this was built

Much of this plugin was written with AI assistance (Claude Code). Every release is reviewed and
covered by the test suite above — the tests are what let me stand behind the code, not the fact
that a human typed it.

Two things follow, and I'd rather say them than have you find out:

- **Please report bugs.** Hardware toolchains have a lot of surface area, and a bug report is
  the fastest route to a regression test.
- **Generated Tcl runs against your real projects.** Read what a command will do before pointing
  it at work you care about.

## Roadmap

- Go to definition, find usages, rename
- Deeper SystemVerilog support (classes, interfaces)
- Waveform viewer and timing analysis integration
- Quick fixes for common lint errors

## Contributing

Issues and pull requests welcome. Please run `./gradlew test` first. This is a side project —
expect replies in days rather than hours.

## License

MIT — see [LICENSE](LICENSE).

Not affiliated with, endorsed by, or sponsored by AMD/Xilinx or JetBrains.
Vivado, Vitis, and Xilinx are trademarks of Advanced Micro Devices, Inc.
IntelliJ IDEA is a trademark of JetBrains s.r.o.
