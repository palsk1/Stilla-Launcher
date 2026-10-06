package se.stilla.engine

import java.text.Normalizer

/** Text helpers shared by search and the A–Z list. */
object TextNorm {
    private val combiningMarks = Regex("\\p{M}+")

    /**
     * Lowercase and strip accents so that "Väder", "vader" and "VÄDER" all
     * compare equal. å/ä/ö become a/a/o.
     */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        return combiningMarks.replace(decomposed, "")
            .replace('ø', 'o')
            .replace('æ', 'a')
            .replace('ß', 's')
            .trim()
    }

    /** Splits a normalized label into words: "svt play" → [svt, play]. */
    fun words(normalized: String): List<String> =
        normalized.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
}
