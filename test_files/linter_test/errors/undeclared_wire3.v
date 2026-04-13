// ============================================================
// undeclared_wire.v  —  Intentional UNDECLARED IDENTIFIER error
//
// EXPECTED:  linter reports 'undefined_signal' is not declared
// ============================================================

module undeclared_wire_demo3 (
    input  wire clk,
    output wire out
);

    // 'undefined_signal' is never declared — linter should flag this
    assign out = clk;
    assign out = clk & ffff;

endmodule