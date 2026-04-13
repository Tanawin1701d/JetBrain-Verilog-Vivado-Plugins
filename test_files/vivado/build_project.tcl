# ============================================================
# build_project.tcl  —  Sample Vivado build script
#
# HOW TO TEST:
#   Right-click this file in the Project view →
#   Vivado → Run Tcl Script
#   (Requires Vivado path configured in HDL Settings)
# ============================================================

# Print a header
puts "=== HDL Plugin: Sample Build Script ==="
puts "Vivado version: [version -short]"

# Create a project (adjust path as needed)
set proj_name "test_project"
set proj_dir  "[file dirname [info script]]/vivado_output"
set part      "xc7a35tcpg236-1"

create_project $proj_name $proj_dir -part $part -force

# Add source files
set src_dir "[file dirname [info script]]/../linter_test/top_module"
add_files -norecurse [glob -nocomplain $src_dir/*.v]
update_compile_order -fileset sources_1

puts "Files added: [get_files -filter {FILE_TYPE == Verilog}]"
puts "=== Script complete ==="
