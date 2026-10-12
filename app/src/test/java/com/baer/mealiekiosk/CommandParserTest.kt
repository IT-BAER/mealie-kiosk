package com.baer.mealiekiosk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandParserTest {
    private val p = CommandParser()

    @Test fun scrollAndStepPhrases() {
        assertEquals(Command(Kind.DOWN), p.parse("runter", 1f))
        assertEquals(Command(Kind.UP), p.parse("rauf", 1f))
        assertEquals(Command(Kind.UP), p.parse("hoch", 1f))
        assertEquals(Command(Kind.NEXT), p.parse("weiter", 1f))
        assertEquals(Command(Kind.PREV), p.parse("zurück", 1f))
    }

    @Test fun doneWithAndWithoutNumber() {
        assertEquals(Command(Kind.DONE), p.parse("erledigt", 1f))
        assertEquals(Command(Kind.DONE, 1), p.parse("schritt eins erledigt", 1f))
        assertEquals(Command(Kind.DONE, 12), p.parse("schritt zwölf erledigt", 1f))
    }

    @Test fun focusStepNumbers() {
        assertEquals(Command(Kind.FOCUS, 3), p.parse("schritt drei", 1f))
        assertEquals(Command(Kind.FOCUS, 20), p.parse("schritt zwanzig", 1f))
    }

    @Test fun unknownOrNoisyTextIsRejected() {
        assertNull(p.parse("", 1f))
        assertNull(p.parse("[unk]", 1f))
        assertNull(p.parse("schritt [unk] erledigt", 1f))
        assertNull(p.parse("runter das brot", 1f))
        assertNull(p.parse("schritt", 1f))
        assertNull(p.parse("schritt dreiundzwanzig", 1f))
    }

    @Test fun lowConfidenceIsRejected() {
        assertNull(p.parse("runter", 0.5f))
        assertEquals(Command(Kind.DOWN), p.parse("runter", 0.8f))
    }

    @Test fun grammarCoversEveryPhrase() {
        val g = p.grammar()
        assert(g.contains("schritt zwanzig erledigt")) { g }
        listOf("runter", "rauf", "hoch", "weiter", "zurück", "erledigt", "[unk]").forEach {
            assert(g.contains("\"$it\"")) { "$it missing in $g" }
        }
    }
}
