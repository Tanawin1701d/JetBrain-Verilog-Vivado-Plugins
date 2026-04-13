// ============================================================
// pkg_types.sv  —  SystemVerilog package with type definitions
// ============================================================

package pkg_types;

    // Custom typedef for 8-bit bus
    typedef logic [7:0] byte_t;

    // Enumeration for FSM states
    typedef enum logic [1:0] {
        STATE_IDLE  = 2'b00,
        STATE_RUN   = 2'b01,
        STATE_DONE  = 2'b10,
        STATE_ERROR = 2'b11
    } state_t;

endpackage
