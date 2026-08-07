# Constraints for the MCP fixture design.
#
# Pin assignments are commented out on purpose — this fixture must synthesise
# on any part. Uncomment and edit the block at the bottom only if you want to
# take it all the way to a bitstream on real hardware.

create_clock -period 10.000 -name sys_clk [get_ports clk]

set_false_path -from [get_ports rst_n]
set_output_delay -clock sys_clk -max 3.000 [get_ports {led[*]}]

set_property CFGBVS VCCO        [current_design]
set_property CONFIG_VOLTAGE 3.3 [current_design]

# ---- board-specific pins (example: Arty A7-35T) -------------------------------
# set_property -dict {PACKAGE_PIN E3  IOSTANDARD LVCMOS33} [get_ports clk]
# set_property -dict {PACKAGE_PIN C2  IOSTANDARD LVCMOS33} [get_ports rst_n]
# set_property -dict {PACKAGE_PIN H5  IOSTANDARD LVCMOS33} [get_ports {led[0]}]
# set_property -dict {PACKAGE_PIN J5  IOSTANDARD LVCMOS33} [get_ports {led[1]}]
# set_property -dict {PACKAGE_PIN T9  IOSTANDARD LVCMOS33} [get_ports {led[2]}]
# set_property -dict {PACKAGE_PIN T10 IOSTANDARD LVCMOS33} [get_ports {led[3]}]
