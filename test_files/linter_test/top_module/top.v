// ============================================================
// top.v  —  Top-level module for linter testing
//
// HOW TO TEST:
//   1. Set 'test_files/linter_test/top_module' as the Top Folder
//      (right-click → Set as Verilog Top Folder)
//   2. Right-click this file → Set as Verilog Top File
//   3. Open any file in the folder; the linter should report
//      ZERO errors when the design is correct.
// ============================================================

module top (
    input  wire        clk,
    input  wire        rst_n,
    output wire [7:0]  count_out,
    output wire        carry_out
);

    wire [7:0] adder_result;
    wire       adder_carry;

    // Instantiate the counter sub-module
    counter u_counter (
        .clk      (clk),
        .rst_n    (rst_n),
        .count    (count_out),
        .carry    (carry_out)
    );

endmodule
