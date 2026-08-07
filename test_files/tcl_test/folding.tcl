# Fixture: TclFoldingBuilder — every construct that should collapse.
#
# How to test: open this file and use Ctrl+Shift+Minus (collapse all).
# Each block below must fold to a single line, and Ctrl+Shift+Plus must
# restore it. Nested blocks must fold independently of their parent.

# ---- proc body ---------------------------------------------------------------
proc build_project {name part} {
    create_project $name ./$name -part $part -force
    set_property target_language Verilog [current_project]
    return $name
}

# ---- proc with a nested proc -------------------------------------------------
proc outer_scope {} {
    proc inner_scope {} {
        puts "inner"
    }
    inner_scope
}

# ---- for loop ----------------------------------------------------------------
for {set i 0} {$i < 8} {incr i} {
    puts "iteration $i"
}

# ---- foreach over a list -----------------------------------------------------
foreach src [glob -nocomplain ./rtl/*.v] {
    add_files -norecurse $src
}

# ---- while loop --------------------------------------------------------------
set countdown 3
while {$countdown > 0} {
    puts "t-minus $countdown"
    incr countdown -1
}

# ---- if / elseif / else ------------------------------------------------------
if {[llength [get_projects]] == 0} {
    puts "no project open"
} elseif {[current_project] eq "scratch"} {
    puts "scratch project"
} else {
    puts "project: [current_project]"
}

# ---- switch ------------------------------------------------------------------
switch -- $::env(USER) {
    root    { puts "running as root" }
    default { puts "running as a normal user" }
}

# ---- a bare braced block -----------------------------------------------------
namespace eval ::fixture {
    variable version "1.0"
    proc report {} {
        variable version
        puts "fixture $version"
    }
}

# ---- deeply nested braces ----------------------------------------------------
proc deep {} {
    if {1} {
        for {set i 0} {$i < 2} {incr i} {
            foreach x {a b} {
                if {$x eq "a"} {
                    puts "deep: $i $x"
                }
            }
        }
    }
}
