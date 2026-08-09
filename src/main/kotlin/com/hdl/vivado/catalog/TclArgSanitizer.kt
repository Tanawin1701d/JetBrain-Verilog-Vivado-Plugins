package com.hdl.vivado.catalog

/**
 * Validates the argument string handed to runVivadoCommand.
 *
 * runVivadoCommand builds "<command> <args>" where <command> is already pinned to a name in
 * [VivadoCommandCatalog], so `exec`, `source`, `eval` and `file` are unreachable as the
 * command itself. This class closes the other half: it stops <args> from smuggling a second
 * command in behind the first.
 *
 * That boundary is what lets runVivadoCommand stay available in Safe mode, where runTclRaw
 * and runTclScript are hidden — it is strictly narrower than raw Tcl, not a rename of it.
 *
 * Real Vivado arguments need command substitution (`-period 10 [get_ports clk]` is the normal
 * way to write almost anything), so `[...]` cannot simply be banned. Instead only read-only
 * query commands are allowed to head a substitution; everything else is refused.
 */
object TclArgSanitizer {

    // Heads permitted inside [...]: object queries plus the pure list/string builtins that
    // show up in dict and list arguments. None of these touch the filesystem or spawn a process.
    private val ALLOWED_SUBSTITUTION_HEAD = Regex(
        "^(get_[a-z0-9_]+|all_[a-z0-9_]+|current_[a-z0-9_]+|" +
            "list|lindex|llength|lrange|lsort|lsearch|lreverse|concat|join|split|expr|format|string)$"
    )

    /**
     * Returns null when [args] is safe to append to a whitelisted command, or a message
     * explaining the refusal. Callers surface the message to the model so it can retry.
     */
    fun reject(args: String): String? {
        if (args.isBlank()) return null

        // Statement separators and line breaks are the direct route to a second command.
        if (args.contains('\n') || args.contains('\r')) {
            return "arguments may not contain line breaks (they would start a second Tcl command)"
        }
        if (args.contains(';')) {
            return "';' is not allowed in arguments — it would start a second Tcl command"
        }
        // Backslash is Tcl's escape character: \n, \x0a and line continuations all reintroduce
        // the separators banned above. Vivado accepts forward slashes in paths on every platform.
        if (args.contains('\\')) {
            return "'\\' is not allowed in arguments — use forward slashes in paths"
        }
        if (args.contains('`')) {
            return "'`' is not allowed in arguments"
        }
        // Variable substitution can pull in anything the Vivado session happens to hold.
        if (args.contains('$')) {
            return "'\$' variable substitution is not allowed in arguments"
        }
        // {*} expands a list into separate words, which can inject option words the caller
        // never wrote — and defeats the substitution-head check below.
        if (args.contains("{*}")) {
            return "'{*}' argument expansion is not allowed"
        }

        var braces = 0
        var brackets = 0
        var quotes = 0
        var i = 0
        while (i < args.length) {
            when (args[i]) {
                '{' -> braces++
                '}' -> {
                    braces--
                    if (braces < 0) return "unbalanced '}' in arguments"
                }
                '"' -> quotes++
                ']' -> {
                    brackets--
                    if (brackets < 0) return "unbalanced ']' in arguments"
                }
                '[' -> {
                    brackets++
                    val head = substitutionHead(args, i + 1)
                    if (!ALLOWED_SUBSTITUTION_HEAD.matches(head)) {
                        return "'[$head ...]' is not allowed — only read-only queries " +
                            "(get_*, all_*, current_*, list, lindex, expr, ...) may be substituted " +
                            "inside arguments"
                    }
                }
            }
            i++
        }
        if (braces != 0) return "unbalanced '{' in arguments"
        if (brackets != 0) return "unbalanced '[' in arguments"
        if (quotes % 2 != 0) return "unbalanced '\"' in arguments"
        return null
    }

    // The first word after a '[', which is the command Tcl would run for that substitution.
    private fun substitutionHead(args: String, from: Int): String {
        var start = from
        while (start < args.length && args[start] == ' ') start++
        var end = start
        while (end < args.length && !args[end].isWhitespace() && args[end] != ']' && args[end] != '[') end++
        return args.substring(start, end)
    }
}
