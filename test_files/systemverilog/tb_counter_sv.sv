// ============================================================
// tb_counter_sv.sv  —  SystemVerilog testbench
// ============================================================

`timescale 1ns/1ps

module tb_counter_sv;

    logic      clk;
    logic      rst_n;
    logic [7:0] count;
    logic [1:0] state;

    // Instantiate the DUT
    counter_sv dut (
        .clk   (clk),
        .rst_n (rst_n),
        .count (count),
        .state (state)
    );

    // Clock: 10 ns period
    initial clk = 0;
    always #5 clk = ~clk;

    initial begin
        rst_n = 0;
        #20;
        rst_n = 1;
        #300;
        $finish;
    end

endmodule
