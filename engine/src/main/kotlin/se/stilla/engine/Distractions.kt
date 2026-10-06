package se.stilla.engine

/**
 * Which apps Stilla treats as time-wasters out of the box: the ones that get
 * "how long?" before they open. Everything else (bank, email, maps, messages)
 * opens normally. You can add or remove any app from its long-press menu.
 *
 * An app counts if it's on the known list below, or if its developer files it
 * under Social or Games on Google Play (Android reports this as the app's
 * category), unless it's on the never list (chat apps that call themselves
 * "social", email, banking).
 */
object Distractions {

    /** What Android says the app is. Only the categories that matter here. */
    enum class Category { SOCIAL, GAME, VIDEO, OTHER }

    /** Known scroll-and-watch apps, whatever category they declare. */
    val KNOWN: Set<String> = setOf(
        // Social and short video
        "com.instagram.android",
        "com.instagram.barcelona", // Threads
        "com.zhiliaoapp.musically", // TikTok
        "com.ss.android.ugc.trill", // TikTok (some regions)
        "com.zhiliaoapp.musically.go",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.snapchat.android",
        "com.twitter.android", // X
        "com.pinterest",
        "com.tumblr",
        "com.bereal.ft",
        "com.ninegag.android.app",
        "com.linkedin.android",
        // Reddit and Reddit clients
        "com.reddit.frontpage",
        "ml.docilealligator.infinityforreddit",
        "com.laurencedawson.reddit_sync",
        "com.rubenmayayo.reddit", // Boost
        "free.reddit.news", // Relay
        // Video
        "com.google.android.youtube",
        "app.revanced.android.youtube",
        "tv.twitch.android.app",
        "com.netflix.mediaclient",
        "com.disney.disneyplus",
        "com.amazon.avod.thirdpartyclient", // Prime Video
        "com.wbd.stream", // Max
        "com.hbo.hbonow",
        "se.svt.svtplay.android", // SVT Play
        "se.tv4.tv4playtab", // TV4 Play
    )

    /** Never suggested, even if they call themselves social. */
    val NEVER: Set<String> = setOf(
        // Chat
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.facebook.orca", // Messenger
        "org.telegram.messenger",
        "org.thoughtcrime.securesms", // Signal
        "com.discord",
        "com.viber.voip",
        "jp.naver.line.android",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        // Email
        "com.google.android.gm",
        "com.microsoft.office.outlook",
        "com.samsung.android.email.provider",
        "ch.protonmail.android",
        // Money
        "com.bankid.bus",
        "se.bankgirot.swish",
    )

    fun isSuggested(packageName: String, category: Category): Boolean = when {
        packageName in NEVER -> false
        packageName in KNOWN -> true
        else -> category == Category.SOCIAL || category == Category.GAME
    }
}
