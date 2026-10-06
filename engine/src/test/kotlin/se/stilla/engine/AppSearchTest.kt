package se.stilla.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchTest {

    private val apps = listOf(
        "1177", "BankID", "Kalender", "Kamera", "Klarna", "Klocka", "Meddelanden",
        "Messenger", "Skatteverket", "SL-Reseplanerare", "Spotify", "SVT Play", "Swish",
        "Telefon", "Väder", "Vårdguiden", "YouTube", "Instagram",
    )

    private fun index(hidden: Set<String> = emptySet()) =
        AppSearch(apps.map { SearchItem(it, it, hidden = it in hidden) })

    @Test fun emptyQueryListsEverythingVisible() {
        assertEquals(apps.size - 1, index(setOf("Instagram")).search("").size)
    }

    @Test fun prefixComesFirst() {
        assertEquals(listOf("Kalender", "Kamera"), index().search("ka").take(2))
        assertEquals(listOf("Kalender", "Kamera", "Klocka", "Skatteverket"), index().search("ka"))
    }

    @Test fun exactBeatsPrefix() {
        assertEquals("Swish", index().search("swish").first())
    }

    @Test fun laterWordMatches() {
        assertEquals("SVT Play", index().search("play").first())
    }

    @Test fun accentsAreIgnored() {
        assertEquals("Väder", index().search("vader").first())
        assertEquals("Vårdguiden", index().search("vard").first())
        assertEquals("Väder", index().search("VÄD").first())
    }

    @Test fun digits() {
        assertEquals(listOf("1177"), index().search("117"))
    }

    @Test fun oneTypoStillFinds() {
        assertEquals("Skatteverket", index().search("skattevreket").first())
        assertEquals("Kalender", index().search("kalemder").first())
        assertEquals("Swish", index().search("swsh").first())
    }

    @Test fun shortQueriesAreNotFuzzy() {
        assertTrue(index().search("zx").isEmpty())
    }

    @Test fun hiddenAppsNeedTheFullName() {
        val idx = index(setOf("Instagram"))
        assertFalse("Instagram" in idx.search("insta"))
        assertEquals(listOf("Instagram"), idx.search("instagram"))
        assertEquals(listOf("Instagram"), idx.search("  Instagram "))
    }

    @Test fun limitIsRespected() {
        assertEquals(1, index().search("k", limit = 1).size)
    }

    @Test fun distanceHelpers() {
        assertEquals(1, AppSearch.osaDistance("ab", "ba", 1))
        assertEquals(2, AppSearch.osaDistance("abc", "xyz", 1))
        assertTrue(AppSearch.isSubsequence("svp", "svt play"))
    }
}
