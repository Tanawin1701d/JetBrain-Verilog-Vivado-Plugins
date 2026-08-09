package com.hdl.vivado.catalog

/**
 * The whole UG835 Tcl command reference (~770 commands) as searchable data.
 *
 * Deliberately NOT exposed as MCP tools: name + summary + syntax for every command is
 * roughly 37k tokens, which would flood the model's context on a single tools/list call.
 * Instead three gateway tools (searchVivadoCommands / describeVivadoCommand /
 * runVivadoCommand) read this catalogue on demand, so tools/list stays around 30 entries.
 *
 * Backed by four resources under /vivado:
 *  - ug835-index.tsv        generated, one line per command (name, categories, summary, syntax)
 *  - ug835-details.tsv      generated, the full reference entry per command
 *  - ug835-tier-core.txt    hand-curated command names -> [Tier.CORE]
 *  - ug835-tier-extended.txt hand-curated category names -> [Tier.EXTENDED]
 *
 * The two generated files come from tools/extract_ug835.py and can be regenerated against a
 * newer UG835 without touching the curated tier files.
 */
object VivadoCommandCatalog {

    /**
     * Search ranking tier. This is the only thing a tier controls — every tier is equally
     * runnable through runVivadoCommand.
     *
     * CORE ranks first, EXTENDED next, and REST is withheld from ordinary searches entirely
     * (see [search]) so that the ~322 device/object/GUI accessors cannot crowd out the flow
     * commands in a result set.
     */
    enum class Tier { CORE, EXTENDED, REST }

    data class CatalogEntry(
        val name: String,
        val tier: Tier,
        val categories: List<String>,
        val summary: String,
        val syntax: String
    ) {
        /** One compact search-result line: enough for the model to pick a command. */
        fun toLine(): String = buildString {
            append(name)
            if (summary.isNotBlank()) append(" — ").append(summary)
            if (categories.isNotEmpty()) append("  [").append(categories.joinToString(", ")).append("]")
        }
    }

    private const val INDEX_RESOURCE = "/vivado/ug835-index.tsv"
    private const val DETAILS_RESOURCE = "/vivado/ug835-details.tsv"
    private const val CORE_RESOURCE = "/vivado/ug835-tier-core.txt"
    private const val EXTENDED_RESOURCE = "/vivado/ug835-tier-extended.txt"

    /** Every documented command, sorted by name. Parsed once, on first use. */
    val all: List<CatalogEntry> by lazy { loadIndex() }

    private val byName: Map<String, CatalogEntry> by lazy { all.associateBy { it.name } }

    /** Every category that appears in the reference, sorted — used by the UI browser's filter. */
    val categories: List<String> by lazy {
        all.flatMap { it.categories }.distinct().sortedBy { it.lowercase() }
    }

    fun byName(name: String): CatalogEntry? = byName[name.trim()]

    /**
     * Ranked matches for a free-text query, best first.
     *
     * Every whitespace-separated token must appear somewhere in the command's name, summary
     * or categories, so "bd cell" narrows rather than widens. Ranking is match quality first
     * (exact name, then name prefix, then name substring, then summary/category text), tier
     * second, name third.
     *
     * REST entries are withheld unless [includeAll] is set — except for an exact name match,
     * which always resolves, so a model that already knows the command it wants is never
     * blocked by the tier split.
     *
     * Returns the full ranked list; callers apply their own limit.
     */
    fun search(query: String, category: String? = null, includeAll: Boolean = false): List<CatalogEntry> {
        val exact = query.trim().lowercase()
        val tokens = exact.split(Regex("[^a-z0-9_*]+")).filter { it.isNotBlank() }
        val wantedCategory = category?.trim()?.takeIf { it.isNotBlank() }?.lowercase()

        return all.asSequence()
            .filter { entry ->
                if (wantedCategory != null && entry.categories.none { it.lowercase() == wantedCategory }) return@filter false
                // A tierless caller still gets an exact hit; otherwise REST stays out of the way.
                if (entry.tier == Tier.REST && !includeAll && entry.name != exact) return@filter false
                if (tokens.isEmpty()) return@filter true
                val haystack = (entry.name + " " + entry.summary + " " + entry.categories.joinToString(" ")).lowercase()
                tokens.all { haystack.contains(it) }
            }
            .sortedWith(compareBy({ score(it, exact, tokens) }, { it.tier.ordinal }, { it.name }))
            .toList()
    }

