# MCP End-to-End Fixture

Exercises the AI-facing path — `VivaMcpServer` → `PredefinedCommandLibrary` →
`TclBridgeService` → live Vivado — with a design small enough to synthesise in
under a minute on any part.

The pure logic on that path (JSON parsing, schema generation, every
`tclGenerator`) is covered by `./gradlew test`. **This fixture covers what unit
tests cannot: that the generated Tcl is actually valid Vivado Tcl.**

---

## Setup

1. **HDL Settings** → set the Vivado executable path → **Apply**
2. Open the **Vivado Console** tool window (bottom)
3. Click **Launch Vivado** → leave both fields blank → **OK**
   - Status badge must go `● STOPPED` → `● STARTING` → `● RUNNING`
   - Console must print `[VivaCo-Term] Bridge active.`
4. Click **Start MCP** → choose **Safe** (raw Tcl disabled) for §1–§3
5. Point your MCP client at the URL shown in the status bar
   (`http://127.0.0.1:19999` by default — click the label to copy it)

---

## §1 — Handshake and catalogue

| # | Call | Expected |
|---|------|----------|
| 1.1 | `initialize` | `protocolVersion: "2024-11-05"`, `serverInfo.name: "VivaCo-Term MCP"` |
| 1.2 | `tools/list` | **25** tools when started Safe; **27** when started with raw Tcl |
| 1.3 | `tools/list` (Safe) | `runTclRaw` and `runTclScript` are **absent**, not merely erroring |
| 1.4 | `ping` | empty result, no error |
| 1.5 | unknown method `foo/bar` | JSON-RPC error `-32601` |

> **1.2 is the one that catches drift.** The count comes from
> `PredefinedCommandLibrary.commands`. If it disagrees, either a command was
> added without updating this file, or the gate filter is wrong.

## §2 — Guards (no Vivado work should happen)

Run these **before** creating a project.

| # | Call | Expected |
|---|------|----------|
| 2.1 | `tools/call` name `noSuchTool` | result with `isError: true`, text `Unknown tool` |
| 2.2 | `tools/call` `runTclRaw` while Safe | `isError: true`, text mentions raw Tcl is disabled |
| 2.3 | `tools/call` `runSynthesis` with Vivado **stopped** | `isError: true`, `Vivado is not running` |
| 2.4 | `tools/call` `openProject` with no `xprPath` | `isError: true`, `Missing required parameter 'xprPath'` |
| 2.5 | `tools/call` with no `params` | JSON-RPC error `-32602` |

Note the difference 2.1–2.4 vs 2.5: tool failures come back as *successful*
JSON-RPC results carrying `isError`, so the model can read and react to them.
Only protocol failures use the `error` envelope.

## §3 — Project flow

Run in order. After each call, the Vivado Console must show an `[AI] <tool>:`
line echoing the generated Tcl **before** the command runs.

| # | Tool | Arguments | Expected |
|---|------|-----------|----------|
| 3.1 | `createProject` | `name: "mcp_fixture"`, `path: "<abs path to this folder>"`, `part: "xc7a35tcpg236-1"` | project opens in the Vivado GUI |
| 3.2 | `addFiles` | `filesPath: "<this folder>/rtl"` | `blink.v` appears in Sources |
| 3.3 | `addFiles` | `filesPath: "<this folder>/constraints"` | `blink.xdc` appears in Constraints |
| 3.4 | `setTopModule` | `moduleName: "blink"` | Sources shows `blink` in bold as top |
| 3.5 | `getProjectStatus` | — | reports the open project, part, and top |
| 3.6 | `runSynthesis` | `jobs: 4` | completes; the call **blocks** until it finishes |
| 3.7 | `runImplementation` | `jobs: 4` | completes |
| 3.8 | `generateBitstreamAsync` | — | returns **immediately**, unlike 3.6/3.7 |
| 3.9 | `closeProject` | — | project closes |

**3.8 is the one worth watching.** `generateBitstream` embeds `wait_on_run` and
holds the HTTP connection for the whole build; the async variant does not.
Poll `mcp_fixture/mcp_fixture.runs/impl_1/blink.bit` on disk instead.

## §4 — Block design

Needs a Zynq/ZynqMP part. Substitute your own part and cell VLNVs.

| # | Tool | Expected |
|---|------|----------|
| 4.1 | `createBlockDesign` name `design_1` | BD canvas opens |
| 4.2 | `addBdCell` | cell appears on the canvas |
| 4.3 | `applyBoardPreset` | cell reconfigures to the board defaults |
| 4.4 | `connectBdIntfNet` | interface net drawn |
| 4.5 | `connectBdNet` | clock/reset net drawn |
| 4.6 | `applyConnectionAutomation` | AXI interconnect + reset inserted |
| 4.7 | `assignBdAddress` | Address Editor is populated |
| 4.8 | `validateBlockDesign` | passes, or reports real errors |
| 4.9 | `getBlockDesignLayout` | returns the cell/net list as text |
| 4.10 | `generateBdWrapper` | wrapper generated **and imported** into the fileset |

> **4.10:** `make_wrapper` without `-import` leaves the wrapper outside the
> fileset and synthesis then fails with a missing-top error. Pinned by
> `PredefinedCommandLibraryTest."generateBdWrapper imports the generated wrapper"`.

## §5 — Raw Tcl gate

1. **Stop MCP**, then **Start MCP** again and choose **Enable raw Tcl**
2. Status label must read `[raw Tcl ON]`

| # | Call | Expected |
|---|------|----------|
| 5.1 | `tools/list` | now **27** tools; `runTclRaw` and `runTclScript` present |
| 5.2 | `runTclRaw` with `tclString: "puts hello"` | output `hello` |
| 5.3 | `runTclRaw` with a path containing backslashes | path arrives intact — see below |
| 5.4 | Stop MCP, restart **Safe**, call `runTclRaw` | refused again; permission does not persist |

### 5.3 — the backslash regression

Call `runTclRaw` with:

```json
{"tclString": "puts {C:\\proj\\new}"}
```

Expected output: `C:\proj\new`

Before the single-pass unescaper this printed `C:\proj\` followed by a
**newline** and `ew` — the `\n` pair was matched across the escape boundary.
Pinned by `McpJsonTest."unescape keeps a backslash followed by n as two characters"`.

Also try a Vivado property string, which mixes braces and backslashes:

```json
{"tclString": "puts {CONFIG.C_A {1} CONFIG.C_B {0}}"}
```

The braces must survive — `McpJson.objectField` skips string literals while
counting brace depth precisely so this does not truncate.

## §6 — Serialisation

With Vivado running, issue `runSynthesis` from your MCP client and, while it is
still going, type `puts alive` into the Vivado Console input field.

Expected: `alive` prints only **after** synthesis finishes. Every Tcl command
from every source funnels through one `Channel` in `TclBridgeService`, so a
human and a model can share one interpreter safely.

---

## Cleanup

The build output (`mcp_fixture.cache/`, `.gen/`, `.runs/`, `.srcs/`, `.xpr`) is
gitignored — see the `### Test fixtures ###` block in the repo `.gitignore`.
Delete `mcp_fixture/` when you are done; everything here regenerates from
`rtl/` and `constraints/`.
