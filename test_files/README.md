# HDL Plugin — Test Files

Fixtures for manually testing the parts of the plugin that need a real IDE, a
real linter, or a real Vivado.

**Automated tests live elsewhere.** Run them first — they are fast and need
none of the above:

```bash
./gradlew test
```

They cover the MCP JSON layer, the tool-schema projection, all 27 predefined
commands, and Verilog tokenisation. Anything they can catch is not repeated
here.

---

## Layout

```
test_files/
├── linter_test/          # Linter — cross-module resolution and error reporting
│   ├── top_module/       #   Clean multi-file design (zero errors expected)
│   └── errors/           #   Intentional errors, one per file
├── systemverilog/        # SystemVerilog package, module, testbench
├── brace_match/          # Structural keyword pairs (v0.3 lexer feature)
├── tcl_test/             # Tcl + XDC language support: folding, highlighting, completion
├── vivado/               # Standalone Tcl/XDC scripts for the action layer
├── test_ip/              # A packaged IP, for IP-repository features
└── mcp/                  # AI/MCP end-to-end fixture — see mcp/README.md
```

Vivado build output under any of these is gitignored. See the
`### Test fixtures ###` block in the repo `.gitignore`; a single block-design
build writes over 170 MB.

---

## 1. Linter — single file

1. Open `linter_test/errors/syntax_error.v`
2. A red squiggle appears around line 14; hover shows the message
3. Open the **Verilog Linter Debugger** panel (bottom) to see the raw output

Repeat for `undeclared_wire.v`, `undeclared_wire2.v`, `undeclared_wire3.v`,
and `width_mismatch.v` (the last is a Verilator warning — switch the active
linter in HDL Settings to see it).

## 2. Linter — top folder (cross-module)

1. Right-click `linter_test/top_module/` → **Set as Verilog Top Folder**
   - The folder gets a highlighted background and a `[Top Folder]` label
2. Open `top.v` — **no** errors: `counter` and `adder` now resolve
3. Right-click a different folder and set it as the top folder instead
   - `top.v` should immediately regain its unresolved-module errors, with no
     manual refresh — `LinterSettingsBroadcaster` restarts the daemon

## 3. Linter — files outside the top folder

With `linter_test/top_module/` set as top folder, open
`linter_test/errors/syntax_error.v`.

Expected: **no** squiggles, because `collectInformation` skips files whose path
is outside the top folder. This is the gate, not a bug.

## 4. SystemVerilog

1. Right-click `systemverilog/` → **Set as Verilog Top Folder**
2. Open `counter_sv.sv` — no errors; `pkg_types.sv` resolves
3. Check `.sv` and `.svh` both get the Verilog file-type icon

## 5. Brace matching — `brace_match/`

Open `all_pairs.v` and place the caret on each opening keyword. Its partner
must highlight:

| Opens | Closes |
|---|---|
| `module` | `endmodule` |
| `begin` | `end` |
| `case` | `endcase` |
| `function` | `endfunction` |
| `task` | `endtask` |
| `generate` | `endgenerate` |

Then open `not_keywords.v`. Nothing there should match: every identifier only
*looks* like a keyword (`endmodules`, `module_name`, `beginning`), and the
keywords inside the comment and the `$display` string must be inert.

## 6. Tcl and XDC — `tcl_test/`

**Folding** — open `folding.tcl`, press Ctrl+Shift+Minus. Every `proc`, `for`,
`foreach`, `while`, `if`, `switch`, `namespace eval` and bare braced block
collapses; nested blocks fold independently.

**Highlighting** — open `syntax_and_vars.tcl`. Comments, strings, `$var`,
`${var}`, numbers and Vivado commands each get a distinct colour. Compare with
Settings → Editor → Color Scheme → Tcl.

**Completion** — the checklist at the bottom of `syntax_and_vars.tcl` lists
prefixes to type and the commands each should offer.

**XDC** — open `timing.xdc`. It must get the same Tcl highlighting and folding;
`.xdc` is registered against `TclFileType`.

## 7. Vivado actions — `vivado/`

1. Configure the Vivado path in HDL Settings → **Apply**
2. Right-click `vivado/build_project.tcl` → **Vivado → Run Tcl Script**
   (or Ctrl+Alt+R) — Vivado opens and sources the script
3. Right-click `linter_test/top_module/` → **Vivado → Build Project** —
   Vivado opens with all `.v` files added
4. Right-click `test_ip/` → **Vivado → IP Composer**

Note these use the one-shot `ProcessBuilder` path in `VivadoUtils`, which is
**separate** from the Viva-CoTerm session. A project opened this way is a
different Vivado instance from the one the console is attached to.

## 8. Vivado Console (Viva-CoTerm)

1. Right-click any folder → **Vivado → Launch Vivado Console**
2. Status badge goes `● STOPPED` → `● STARTING` → `● RUNNING`
3. Console prints `[VivaCo-Term] Bridge active.`
4. Type `puts hello` → prints `hello`
5. Type `set x [expr {1/0}]` → the error line is red
6. Up/Down arrow cycles command history
7. **Run Command ▼** → pick a command → parameter dialog → runs
8. **Restart** relaunches; **Stop** shuts down and disables the input field

The GUI must stay usable throughout — that is the whole point of the socket
bridge over stdin.

## 9. HDL Settings

1. Edit any field — **● Unsaved changes** and **Apply** start blinking
2. **Apply** stops the blinking; **Reset** discards and stops it
3. Set the Iverilog path to `/usr/bin/iverilog` → **Test** → shows a version
4. Set it to `/usr/bin/ls` → **Test** → reports the wrong binary
5. Set it to `/nonexistent/path` → **Test** → reports file not found
6. **? Help** opens the tutorial; Next/Previous walk all sections

## 10. MCP / AI integration

See **[`mcp/README.md`](mcp/README.md)** — handshake, the six `tools/call`
guards, the project flow, block-design ops, the raw-Tcl gate, and the
serialisation check.

---

## Icon legend

| Icon | Meaning |
|------|---------|
| Highlighted folder + `[Top Folder]` | Linter top folder |
| Green square with `V` | Verilog / SystemVerilog file |
| Blue chip | HDL Settings, Linter Debugger, Vivado Console panels |

---

## `ai/` — session records, not fixtures

`ai/` holds the Tcl and console logs from real AI-driven MCP sessions
(`build_bd.tcl`, `run_impl.tcl`, `my_dma_project_build.tcl` and their logs).
They are kept as a record of what the tools actually generated and what Vivado
replied.

They are **not runnable as-is** — they contain absolute paths from the machine
that produced them. For a portable, runnable exercise use `mcp/` instead.
