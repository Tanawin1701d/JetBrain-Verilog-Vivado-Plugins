# Verilog and Vivado IntelliJ Plugin

An IntelliJ IDEA plugin providing comprehensive Verilog language support and Vivado integration for FPGA development.

## Features

### 1. Verilog Language Support

#### Syntax Highlighting
- Full syntax highlighting for Verilog/SystemVerilog files (.v, .vh, .sv, .svh)
- Keywords, comments, strings, numbers, and operators highlighted
- Support for both Verilog-2001 and SystemVerilog constructs

#### Auto-completion
- Keyword auto-completion (module, always, reg, wire, etc.)
- System task auto-completion ($display, $monitor, etc.)
- Context-aware suggestions

#### Code Features
- Line and block comment support (//, /* */)
- Brace matching for parentheses, brackets, and braces
- Proper indentation support

### 2. Tcl Language Support

#### Syntax Highlighting
- Full syntax highlighting for Tcl scripts (.tcl) and Xilinx Design Constraints (.xdc)
- Keywords, Vivado-specific commands, variables, strings, and numbers highlighted
- Support for `${var}` variable syntax

#### Auto-completion
- Comprehensive Tcl keyword suggestions
- Common Vivado and Xilinx Tcl commands (create_bd_cell, set_property, etc.)

#### Code Features
- Line comment support (#)
- Code folding for `proc` bodies, `for` loops, and any braced blocks (`{ ... }`)
- Nested block support for complex scripts

### 3. Linting Support

#### Generalized Linter Interface
- Pluggable architecture supporting multiple linters
- Extensible design for adding custom linters

#### Icarus Verilog (iverilog) Integration
- Real-time syntax error detection
- Warning and error highlighting in the editor
- Automatic linting on file save

#### Top Folder Selection
- Right-click on any folder → "Set as Verilog Top Folder"
- When set, linter processes all Verilog files in the folder together
- Enables cross-file dependency checking

### 3. Vivado Integration

#### Configuration
- Settings → Tools → Vivado Settings
- Configure:
  - Vivado installation path (e.g., `/tools/Xilinx/Vivado/2023.2/bin/vivado`)
  - Target FPGA part (e.g., `xc7a35tcpg236-1`)
  - Target board (optional)

#### IP Composer
- Right-click folder → Vivado → Launch Vivado IP Composer
- Opens Vivado in IP Catalog mode
- Allows manual IP creation and configuration
- User exports IP when ready

#### Project Building
- Right-click folder → Vivado → Build Vivado Project
- Automatically collects all HDL files recursively
- Generates Vivado project with proper file hierarchy
- Supports both Verilog (.v, .sv) and VHDL (.vhd, .vhdl) files
- Opens Vivado GUI with the created project

#### Run Tcl Script
- Right-click .tcl or .xdc file → Vivado → Run Tcl Script
- Launches Vivado in GUI mode and executes the script automatically
- Convenient for running project creation scripts or timing constraints

#### Open Existing Projects
- Right-click .xpr file or folder → Vivado → Open Vivado Project
- Launches Vivado with the existing project
- Searches for .xpr files in selected directory

#### Synthesis
- Right-click .xpr file or folder → Vivado → Run Synthesis
- Runs synthesis in batch mode
- Opens synthesized design when complete
- Provides notifications on completion

## Installation

1. Clone this repository
2. Open in IntelliJ IDEA
3. Build the plugin: `./gradlew buildPlugin`
4. Install from disk: Settings → Plugins → ⚙️ → Install Plugin from Disk
5. Select the generated zip file from `build/distributions/`

## Prerequisites

### For Linting
- Install Icarus Verilog: `sudo apt install iverilog` (Linux) or download from [iverilog.icarus.com](http://iverilog.icarus.com/)

### For Vivado Integration
- Xilinx Vivado installation (2023.2 or later recommended)
- Configure the Vivado path in plugin settings

## Usage

### Basic Verilog Development
1. Create or open a .v or .sv file
2. Start coding with syntax highlighting and auto-completion
3. Errors from iverilog will appear inline

### Setting Up Linting Context
1. Right-click the root folder of your Verilog project
2. Select "Set as Verilog Top Folder"
3. Now all Verilog files in this folder will be analyzed together

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
- Top folder path (set via context menu action)
- Linter selection (future: support for multiple linters)

## Architecture

### Verilog Support
- `VerilogLanguage`: Language definition
- `VerilogLexer`: Tokenization
- `VerilogParser`: AST construction
- `VerilogSyntaxHighlighter`: Color scheme
- `VerilogCompletionContributor`: Auto-completion

### Tcl Support
- `TclLanguage`: Language definition
- `TclLexer`: Tokenization (supports Tcl and Vivado commands)
- `TclParser`: AST construction with block detection
- `TclSyntaxHighlighter`: Color scheme
- `TclCompletionContributor`: Intelligent suggestions
- `TclFoldingBuilder`: Code folding logic for braced blocks

### Linter Framework
- `VerilogLinter`: Interface for all linters
- `IcarusVerilogLinter`: iverilog implementation
- `VerilogExternalAnnotator`: Integrates linter results into IDE
- Extensible for additional linters (Verilator, Slang, etc.)

### Vivado Integration
- `VivadoSettingsState`: Persistent configuration
- `VivadoUtils`: TCL script generation and process management
- Action classes: Context menu handlers
- Asynchronous process execution

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

## License

MIT License

## Contributing

Contributions welcome! Please submit issues and pull requests on GitHub.
