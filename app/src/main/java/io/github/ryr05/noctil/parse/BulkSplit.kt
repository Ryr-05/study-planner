package io.github.ryr05.noctil.parse

import java.time.LocalDateTime

/**
 * Splits text into one line per task, without AI: lines, semicolons and bullets. Commas split
 * only when every piece has its own date ("lab fri, essay mon"), so "Email prof, ask about grade"
 * stays one task.
 */
object BulkSplit {
    private val bullet = Regex("^\\s*(?:[-*•▪◦·]|\\d{1,2}[.)]|\\[\\s?[xX ]?\\s?\\])\\s+")

    fun split(text: String, now: LocalDateTime): List<String> {
        val pieces = text.split('\n', ';')
            .map { it.replace(bullet, "").trim().trim(',', '.').trim() }
            .filter { line -> line.count { it.isLetter() } >= 2 }
        if (pieces.size != 1) return pieces
        val commas = pieces.single().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val allDated = commas.size >= 2 && commas.all { SmartParser.parse(it, now).date != null }
        return if (allDated) commas else pieces
    }
}
