package com.baer.mealiekiosk

enum class Kind { DOWN, UP, NEXT, PREV, DONE, FOCUS }

data class Command(val kind: Kind, val step: Int? = null)

/** Turns a Vosk hypothesis into a kiosk command. Anything outside the phrase list is rejected. */
class CommandParser(private val minConfidence: Float = 0.7f) {

    private val numbers = listOf(
        "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn",
        "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn", "sechzehn", "siebzehn", "achtzehn", "neunzehn", "zwanzig",
    )
    private val words = mapOf(
        "runter" to Kind.DOWN, "rauf" to Kind.UP, "hoch" to Kind.UP,
        "weiter" to Kind.NEXT, "zurück" to Kind.PREV,
    )

    /** Phrase list for the recognizer, as a JSON array. */
    fun grammar(): String {
        val phrases = words.keys + "erledigt" +
            numbers.flatMap { listOf("schritt $it", "schritt $it erledigt") } + "[unk]"
        return phrases.joinToString(",", "[", "]") { "\"$it\"" }
    }

    fun parse(text: String, confidence: Float): Command? {
        if (confidence < minConfidence) return null
        val t = text.trim().lowercase().split(" ").filter { it.isNotEmpty() }
        if (t.isEmpty() || t.contains("[unk]")) return null
        words[t.singleOrNull()]?.let { return Command(it) }
        if (t == listOf("erledigt")) return Command(Kind.DONE)
        if (t.size < 2 || t[0] != "schritt") return null
        val step = numbers.indexOf(t[1]) + 1
        if (step == 0) return null
        return when {
            t.size == 2 -> Command(Kind.FOCUS, step)
            t.size == 3 && t[2] == "erledigt" -> Command(Kind.DONE, step)
            else -> null
        }
    }
}
