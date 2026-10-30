package com.onis.kanji

import android.content.Context
import com.atilika.kuromoji.ipadic.Tokenizer
import org.json.JSONArray
import org.json.JSONObject

data class TokenAnalysis(
    val surface: String,
    val baseForm: String,
    val reading: String,
    val partOfSpeech: String,
    val meaning: String? = null,
    val hasKanji: Boolean = false
)

data class GrammarMatch(
    val pattern: String,
    val meaning: String,
    val level: String
)

data class SingleSentenceResult(
    val sentence: String,
    val tokens: List<TokenAnalysis>,
    val grammars: List<GrammarMatch>
)

data class FullAnalysisResult(
    val fullText: String,
    val sentences: List<SingleSentenceResult>,
    val allUniqueTokens: List<TokenAnalysis>,
    val allUniqueGrammars: List<GrammarMatch>
)

class SentenceAnalyzer(private val context: Context) {
    private val tokenizer by lazy { Tokenizer() }
    private val grammarList = mutableListOf<GrammarMatch>()
    private val grammarRegexes = mutableListOf<Pair<Regex, GrammarMatch>>()
    private val vocabCache = mutableMapOf<String, String>()

    init {
        loadGrammarRules()
        loadCompactDictionary()
    }

    private fun loadCompactDictionary() {
        try {
            val jsonStr = context.assets.open("dictionary_compact.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(jsonStr)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                vocabCache[key] = obj.getString(key)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

    fun analyzeFull(text: String): FullAnalysisResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return FullAnalysisResult(text, emptyList(), emptyList(), emptyList())
        }

        // Tách thành từng câu theo dấu ngắt câu tiếng Nhật hoặc dấu chấm/xuống dòng
        val rawSentences = trimmed.split(Regex("(?<=[。！？\n])|(?<=[.!?])")).map { it.trim() }.filter { it.isNotEmpty() }
        val sentenceList = if (rawSentences.isEmpty()) listOf(trimmed) else rawSentences

        val sentenceResults = sentenceList.map { s -> analyzeSentence(s) }

        // Gom tất cả từ vựng không trùng lặp (ưu tiên theo surface hoặc baseForm)
        val seenTokens = mutableSetOf<String>()
        val uniqueTokens = mutableListOf<TokenAnalysis>()
        for (sr in sentenceResults) {
            for (t in sr.tokens) {
                // Lọc bỏ ký hiệu / dấu câu nếu chỉ là ký tự đặc biệt
                if (t.surface.isBlank() || t.surface.all { it in "。、！？,.!?\n\r\t " }) continue
                val key = "${t.baseForm}_${t.surface}"
                if (key !in seenTokens) {
                    seenTokens.add(key)
                    uniqueTokens.add(t)
                }
            }
        }

        // Gom tất cả ngữ pháp không trùng lặp
        val seenGrammar = mutableSetOf<String>()
        val uniqueGrammars = mutableListOf<GrammarMatch>()
        for (sr in sentenceResults) {
            for (g in sr.grammars) {
                if (g.pattern !in seenGrammar) {
                    seenGrammar.add(g.pattern)
                    uniqueGrammars.add(g)
                }
            }
        }

        return FullAnalysisResult(
            fullText = trimmed,
            sentences = sentenceResults,
            allUniqueTokens = uniqueTokens,
            allUniqueGrammars = uniqueGrammars
        )
    }

    private fun analyzeSentence(sentence: String): SingleSentenceResult {
        // 1. Phân tích ngữ pháp
        val matchedGrammars = mutableListOf<GrammarMatch>()
        for ((regex, grammar) in grammarRegexes) {
            if (regex.containsMatchIn(sentence) || sentence.contains(grammar.pattern)) {
                if (matchedGrammars.none { it.pattern == grammar.pattern }) {
                    matchedGrammars.add(grammar)
                }
            }
        }

        // 2. Tách từ vựng bằng Kuromoji
        val rawTokens = tokenizer.tokenize(sentence)
        val tokens = rawTokens.map { token ->
            val surface = token.surface
            val baseForm = if (token.baseForm != "*") token.baseForm else surface
            val reading = if (token.reading != "*") katakanaToHiragana(token.reading) else ""
            val pos = token.partOfSpeechLevel1
            val hasKanji = containsKanji(surface)

            val meaning = lookupMeaning(surface, baseForm)

            TokenAnalysis(
                surface = surface,
                baseForm = baseForm,
                reading = reading,
                partOfSpeech = pos,
                meaning = meaning,
                hasKanji = hasKanji
            )
        }

        return SingleSentenceResult(
            sentence = sentence,
            tokens = tokens,
            grammars = matchedGrammars
        )
    }

    private fun lookupMeaning(surface: String, baseForm: String): String? {
        // 1. Tra cứu trực tiếp từ từ điển compact offline (ưu tiên theo surface hoặc baseForm)
        vocabCache[surface]?.let { return it }
        vocabCache[baseForm]?.let { return it }

        // 2. Tra cứu kanji nếu là chữ đơn
        val target = if (surface.length == 1 && containsKanji(surface)) surface
                     else if (baseForm.length == 1 && containsKanji(baseForm)) baseForm
                     else null

        if (target != null) {
            val found = lookupKanjiMeaning(target)
            if (found != null) {
                vocabCache[target] = found
                return found
            }
        }

        // 3. Nếu là từ ghép chứa Kanji đầu tiên, thử tìm trong vocabulary của chữ Kanji đó
        val firstKanji = surface.firstOrNull { isKanjiChar(it) } ?: baseForm.firstOrNull { isKanjiChar(it) }
        if (firstKanji != null) {
            val fkStr = firstKanji.toString()
            val vocabMeaning = lookupVocabFromKanji(fkStr, surface, baseForm)
            if (vocabMeaning != null) {
                vocabCache[surface] = vocabMeaning
                return vocabMeaning
            }
        }

        return null
    }

    private fun lookupVocabFromKanji(kanji: String, surface: String, baseForm: String): String? {
        return try {
            val jsonString = context.assets.open("metadata/$kanji.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(jsonString)
            val vArr = obj.optJSONArray("vocabulary")
            if (vArr != null) {
                for (i in 0 until vArr.length()) {
                    val item = vArr.optJSONObject(i) ?: continue
                    val word = item.optString("word", "")
                    if (word == surface || word == baseForm) {
                        return if (item.has("meaning") && !item.isNull("meaning")) item.getString("meaning") else null
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun lookupKanjiMeaning(kanji: String): String? {
        return try {
            val jsonString = context.assets.open("metadata/$kanji.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(jsonString)
            val amHan = obj.optJSONObject("reading")?.optString("am_han", "")
            val vi = obj.optJSONObject("meaning")?.optString("vi", "")
            if (!amHan.isNullOrBlank() && !vi.isNullOrBlank()) "$amHan: $vi"
            else if (!vi.isNullOrBlank()) vi
            else if (!amHan.isNullOrBlank()) amHan
            else null
        } catch (e: Exception) {
            null
        }
    }

    fun containsKanji(text: String): Boolean {
        return text.any { isKanjiChar(it) }
    }

    fun isKanjiChar(c: Char): Boolean {
        val code = c.code
        return code in 0x4E00..0x9FAF || code in 0x3400..0x4DBF
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