    // Lower is better. Splitting on where the query landed keeps "clock" from ranking
    // report_clock_networks above create_clock.
    private fun score(entry: CatalogEntry, exact: String, tokens: List<String>): Int {
        val name = entry.name
        if (name == exact) return 0
        if (tokens.size == 1) {
            val t = tokens[0]
            if (name == t) return 0
            if (name.startsWith(t)) return 1
            if (name.contains(t)) return 2
        } else if (tokens.all { name.contains(it) }) {
            return 2
        }
        return if (tokens.any { name.contains(it) }) 3 else 4
    }

    /**
     * The full UG835 entry (syntax, argument table, description, examples) for one command,
     * or null if the name is not documented.
     *
     * Read off the 3 MB details resource rather than held in memory: the whole reference is
     * far more than any one session looks at. The last few reads are cached because the
     * command browser calls this on every selection change, which happens per keystroke.
     */
    fun details(name: String): String? {
        val wanted = name.trim()
        if (wanted.isEmpty()) return null
        synchronized(detailsCache) { if (detailsCache.containsKey(wanted)) return detailsCache[wanted] }

        val prefix = "$wanted\t"
        val hit = withResourceLines(DETAILS_RESOURCE) { lines -> lines.firstOrNull { it.startsWith(prefix) } }
        val text = hit?.let { unescape(it.substring(prefix.length)) }
        synchronized(detailsCache) { detailsCache[wanted] = text }
        return text
    }

    // Access-ordered LRU; misses are cached too, so a repeated bad name costs one scan.
    private val detailsCache = object : LinkedHashMap<String, String?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>) = size > DETAILS_CACHE_SIZE
    }

    private const val DETAILS_CACHE_SIZE = 24

    /** Names closest to a misspelling, for "did you mean" replies. Best guesses first. */
    fun closestNames(name: String, limit: Int = 5): List<String> {
        val wanted = name.trim().lowercase()
        if (wanted.isEmpty()) return emptyList()
        return all.asSequence()
            .map { it.name to commonPrefix(it.name, wanted) }
            .filter { (candidate, shared) -> shared >= 3 || candidate.contains(wanted) || wanted.contains(candidate) }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.length })
            .map { it.first }
            .take(limit)
            .toList()
    }

    private fun commonPrefix(a: String, b: String): Int {
        var i = 0
        while (i < a.length && i < b.length && a[i] == b[i]) i++
        return i
    }

    // ---- Resource loading ----

    private fun loadIndex(): List<CatalogEntry> {
        val coreNames = readCurated(CORE_RESOURCE).toSet()
        val extendedRules = readCurated(EXTENDED_RESOURCE).toSet()

        return withResourceLines(INDEX_RESOURCE) { lines ->
            lines.mapNotNull { line ->
                if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
                val parts = line.split('\t')
                if (parts.size < 4) return@mapNotNull null
                val name = parts[0]
                val categories = unescape(parts[1]).split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val tier = when {
                    name in coreNames -> Tier.CORE
                    name in extendedRules || categories.any { it in extendedRules } -> Tier.EXTENDED
                    else -> Tier.REST
                }
                CatalogEntry(name, tier, categories, unescape(parts[2]), unescape(parts[3]))
            }.toList()
        }
    }

    // Curated tier files: one entry per line, '#' comments and blank lines ignored.
    private fun readCurated(resource: String): List<String> =
        withResourceLines(resource) { lines ->
            lines.map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toList()
        }

    // The line sequence is only valid while the stream is open, so every read goes through
    // here — useLines closes the reader once block returns.
    private inline fun <T> withResourceLines(resource: String, block: (Sequence<String>) -> T): T {
        val stream = VivadoCommandCatalog::class.java.getResourceAsStream(resource)
            ?: error("Missing plugin resource $resource — run tools/extract_ug835.py")
        return stream.bufferedReader(Charsets.UTF_8).useLines(block)
    }

    // Mirrors the escaping in tools/extract_ug835.py.
    private fun unescape(field: String): String {
        if ('\\' !in field) return field
        val out = StringBuilder(field.length)
        var i = 0
        while (i < field.length) {
            val c = field[i]
            if (c == '\\' && i + 1 < field.length) {
                when (field[i + 1]) {
                    'n' -> { out.append('\n'); i += 2; continue }
                    't' -> { out.append('\t'); i += 2; continue }
                    '\\' -> { out.append('\\'); i += 2; continue }
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }
}
