// Fixture: every structural keyword pair the v0.3 lexer emits a distinct token for.
//
// How to test: put the caret on each opening keyword below. The IDE should
// highlight its matching closing keyword, and vice versa. Covered by
// VerilogLexerTest, but brace *matching* itself is only visible in the editor.

module all_pairs #(
    parameter WIDTH = 8
)(
    input  wire             clk,
    input  wire             rst_n,
    input  wire [WIDTH-1:0] data_in,
    output reg  [WIDTH-1:0] data_out
);

    // ---- function / endfunction -------------------------------------------
    function [WIDTH-1:0] invert;
        input [WIDTH-1:0] value;
        begin                                   // begin / end (inside function)
            invert = ~value;
        end
    endfunction

    // ---- task / endtask ----------------------------------------------------
    task reset_output;
        begin                                   // begin / end (inside task)
            data_out = {WIDTH{1'b0}};
        end
    endtask

    // ---- generate / endgenerate -------------------------------------------
    genvar i;
    generate
        for (i = 0; i < WIDTH; i = i + 1) begin : gen_loop
            // named generate block — begin / end must still pair here
            wire unused_bit;
            assign unused_bit = data_in[i];
        end
    endgenerate

    // ---- case / endcase ----------------------------------------------------
    reg [1:0] mode;

    always @(posedge clk or negedge rst_n) begin
        if (!rst_n) begin
            reset_output;
            mode <= 2'b00;
        end else begin
            case (mode)                         // case / endcase
                2'b00:   data_out <= data_in;
                2'b01:   data_out <= invert(data_in);
                2'b10:   begin                  // begin / end inside a case arm
                             data_out <= data_in + 1'b1;
                             mode     <= 2'b11;
                         end
                default: data_out <= {WIDTH{1'b0}};
            endcase
        end
    end

endmodule
