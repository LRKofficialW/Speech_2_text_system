package com.neuroscribe

import com.neuroscribe.core.ai.LlmEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmLexicalEquivalenceTest {

    private val llm = LlmEngine()

    @Test
    fun testLexicalEquivalenceAcceptsPunctuationAndCaseChanges() {
        val original = "привет как твои дела"
        val formatted = "Привет, как твои дела?"
        assertTrue(llm.verifyLexicalEquivalence(original, formatted))
    }

    @Test
    fun testLexicalEquivalenceRejectsHallucinatedWords() {
        val original = "привет как твои дела"
        val formatted = "Привет, друг, как твои дела?" // added word "друг"
        assertFalse(llm.verifyLexicalEquivalence(original, formatted))
    }

    @Test
    fun testLexicalEquivalenceRejectsDeletedWords() {
        val original = "привет как твои дела"
        val formatted = "Привет как дела?" // missing "твои"
        assertFalse(llm.verifyLexicalEquivalence(original, formatted))
    }

    @Test
    fun testLexicalEquivalenceRejectsSubstitutedWords() {
        val original = "сегодня хорошая погода"
        val formatted = "Сегодня отличная погода." // substituted "хорошая" -> "отличная"
        assertFalse(llm.verifyLexicalEquivalence(original, formatted))
    }

    @Test
    fun testFormatTextPreservesWordsUnderLexicalRule() {
        llm.load()
        val raw = "добрый день уважаемые коллеги сегодня мы обсудим проект"
        val formatted = llm.formatText(raw)

        // Lexical check must hold
        assertTrue(llm.verifyLexicalEquivalence(raw, formatted))
        // Must start with capital letter
        assertTrue(formatted.startsWith("Добрый"))
        llm.unload()
    }
}
