// ============================================================
// counter_sv.sv  —  SystemVerilog counter using package types
//
// HOW TO TEST:
//   Set 'test_files/systemverilog' as Top Folder and this file
//   as Top File, then open it — linter should report no errors.
// ============================================================

import pkg_types::*;

module counter_sv (
    input  logic      clk,
    input  logic      rst_n,
    output byte_t     count,
    output state_t    state
);

    always_ff @(posedge clk or negedge rst_n) begin
        if (!rst_n) begin
            count <= '0;
            state <= STATE_IDLE;
        end else begin
            count <= count + 1'b1;
            state <= (count == 8'hFF) ? STATE_DONE : STATE_RUN;
        end
    end

endmodule
