# Fixture: TclSyntaxHighlighter + TclCompletionContributor.
#
# How to test:
#   1. Highlighting — every category below should get a distinct colour.
#      Compare against Settings > Editor > Color Scheme > Tcl.
#   2. Completion — type the prefixes listed at the bottom and check the popup.

# ---- comments ----------------------------------------------------------------
# A full-line comment.
set x 1  ;# a trailing comment after a command

# ---- strings -----------------------------------------------------------------
set plain      "a double quoted string"
set with_esc   "an escaped quote \" and a backslash \\"
set braced     {a braced literal — no substitution happens in here}
set with_brace "a string containing braces {1} and {0}"

# ---- variables in every form -------------------------------------------------
set simple    $x
set curly     ${x}
set in_string "value is $x and ${x}"
set array_el  $::env(HOME)
set nested    ${::fixture::version}
set cmd_subst [expr {$x + 1}]

# ---- numbers -----------------------------------------------------------------
set decimal   42
set negative  -17
set floating  3.14159
set hex       0xDEADBEEF
set binary    0b1010
set verilog   8'hFF

# ---- Vivado commands (completion targets) ------------------------------------
create_project fixture ./fixture -part xc7a35tcpg236-1 -force
set_property target_language Verilog [current_project]
add_files -norecurse ./rtl/blink.v
add_files -fileset constrs_1 -norecurse ./constraints/blink.xdc
set_property top blink [current_fileset]
update_compile_order -fileset sources_1

launch_runs synth_1 -jobs 4
wait_on_run synth_1

launch_runs impl_1 -to_step write_bitstream -jobs 4
wait_on_run impl_1

open_run impl_1
report_timing_summary -file ./timing.rpt
report_utilization    -file ./utilization.rpt

close_project

# ---- completion checklist ----------------------------------------------------
# Type each prefix on a blank line and confirm the popup offers the command:
#   create_   -> create_project, create_bd_design, create_ip, create_clock
#   launch_   -> launch_runs
#   wait_     -> wait_on_run
#   set_prop  -> set_property
#   get_bd    -> get_bd_cells, get_bd_intf_pins, get_bd_pins
#   report_   -> report_timing_summary, report_utilization
#   connect_  -> connect_bd_intf_net, connect_bd_net
