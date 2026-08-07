// Minimal, board-independent design for the MCP end-to-end fixture.
//
// Deliberately trivial: the point is exercising the MCP tool chain
// (createProject -> addFiles -> setTopModule -> runSynthesis -> ...),
// not the RTL. Synthesises in seconds on any 7-series or Zynq part.

module blink #(
    parameter integer COUNTER_WIDTH = 24
)(
    input  wire       clk,
    input  wire       rst_n,
    output wire [3:0] led
);

    reg [COUNTER_WIDTH-1:0] counter;

    always @(posedge clk or negedge rst_n) begin
        if (!rst_n) begin
            counter <= {COUNTER_WIDTH{1'b0}};
        end else begin
            counter <= counter + 1'b1;
        end
    end

    // Top four counter bits drive the LEDs.
    assign led = counter[COUNTER_WIDTH-1 -: 4];

endmodule
