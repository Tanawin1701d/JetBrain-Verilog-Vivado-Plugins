// Fixture: identifiers that LOOK like structural keywords but are not.
//
// A prefix-based lexer would wrongly tokenise these and produce phantom brace
// matches. Every name below must lex as a plain IDENTIFIER.
// Pinned by VerilogLexerTest."keyword matching is exact not prefix based".

module not_keywords (
    input  wire clk,
    output reg  done
);

    // Identifiers whose text starts with, or contains, a structural keyword.
    reg endmodules;        // "endmodule" + s
    reg module_name;       // "module" + _name
    reg beginning;         // "begin" + ning
    reg ending;            // "end" + ing
    reg casement;          // "case" + ment
    reg endcases;          // "endcase" + s
    reg my_generate;       // ends with "generate"
    reg task_id;           // "task" + _id
    reg functional;        // "function" + al

    // Real pairs, so the file still has something valid to match against.
    always @(posedge clk) begin
        case (module_name)
            1'b0:    done <= beginning;
            default: done <= ending;
        endcase
    end

    // Keywords appearing inside a comment must not match:
    // module begin case function task generate
    // endmodule end endcase endfunction endtask endgenerate

    initial begin
        // Keywords inside a string literal must not match either.
        $display("module begin case endmodule end endcase");
    end

endmodule
