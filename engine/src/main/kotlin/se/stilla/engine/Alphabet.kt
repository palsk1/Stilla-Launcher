package se.stilla.engine

/** The letters of the A–Z rail, in Swedish order (Å, Ä, Ö come after Z). */
object Alphabet {
    const val OTHER = "#"

    private val swedishExtra = listOf("Å", "Ä", "Ö")

    val letters: List<String> = ('A'..'Z').map { it.toString() } + swedishExtra + OTHER

    /** Which rail letter a label belongs under. "Ölkollen" → Ö, "Été" → E, "1177" → #. */
    fun sectionOf(label: String): String {
        val first = label.trim().firstOrNull() ?: return OTHER
        val upper = first.uppercaseChar().toString()
        if (upper in swedishExtra) return upper
        if (upper.length == 1 && upper[0] in 'A'..'Z') return upper
        val plain = TextNorm.normalize(first.toString()).uppercase()
        if (plain.length == 1 && plain[0] in 'A'..'Z') return plain
        return OTHER
    }

    /** Position of a section letter in [letters], for sorting sections. */
    fun orderOf(section: String): Int = letters.indexOf(section).let { if (it < 0) letters.size else it }
}
