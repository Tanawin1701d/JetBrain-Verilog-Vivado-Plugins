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
 * the user had open could drive their Vivado session. All three gates are
 * pinned here.
 */
class McpAuthTest {

    private val HOST = "127.0.0.1:19999"
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
            McpAuth.evaluate(HOST, "https://evil.example", "Bearer $TOKEN", TOKEN)
        )
    }

    @Test
    fun `any origin is refused, not just a known-bad one`() {
        for (origin in listOf("http://localhost:3000", "null", "https://example.com", "file://")) {
            assertEquals(
                McpAuth.Decision.DENY_BROWSER_ORIGIN,
                McpAuth.evaluate(HOST, origin, "Bearer $TOKEN", TOKEN),
                "origin '$origin' should be refused"
            )
        }
    }

    @Test
    fun `a blank Origin header is treated as absent`() {
        // Some proxies attach an empty header; that is not a browser origin.
        assertEquals(McpAuth.Decision.ALLOW, McpAuth.evaluate(HOST, "", "Bearer $TOKEN", TOKEN))
        assertEquals(McpAuth.Decision.ALLOW, McpAuth.evaluate(HOST, "   ", "Bearer $TOKEN", TOKEN))
    }

    // -------------------------------------------------------------------------
    // Token checking
    // -------------------------------------------------------------------------

    @Test
    fun `a correct bearer token from a non-browser client is allowed`() {
        assertEquals(McpAuth.Decision.ALLOW, McpAuth.evaluate(HOST, null, "Bearer $TOKEN", TOKEN))
    }

    @Test
    fun `a missing Authorization header is refused`() {
        assertEquals(McpAuth.Decision.DENY_MISSING_TOKEN, McpAuth.evaluate(HOST, null, null, TOKEN))
    }

    @Test
    fun `a wrong token is refused`() {
        assertEquals(
            McpAuth.Decision.DENY_BAD_TOKEN,
            McpAuth.evaluate(HOST, null, "Bearer not-the-token", TOKEN)
        )
    }

    @Test
    fun `a token that is a prefix of the real one is refused`() {
        assertEquals(
            McpAuth.Decision.DENY_BAD_TOKEN,
            McpAuth.evaluate(HOST, null, "Bearer ${TOKEN.dropLast(1)}", TOKEN)
        )
    }

    @Test
    fun `a non-bearer scheme is refused`() {
        assertEquals(McpAuth.Decision.DENY_MISSING_TOKEN, McpAuth.evaluate(HOST, null, "Basic $TOKEN", TOKEN))
        assertEquals(McpAuth.Decision.DENY_MISSING_TOKEN, McpAuth.evaluate(HOST, null, TOKEN, TOKEN))
    }

    @Test
    fun `an empty bearer value is refused`() {
        assertEquals(McpAuth.Decision.DENY_MISSING_TOKEN, McpAuth.evaluate(HOST, null, "Bearer ", TOKEN))
        assertEquals(McpAuth.Decision.DENY_MISSING_TOKEN, McpAuth.evaluate(HOST, null, "Bearer    ", TOKEN))
    }

    @Test
    fun `the bearer scheme is matched case-insensitively`() {
        // RFC 7235 auth schemes are case-insensitive; clients spell it both ways.
        assertEquals(McpAuth.Decision.ALLOW, McpAuth.evaluate(HOST, null, "bearer $TOKEN", TOKEN))
        assertEquals(McpAuth.Decision.ALLOW, McpAuth.evaluate(HOST, null, "BEARER $TOKEN", TOKEN))
    }

    @Test
    fun `surrounding whitespace on the header is tolerated`() {
        assertEquals(McpAuth.Decision.ALLOW, McpAuth.evaluate(HOST, null, "  Bearer $TOKEN  ", TOKEN))
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
        assertEquals(McpAuth.Decision.ALLOW, McpAuth.evaluate(HOST, null, "Bearer $a", a))
        assertEquals(McpAuth.Decision.DENY_BAD_TOKEN, McpAuth.evaluate(HOST, null, "Bearer $b", a))
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
            McpAuth.evaluate(HOST, "https://evil.example", null, TOKEN)
        )
    }

    @Test
    fun `the host check runs before everything else`() {
        // DNS rebinding produces a request that looks perfectly well-formed apart
        // from Host, so Host has to be judged on its own rather than as a tiebreak.
        assertEquals(
            McpAuth.Decision.DENY_BAD_HOST,
            McpAuth.evaluate("evil.example:19999", null, "Bearer $TOKEN", TOKEN)
        )
    }

    // -------------------------------------------------------------------------
    // DNS rebinding — the attack the Origin check alone does not stop
    // -------------------------------------------------------------------------

    @Test
    fun `a rebound hostname is refused even though it carries no Origin`() {
        // The attacker points evil.example at 127.0.0.1. The browser then considers
        // the request same-origin and sends NO Origin header — so only Host betrays it.
        assertEquals(
            McpAuth.Decision.DENY_BAD_HOST,
            McpAuth.evaluate("evil.example:19999", null, null, TOKEN)
        )
    }

    @Test
    fun `a subdomain of a loopback name is not a loopback name`() {
        for (host in listOf(
            "localhost.evil.example",
            "127.0.0.1.evil.example",
            "notlocalhost",
            "localhostx:19999"
        )) {
            assertEquals(
                McpAuth.Decision.DENY_BAD_HOST,
                McpAuth.evaluate(host, null, "Bearer $TOKEN", TOKEN),
                "host '$host' should be refused"
            )
        }
    }

    @Test
    fun `a missing Host header is refused`() {
        // HTTP/1.1 requires Host; absence means a hand-rolled client, not a real one.
        assertEquals(McpAuth.Decision.DENY_BAD_HOST, McpAuth.evaluate(null, null, "Bearer $TOKEN", TOKEN))
        assertEquals(McpAuth.Decision.DENY_BAD_HOST, McpAuth.evaluate("  ", null, "Bearer $TOKEN", TOKEN))
    }

    @Test
    fun `the loopback forms a real client sends are accepted`() {
        for (host in listOf(
            "127.0.0.1:19999", "127.0.0.1",
            "localhost:19999", "localhost",
            "LOCALHOST:19999", "LocalHost",
            "[::1]:19999", "[::1]"
        )) {
            assertTrue(McpAuth.isLoopbackHost(host), "host '$host' should be accepted")
            assertEquals(
                McpAuth.Decision.ALLOW,
                McpAuth.evaluate(host, null, "Bearer $TOKEN", TOKEN),
                "host '$host' should be allowed"
            )
        }
    }

    @Test
    fun `a malformed IPv6 host is refused rather than parsed loosely`() {
        assertTrue(!McpAuth.isLoopbackHost("[::1"))
        assertTrue(!McpAuth.isLoopbackHost("[evil]"))
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
