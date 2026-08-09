package com.hdl.vivado

import com.hdl.vivado.catalog.TclArgSanitizer
import com.hdl.vivado.catalog.VivadoCommandCatalog

object PredefinedCommandLibrary {

    private fun param(name: String, type: ParameterType, required: Boolean, desc: String, default: Any? = null) =
        CommandParameter(name, type, required, desc, default)

    private fun str(n: String, req: Boolean, desc: String) = param(n, ParameterType.STRING, req, desc)
    private fun strd(n: String, desc: String, def: String) = param(n, ParameterType.STRING, false, desc, def)
    private fun int(n: String, req: Boolean, desc: String, def: Int? = null) = param(n, ParameterType.INT, req, desc, def)

    // A command answered from plugin-local data instead of by Vivado. The tclGenerator can
    // never fire (VivaMcpServer checks localHandler first), so it is wired to fail loudly
    // rather than silently sending something to the Tcl bridge.
    private fun local(
        id: String,
        name: String,
        description: String,
        parameters: List<CommandParameter>,
        handler: (Map<String, Any>) -> String
    ) = PredefinedCommand(
        id = id,
        name = name,
        description = description,
        parameters = parameters,
        tclGenerator = { error("$id is answered locally and is never sent to Vivado") },
        localHandler = handler
    )

