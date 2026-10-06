package se.stilla.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import se.stilla.engine.Distractions.Category

class DistractionsTest {
    @Test fun table() {
        data class Row(val pkg: String, val category: Category, val suggested: Boolean)
        listOf(
            Row("com.instagram.android", Category.OTHER, true),
            Row("com.google.android.youtube", Category.VIDEO, true),
            Row("com.reddit.frontpage", Category.OTHER, true),
            Row("com.king.candycrushsaga", Category.GAME, true), // any game
            Row("com.example.newsocial", Category.SOCIAL, true), // any social app
            Row("com.whatsapp", Category.SOCIAL, false), // chat, even though "social"
            Row("com.facebook.orca", Category.SOCIAL, false), // Messenger
            Row("com.google.android.gm", Category.OTHER, false), // Gmail
            Row("se.bankgirot.swish", Category.OTHER, false),
            Row("se.nordea.mobilebank", Category.OTHER, false), // a bank: not social, not a game
            Row("org.videolan.vlc", Category.VIDEO, false), // a video player isn't a feed
        ).forEach { assertEquals(it.pkg, it.suggested, Distractions.isSuggested(it.pkg, it.category)) }
    }
}
