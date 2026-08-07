package com.hdl.mcp

/**
 * Minimal JSON helpers for the MCP wire format.
 *
 * Deliberately dependency-free: MCP only ever sends shallow objects, so a few
 * regexes plus a brace scanner are enough and keep the plugin jar clean.
 *
 * Extracted from VivaMcpServer so these are pure functions with no Project
 * dependency — that makes them unit-testable without an IDE fixture.
 */
internal object McpJson {

    // -------------------------------------------------------------------------
    // Writing
    // -------------------------------------------------------------------------

    /** Quote + escape a string so it is safe to splice into hand-built JSON. */
    fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when (c) {
                '\\'     -> sb.append("\\\\")
                '"'      -> sb.append("\\\"")
                '\n'     -> sb.append("\\n")
                '\r'     -> sb.append("\\r")
                '\t'     -> sb.append("\\t")
                '\b'     -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                // Any other control character must be escaped or the JSON is invalid.
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    // -------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------

    /**
     * Turn a JSON-escaped string body back into its real characters.
     *
     * Single left-to-right pass: an escape pair is consumed as a unit, so the
     * backslash of one pair can never be re-read as the start of the next.
     * (Chained String.replace calls cannot do this correctly in any order —
     * "\\n" would collapse to a newline instead of backslash + 'n'.)
     */
    fun unescape(s: String): String {
        if (!s.contains('\\')) return s          // fast path: nothing to do

        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            // A trailing lone backslash has no pair — emit it literally.
            if (c != '\\' || i == s.lastIndex) {
                sb.append(c); i++; continue
            }
            when (s[i + 1]) {
                '"'  -> { sb.append('"');  i += 2 }
                '\\' -> { sb.append('\\'); i += 2 }
                '/'  -> { sb.append('/');  i += 2 }
                'n'  -> { sb.append('\n'); i += 2 }
                'r'  -> { sb.append('\r'); i += 2 }
                't'  -> { sb.append('\t'); i += 2 }
                'b'  -> { sb.append('\b'); i += 2 }
                'f'  -> { sb.append('\u000C'); i += 2 }
                'u'  -> {
                    val hex = s.substring(i + 2, minOf(i + 6, s.length))
                    val code = if (hex.length == 4) hex.toIntOrNull(16) else null
                    if (code != null) { sb.append(code.toChar()); i += 6 }
                    else { sb.append(c); i++ }   // malformed \u — keep the backslash
                }
                // Not a JSON escape (e.g. a Tcl "\{"): keep the backslash as-is.
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString()
    }

    /** Pull a string-valued field out of raw JSON, unescaped. */
    fun stringField(json: String, field: String): String? {
        val pattern = Regex("\"${Regex.escape(field)}\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        return pattern.find(json)?.groupValues?.get(1)?.let(::unescape)
    }

    /** Pull a non-string scalar (number / bool / null) up to the next delimiter. */
    fun scalarField(json: String, field: String): String? {
        val pattern = Regex("\"${Regex.escape(field)}\"\\s*:\\s*([^,}\\]]+)")
        return pattern.find(json)?.groupValues?.get(1)?.trim()
    }

    /**
     * Extract a nested object value by scanning brace depth — a regex cannot
     * balance braces. String literals are skipped so that braces *inside* a
     * value (Vivado property strings are full of them, e.g. "CONFIG.FOO {1}")
     * do not throw the depth count off.
     */
    fun objectField(json: String, field: String): String? {
        val key = "\"$field\""
        val keyAt = json.indexOf(key)
        if (keyAt < 0) return null

        val start = json.indexOf('{', keyAt + key.length)
        if (start < 0) return null

        var depth = 0
        var inString = false
        var i = start
        while (i < json.length) {
            val c = json[i]
            if (inString) {
                when (c) {
                    '\\' -> i++             // skip whatever this escape covers
                    '"'  -> inString = false
                }
            } else {
                when (c) {
                    '"'  -> inString = true
                    '{'  -> depth++
                    '}'  -> { depth--; if (depth == 0) return json.substring(start, i + 1) }
                }
            }
            i++
        }
        return null   // unbalanced input
    }

    // "key": followed by a string, a number, or a bare literal.
    private val KEY_VALUE = Regex(
        "\"([^\"\\\\]*)\"\\s*:\\s*(?:" +
            "\"((?:[^\"\\\\]|\\\\.)*)\"" +                  // group 2 — string body
            "|(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)" +     // group 3 — number
            "|(true|false|null)" +                          // group 4 — literal
        ")"
    )

    /**
     * Parse a flat JSON object into a String→String map.
     *
     * Every value arrives as a String, including numbers — the command
     * generators all interpolate with toString(), so this is intentional.
     */
    fun flatObject(json: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (m in KEY_VALUE.findAll(json)) {
            val key = m.groupValues[1]
            // Group presence (not emptiness) picks the branch, so "" parses as an empty string.
            val value = m.groups[2]?.value?.let(::unescape)
                ?: m.groups[3]?.value
                ?: m.groups[4]?.value
                ?: continue
            out[key] = value
        }
        return out
    }
}
