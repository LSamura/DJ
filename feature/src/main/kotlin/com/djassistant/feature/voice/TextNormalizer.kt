package com.djassistant.feature.voice

/**
 * Shared text normalization for the Intent Parser and Debug Screen
 * diagnostics: lowercase, ё→е, punctuation stripped, whitespace collapsed.
 * Deliberately simple and regex-based — no stemming/NLP — matching the
 * "dictionary + regex + normalization + synonyms" constraint for the Intent
 * Parser (no LLM, no neural nets).
 */
object TextNormalizer {

    private val punctuationRegex = Regex("[^а-я0-9a-z\\s]")
    private val whitespaceRegex = Regex("\\s+")

    fun normalize(text: String): String {
        var result = text.lowercase().replace('ё', 'е')
        result = punctuationRegex.replace(result, "")
        result = whitespaceRegex.replace(result, " ")
        return result.trim()
    }
}
