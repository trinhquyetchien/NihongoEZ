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
    val partOfSpeechVi: String = "",
    val partOfSpeechEn: String = "",
    val meaningVi: String? = null,
    val meaningEn: String? = null,
    val hasKanji: Boolean = false,
    val amHan: String? = null
) {
    fun meaning(lang: AppLanguage): String? = if (lang == AppLanguage.ENGLISH) {
        meaningEn ?: meaningVi
    } else {
        meaningVi ?: meaningEn
    }

    fun pos(lang: AppLanguage): String = if (lang == AppLanguage.ENGLISH) {
        if (partOfSpeechEn.isNotBlank()) partOfSpeechEn else partOfSpeech
    } else {
        if (partOfSpeechVi.isNotBlank()) partOfSpeechVi else partOfSpeech
    }
}

data class GrammarMatch(
    val pattern: String,
    val meaningVi: String,
    val meaningEn: String,
    val level: String
) {
    fun meaning(lang: AppLanguage): String = if (lang == AppLanguage.ENGLISH) meaningEn else meaningVi
}

data class SingleSentenceResult(
    val sentence: String,
    val tokens: List<TokenAnalysis>,
    val grammars: List<GrammarMatch>,
    val translationVi: String,
    val translationEn: String
) {
    fun translation(lang: AppLanguage): String = if (lang == AppLanguage.ENGLISH) translationEn else translationVi
}

data class FullAnalysisResult(
    val fullText: String,
    val sentences: List<SingleSentenceResult>,
    val allUniqueTokens: List<TokenAnalysis>,
    val allUniqueGrammars: List<GrammarMatch>,
    val fullTranslationVi: String,
    val fullTranslationEn: String
) {
    fun fullTranslation(lang: AppLanguage): String = if (lang == AppLanguage.ENGLISH) fullTranslationEn else fullTranslationVi
}

class SentenceAnalyzer(private val context: Context) {
    private val tokenizer by lazy {
        try {
            Tokenizer()
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }
    private val grammarList = mutableListOf<GrammarMatch>()
    private val grammarRegexes = mutableListOf<Pair<Regex, GrammarMatch>>()
    private val vocabCacheVi = mutableMapOf<String, String>()
    private val vocabCacheEn = mutableMapOf<String, String>()

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
                vocabCacheVi[key] = obj.getString(key)
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
                val meaningVi = obj.getString("meaning")
                val meaningEn = obj.optString("meaning_en", meaningVi)
                val level = obj.optString("level", "JLPT")
                val g = GrammarMatch(pattern, meaningVi, meaningEn, level)
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
            return FullAnalysisResult(text, emptyList(), emptyList(), emptyList(), "", "")
        }

        // Tách thành từng câu theo dấu ngắt câu tiếng Nhật hoặc dấu chấm/xuống dòng
        val rawSentences = trimmed.split(Regex("(?<=[。！？\n])|(?<=[.!?])")).map { it.trim() }.filter { it.isNotEmpty() }
        val sentenceList = if (rawSentences.isEmpty()) listOf(trimmed) else rawSentences

        val sentenceResults = sentenceList.map { s -> analyzeSentence(s) }

