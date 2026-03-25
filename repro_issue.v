module fourSp(
    input clk,
    input rst_n,
    input [3:0] in_now,
    output reg [7:0] load_reset,
    output reg [7:0] store_reset,
    output reg [7:0] load_init,
    output reg [7:0] store_init
);

reg [3:0] in_d;

always @(posedge clk) begin
    if (!rst_n) begin
        in_d <= 4'b0000;
    end else begin
        in_d <= in_now;
    end
end

always @(posedge clk) begin
    if (!rst_n) begin
        load_reset <= 1'b0;
        store_reset <= 1'b0;
        load_init <= 1'b0;
        store_init <= 1'b0;
    end else begin
        load_reset[1]  <= in_now[0] & ~in_d[0];
        store_reset[1] <= in_now[1] & ~in_d[1];
        load_init[1]   <= in_now[2] & ~in_d[2];
        store_init[1]  <= in_now[3] & ~in_d[3];
    end
end

endmodule
