package com.onis.kanji

import android.content.Context
import com.atilika.kuromoji.ipadic.Tokenizer
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader

data class TokenAnalysis(
    val surface: String,
    val baseForm: String,
    val reading: String,
    val partOfSpeech: String,
    val meaning: String? = null
)

data class GrammarMatch(
    val pattern: String,
    val meaning: String,
    val level: String
)

data class SentenceAnalysisResult(
    val sentence: String,
    val tokens: List<TokenAnalysis>,
    val grammars: List<GrammarMatch>
)

class SentenceAnalyzer(private val context: Context) {
    private val tokenizer by lazy { Tokenizer() }
    private val grammarList = mutableListOf<GrammarMatch>()
    private val grammarRegexes = mutableListOf<Pair<Regex, GrammarMatch>>()

    init {
        loadGrammarRules()
    }

    private fun loadGrammarRules() {
        try {
            val jsonStr = context.assets.open("grammar_rules.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val pattern = obj.getString("pattern")
                val regexStr = obj.optString("regex", pattern)
                val meaning = obj.getString("meaning")
                val level = obj.optString("level", "JLPT")
                val g = GrammarMatch(pattern, meaning, level)
                grammarList.add(g)
                grammarRegexes.add(Pair(Regex(regexStr), g))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun analyze(text: String): SentenceAnalysisResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return SentenceAnalysisResult(text, emptyList(), emptyList())
        }

        // 1. Phân tích ngữ pháp
        val matchedGrammars = mutableListOf<GrammarMatch>()
        for ((regex, grammar) in grammarRegexes) {
            if (regex.containsMatchIn(trimmed) || trimmed.contains(grammar.pattern)) {
                if (matchedGrammars.none { it.pattern == grammar.pattern }) {
                    matchedGrammars.add(grammar)
                }
            }
        }

        // 2. Tách từ vựng bằng Kuromoji
        val rawTokens = tokenizer.tokenize(trimmed)
        val tokens = rawTokens.map { token ->
            val surface = token.surface
            val baseForm = if (token.baseForm != "*") token.baseForm else surface
            val reading = if (token.reading != "*") katakanaToHiragana(token.reading) else ""
            val pos = token.partOfSpeechLevel1

            // Thử tra cứu nghĩa offline từ metadata Kanji nếu từ đó khớp với 1 chữ Kanji
            var meaning: String? = null
            if (baseForm.length == 1) {
                meaning = lookupKanjiMeaning(baseForm)
            }

            TokenAnalysis(
                surface = surface,
                baseForm = baseForm,
                reading = reading,
                partOfSpeech = pos,
                meaning = meaning
            )
        }

        return SentenceAnalysisResult(
            sentence = trimmed,
            tokens = tokens,
            grammars = matchedGrammars
        )
    }

    private fun lookupKanjiMeaning(kanji: String): String? {
        return try {
            val jsonString = context.assets.open("metadata/$kanji.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(jsonString)
            val amHan = obj.optJSONObject("reading")?.optString("am_han", null)
            val vi = obj.optJSONObject("meaning")?.optString("vi", null)
            if (amHan != null && vi != null) "$amHan ($vi)"
            else vi ?: amHan
        } catch (e: Exception) {
            null
        }
    }

    private fun katakanaToHiragana(katakana: String): String {
        val sb = StringBuilder()
        for (c in katakana) {
            if (c in '\u30A1'..'\u30F6') {
                sb.append((c.code - 0x60).toChar())
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }
}
