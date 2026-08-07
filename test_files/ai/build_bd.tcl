######################################################################
# KV260 : Zynq UltraScale+ PS + AXI DMA (MM2S/S2MM looped through an
# AXI4-Stream Data FIFO).  Fully explicit wiring (no apply_bd_automation
# for the data path, which mis-handles 2-masters-to-1-slave).
# Builds + validates the block design and generates the HDL wrapper.
######################################################################

set proj_name dma_ps_kv260
set proj_dir  [file normalize [file dirname [info script]]]
set part      xck26-sfvc784-2LV-c
set board     xilinx.com:kv260_som:part0:1.4
set bd_name   design_1

# ---- project -------------------------------------------------------
create_project $proj_name $proj_dir -part $part -force
set_property board_part $board [current_project]

# ---- block design --------------------------------------------------
create_bd_design $bd_name

# Processing System (Zynq UltraScale+) + KV260 board preset
create_bd_cell -type ip -vlnv xilinx.com:ip:zynq_ultra_ps_e zynq_ultra_ps_e_0
apply_bd_automation -rule xilinx.com:bd_rule:zynq_ultra_ps_e \
    -config {apply_board_preset 1} [get_bd_cells zynq_ultra_ps_e_0]

# Enable the PL-facing ports we need on top of the board preset:
#   M_AXI_HPM0_FPD (GP0) -> DMA control,  S_AXI_HP0_FPD (GP2) -> DMA data,
#   one PL clock @100MHz, one fabric reset, and PL->PS IRQ group 0.
set_property -dict [list \
    CONFIG.PSU__USE__M_AXI_GP0 {1} \
    CONFIG.PSU__USE__M_AXI_GP1 {0} \
    CONFIG.PSU__USE__S_AXI_GP2 {1} \
    CONFIG.PSU__USE__IRQ0 {1} \
    CONFIG.PSU__FPGA_PL0_ENABLE {1} \
    CONFIG.PSU__NUM_FABRIC_RESETS {1} \
    CONFIG.PSU__CRL_APB__PL0_REF_CTRL__FREQMHZ {100} \
] [get_bd_cells zynq_ultra_ps_e_0]

# AXI DMA : simple (register-direct) mode, MM2S + S2MM, 32-bit stream
create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dma axi_dma_0
set_property -dict [list \
    CONFIG.c_include_sg {0} \
    CONFIG.c_sg_include_stscntrl_strm {0} \
    CONFIG.c_include_mm2s {1} \
    CONFIG.c_include_s2mm {1} \
] [get_bd_cells axi_dma_0]

# AXI4-Stream Data FIFO : loopback MM2S -> S2MM (never wire them directly)
create_bd_cell -type ip -vlnv xilinx.com:ip:axis_data_fifo axis_data_fifo_0
set_property -dict [list \
    CONFIG.TDATA_NUM_BYTES {4} \
    CONFIG.FIFO_DEPTH {512} \
] [get_bd_cells axis_data_fifo_0]

# Interrupt concat (2 -> 1)
create_bd_cell -type ip -vlnv xilinx.com:ip:xlconcat xlconcat_0
set_property CONFIG.NUM_PORTS {2} [get_bd_cells xlconcat_0]

# SmartConnects : one for control (1->1), one for data (2->1)
create_bd_cell -type ip -vlnv xilinx.com:ip:smartconnect axi_smc_ctrl
set_property -dict [list CONFIG.NUM_SI {1} CONFIG.NUM_MI {1}] [get_bd_cells axi_smc_ctrl]
create_bd_cell -type ip -vlnv xilinx.com:ip:smartconnect axi_smc_data
set_property -dict [list CONFIG.NUM_SI {2} CONFIG.NUM_MI {1}] [get_bd_cells axi_smc_data]

# Processor System Reset
create_bd_cell -type ip -vlnv xilinx.com:ip:proc_sys_reset rst_ps_0

# ---- AXI interface connections -------------------------------------
# Control : PS HPM0_FPD -> smc_ctrl -> DMA S_AXI_LITE
connect_bd_intf_net [get_bd_intf_pins zynq_ultra_ps_e_0/M_AXI_HPM0_FPD] [get_bd_intf_pins axi_smc_ctrl/S00_AXI]
connect_bd_intf_net [get_bd_intf_pins axi_smc_ctrl/M00_AXI]            [get_bd_intf_pins axi_dma_0/S_AXI_LITE]
# Data : DMA MM2S + S2MM -> smc_data -> PS HP0_FPD
connect_bd_intf_net [get_bd_intf_pins axi_dma_0/M_AXI_MM2S] [get_bd_intf_pins axi_smc_data/S00_AXI]
connect_bd_intf_net [get_bd_intf_pins axi_dma_0/M_AXI_S2MM] [get_bd_intf_pins axi_smc_data/S01_AXI]
connect_bd_intf_net [get_bd_intf_pins axi_smc_data/M00_AXI] [get_bd_intf_pins zynq_ultra_ps_e_0/S_AXI_HP0_FPD]

# ---- stream loopback through the FIFO ------------------------------
connect_bd_intf_net [get_bd_intf_pins axi_dma_0/M_AXIS_MM2S] [get_bd_intf_pins axis_data_fifo_0/S_AXIS]
connect_bd_intf_net [get_bd_intf_pins axis_data_fifo_0/M_AXIS] [get_bd_intf_pins axi_dma_0/S_AXIS_S2MM]

# ---- interrupts : DMA -> concat -> PS IRQ0 -------------------------
connect_bd_net [get_bd_pins axi_dma_0/mm2s_introut] [get_bd_pins xlconcat_0/In0]
connect_bd_net [get_bd_pins axi_dma_0/s2mm_introut] [get_bd_pins xlconcat_0/In1]
connect_bd_net [get_bd_pins xlconcat_0/dout]        [get_bd_pins zynq_ultra_ps_e_0/pl_ps_irq0]

# ---- clocks (everything on pl_clk0) --------------------------------
set clk [get_bd_pins zynq_ultra_ps_e_0/pl_clk0]
foreach p {
    zynq_ultra_ps_e_0/maxihpm0_fpd_aclk
    zynq_ultra_ps_e_0/saxihp0_fpd_aclk
    axi_dma_0/s_axi_lite_aclk
    axi_dma_0/m_axi_mm2s_aclk
    axi_dma_0/m_axi_s2mm_aclk
    axi_smc_ctrl/aclk
    axi_smc_data/aclk
    axis_data_fifo_0/s_axis_aclk
    rst_ps_0/slowest_sync_clk
} { connect_bd_net $clk [get_bd_pins $p] }

# ---- resets --------------------------------------------------------
connect_bd_net [get_bd_pins zynq_ultra_ps_e_0/pl_resetn0] [get_bd_pins rst_ps_0/ext_reset_in]
set rstn [get_bd_pins rst_ps_0/peripheral_aresetn]
foreach p {
    axi_dma_0/axi_resetn
    axi_smc_ctrl/aresetn
    axi_smc_data/aresetn
    axis_data_fifo_0/s_axis_aresetn
} { connect_bd_net $rstn [get_bd_pins $p] }

# ---- finalize ------------------------------------------------------
regenerate_bd_layout
assign_bd_address
save_bd_design
validate_bd_design

# ---- HDL wrapper (import so it lives in the project) ---------------
make_wrapper -files [get_files ${bd_name}.bd] -top -import
set_property top ${bd_name}_wrapper [current_fileset]
update_compile_order -fileset sources_1

puts "==== BD_BUILD_DONE top=[get_property top [current_fileset]] ===="
