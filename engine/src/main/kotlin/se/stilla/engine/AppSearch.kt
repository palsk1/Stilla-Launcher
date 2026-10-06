package se.stilla.engine

/** One searchable app. [key] is whatever the caller uses to identify the app. */
data class SearchItem<K>(
    val key: K,
    val label: String,
    val hidden: Boolean = false,
)

/**
 * Fast, accent-insensitive, typo-tolerant app search.
 *
 * Normalized names are computed once when the index is built, so each
 * keystroke only does cheap string comparisons. Ranking, best first:
 *   0 exact name · 1 name starts with query · 2 a later word starts with it
 *   3 contains it · 4 one typo in the start of the name · 5 letters in order
 * Ties keep the order the items were given in (alphabetical in the app).
 *
 * Hidden apps never appear unless the query is their full name.
 */
class AppSearch<K>(items: List<SearchItem<K>>) {

    private class Entry<K>(val item: SearchItem<K>, val norm: String, val words: List<String>, val index: Int)

    private val entries: List<Entry<K>> = items.mapIndexed { i, item ->
        val norm = TextNorm.normalize(item.label)
        Entry(item, norm, TextNorm.words(norm), i)
    }

    fun search(query: String, limit: Int = Int.MAX_VALUE): List<K> {
        val q = TextNorm.normalize(query)
        if (q.isEmpty()) return entries.filter { !it.item.hidden }.take(limit).map { it.item.key }

        val scored = ArrayList<Pair<Int, Entry<K>>>()
        for (e in entries) {
            if (e.item.hidden) {
                if (e.norm == q) scored += 0 to e
                continue
            }
            val score = score(q, e) ?: continue
            scored += score to e
        }
        scored.sortWith(compareBy<Pair<Int, Entry<K>>> { it.first }.thenBy { it.second.index })
        return scored.take(limit).map { it.second.item.key }
    }

    private fun score(q: String, e: Entry<K>): Int? {
        val name = e.norm
        return when {
            name == q -> 0
            name.startsWith(q) -> 1
            e.words.drop(1).any { it.startsWith(q) } -> 2
            name.contains(q) -> 3
            q.length >= 3 && oneTypoFromStart(q, name) -> 4
            q.length >= 3 && isSubsequence(q, name) -> 5
            else -> null
        }
    }

    companion object {
        /** True if [q] is at most one edit (swap, change, add, drop) away from the start of [name]. */
        internal fun oneTypoFromStart(q: String, name: String): Boolean {
            for (len in (q.length - 1)..(q.length + 1)) {
                if (len <= 0 || len > name.length) continue
                if (osaDistance(q, name.substring(0, len), 1) <= 1) return true
            }
            return false
        }

        internal fun isSubsequence(q: String, name: String): Boolean {
            var i = 0
            for (c in name) {
                if (i < q.length && q[i] == c) i++
            }
            return i == q.length
        }

        /** Optimal string alignment distance, stopping early once it exceeds [max]. */
        internal fun osaDistance(a: String, b: String, max: Int): Int {
            if (kotlin.math.abs(a.length - b.length) > max) return max + 1
            val d = Array(a.length + 1) { IntArray(b.length + 1) }
            for (i in 0..a.length) d[i][0] = i
            for (j in 0..b.length) d[0][j] = j
            for (i in 1..a.length) {
                var rowMin = Int.MAX_VALUE
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    var v = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                    if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                        v = minOf(v, d[i - 2][j - 2] + 1)
                    }
                    d[i][j] = v
                    if (v < rowMin) rowMin = v
                }
                if (rowMin > max) return max + 1
            }
            return d[a.length][b.length]
        }
    }
}
