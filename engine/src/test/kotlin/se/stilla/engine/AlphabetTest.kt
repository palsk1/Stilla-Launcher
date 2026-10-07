package se.stilla.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphabetTest {
    @Test fun sections() {
        assertEquals("K", Alphabet.sectionOf("Kalender"))
        assertEquals("K", Alphabet.sectionOf("kalender"))
        assertEquals("Ö", Alphabet.sectionOf("Östgötatrafiken"))
        assertEquals("Å", Alphabet.sectionOf("åhléns"))
        assertEquals("E", Alphabet.sectionOf("Été"))
        assertEquals("#", Alphabet.sectionOf("1177"))
        assertEquals("#", Alphabet.sectionOf(""))
    }

    @Test fun swedishLettersComeAfterZ() {
        assertTrue(Alphabet.orderOf("Z") < Alphabet.orderOf("Å"))
        assertTrue(Alphabet.orderOf("Å") < Alphabet.orderOf("Ä"))
        assertTrue(Alphabet.orderOf("Ä") < Alphabet.orderOf("Ö"))
        assertTrue(Alphabet.orderOf("Ö") < Alphabet.orderOf("#"))
    }
}

class EssentialsTest {
    @Test fun phoneIsAlwaysEssential() {
        assertTrue(Essentials.isEssential("com.samsung.android.dialer", removed = setOf("com.samsung.android.dialer")))
        assertTrue(!Essentials.canRemove("com.samsung.android.dialer"))
    }

    @Test fun alarmClockIsAlwaysEssential() {
        val clock = "com.sec.android.app.clockpackage"
        assertTrue(Essentials.isEssential(clock, removed = setOf(clock)))
        assertTrue(!Essentials.canRemove(clock))
    }

    @Test fun defaultsCanBeTurnedOff() {
        assertTrue(Essentials.isEssential("com.bankid.bus"))
        assertTrue(!Essentials.isEssential("com.bankid.bus", removed = setOf("com.bankid.bus")))
    }

    @Test fun ownAdditionsAndSelf() {
        assertTrue(Essentials.isEssential("se.example.bank", added = setOf("se.example.bank")))
        assertTrue(Essentials.isEssential("se.stilla.launcher", self = "se.stilla.launcher"))
        assertTrue(!Essentials.isEssential("com.google.android.youtube"))
    }
}
