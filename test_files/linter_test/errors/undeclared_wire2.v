
module undeclared_wire_demo2 (
    input  wire clk,
    output wire out
);

    // 'undefined_signal' is never declared — linter should flag this

    undeclared_wire_demo3 f(clk, out);

endmodule