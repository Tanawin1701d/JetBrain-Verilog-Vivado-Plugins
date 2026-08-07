# Fixture: .xdc files route through the Tcl language support.
#
# How to test: this file must get Tcl syntax highlighting (not plain text),
# folding on braced blocks, and Vivado command completion — the .xdc extension
# is registered against TclFileType alongside .tcl.

# ---- clock definition --------------------------------------------------------
create_clock -period 10.000 -name sys_clk -waveform {0.000 5.000} [get_ports clk]

# ---- generated clock ---------------------------------------------------------
create_generated_clock -name clk_div2 -source [get_ports clk] -divide_by 2 \
    [get_pins clk_divider/q_reg[0]/Q]

# ---- input / output delays ---------------------------------------------------
set_input_delay  -clock sys_clk -max 2.000 [get_ports {data_in[*]}]
set_input_delay  -clock sys_clk -min 0.500 [get_ports {data_in[*]}]
set_output_delay -clock sys_clk -max 3.000 [get_ports {led[*]}]

# ---- pin assignments ---------------------------------------------------------
set_property -dict {PACKAGE_PIN W5  IOSTANDARD LVCMOS33} [get_ports clk]
set_property -dict {PACKAGE_PIN U16 IOSTANDARD LVCMOS33} [get_ports {led[0]}]
set_property -dict {PACKAGE_PIN E19 IOSTANDARD LVCMOS33} [get_ports {led[1]}]
set_property -dict {PACKAGE_PIN U19 IOSTANDARD LVCMOS33} [get_ports {led[2]}]
set_property -dict {PACKAGE_PIN V19 IOSTANDARD LVCMOS33} [get_ports {led[3]}]
set_property -dict {PACKAGE_PIN U18 IOSTANDARD LVCMOS33} [get_ports rst_n]

# ---- false paths and exceptions ----------------------------------------------
set_false_path -from [get_ports rst_n]
set_multicycle_path -setup 2 -from [get_cells slow_reg] -to [get_cells slow_dst_reg]

# ---- configuration -----------------------------------------------------------
set_property CFGBVS VCCO        [current_design]
set_property CONFIG_VOLTAGE 3.3 [current_design]

# ---- a foldable braced block -------------------------------------------------
foreach port {led[0] led[1] led[2] led[3]} {
    set_property DRIVE 12 [get_ports $port]
    set_property SLEW SLOW [get_ports $port]
}
