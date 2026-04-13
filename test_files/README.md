# HDL Plugin — Test Files

This directory contains sample files for manually testing every feature of the plugin.

---

## Directory Layout

```
test_files/
├── linter_test/
│   ├── top_module/          # Clean multi-file design (zero linter errors)
│   │   ├── top.v            # Top-level module  ← set as Top File
│   │   ├── counter.v        # Sub-module
│   │   └── adder.v          # Sub-module
│   └── errors/              # Files with intentional errors
│       ├── syntax_error.v   # Parse/syntax error (line ~14)
│       ├── undeclared_wire.v# Undeclared identifier
│       └── width_mismatch.v # Width mismatch (Verilator warning)
├── systemverilog/
│   ├── pkg_types.sv         # SV package with typedef/enum
│   ├── counter_sv.sv        # SV module using package types  ← set as Top File
│   └── tb_counter_sv.sv     # SV testbench
└── vivado/
    ├── build_project.tcl    # Sample Vivado build script
    └── constraints.xdc      # Sample XDC constraint file
```

---

## Feature Testing Checklist

### 1. Linter — Single-File Mode
1. Open `linter_test/errors/syntax_error.v`
2. Verify a red squiggle appears on ~line 14
3. Hover to read the error message
4. Open the **Verilog Linter Debugger** (bottom panel) to see raw output

### 2. Linter — Top Folder Mode (multi-file)
1. Right-click `linter_test/top_module/` → **Set as Verilog Top Folder**
   - The folder should get a gold folder icon
2. Open `top.v` — no errors should appear (all modules are found)
3. Try `linter_test/errors/undeclared_wire.v` — error should still appear

### 3. Top File (elaboration entry-point)
1. Set `linter_test/top_module/` as Top Folder (see above)
2. Right-click `top.v` → **Set as Verilog Top File**
   - The file should get a gold star icon
3. Open any file in the folder — linter now passes `-s top` / `--top-module top`
4. Check the **Verilog Linter Debugger** raw output to confirm the flag appears

### 4. SystemVerilog
1. Right-click `systemverilog/` → **Set as Verilog Top Folder**
2. Right-click `counter_sv.sv` → **Set as Verilog Top File**
3. Open `counter_sv.sv` — no errors for a correct SV design

### 5. Apply Button Blinking
1. Open **HDL Settings** (right-side panel)
2. Edit any field (e.g., change the Board value)
3. The **● Unsaved changes** label and the **Apply** button should start blinking
4. Click **Apply** — blinking stops
5. Click **Reset** — blinking stops and edits are discarded

### 6. Test Button Verification
1. In HDL Settings, set the Iverilog Path to a valid path (e.g. `/usr/bin/iverilog`)
2. Click **Test** — should show version information
3. Set it to `/usr/bin/ls` (wrong binary) and click **Test** — should show an error
4. Set it to `/nonexistent/path` and click **Test** — should say "File not found"

### 7. Tutorial
1. In HDL Settings, click the **? Help** button
2. Navigate through the 6 tutorial sections using **Next ›** / **‹ Previous**

### 8. Vivado Integration
1. Configure Vivado executable path in HDL Settings → Apply
2. Right-click `vivado/build_project.tcl` → **Vivado → Run Tcl Script**
   - Vivado should open in GUI mode and execute the script
3. Right-click `linter_test/top_module/` → **Vivado → Build Project**
   - Vivado should open and create a project with all `.v` files

### 9. Icon Legend
| Icon | Meaning |
|------|---------|
| Gold folder | Top Folder |
| Green file with gold star | Top File |
| Green square with `V` | Verilog/SV file type |
| Blue chip | HDL Settings / Linter Debugger panel |
