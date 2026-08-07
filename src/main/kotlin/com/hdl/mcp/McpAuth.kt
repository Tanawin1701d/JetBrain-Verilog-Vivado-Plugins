package com.hdl.mcp

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Access control for the embedded MCP server.
 *
 * Binding to loopback is not a security boundary on its own: any process on the
 * machine can reach 127.0.0.1, and — because a browser will happily POST to a
 * loopback address — so can any web page the user has open. Two gates close that:
 *
 *  1. Reject anything carrying an `Origin` header. Browsers always send it on a
 *     cross-origin request; MCP clients (Claude Code, Codex, Junie) never do.
 *  2. Require a per-session bearer token that only the user can have copied out
 *     of the Vivado Console panel.
 *
 * Pure and side-effect free so the gate can be unit-tested without a live server.
 */
internal object McpAuth {

    /** Result of evaluating one request's credentials. */
    enum class Decision(val httpStatus: Int, val message: String) {
        ALLOW(200, "ok"),
        DENY_BROWSER_ORIGIN(403, "Requests from a browser origin are not accepted"),
        DENY_MISSING_TOKEN(401, "Missing Authorization: Bearer <token> header"),
        DENY_BAD_TOKEN(401, "Invalid MCP token")
    }

    private val RANDOM = SecureRandom()

    /** A fresh 256-bit session token, URL-safe so it survives config files intact. */
    fun newToken(): String {
        val bytes = ByteArray(32)
        RANDOM.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Decide whether one request may proceed.
     *
     * @param originHeader the request's `Origin` header, or null when absent
     * @param authHeader   the request's `Authorization` header, or null when absent
     * @param expectedToken the token minted for this server session
     */
    fun evaluate(originHeader: String?, authHeader: String?, expectedToken: String): Decision {
        // A present Origin means a browser sent this. No MCP client does.
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