        // Gom tất cả từ vựng không trùng lặp
        val seenTokens = mutableSetOf<String>()
        val uniqueTokens = mutableListOf<TokenAnalysis>()
        for (sr in sentenceResults) {
            for (t in sr.tokens) {
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

        val fullTransVi = sentenceResults.joinToString(" ") { it.translationVi }.trim()
        val fullTransEn = sentenceResults.joinToString(" ") { it.translationEn }.trim()

        return FullAnalysisResult(
            fullText = trimmed,
            sentences = sentenceResults,
            allUniqueTokens = uniqueTokens,
            allUniqueGrammars = uniqueGrammars,
            fullTranslationVi = fullTransVi,
            fullTranslationEn = fullTransEn
        )
    }

    private fun analyzeSentence(sentence: String): SingleSentenceResult {
        // 1. Phân tích ngữ pháp
        val matchedGrammars = mutableListOf<GrammarMatch>()
        for ((regex, grammar) in grammarRegexes) {
            try {
                if (regex.containsMatchIn(sentence) || sentence.contains(grammar.pattern)) {
                    if (matchedGrammars.none { it.pattern == grammar.pattern }) {
                        matchedGrammars.add(grammar)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Tách từ vựng bằng Kuromoji
        val tok = tokenizer
        val tokens = if (tok != null) {
            try {
                val rawTokens = tok.tokenize(sentence)
                rawTokens.map { token ->
                    val surface = token.surface
                    val baseForm = if (token.baseForm != "*") token.baseForm else surface
                    val reading = if (token.reading != "*") katakanaToHiragana(token.reading) else ""
                    val rawPos = token.partOfSpeechLevel1
                    val hasKanji = containsKanji(surface)

                    val posVi = mapPosToVietnamese(rawPos)
                    val posEn = mapPosToEnglish(rawPos)

                    val (meaningVi, meaningEn, amHan) = lookupMeanings(surface, baseForm)

                    TokenAnalysis(
                        surface = surface,
                        baseForm = baseForm,
                        reading = reading,
                        partOfSpeech = rawPos,
                        partOfSpeechVi = posVi,
                        partOfSpeechEn = posEn,
                        meaningVi = meaningVi,
                        meaningEn = meaningEn,
                        hasKanji = hasKanji,
                        amHan = amHan
                    )
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                fallbackTokenize(sentence)
            }
        } else {
            fallbackTokenize(sentence)
        }

        // 3. Xây dựng bản dịch tự động cho câu
        val transVi = synthesizeSentenceTranslation(sentence, tokens, matchedGrammars, isEnglish = false)
        val transEn = synthesizeSentenceTranslation(sentence, tokens, matchedGrammars, isEnglish = true)

        return SingleSentenceResult(
            sentence = sentence,
            tokens = tokens,
            grammars = matchedGrammars,
            translationVi = transVi,
            translationEn = transEn
        )
    }

    private fun synthesizeSentenceTranslation(
        sentence: String,
        tokens: List<TokenAnalysis>,
        grammars: List<GrammarMatch>,
        isEnglish: Boolean
    ): String {
        // Lấy nghĩa các thành phần chính (danh từ, động từ, tính từ, phó từ)
        val meaningfulParts = tokens.filter { token ->
            val pos = token.partOfSpeech
            pos in listOf("名詞", "動詞", "形容詞", "形容動詞", "副詞", "連体詞", "感動詞") &&
                    !token.surface.all { it in "。、！？,.!?\n\r\t " }
        }.mapNotNull { token ->
            val raw = if (isEnglish) token.meaningEn ?: token.meaningVi else token.meaningVi ?: token.meaningEn
            cleanFirstPhrase(raw)
        }

        if (meaningfulParts.isEmpty()) {
            return if (isEnglish) "Sentence: $sentence" else "Câu: $sentence"
        }

        // Ghép tự nhiên theo cụm nghĩa
        val joined = meaningfulParts.distinct().joinToString(" ")
        return if (joined.isNotBlank()) {
            joined.replaceFirstChar { it.uppercase() } + "."
        } else {
            sentence
        }
    }

    private fun cleanFirstPhrase(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val noColon = if (text.contains(":")) text.substringAfter(":").trim() else text
        val firstSemi = noColon.split(";")[0].trim()
        val firstComma = firstSemi.split(",")[0].trim()
        return firstComma.ifBlank { null }
    }

    private fun fallbackTokenize(sentence: String): List<TokenAnalysis> {
        return sentence.map { ch ->
            val s = ch.toString()
            val hasK = isKanjiChar(ch)
            val (meaningVi, meaningEn, amHan) = lookupMeanings(s, s)
            TokenAnalysis(
                surface = s,
                baseForm = s,
                reading = "",
                partOfSpeech = if (hasK) "名詞" else "記号",
                partOfSpeechVi = if (hasK) "Danh từ" else "Ký hiệu",
                partOfSpeechEn = if (hasK) "Noun" else "Symbol",
                meaningVi = meaningVi,
                meaningEn = meaningEn,
                hasKanji = hasK,
                amHan = amHan
            )
        }
    }

    private fun lookupMeanings(surface: String, baseForm: String): Triple<String?, String?, String?> {
        var viMeaning: String? = vocabCacheVi[surface] ?: vocabCacheVi[baseForm]
        var enMeaning: String? = vocabCacheEn[surface] ?: vocabCacheEn[baseForm]
        var amHanStr: String? = null

        // Tra cứu kanji nếu là chữ đơn
        val target = if (surface.length == 1 && containsKanji(surface)) surface
        else if (baseForm.length == 1 && containsKanji(baseForm)) baseForm
        else null

        if (target != null) {
            val (kVi, kEn, kAmHan) = lookupKanjiMetadata(target)
            if (viMeaning == null) viMeaning = kVi
            if (enMeaning == null) enMeaning = kEn
            amHanStr = kAmHan
        }

        // Nếu là từ ghép chứa Kanji đầu tiên, thử tìm trong vocabulary của chữ Kanji đó
        if (viMeaning == null || enMeaning == null) {
            val firstKanji = surface.firstOrNull { isKanjiChar(it) } ?: baseForm.firstOrNull { isKanjiChar(it) }
            if (firstKanji != null) {
                val fkStr = firstKanji.toString()
                val (vVi, vEn) = lookupVocabFromKanji(fkStr, surface, baseForm)
                if (viMeaning == null) viMeaning = vVi
                if (enMeaning == null) enMeaning = vEn
            }
        }

        return Triple(viMeaning, enMeaning, amHanStr)
    }

    private fun lookupVocabFromKanji(kanji: String, surface: String, baseForm: String): Pair<String?, String?> {
        return try {
            val jsonString = context.assets.open("metadata/$kanji.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(jsonString)
            val vArr = obj.optJSONArray("vocabulary")
            if (vArr != null) {
                for (i in 0 until vArr.length()) {
                    val item = vArr.optJSONObject(i) ?: continue
                    val word = item.optString("word", "")
                    if (word == surface || word == baseForm) {
                        val vi = if (item.has("meaning") && !item.isNull("meaning")) item.getString("meaning") else null
                        val en = if (item.has("meaning_en") && !item.isNull("meaning_en")) item.getString("meaning_en") else null
                        return Pair(vi, en)
                    }
                }
            }
            Pair(null, null)
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    private fun lookupKanjiMetadata(kanji: String): Triple<String?, String?, String?> {
        return try {
            val jsonString = context.assets.open("metadata/$kanji.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(jsonString)
            val amHan = obj.optJSONObject("reading")?.optString("am_han", "")
            val vi = obj.optJSONObject("meaning")?.optString("vi", "")
            val en = obj.optJSONObject("meaning")?.optString("en", "")
            val viFull = if (!amHan.isNullOrBlank() && !vi.isNullOrBlank()) "$amHan: $vi"
            else vi ?: amHan
            Triple(viFull, en, amHan)
        } catch (e: Exception) {
            Triple(null, null, null)
        }
    }

    private fun mapPosToVietnamese(pos: String): String = when (pos) {
        "名詞" -> "Danh từ"
        "動詞" -> "Động từ"
        "形容詞" -> "Tính từ (i)"
        "形容動詞" -> "Tính từ (na)"
        "副詞" -> "Phó từ"
        "助詞" -> "Trợ từ"
        "助動詞" -> "Trợ động từ"
        "接続詞" -> "Liên từ"
        "感動詞" -> "Thán từ"
        "連体詞" -> "Từ hạn định"
        "接頭詞" -> "Tiền tố"
        "記号" -> "Ký hiệu"
        else -> pos
    }

    private fun mapPosToEnglish(pos: String): String = when (pos) {
        "名詞" -> "Noun"
        "動詞" -> "Verb"
        "形容詞" -> "I-Adjective"
        "形容動詞" -> "Na-Adjective"
        "副詞" -> "Adverb"
        "助詞" -> "Particle"
        "助動詞" -> "Auxiliary Verb"
        "接続詞" -> "Conjunction"
        "感動詞" -> "Interjection"
        "連体詞" -> "Adnominal"
        "接頭詞" -> "Prefix"
        "記号" -> "Symbol"
        else -> pos
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
