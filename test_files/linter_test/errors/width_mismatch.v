// ============================================================
// width_mismatch.v  —  Intentional WIDTH MISMATCH warning
//
// EXPECTED:  Verilator reports a width mismatch warning
//            (iverilog may pass silently for this one)
// ============================================================

module width_mismatch_demo (
    input  wire [7:0] data_in,
    output reg  [3:0] data_out
);

    // Assigning 8-bit signal to 4-bit register — truncation warning
    always @(*) begin
        data_out = data_in;  // <-- WARNING: implicit truncation
    end

endmodule
