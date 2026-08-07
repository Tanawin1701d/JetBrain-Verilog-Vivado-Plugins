# HDL and Vivado Support for IntelliJ IDEA

Elevate your FPGA development with professional HDL and Xilinx Vivado integration. This plugin transforms IntelliJ IDEA into a powerful hardware development environment with advanced support for Verilog, SystemVerilog, and Tcl scripts.

## Core Features

### 1. Advanced Verilog Language Support
- **Full Syntax Highlighting:** Intelligent highlighting for .v, .vh, .sv, and .svh files.
- **Smart Auto-completion:** Context-aware suggestions for keywords (module, always, etc.) and system tasks.
- **Brace Matching:** Reliable matching for parentheses, brackets, and braces.
- **Indentation Support:** Automatic and proper code indentation for better readability.

### 2. Comprehensive Tcl/XDC Integration
- **Full Syntax Highlighting:** Support for Tcl scripts (.tcl) and Xilinx Design Constraints (.xdc).
- **Vivado-Specific Support:** Smart completion for common Vivado and Xilinx Tcl commands.
- **Code Folding:** Powerful folding for `proc` bodies, `for` loops, and any braced blocks.
- **Variable Support:** Intelligent handling of `${var}` variable syntax.

### 3. Professional Real-time Linting
- **Pluggable Architecture:** Support for multiple linters including Icarus Verilog and Verilator.
- **Instant Feedback:** Syntax error detection and warning highlighting directly in the editor.
- **Smart "Top Folder" Context:**
    - Right-click any folder → **"Set as Verilog Top Folder"**.
    - **Visual Recognition:** The top folder is marked with a yellow background and a `[Top Folder]` label in the Project View.
    - **Global Analysis:** Resolves cross-module dependencies and multi-file syntax errors automatically.
- **Debugger Panel:** Integrated linter output panel with a searchable table of errors and raw console output.

### 4. Seamless Vivado Integration
- **One-Click Project Building:** Automatically generate and open Vivado projects from your HDL source tree.
- **Interactive IP Management:** Launch and manage Vivado IP Composer directly from the IDE.
- **Direct Script Execution:** Run Tcl and XDC scripts in Vivado with a single click or shortcut (`Ctrl+Alt+R`).
- **Project Management:** Open existing `.xpr` projects or run synthesis in batch mode.

### 5. Vivado Console (Viva-CoTerm)
- **Live Tcl terminal:** A bottom tool window attached to a running Vivado session — type Tcl, see output, with command history on the up/down arrows.
- **The GUI stays usable.** Commands travel over a socket rather than stdin, so `start_gui` does not cut off the channel.
- **Run Command palette:** 27 parameterised commands in the toolbar, with a dialog for each command's arguments.

### 6. AI Assistant Support (MCP)

