# ============================================================
# constraints.xdc  —  Sample XDC constraint file
#
# This file is treated as a Tcl/XDC file by the plugin and
# supports syntax highlighting.
#
# HOW TO TEST:
#   Open this file — you should see Tcl syntax highlighting.
#   Right-click → Vivado → Run Tcl Script (loads in Vivado GUI).
# ============================================================

# Clock constraint — 100 MHz on pin E3
create_clock -period 10.000 -name sys_clk [get_ports clk]

# I/O constraints for Nexys A7-35T
set_property PACKAGE_PIN E3     [get_ports clk]
set_property IOSTANDARD  LVCMOS33 [get_ports clk]

set_property PACKAGE_PIN J15    [get_ports rst_n]
set_property IOSTANDARD  LVCMOS33 [get_ports rst_n]

# 8-bit LED output (count_out)
set_property PACKAGE_PIN H17    [get_ports {count_out[0]}]
set_property PACKAGE_PIN K15    [get_ports {count_out[1]}]
set_property PACKAGE_PIN J13    [get_ports {count_out[2]}]
set_property PACKAGE_PIN N14    [get_ports {count_out[3]}]
set_property PACKAGE_PIN R18    [get_ports {count_out[4]}]
set_property PACKAGE_PIN V17    [get_ports {count_out[5]}]
set_property PACKAGE_PIN U17    [get_ports {count_out[6]}]
set_property PACKAGE_PIN U16    [get_ports {count_out[7]}]

set_property IOSTANDARD  LVCMOS33 [get_ports {count_out[*]}]
