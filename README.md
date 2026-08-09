# HDL and Vivado + MCP Support

Hand your live Xilinx Vivado session to an AI assistant — Claude Code, Junie, Codex,
or any MCP client — from inside IntelliJ IDEA. Plus the things you'd want anyway:
Verilog and SystemVerilog language support, cross-module linting, and a Tcl terminal
attached to the running session.

**Version 1.0.1** · IntelliJ IDEA 2025.1+ · [Changelog](CHANGELOG.md)

---

## What it does

| | |
|---|---|
| **AI assistant (MCP)** | Connect Claude Code, Junie, Codex or any MCP client and let it drive Vivado — 30 parameter-checked commands plus the searchable UG835 reference, off by default, safety-gated. [Details](#ai-assistant-support-mcp) |
| **Verilog / SystemVerilog** | Highlighting, completion, commenting, and brace matching for `module`/`endmodule`, `begin`/`end`, `case`/`endcase`, `function`, `task`, `generate`. Files: `.v .vh .sv .svh` |
| **Tcl / XDC** | Highlighting, folding for `proc` bodies and braced blocks, `${var}` handling, Vivado command completion. Files: `.tcl .xdc` |
| **Linting** | Icarus Verilog or Verilator, inline. Set a **Top Folder** and modules are analysed together, so cross-file instantiations resolve. |
| **Vivado** | Build a project from a folder, open an existing `.xpr`, run Tcl/XDC scripts (`Ctrl+Alt+R`), package IP through IP Composer. |
| **Vitis** | Open a Vitis workspace, create a Vitis HLS kernel. |
| **Vivado Console** | A bidirectional Tcl terminal on a live session. Runs over a socket rather than stdin, so the Vivado GUI stays usable while you type. 30 commands in a toolbar palette, plus a searchable browser over every documented Vivado Tcl command. |
| **Session recording** | Record everything sent to and received from Vivado — yours and the assistant's — to a log file you choose. [Details](#recording-a-session) |

## Install

**From the JetBrains Marketplace** — Settings → Plugins → Marketplace → search *HDL and Vivado + MCP Support*.

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
30 parameter-checked commands the Run Command palette offers — projects, sources, block design,
synthesis, implementation, bitstream, programming — and, through those, the whole Vivado Tcl
reference (see [The command catalogue](#the-command-catalogue)).

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

## The command catalogue

The 30 tools are curated workflows — `buildProject`, `generateBdWrapper`,
`applyConnectionAutomation` and so on — each one several Tcl commands deep. Behind them sits the
whole *Vivado Design Suite Tcl Command Reference* (UG835): **771 commands**, with syntax,
every argument, and the worked examples from the guide.

Those 771 are **not** tools. Their names, summaries and syntax come to roughly 37k tokens, which
would swamp an assistant's context on a single `tools/list` call. They are shipped as data and
reached through three tools instead, so the tool list stays at 30 no matter how large the
reference grows:

| Tool | What it does |
|---|---|
| `searchVivadoCommands` | Keyword search over the reference. Returns names, one-line summaries and categories. |
| `describeVivadoCommand` | The full UG835 entry for one command — syntax, argument table, description, examples. |
| `runVivadoCommand` | Runs one of them. The name is checked against the reference and the arguments are validated. |

`runVivadoCommand` **works with raw Tcl disabled**, because it is not raw Tcl: the command name
must be one of the 771, and the argument string may not contain `;`, a line break, `$`, a
backslash or a backtick, with `[...]` substitution limited to read-only queries (`get_*`,
`all_*`, `current_*`, `list`, `lindex`, `expr`, …). A multi-command script still needs
`runTclRaw` and the raw-Tcl permission.

Search results are tiered so the useful commands surface first: 112 flow commands rank top,
337 more are returned by an ordinary search, and the 322-command long tail of device and GUI
accessors appears only on an exact name or with `scope=all`. The tiers are plain text files
you can edit — `src/main/resources/vivado/ug835-tier-core.txt` and `-tier-extended.txt`.

You get the same reach: **Run Command ▼ → Browse Vivado Commands…** opens a searchable browser
over the catalogue and drops the command you pick into the console input.

Regenerating the catalogue after a UG835 revision (the PDF is not committed):

```bash
# download UG835 from AMD, save it as docs/UG835.pdf
python3 tools/extract_ug835.py     # needs pdftotext (poppler-utils)
```

## Recording a session

**Record** in the Vivado Console toolbar writes the whole conversation with Vivado to a file:
every command sent and every line received, from the moment you press it until you press it
again. A red dot blinks on the button while it runs, and the status bar shows the file name and
a live record count.

You pick the file when you start — the dialog offers a timestamped name in the folder you used
last, with an append option and an overwrite prompt. Set the starting folder in
Settings → Tools → **HDL Settings** → *Console Log Folder*.

The tap sits on the Tcl bridge rather than on the console panel, which means two things: it
records the commands an MCP client issues as well as the ones you type — the audit trail for a
session you let an assistant drive — and the recording keeps going if you close the tool window.

Everything the console prints goes into the log, including its own notices (`[AI] Executing: ...`,
MCP start/stop, parameter errors). Two deliberate exceptions: the `tcl>` echo, which is already
there as a sent record, and the MCP session token — it is a live credential and logs get shared,
so the log notes only that one was issued.

```
# VivaCo-Term console log
# project : blink
# started : 2026-08-08 14:23:01
# legend  : >> sent to Vivado   << received from Vivado   -- plugin notice
#
[2026-08-08 14:23:05.120] >> open_project {/home/me/blink/blink.xpr}
[2026-08-08 14:23:06.001] << INFO: [Common 17-206] Opening project...
[2026-08-08 14:23:06.010] -- [VivaCo-Term] Bridge active. Type TCL commands below.
#
# stopped : 2026-08-08 14:40:11
# records : 214 (sent 12, received 198, notices 4)
```

One record per line, so `grep '>>' session.log` gives you the commands that ran, in order. Each
carries a full date and time to the millisecond, stamped when the data crossed the bridge rather
than when it reached the disk — so the log keeps the real timing even when Vivado dumps output in
bursts, and long runs stay readable across midnight.

## Building and testing

```bash
./gradlew test          # unit tests — no IDE, no Vivado, no linter required
./gradlew verifyPlugin  # IntelliJ Plugin Verifier
./gradlew runIde        # sandbox IDE with the plugin installed
./gradlew buildPlugin   # installable zip in build/distributions/
```

The suite covers the MCP access gate, the JSON layer, tool-schema generation, all 30 commands,
the UG835 catalogue and its argument validator,
Verilog tokenisation, the session-log format, and the launcher's working-directory layout. Anything needing a real IDE,
linter, or Vivado is a manual checklist in [`test_files/README.md`](test_files/README.md);
the AI path has its own in [`test_files/mcp/README.md`](test_files/mcp/README.md).

## Layout

```
src/main/kotlin/com/hdl/
├── verilog/         Language support + the linter framework
├── tcl/             Tcl and XDC language support
├── vivado/          Process launching, settings, the Tcl bridge, the command library
│   └── catalog/     The UG835 command catalogue and its argument validator
├── coterm/          Vivado Console tool window, session recorder, command browser
├── mcp/             MCP server, access control, JSON, tool schemas
└── settings/        HDL Settings UI and tutorial

src/main/resources/vivado/
├── ug835-index.tsv          generated: one line per documented command
├── ug835-details.tsv        generated: the full reference entry per command
├── ug835-tier-core.txt      curated: the ~100 commands a real flow uses
└── ug835-tier-extended.txt  curated: categories worth an ordinary search

tools/extract_ug835.py       regenerates the two generated files from docs/UG835.pdf
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