The plugin can expose your live Vivado session to an AI assistant — **Claude Code**, **Junie**,
**Codex**, or any other client that speaks the [Model Context Protocol](https://modelcontextprotocol.io) —
through an embedded MCP server.

The assistant gets 27 curated, parameter-checked Vivado tools: create and open projects, add
sources, set the top module, build block designs, connect and configure IP, apply board presets
and connection automation, assign addresses, generate wrappers, run synthesis and implementation,
generate bitstreams, and program a device.

**It is off by default and designed to stay under your control:**

| Control | What it does |
|---|---|
| **Manual start only** | The server never starts on its own. You start it from the Vivado Console toolbar, and accept a safety agreement each time. |
| **Arbitrary Tcl is opt-in** | `runTclRaw` and `runTclScript` are hidden from the assistant's tool list and refused unless you enable them for that session. |
| **Loopback + bearer token** | Binds to `127.0.0.1`; refuses any request whose `Host` is not a loopback name (DNS-rebinding defence) or that carries a browser `Origin` header; and requires a per-session token you copy from the panel. A fresh token is minted on every start. No CORS headers are sent. |
| **Everything is echoed** | Each command the assistant issues appears in the Vivado Console as an `[AI]` line *before* it runs. |
| **Vivado must be running** | Every tool is refused until you have launched a session. |
| **Serialised** | Your commands and the assistant's share one queue, so you can type while it works without interleaving. |

#### Connecting a client

Start the server from the Vivado Console (**Start MCP**). The dialog that appears gives you the
URL, the session token, and a copyable config. For Claude Code:

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

See [`test_files/mcp/README.md`](test_files/mcp/README.md) for an end-to-end checklist.

## Installation

1. Clone this repository
2. Open in IntelliJ IDEA
3. Build the plugin: `./gradlew buildPlugin`
4. Install from disk: Settings → Plugins → ⚙️ → Install Plugin from Disk
5. Select the generated zip file from `build/distributions/`

## Prerequisites

### For Linting
- **Icarus Verilog:** Install via `sudo apt install iverilog` (Linux) or from [iverilog.icarus.com](http://iverilog.icarus.com/).
- **Verilator:** (Optional) High-performance linter. Install via `sudo apt install verilator` or from [verilator.org](https://www.verilator.org/).

### For Vivado Integration
- Xilinx Vivado installation (2023.2 or later recommended)
- Configure the Vivado path in plugin settings

## Usage

### Basic Verilog Development
1. Create or open a `.v` or `.sv` file.
2. Start coding with syntax highlighting and auto-completion.
3. Errors from the active linter (Iverilog or Verilator) will appear inline.
4. Check the **Verilog Linter Debugger** panel (bottom) for detailed output.

### Setting Up Linting Context
1. Right-click the root folder of your Verilog project in the Project View.
2. Select **"Set as Verilog Top Folder"**.
3. The folder will be highlighted in yellow, and all Verilog files within will be analyzed together for cross-file dependency checking.

### Creating a Vivado Project
1. Organize your HDL files in a folder
2. Right-click the folder
3. Select Vivado → Build Vivado Project
4. Vivado opens with all files added and ready

### Working with IP Cores
1. Right-click the folder where you want to create IP
2. Select Vivado → Launch Vivado IP Composer
3. Create/configure your IP in Vivado
4. Click Generate/Export in Vivado when done

### Running Synthesis
1. Right-click a folder containing a .xpr file
2. Select Vivado → Run Synthesis
3. Check notifications for completion status

### Running Tcl Scripts
1. Right-click a .tcl or .xdc file in the project view
2. Select Vivado → Run Tcl Script
3. Vivado will open and source the script automatically

## Configuration

### Vivado Settings
- Path to Vivado executable
- Default FPGA part number
- Default board (optional, overrides part if specified)

### Linter Settings
- **Top Folder:** Designated project root for multi-pass linting.
- **Active Linter:** Toggle between Icarus Verilog and Verilator.
- **Binary Paths:** Customizable paths for all tool executables.

## Architecture

### Verilog/SystemVerilog Support
- `VerilogLanguage`: Language definition.
- `VerilogLexer`: Tokenization (Verilog-2001 and SV support).
- `VerilogParser`: AST construction.
- `VerilogSyntaxHighlighter`: Theme-aware coloring.
- `VerilogExternalAnnotator`: Real-time linter integration and error marking.
- `TopFolderProjectViewDecorator`: Visual labeling for the top folder.

### Tcl/XDC Support
- `TclLanguage`: Full Tcl support for Vivado-specific commands.
- `TclFoldingBuilder`: Block-based code folding.
- `TclCompletionContributor`: Smart suggestions for Tcl and Vivado.

### Linter Framework
- `VerilogLinter`: Interface for extending linter support.
- `IcarusVerilogLinter`: Integration with iverilog.
- `VerilatorLinter`: High-performance linting with Verilator.
- `LinterDebuggerService`: Global state management for linter results.

### Vivado Integration
- `VivadoSettingsState`: Persistent user configuration.
- `VivadoUtils`: Tcl generation and Vivado process orchestration.
- `BuildProjectAction`: Automated project generation logic.

## Adding More Linters

To add support for another linter:

1. Implement the `VerilogLinter` interface
2. Parse the linter's output format
3. Return `List<LintResult>` with errors/warnings
4. Register in `VerilogExternalAnnotator`

Example:
```kotlin
class MyCustomLinter : VerilogLinter {
    override val name = "mycustomlinter"
    
    override fun isAvailable(): Boolean {
        // Check if linter is installed
    }
    
    override fun lint(file: VirtualFile, topFolder: VirtualFile?): List<LintResult> {
        // Run linter and parse output
    }
}
```

## Future Enhancements

- Enhanced SystemVerilog support (classes, interfaces, etc.)
- Code navigation (go to definition, find usages)
- Refactoring support (rename, extract module)
- Waveform viewer integration
- Timing analysis integration
- Multiple linter selection in settings
- Quick fixes for common errors

## Testing

```bash
./gradlew test          # unit tests — no IDE, no Vivado, no linter needed
./gradlew verifyPlugin  # IntelliJ Plugin Verifier
./gradlew runIde        # sandbox IDE with the plugin installed
```

Everything that needs a real IDE, linter, or Vivado is a manual checklist in
[`test_files/README.md`](test_files/README.md).

## How this was built

Much of this plugin was written with AI assistance (Claude Code). Every release is
reviewed before it ships and covered by the unit test suite above — the tests are the
part that lets me stand behind the code, not the fact that a human typed it.

Two things follow from that, and I'd rather state them than have you discover them:

- **Please report bugs.** Hardware toolchains have a lot of surface area, and a bug
  report is the fastest way something gets a regression test.
- **The generated Tcl runs against your real projects.** Read what a command will do
  before you point it at work you care about, and keep backups.

## Compatibility

Built and tested against IntelliJ IDEA 2025.1 (build 251) and Vivado 2023.2.
`sinceBuild` is set to `251` because that is what is actually verified — it will be
widened once older builds have been run through `./gradlew verifyPlugin`.

## License

MIT License. See [LICENSE](LICENSE).

Not affiliated with, endorsed by, or sponsored by AMD/Xilinx or JetBrains.
Vivado, Vitis, and Xilinx are trademarks of Advanced Micro Devices, Inc.
IntelliJ IDEA is a trademark of JetBrains s.r.o.

## Contributing

Issues and pull requests are welcome on
[GitHub](https://github.com/Merge-Panic-Society/JetBrain-Verilog-Vivado-Plugins).
This is a side project, so expect replies in days rather than hours. Please run
`./gradlew test` before opening a PR.
