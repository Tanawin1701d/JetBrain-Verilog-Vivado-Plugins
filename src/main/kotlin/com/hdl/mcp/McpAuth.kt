package com.hdl.mcp

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Access control for the embedded MCP server.
 *
 * Binding to loopback is not a security boundary on its own: any process on the
 * machine can reach 127.0.0.1, and — because a browser will happily POST to a
 * loopback address — so can any web page the user has open. Three gates close that:
 *
 *  1. Reject anything whose `Host` header is not a loopback name. This is the
 *     DNS-rebinding defence: an attacker who points evil.com at 127.0.0.1 makes
 *     the browser treat the request as *same-origin*, so no `Origin` header is
 *     sent and gate 2 never fires — but `Host` still says `evil.com`.
 *  2. Reject anything carrying an `Origin` header. Browsers always send it on a
 *     cross-origin request; MCP clients (Claude Code, Codex, Junie) never do.
 *  3. Require a per-session bearer token that only the user can have copied out
 *     of the Vivado Console panel.
 *
 * Pure and side-effect free so the gate can be unit-tested without a live server.
 */
internal object McpAuth {

    /** Result of evaluating one request's credentials. */
    enum class Decision(val httpStatus: Int, val message: String) {
        ALLOW(200, "ok"),
        DENY_BAD_HOST(403, "Host header must be a loopback address"),
        DENY_BROWSER_ORIGIN(403, "Requests from a browser origin are not accepted"),
        DENY_MISSING_TOKEN(401, "Missing Authorization: Bearer <token> header"),
        DENY_BAD_TOKEN(401, "Invalid MCP token")
    }

    /** Names that resolve to this machine. Anything else means the request was rebound. */
    private val LOOPBACK_HOSTS = setOf("127.0.0.1", "localhost", "[::1]")

    private val RANDOM = SecureRandom()

    /**
     * True when [hostHeader] names a loopback address, with or without a port.
     *
     * HTTP/1.1 makes `Host` mandatory, so an absent header is refused rather than
     * waved through.
     */
    fun isLoopbackHost(hostHeader: String?): Boolean {
        val raw = hostHeader?.trim().orEmpty()
        if (raw.isEmpty()) return false

        // IPv6 literals are bracketed: "[::1]" or "[::1]:19999".
        val name = if (raw.startsWith("[")) {
            val close = raw.indexOf(']')
            if (close < 0) return false
            raw.substring(0, close + 1)
        } else {
            raw.substringBefore(':')
        }
        return name.lowercase() in LOOPBACK_HOSTS
    }

    /** A fresh 256-bit session token, URL-safe so it survives config files intact. */
    fun newToken(): String {
        val bytes = ByteArray(32)
        RANDOM.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Decide whether one request may proceed.
     *
     * @param hostHeader   the request's `Host` header, or null when absent
     * @param originHeader the request's `Origin` header, or null when absent
     * @param authHeader   the request's `Authorization` header, or null when absent
     * @param expectedToken the token minted for this server session
     */
    fun evaluate(
        hostHeader: String?,
        originHeader: String?,
        authHeader: String?,
        expectedToken: String
    ): Decision {
        // Anything but a loopback Host means the name was rebound to point here.
        if (!isLoopbackHost(hostHeader)) return Decision.DENY_BAD_HOST

        // A present Origin means a browser sent this cross-origin. No MCP client does.
        if (!originHeader.isNullOrBlank()) return Decision.DENY_BROWSER_ORIGIN

        val presented = authHeader
            ?.trim()
            ?.takeIf { it.regionMatches(0, "Bearer ", 0, 7, ignoreCase = true) }
            ?.substring(7)
            ?.trim()
            ?: return Decision.DENY_MISSING_TOKEN

        if (presented.isEmpty()) return Decision.DENY_MISSING_TOKEN

        // Constant-time compare so a wrong token leaks no timing signal.
        val a = presented.toByteArray(Charsets.UTF_8)
        val b = expectedToken.toByteArray(Charsets.UTF_8)
        return if (MessageDigest.isEqual(a, b)) Decision.ALLOW else Decision.DENY_BAD_TOKEN
    }
}
