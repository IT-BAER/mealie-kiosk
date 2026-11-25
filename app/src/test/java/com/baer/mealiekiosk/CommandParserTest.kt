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

    @Test fun englishPhrases() {
        val en = CommandParser(Vocabulary.EN)
        assertEquals(Command(Kind.DOWN), en.parse("down", 1f))
        assertEquals(Command(Kind.UP), en.parse("up", 1f))
        assertEquals(Command(Kind.NEXT), en.parse("next", 1f))
        assertEquals(Command(Kind.PREV), en.parse("back", 1f))
        assertEquals(Command(Kind.PREV), en.parse("previous", 1f))
        assertEquals(Command(Kind.DONE), en.parse("done", 1f))
        assertEquals(Command(Kind.FOCUS, 17), en.parse("step seventeen", 1f))
        assertEquals(Command(Kind.DONE, 4), en.parse("step four done", 1f))
        assertNull(en.parse("weiter", 1f))
    }

    @Test fun frenchPhrases() {
        val fr = CommandParser(Vocabulary.FR)
        assertEquals(Command(Kind.DOWN), fr.parse("descends", 1f))
        assertEquals(Command(Kind.UP), fr.parse("monte", 1f))
        assertEquals(Command(Kind.NEXT), fr.parse("suivant", 1f))
        assertEquals(Command(Kind.PREV), fr.parse("précédent", 1f))
        assertEquals(Command(Kind.PREV), fr.parse("retour", 1f))
        assertEquals(Command(Kind.DONE), fr.parse("terminé", 1f))
        assertEquals(Command(Kind.DONE), fr.parse("terminée", 1f))
        assertEquals(Command(Kind.FOCUS, 17), fr.parse("étape dix-sept", 1f))
        assertEquals(Command(Kind.DONE, 20), fr.parse("étape vingt terminée", 1f))
        assertNull(fr.parse("étape vingt-et-un", 1f))
    }

    @Test fun languageFallsBackToGerman() {
        assertEquals(Vocabulary.EN, Vocabulary.forLanguage("en"))
        assertEquals(Vocabulary.FR, Vocabulary.forLanguage("fr"))
        assertEquals(Vocabulary.DE, Vocabulary.forLanguage("de"))
        assertEquals(Vocabulary.DE, Vocabulary.forLanguage("it"))
    }

    @Test fun grammarCoversEveryLanguage() {
        listOf(Vocabulary.DE, Vocabulary.EN, Vocabulary.FR).forEach { v ->
            val g = CommandParser(v).grammar()
            (v.words.keys + v.done + v.numbers.map { "${v.step} $it" }).forEach {
                assert(g.contains("\"$it\"")) { "$it missing in $g" }
            }
        }
    }
}
