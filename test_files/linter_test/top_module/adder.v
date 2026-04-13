// ============================================================
// adder.v  —  Simple 8-bit adder (auxiliary sub-module)
// ============================================================

module adder (
    input  wire [7:0] a,
    input  wire [7:0] b,
    output wire [7:0] sum,
    output wire       carry_out
);

    assign {carry_out, sum} = a + b;

endmodule