    val commands: List<PredefinedCommand> = listOf(

        PredefinedCommand(
            id = "openProject",
            name = "Open Project",
            description = "Open an existing Vivado project (.xpr file)",
            parameters = listOf(str("xprPath", true, "Full path to the .xpr file")),
            tclGenerator = { args ->
                val xpr = args["xprPath"] ?: error("xprPath is required")
                "open_project {$xpr}"
            }
        ),

        PredefinedCommand(
            id = "closeProject",
            name = "Close Project",
            description = "Close the currently open Vivado project (no error if none is open). " +
                "Use before recreating a project at the same location.",
            parameters = emptyList(),
            tclGenerator = { "close_project -quiet" }
        ),

        PredefinedCommand(
            id = "createProject",
            name = "Create Project",
            description = "Create a new Vivado project, optionally set board/part, add files",
            parameters = listOf(
                str("name", true, "Project name"),
                str("path", true, "Directory where the project will be created"),
                str("part", true, "FPGA part (e.g. xc7a35tcpg236-1)"),
                str("board", false, "Board part string (optional)")
            ),
            tclGenerator = { args ->
                val name = args["name"] ?: error("name is required")
                val path = args["path"] ?: error("path is required")
                val part = args["part"] ?: error("part is required")
                val board = args["board"]?.toString()?.takeIf { it.isNotBlank() }
                buildString {
                    appendLine("create_project {$name} {$path} -part $part")
                    if (board != null) appendLine("set_property board_part $board [current_project]")
                    appendLine("update_compile_order -fileset sources_1")
                }
            }
        ),

        PredefinedCommand(
            id = "addFiles",
            name = "Add Files",
            description = "Add HDL files or a folder of files to the project",
            parameters = listOf(str("fileOrFolder", true, "Path to a file or folder to add")),
            tclGenerator = { args ->
                val path = args["fileOrFolder"] ?: error("fileOrFolder is required")
                "add_files -norecurse {$path}\nupdate_compile_order -fileset sources_1"
            }
        ),

        PredefinedCommand(
            id = "runSynthesis",
            name = "Run Synthesis",
            description = "Launch synthesis (synth_1) and wait for it to complete",
            parameters = listOf(int("jobs", false, "Number of parallel jobs", 4)),
            tclGenerator = { args ->
                val jobs = args["jobs"]?.toString() ?: "4"
                "launch_runs synth_1 -jobs $jobs\nwait_on_run synth_1"
            }
        ),

        PredefinedCommand(
            id = "runImplementation",
            name = "Run Implementation",
            description = "Launch implementation (impl_1) and wait for it to complete",
            parameters = listOf(int("jobs", false, "Number of parallel jobs", 4)),
            tclGenerator = { args ->
                val jobs = args["jobs"]?.toString() ?: "4"
                "launch_runs impl_1 -jobs $jobs\nwait_on_run impl_1"
            }
        ),

        PredefinedCommand(
            id = "generateBitstream",
            name = "Generate Bitstream",
            description = "Run implementation through write_bitstream step",
            parameters = emptyList(),
            tclGenerator = {
                "launch_runs impl_1 -to_step write_bitstream\nwait_on_run impl_1"
            }
        ),

        PredefinedCommand(
            id = "buildProject",
            name = "Build Project (Full Flow)",
            description = "Create project, add files, run synthesis, implementation, and generate bitstream",
            parameters = listOf(
                str("name", true, "Project name"),
                str("path", true, "Project directory"),
                str("part", true, "FPGA part string"),
                str("board", false, "Board part string (optional)"),
                str("filesPath", false, "Path to folder of HDL files (optional)"),
                int("jobs", false, "Parallel jobs", 4)
            ),
            tclGenerator = { args ->
                val name = args["name"] ?: error("name is required")
                val path = args["path"] ?: error("path is required")
                val part = args["part"] ?: error("part is required")
                val board = args["board"]?.toString()?.takeIf { it.isNotBlank() }
                val filesPath = args["filesPath"]?.toString()?.takeIf { it.isNotBlank() }
                val jobs = args["jobs"]?.toString() ?: "4"
                buildString {
                    appendLine("create_project {$name} {$path} -part $part")
                    if (board != null) appendLine("set_property board_part $board [current_project]")
                    if (filesPath != null) {
                        appendLine("add_files -norecurse {$filesPath}")
                        appendLine("update_compile_order -fileset sources_1")
                    }
                    appendLine("launch_runs synth_1 -jobs $jobs")
                    appendLine("wait_on_run synth_1")
                    appendLine("launch_runs impl_1 -jobs $jobs")
                    appendLine("wait_on_run impl_1")
                    appendLine("launch_runs impl_1 -to_step write_bitstream")
                    appendLine("wait_on_run impl_1")
                }
            }
        ),

        PredefinedCommand(
            id = "programDevice",
            name = "Program Device",
            description = "Open hardware manager and program the connected FPGA device",
            parameters = listOf(str("bitstreamPath", false, "Path to .bit file (uses last generated if omitted)")),
            tclGenerator = { args ->
                val bit = args["bitstreamPath"]?.toString()?.takeIf { it.isNotBlank() }
                buildString {
                    appendLine("open_hw_manager")
                    appendLine("connect_hw_server -allow_non_jtag")
                    appendLine("open_hw_target")
                    if (bit != null) {
                        appendLine("set_property PROGRAM.FILE {$bit} [get_hw_devices]")
                    }
                    appendLine("program_hw_devices [get_hw_devices]")
                    appendLine("close_hw_target")
                    appendLine("close_hw_manager")
                }
            }
        ),

        PredefinedCommand(
            id = "runTclScript",
            name = "Run Tcl Script",
            description = "Source (run) a Tcl script file in Vivado",
            parameters = listOf(str("scriptPath", true, "Full path to the .tcl script")),
            tclGenerator = { args ->
                val script = args["scriptPath"] ?: error("scriptPath is required")
                "source {$script}"
            }
        ),

        PredefinedCommand(
            id = "runTclRaw",
            name = "Run Raw Tcl",
            description = "Send a raw Tcl string directly to Vivado for evaluation",
            parameters = listOf(str("tclString", true, "Tcl command string to evaluate")),
            tclGenerator = { args ->
                args["tclString"]?.toString() ?: error("tclString is required")
            }
        ),

        PredefinedCommand(
            id = "setTopModule",
            name = "Set Top Module",
            description = "Set the top-level module for synthesis and update compile order",
            parameters = listOf(str("moduleName", true, "Name of the top-level module")),
            tclGenerator = { args ->
                val mod = args["moduleName"] ?: error("moduleName is required")
                "set_property top $mod [current_fileset]\nupdate_compile_order -fileset sources_1"
            }
        ),

        PredefinedCommand(
            id = "addIpRepo",
            name = "Add IP Repository",
            description = "Add a custom IP repository path and refresh the IP catalog",
            parameters = listOf(str("repoPath", true, "Path to the IP repository directory")),
            tclGenerator = { args ->
                val repo = args["repoPath"] ?: error("repoPath is required")
                "set_property ip_repo_paths {$repo} [current_project]\nupdate_ip_catalog"
            }
        ),

        PredefinedCommand(
            id = "getProjectStatus",
            name = "Get Project Status",
            description = "Print project name, part, and status of all runs",
            parameters = emptyList(),
            tclGenerator = {
                """
                puts "Project: [current_project]"
                puts "Part: [get_property PART [current_project]]"
                puts "Runs:"
                foreach r [get_runs] {
                    puts "  ${'$'}r: [get_property STATUS [get_runs ${'$'}r]]"
                }
                """.trimIndent()
            }
        ),

        // -------------------------------------------------------------------------
        // Block-design (IP integrator) commands — distilled from a real PS+DMA build.
        // These cover the operations that previously required raw Tcl.
        // -------------------------------------------------------------------------

        PredefinedCommand(
            id = "createBlockDesign",
            name = "Create Block Design",
            description = "Create a new IP-integrator block design in the open project",
            parameters = listOf(strd("name", "Block design name", "design_1")),
            tclGenerator = { args ->
                val nm = args["name"]?.toString()?.takeIf { it.isNotBlank() } ?: "design_1"
                "create_bd_design {$nm}"
            }
        ),

        PredefinedCommand(
            id = "addBdCell",
            name = "Add Block-Design IP Cell",
            description = "Instantiate an IP into the open block design by VLNV (e.g. xilinx.com:ip:axi_dma)",
            parameters = listOf(
                str("vlnv", true, "Full IP VLNV, e.g. xilinx.com:ip:axi_dma:7.1 (version optional)"),
                str("cellName", true, "Instance name for the new cell, e.g. axi_dma_0")
            ),
            tclGenerator = { args ->
                val vlnv = args["vlnv"] ?: error("vlnv is required")
                val cell = args["cellName"] ?: error("cellName is required")
                "create_bd_cell -type ip -vlnv $vlnv {$cell}"
            }
        ),

        PredefinedCommand(
            id = "connectBdIntfNet",
            name = "Connect BD Interface Pins",
            description = "Wire two block-design interface pins together (e.g. an AXI-Stream master to a slave)",
            parameters = listOf(
                str("intfPinA", true, "First interface pin, e.g. axi_dma_0/M_AXIS_MM2S"),
                str("intfPinB", true, "Second interface pin, e.g. axis_data_fifo_0/S_AXIS")
            ),
            tclGenerator = { args ->
                val a = args["intfPinA"] ?: error("intfPinA is required")
                val b = args["intfPinB"] ?: error("intfPinB is required")
                "connect_bd_intf_net [get_bd_intf_pins {$a}] [get_bd_intf_pins {$b}]"
            }
        ),

        PredefinedCommand(
            id = "connectBdNet",
            name = "Connect BD Pins (clock/reset/signal)",
            description = "Wire two scalar block-design pins together (clocks, resets, simple signals)",
            parameters = listOf(
                str("pinA", true, "First pin, e.g. zynq_ultra_ps_e_0/pl_clk0"),
                str("pinB", true, "Second pin, e.g. axis_data_fifo_0/s_axis_aclk")
            ),
            tclGenerator = { args ->
                val a = args["pinA"] ?: error("pinA is required")
                val b = args["pinB"] ?: error("pinB is required")
                "connect_bd_net [get_bd_pins {$a}] [get_bd_pins {$b}]"
            }
        ),

        PredefinedCommand(
            id = "connectBdPinToNetOf",
            name = "Fan a BD Pin onto an Existing Net",
            description = "Attach a target pin to the net that a reference pin already belongs to. " +
                "The robust idiom for fanning a clock or reset out to an extra sink (e.g. giving an " +
                "AXI-Stream FIFO the same clock/reset net as a DMA) without re-driving it.",
            parameters = listOf(
                str("refPin", true, "Pin already on the desired net, e.g. axi_dma_0/s_axi_lite_aclk"),
                str("targetPin", true, "Pin to attach to that net, e.g. axis_data_fifo_0/s_axis_aclk")
            ),
            tclGenerator = { args ->
                val ref = args["refPin"] ?: error("refPin is required")
                val target = args["targetPin"] ?: error("targetPin is required")
                "connect_bd_net -net [get_bd_nets -of_objects [get_bd_pins {$ref}]] [get_bd_pins {$target}]"
            }
        ),

        PredefinedCommand(
            id = "validateBlockDesign",
            name = "Validate Block Design",
            description = "Assign addresses, save, and validate the open block design (reports DRC errors)",
            parameters = emptyList(),
            tclGenerator = {
                buildString {
                    appendLine("assign_bd_address")
                    appendLine("regenerate_bd_layout")
                    appendLine("save_bd_design")
                    appendLine("validate_bd_design -force")
                }
            }
        ),

        PredefinedCommand(
            id = "generateBdWrapper",
            name = "Generate BD HDL Wrapper",
            description = "Generate the HDL wrapper for a block design, import it into the project, and set it as top",
            parameters = listOf(strd("bdName", "Block design name", "design_1")),
            tclGenerator = { args ->
                val bd = args["bdName"]?.toString()?.takeIf { it.isNotBlank() } ?: "design_1"
                buildString {
                    // -import adds the generated wrapper to the project as a source (make_wrapper's
                    // return value is unreliable, so don't depend on it for add_files).
                    appendLine("make_wrapper -files [get_files {$bd}.bd] -top -import")
                    appendLine("update_compile_order -fileset sources_1")
                    appendLine("set_property top ${bd}_wrapper [current_fileset]")
                    appendLine("update_compile_order -fileset sources_1")
                }
            }
        ),

        PredefinedCommand(
            id = "configBdCell",
            name = "Configure BD Cell",
            description = "Set CONFIG.* properties on a block-design cell. Pass space-separated " +
                "'CONFIG.key value' pairs, e.g. 'CONFIG.c_include_sg 0 CONFIG.c_include_mm2s 1'",
            parameters = listOf(
                str("cellName", true, "Cell instance name, e.g. axi_dma_0"),
                str("properties", true, "Space-separated CONFIG.key value pairs")
            ),
            tclGenerator = { args ->
                val cell = args["cellName"] ?: error("cellName is required")
                val props = args["properties"] ?: error("properties is required")
                "set_property -dict [list $props] [get_bd_cells {$cell}]"
            }
        ),

        PredefinedCommand(
            id = "applyBoardPreset",
            name = "Apply Board Preset to BD Cell",
            description = "Apply the board automation preset to a processor/board cell (e.g. a Zynq or " +
                "Zynq UltraScale+ PS). The automation rule is derived from the cell's VLNV, so this works " +
                "for any board-aware IP. Requires the project to have a board_part set.",
            parameters = listOf(strd("cellName", "Cell instance name", "zynq_ultra_ps_e_0")),
            tclGenerator = { args ->
                val cell = args["cellName"]?.toString()?.takeIf { it.isNotBlank() } ?: "zynq_ultra_ps_e_0"
                buildString {
                    // derive the bd_rule from the IP name (3rd field of the VLNV), e.g.
                    // xilinx.com:ip:zynq_ultra_ps_e:* -> xilinx.com:bd_rule:zynq_ultra_ps_e
                    appendLine("set _ip [lindex [split [get_property VLNV [get_bd_cells {$cell}]] :] 2]")
                    appendLine("apply_bd_automation -rule xilinx.com:bd_rule:\$_ip -config {apply_board_preset 1} [get_bd_cells {$cell}]")
                }
            }
        ),

        PredefinedCommand(
            id = "applyConnectionAutomation",
            name = "Apply AXI Connection Automation",
            description = "Run IP-integrator connection automation to wire an AXI master interface to a " +
                "slave interface, auto-assigning clocks/resets and inserting/reusing an interconnect. " +
                "Generalizes the PS<->DMA wiring to any master/slave pair.",
            parameters = listOf(
                str("master", true, "Master interface pin, e.g. axi_dma_0/M_AXI_MM2S"),
                str("slave", true, "Slave interface pin, e.g. zynq_ultra_ps_e_0/S_AXI_HP0_FPD"),
                strd("interconnect", "Interconnect: 'Auto' (reuse/create), 'New AXI SmartConnect', " +
                    "'New AXI Interconnect', or an existing instance name", "Auto")
            ),
            tclGenerator = { args ->
                val master = args["master"] ?: error("master is required")
                val slave  = args["slave"] ?: error("slave is required")
                val intc   = args["interconnect"]?.toString()?.takeIf { it.isNotBlank() } ?: "Auto"
                // intc_ip {Auto} reuses an interconnect already attached to the slave, so a second
                // master to the same slave shares it instead of creating a duplicate.
                "apply_bd_automation -rule xilinx.com:bd_rule:axi4 -config [list " +
                    "Master {$master} Slave {$slave} Clk_master {Auto} Clk_slave {Auto} Clk_xbar {Auto} " +
                    "ddr_seg {Auto} intc_ip {$intc} master_apm {0}] [get_bd_intf_pins {$master}]"
            }
        ),

        PredefinedCommand(
            id = "assignBdAddress",
            name = "Assign BD Addresses",
            description = "Auto-assign the address map for the open block design",
            parameters = emptyList(),
            tclGenerator = { "assign_bd_address" }
        ),

        PredefinedCommand(
            id = "getBlockDesignLayout",
            name = "Get Block Design Layout",
            description = "Report the open block design: every cell and its IP (VLNV), all interface " +
                "connections, clock/reset/signal nets, and the assigned address segments. Read-only.",
            parameters = emptyList(),
            tclGenerator = {
                buildString {
                    appendLine("puts \"=== Block Design: [current_bd_design] ===\"")
                    appendLine("puts \"-- Cells (instance : VLNV) --\"")
                    appendLine("foreach c [get_bd_cells] { puts \"  \$c : [get_property VLNV \$c]\" }")
                    appendLine("puts \"-- Interface connections --\"")
                    appendLine("foreach n [get_bd_intf_nets] { puts \"  \$n : [get_bd_intf_pins -quiet -of_objects \$n]\" }")
                    appendLine("puts \"-- Clock/reset/signal nets --\"")
                    appendLine("foreach n [get_bd_nets] { puts \"  \$n : [get_bd_pins -quiet -of_objects \$n]\" }")
                    appendLine("puts \"-- Address segments --\"")
                    appendLine("foreach s [get_bd_addr_segs -quiet] { puts \"  \$s\" }")
                }
            }
        ),

        PredefinedCommand(
            id = "generateBitstreamAsync",
            name = "Generate Bitstream (non-blocking)",
            description = "Launch impl_1 through write_bitstream WITHOUT waiting (returns immediately). " +
                "Unlike generateBitstream, this does not embed wait_on_run, so it won't pin the MCP call " +
                "open for the whole run. Poll <project>.runs/impl_1/<top>_wrapper.bit on disk for completion.",
            parameters = listOf(int("jobs", false, "Number of parallel jobs", 8)),
            tclGenerator = { args ->
                val jobs = args["jobs"]?.toString() ?: "8"
                "launch_runs impl_1 -to_step write_bitstream -jobs $jobs"
            }
        ),

        // -------------------------------------------------------------------------
        // Gateway to the full UG835 Tcl reference (~770 commands).
        //
        // Those commands are NOT tools: their names, summaries and syntax come to about
        // 37k tokens, which would swamp a client's context on one tools/list call. They
        // live in VivadoCommandCatalog as data and are reached through these three tools,
        // so the tool list stays around 30 entries no matter how large the reference gets.
        // -------------------------------------------------------------------------

        local(
            id = "searchVivadoCommands",
            name = "Search Vivado Commands",
            description = "Search the full Vivado Tcl command reference (UG835, ~770 commands) by " +
                "keyword. Returns matching command names with a one-line summary and their " +
                "categories. Use this whenever the task needs a Vivado command that is not already " +
                "one of the tools above — then describeVivadoCommand for its syntax and " +
                "runVivadoCommand to execute it. Works with or without a running Vivado session.",
            parameters = listOf(
                str("query", true, "Keywords to match against command name, summary and category, e.g. 'pblock' or 'bd cell'"),
                str("category", false, "Restrict to one UG835 category, e.g. Project, Timing, IPIntegrator, Report"),
                strd("scope", "'default' hides rarely-used commands; 'all' searches every documented command", "default"),
                int("limit", false, "Maximum results to return (1-100)", 20)
            )
        ) { args ->
            val query = args["query"]?.toString().orEmpty()
            val category = args["category"]?.toString()?.takeIf { it.isNotBlank() }
            val includeAll = args["scope"]?.toString()?.trim()?.equals("all", ignoreCase = true) == true
            val limit = (args["limit"]?.toString()?.trim()?.toIntOrNull() ?: 20).coerceIn(1, 100)

            val hits = VivadoCommandCatalog.search(query, category, includeAll)
            if (hits.isEmpty()) {
                buildString {
                    append("No Vivado commands match '$query'")
                    if (category != null) append(" in category '$category'")
                    append(". Try fewer or different keywords")
                    if (!includeAll) append(", or scope=all to include rarely-used commands")
                    append(".")
                }
            } else buildString {
                hits.take(limit).forEach { appendLine(it.toLine()) }
                if (hits.size > limit) {
                    appendLine("... and ${hits.size - limit} more; narrow the query or raise limit (max 100).")
                }
                if (!includeAll) appendLine("(Rarely-used commands were hidden; pass scope=all to include them.)")
                append("Use describeVivadoCommand for full syntax, runVivadoCommand to execute.")
            }
        },

        local(
            id = "describeVivadoCommand",
            name = "Describe Vivado Command",
            description = "Return the full UG835 reference entry for one Vivado Tcl command: syntax, " +
                "every argument with its description, the long description and worked examples. " +
                "Call this before runVivadoCommand when the exact flags matter. " +
                "Works with or without a running Vivado session.",
            parameters = listOf(
                str("name", true, "Exact command name, e.g. create_clock, resize_pblock, report_timing")
            )
        ) { args ->
            val name = args["name"]?.toString()?.trim().orEmpty()
            VivadoCommandCatalog.details(name)?.let { "$name\n\n$it" } ?: buildString {
                append("'$name' is not in the UG835 Vivado Tcl command reference.")
                val near = VivadoCommandCatalog.closestNames(name)
                if (near.isNotEmpty()) append(" Did you mean: ${near.joinToString(", ")}?")
                append(" Use searchVivadoCommands to find the right name.")
            }
        },

        PredefinedCommand(
            id = "runVivadoCommand",
            name = "Run Any Vivado Command",
            description = "Execute any command from the UG835 Vivado Tcl reference (~770 commands) — " +
                "anything the tools above do not already cover. The command name is checked against " +
                "the reference and the argument string is validated, so this is not arbitrary Tcl: " +
                "no ';', no line breaks, no '\$' variables, no backslashes, and '[...]' substitution " +
                "is limited to read-only queries (get_*, all_*, current_*, list, lindex, expr, ...). " +
                "Find command names with searchVivadoCommands and exact flags with describeVivadoCommand.",
            parameters = listOf(
                str("command", true, "Vivado command name from the UG835 reference, e.g. resize_pblock"),
                str("args", false, "Arguments exactly as they follow the command name, e.g. \"-period 10 [get_ports clk]\"")
            ),
            tclGenerator = { args ->
                val name = args["command"]?.toString()?.trim().orEmpty()
                if (name.isEmpty()) error("command is required")

                // Pinning the head word to a documented command is what keeps exec, source,
                // eval and file structurally out of reach here.
                val entry = VivadoCommandCatalog.byName(name) ?: run {
                    val near = VivadoCommandCatalog.closestNames(name)
                    val hint = if (near.isEmpty()) "" else " Did you mean: ${near.joinToString(", ")}?"
                    error("'$name' is not in the UG835 Vivado Tcl command reference.$hint " +
                        "Use searchVivadoCommands to find the right name.")
                }

                val rest = args["args"]?.toString()?.trim().orEmpty()
                TclArgSanitizer.reject(rest)?.let { reason ->
                    error("$reason. runVivadoCommand runs one checked command; " +
                        "multi-command scripts need runTclRaw, which requires the raw-Tcl permission.")
                }

                if (rest.isEmpty()) entry.name else "${entry.name} $rest"
            }
        )
    )

    fun findById(id: String): PredefinedCommand? = commands.firstOrNull { it.id == id }

    /**
     * Commands that execute arbitrary Tcl. VivaMcpServer hides these from
     * tools/list and refuses tools/call unless the user granted raw-Tcl
     * permission for the session.
     *
     * Declared here, next to the commands themselves, so renaming a command id
     * cannot silently disarm the gate — PredefinedCommandLibraryTest asserts
     * every id in this set still resolves.
     */
    val rawTclToolIds: Set<String> = setOf("runTclRaw", "runTclScript")
}
