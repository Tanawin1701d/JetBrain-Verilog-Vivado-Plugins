// ============================================================
// counter.v  —  8-bit synchronous up-counter
// ============================================================

module counter (
    input  wire       clk,
    input  wire       rst_n,
    output reg  [7:0] count,
    output wire       carry
);

    assign carry = (count == 8'hFF);

    always @(posedge clk or negedge rst_n) begin
        if (!rst_n)
            count <= 8'h00;
        else
            count <= count + 8'h01;
    end

endmodule
