package com.hdl.vivado

object PredefinedCommandLibrary {

    private fun param(name: String, type: ParameterType, required: Boolean, desc: String, default: Any? = null) =
        CommandParameter(name, type, required, desc, default)

    private fun str(n: String, req: Boolean, desc: String) = param(n, ParameterType.STRING, req, desc)
    private fun int(n: String, req: Boolean, desc: String, def: Int? = null) = param(n, ParameterType.INT, req, desc, def)

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
        )
    )

    fun findById(id: String): PredefinedCommand? = commands.firstOrNull { it.id == id }
}
