package se.stilla.engine

/**
 * Who made an app, from its package name, for telling two apps with the
 * same name apart ("Authenticator · Google", "Authenticator · Microsoft").
 */
object Makers {

    /** Package prefixes whose maker name isn't simply the second part. */
    private val KNOWN = listOf(
        "com.azure." to "Microsoft",
        "com.microsoft." to "Microsoft",
        "com.google." to "Google",
        "com.android." to "Google",
        "com.sec." to "Samsung",
        "com.samsung." to "Samsung",
        "com.facebook." to "Meta",
        "com.instagram." to "Meta",
        "com.whatsapp" to "Meta",
        "org.mozilla." to "Mozilla",
        "com.spotify." to "Spotify",
        "se.bankid." to "BankID",
        "com.bankid." to "BankID",
    )

    /** Parts that say nothing about the maker. */
    private val SKIP = setOf("com", "org", "net", "se", "io", "app", "apps", "android", "co", "de", "uk", "us", "nu")

    fun of(packageName: String): String {
        KNOWN.firstOrNull { (prefix, _) -> packageName.startsWith(prefix) || packageName == prefix.trimEnd('.') }
            ?.let { return it.second }
        val part = packageName.split('.').firstOrNull { it.isNotEmpty() && it.lowercase() !in SKIP } ?: packageName
        return part.replaceFirstChar { it.uppercase() }
    }
}
