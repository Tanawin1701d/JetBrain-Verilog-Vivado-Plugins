package com.hdl.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests for the MCP access gate.
 *
 * Before this existed the server bound to loopback and sent
 * `Access-Control-Allow-Origin: *` with no credential — which meant any web page
 * the user had open could drive their Vivado session. Both halves of the fix are
 * pinned here.
 */
class McpAuthTest {

    private val TOKEN = "test-token-abc123"

    // -------------------------------------------------------------------------
    // The regression: browser-originated requests
    // -------------------------------------------------------------------------

    @Test
    fun `a request carrying an Origin header is refused even with a valid token`() {
        // A malicious page can obtain nothing else, but it can always send Origin —
        // and a browser always attaches it on a cross-origin request.
        assertEquals(
            McpAuth.Decision.DENY_BROWSER_ORIGIN,
            McpAuth.evaluate("https://evil.example", "Bearer $TOKEN", TOKEN)
        )
    }

    @Test
    fun `any origin is refused, not just a known-bad one`() {
        for (origin in listOf("http://localhost:3000", "null", "https://example.com", "file://")) {
            assertEquals(
                McpAuth.Decision.DENY_BROWSER_ORIGIN,
                McpAuth.evaluate(origin, "Bearer $TOKEN", TOKEN),
                "origin '$origin' should be refused"
            )
        }
    }

    @Test
    fun `a blank Origin header is treated as absent`() {
        // Some proxies attach an empty header; that is not a browser origin.
        assertEquals(McpAuth.Decision.ALLOW, McpAuth.evaluate("", "Bearer $TOKEN", TOKEN))
        assertEquals(McpAuth.Decision.ALLOW, McpAuth.evaluate("   ", "Bearer $TOKEN", TOKEN))
    }

    // -------------------------------------------------------------------------
    // Token checking
    // -------------------------------------------------------------------------

    @Test
    fun `a correct bearer token from a non-browser client is allowed`() {
        assertEquals(McpAuth.Decision.ALLOW, McpAuth.evaluate(null, "Bearer $TOKEN", TOKEN))
    }

    @Test
    fun `a missing Authorization header is refused`() {
        assertEquals(McpAuth.Decision.DENY_MISSING_TOKEN, McpAuth.evaluate(null, null, TOKEN))
    }

    @Test
    fun `a wrong token is refused`() {
        assertEquals(
            McpAuth.Decision.DENY_BAD_TOKEN,
            McpAuth.evaluate(null, "Bearer not-the-token", TOKEN)
        )
    }

    @Test
    fun `a token that is a prefix of the real one is refused`() {
        assertEquals(
            McpAuth.Decision.DENY_BAD_TOKEN,
            McpAuth.evaluate(null, "Bearer ${TOKEN.dropLast(1)}", TOKEN)
        )
    }

    @Test
    fun `a non-bearer scheme is refused`() {
        assertEquals(McpAuth.Decision.DENY_MISSING_TOKEN, McpAuth.evaluate(null, "Basic $TOKEN", TOKEN))
        assertEquals(McpAuth.Decision.DENY_MISSING_TOKEN, McpAuth.evaluate(null, TOKEN, TOKEN))
    }

    @Test
    fun `an empty bearer value is refused`() {
        assertEquals(McpAuth.Decision.DENY_MISSING_TOKEN, McpAuth.evaluate(null, "Bearer ", TOKEN))
        assertEquals(McpAuth.Decision.DENY_MISSING_TOKEN, McpAuth.evaluate(null, "Bearer    ", TOKEN))
    }

    @Test
    fun `the bearer scheme is matched case-insensitively`() {
        // RFC 7235 auth schemes are case-insensitive; clients spell it both ways.
        assertEquals(McpAuth.Decision.ALLOW, McpAuth.evaluate(null, "bearer $TOKEN", TOKEN))
        assertEquals(McpAuth.Decision.ALLOW, McpAuth.evaluate(null, "BEARER $TOKEN", TOKEN))
    }

    @Test
    fun `surrounding whitespace on the header is tolerated`() {
        assertEquals(McpAuth.Decision.ALLOW, McpAuth.evaluate(null, "  Bearer $TOKEN  ", TOKEN))
    }

    // -------------------------------------------------------------------------
    // Token generation
    // -------------------------------------------------------------------------

    @Test
    fun `generated tokens are unique`() {
        val tokens = (1..200).map { McpAuth.newToken() }.toSet()
        assertEquals(200, tokens.size, "token generation must not repeat")
    }

    @Test
    fun `a generated token is long enough to resist guessing`() {
        val token = McpAuth.newToken()
        assertTrue(token.length >= 40, "token too short: ${token.length} chars")
    }

    @Test
    fun `a generated token is safe to paste into a config file or header`() {
        val token = McpAuth.newToken()
        assertTrue(
            token.all { it.isLetterOrDigit() || it == '-' || it == '_' },
            "token must be URL-safe and quote-free, was: $token"
        )
    }

    @Test
    fun `a generated token authenticates against itself and not against another`() {
        val a = McpAuth.newToken()
        val b = McpAuth.newToken()
        assertNotEquals(a, b)
        assertEquals(McpAuth.Decision.ALLOW, McpAuth.evaluate(null, "Bearer $a", a))
        assertEquals(McpAuth.Decision.DENY_BAD_TOKEN, McpAuth.evaluate(null, "Bearer $b", a))
    }

    // -------------------------------------------------------------------------
    // Gate ordering
    // -------------------------------------------------------------------------

    @Test
    fun `the origin check runs before the token check`() {
        // A browser must be refused as a browser, not merely as an unauthenticated
        // caller — otherwise a leaked token would re-open the hole.
        assertEquals(
            McpAuth.Decision.DENY_BROWSER_ORIGIN,
            McpAuth.evaluate("https://evil.example", null, TOKEN)
        )
    }

    @Test
    fun `every deny decision maps to a 4xx status`() {
        for (decision in McpAuth.Decision.entries.filter { it != McpAuth.Decision.ALLOW }) {
            assertTrue(
                decision.httpStatus in 400..499,
                "${decision.name} should be a client error, was ${decision.httpStatus}"
            )
            assertTrue(decision.message.isNotBlank(), "${decision.name} needs a message")
        }
    }
}
