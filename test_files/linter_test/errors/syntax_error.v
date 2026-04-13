// ============================================================
// syntax_error.v  —  Intentional SYNTAX ERROR for linter test
//
// EXPECTED:  linter reports a syntax/parse error on ~line 14
// ============================================================

module syntax_error_demo (
    input  wire clk,
    output wire out
);

    // Missing semicolon below — linter should flag this
    assign out = clk      // <-- ERROR: missing semicolon

endmodule
