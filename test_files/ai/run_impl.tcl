######################################################################
# Run synthesis -> implementation -> write_bitstream for dma_ps_kv260.
######################################################################
set proj_dir [file normalize [file dirname [info script]]]
open_project $proj_dir/dma_ps_kv260.xpr
update_compile_order -fileset sources_1

# launch_runs pulls synth_1 in automatically if impl_1 is out of date.
launch_runs impl_1 -to_step write_bitstream -jobs 8
wait_on_run impl_1

set bit $proj_dir/dma_ps_kv260.runs/impl_1/design_1_wrapper.bit
if {[file exists $bit]} {
    puts "==== BITSTREAM_DONE $bit ===="
} else {
    puts "==== BITSTREAM_FAILED (no .bit produced) ===="
    puts "synth_1 status: [get_property STATUS [get_runs synth_1]]"
    puts "impl_1  status: [get_property STATUS [get_runs impl_1]]"
}
