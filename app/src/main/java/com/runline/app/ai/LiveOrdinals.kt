package com.runline.app.ai

import java.util.Locale

internal object LiveOrdinals {
    private const val NOUN = "(?:kil[oó]metros?|kilomet(?:re|er)s?|km|vueltas?|laps?)"
    val NUMBER_KEYS = setOf("kilometre_number", "lap_number")
    private val AFTER = Regex("\\b$NOUN\\s+(?:n[uú]mero\\s+|number\\s+)?(\\p{L}+|\\d+)\\b")
    private val BEFORE = Regex("\\b(\\p{L}+)\\s+$NOUN\\b")
    private val CARDINALS =
        listOf(
            listOf("uno", "one"),
            listOf("dos", "two"),
            listOf("tres", "three"),
            listOf("cuatro", "four"),
            listOf("cinco", "five"),
            listOf("seis", "six"),
            listOf("siete", "seven"),
            listOf("ocho", "eight"),
            listOf("nueve", "nine"),
            listOf("diez", "ten"),
            listOf("once", "eleven"),
            listOf("doce", "twelve"),
            listOf("trece", "thirteen"),
            listOf("catorce", "fourteen"),
            listOf("quince", "fifteen"),
            listOf("dieciséis", "sixteen"),
            listOf("diecisiete", "seventeen"),
            listOf("dieciocho", "eighteen"),
            listOf("diecinueve", "nineteen"),
            listOf("veinte", "twenty"),
            listOf("veintiuno", "twenty-one"),
        ).words()
    private val ORDINALS =
        listOf(
            listOf("primer", "primero", "primera", "first"),
            listOf("segundo", "segunda", "second"),
            listOf("tercer", "tercero", "tercera", "third"),
            listOf("cuarto", "cuarta", "fourth"),
            listOf("quinto", "quinta", "fifth"),
            listOf("sexto", "sexta", "sixth"),
            listOf("séptimo", "séptima", "septimo", "septima", "seventh"),
            listOf("octavo", "octava", "eighth"),
            listOf("noveno", "novena", "ninth"),
            listOf("décimo", "décima", "decimo", "decima", "tenth"),
            listOf("undécimo", "undécima", "eleventh"),
            listOf("duodécimo", "duodécima", "twelfth"),
        ).words()

    private fun List<List<String>>.words(): Map<String, Int> = flatMapIndexed { index, names -> names.map { it to index + 1 } }.toMap()

    fun named(text: String): Set<Int> {
        val lower = text.lowercase(Locale.ROOT)
        val after = AFTER.findAll(lower).mapNotNull { m -> m.groupValues[1].let { it.toIntOrNull() ?: CARDINALS[it] ?: ORDINALS[it] } }
        val before = BEFORE.findAll(lower).mapNotNull { m -> ORDINALS[m.groupValues[1]] }
        return (after + before).toSet()
    }

    fun problem(
        line: String,
        point: LivePoint,
    ): String? {
        val allowed =
            named(point.point) +
                point.numbers
                    .filterKeys { it in NUMBER_KEYS }
                    .values
                    .mapNotNull { it.trim().toIntOrNull() }
        val stray = named(line).firstOrNull { it !in allowed } ?: return null
        val known = if (allowed.isEmpty()) "the point names none" else "the point is about number ${allowed.sorted().joinToString()}"
        return "It calls a kilometre or lap number $stray, but $known. Use that number or none."
    }
}
