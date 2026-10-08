package se.stilla.engine

/**
 * Web browsers. You need them for too much (banking, tickets, forms) for
 * Stilla to block them as a whole: they are never suggested as time-wasters
 * and never covered by an "all apps" schedule. You can still pick one
 * yourself from its long-press menu.
 *
 * The app also asks Android which apps open web links, so browsers missing
 * from this list are found too.
 */
object Browsers {
    val KNOWN: Set<String> = setOf(
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "org.mozilla.focus",
        "org.mozilla.klar",
        "com.android.chrome",
        "com.chrome.beta",
        "com.sec.android.app.sbrowser", // Samsung Internet
        "com.sec.android.app.sbrowser.beta",
        "com.brave.browser",
        "com.microsoft.emmx", // Edge
        "com.opera.browser",
        "com.opera.mini.native",
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "com.ecosia.android",
        "org.torproject.torbrowser",
    )

    fun isBrowser(packageName: String, detected: Set<String> = emptySet()): Boolean =
        packageName in KNOWN || packageName in detected
}
