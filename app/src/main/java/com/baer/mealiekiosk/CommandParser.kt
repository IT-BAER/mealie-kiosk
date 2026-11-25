package com.baer.mealiekiosk

enum class Kind { DOWN, UP, NEXT, PREV, DONE, FOCUS }

data class Command(val kind: Kind, val step: Int? = null)

/** Phrases of one language. Every word must be in the Vosk model's vocabulary, or the recognizer drops it. */
data class Vocabulary(
    val words: Map<String, Kind>,
    val done: Set<String>,
    val step: String,
    val numbers: List<String>,
) {
    companion object {
        val DE = Vocabulary(
            words = mapOf(
                "runter" to Kind.DOWN, "rauf" to Kind.UP, "hoch" to Kind.UP,
                "weiter" to Kind.NEXT, "zurück" to Kind.PREV,
            ),
            done = setOf("erledigt"),
            step = "schritt",
            numbers = listOf(
                "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn",
                "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn", "sechzehn", "siebzehn", "achtzehn", "neunzehn", "zwanzig",
            ),
        )

        val EN = Vocabulary(
            words = mapOf(
                "down" to Kind.DOWN, "up" to Kind.UP,
                "next" to Kind.NEXT, "back" to Kind.PREV, "previous" to Kind.PREV,
            ),
            done = setOf("done"),
            step = "step",
            numbers = listOf(
                "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
                "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty",
            ),
        )

        // "terminé" and "terminée" sound the same; accept whichever the recognizer writes.
        val FR = Vocabulary(
            words = mapOf(
                "descends" to Kind.DOWN, "monte" to Kind.UP,
                "suivant" to Kind.NEXT, "précédent" to Kind.PREV, "retour" to Kind.PREV,
            ),
            done = setOf("terminé", "terminée"),
            step = "étape",
            numbers = listOf(
                "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf", "dix",
                "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit", "dix-neuf", "vingt",
            ),
        )

        /** Picks the phrases for an ISO 639 language code; German for anything else. */
        fun forLanguage(language: String): Vocabulary = when (language) {
            "en" -> EN
            "fr" -> FR
            else -> DE
        }
    }
}

/** Turns a Vosk hypothesis into a kiosk command. Anything outside the phrase list is rejected. */
class CommandParser(
    private val vocabulary: Vocabulary = Vocabulary.DE,
    private val minConfidence: Float = 0.7f,
) {

    /** Phrase list for the recognizer, as a JSON array. */
    fun grammar(): String = with(vocabulary) {
        val phrases = words.keys + done +
            numbers.flatMap { n -> listOf("$step $n") + done.map { "$step $n $it" } } + "[unk]"
        phrases.joinToString(",", "[", "]") { "\"$it\"" }
    }

    fun parse(text: String, confidence: Float): Command? {
        if (confidence < minConfidence) return null
        val t = text.trim().lowercase().split(" ").filter { it.isNotEmpty() }
        if (t.isEmpty() || t.contains("[unk]")) return null
        vocabulary.words[t.singleOrNull()]?.let { return Command(it) }
        if (t.size == 1 && t[0] in vocabulary.done) return Command(Kind.DONE)
        if (t.size < 2 || t[0] != vocabulary.step) return null
        val n = vocabulary.numbers.indexOf(t[1]) + 1
        if (n == 0) return null
        return when {
            t.size == 2 -> Command(Kind.FOCUS, n)
            t.size == 3 && t[2] in vocabulary.done -> Command(Kind.DONE, n)
            else -> null
        }
    }
}
