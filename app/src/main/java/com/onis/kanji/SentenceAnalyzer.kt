package com.onis.kanji

import android.content.Context
import com.atilika.kuromoji.ipadic.Tokenizer
import org.json.JSONArray
import org.json.JSONObject

data class ExampleSentence(
    val japanese: String,
    val reading: String,
    val translationVi: String,
    val translationEn: String
)

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
    val amHan: String? = null,
    val usageNotesVi: String? = null,
    val usageNotesEn: String? = null,
    val examples: List<ExampleSentence> = emptyList()
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

    fun usage(lang: AppLanguage): String? = if (lang == AppLanguage.ENGLISH) {
        usageNotesEn ?: usageNotesVi
    } else {
        usageNotesVi ?: usageNotesEn
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
                    val (usageVi, usageEn, examples) = lookupWordExtraInfo(surface, baseForm, rawPos, meaningVi)

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
                        amHan = amHan,
                        usageNotesVi = usageVi,
                        usageNotesEn = usageEn,
                        examples = examples
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
        if (tokens.isEmpty()) return sentence

        // Phân loại thành phần câu tiếng Nhật (SOV)
        // Chủ ngữ (Topic / Subject): đi liền trước は hoặc が
        val topicTokens = mutableListOf<String>()
        val objectTokens = mutableListOf<String>()
        val locationOrTimeTokens = mutableListOf<String>()
        val verbOrAdjTokens = mutableListOf<String>()
        val otherTokens = mutableListOf<String>()

        var pendingTokens = mutableListOf<TokenAnalysis>()

        for (i in tokens.indices) {
            val token = tokens[i]
            val surface = token.surface
            val pos = token.partOfSpeech

            when (surface) {
                "は" -> {
                    val phrase = pendingTokens.mapNotNull { cleanFirstMeaning(it, isEnglish) }.joinToString(" ")
                    if (phrase.isNotBlank()) topicTokens.add(phrase)
                    pendingTokens.clear()
                }
                "が" -> {
                    val phrase = pendingTokens.mapNotNull { cleanFirstMeaning(it, isEnglish) }.joinToString(" ")
                    if (phrase.isNotBlank()) topicTokens.add(phrase)
                    pendingTokens.clear()
                }
                "を" -> {
                    val phrase = pendingTokens.mapNotNull { cleanFirstMeaning(it, isEnglish) }.joinToString(" ")
                    if (phrase.isNotBlank()) objectTokens.add(phrase)
                    pendingTokens.clear()
                }
                "に", "で", "へ" -> {
                    val phrase = pendingTokens.mapNotNull { cleanFirstMeaning(it, isEnglish) }.joinToString(" ")
                    if (phrase.isNotBlank()) {
                        val prep = if (isEnglish) {
                            if (surface == "で") "at/by" else if (surface == "へ") "to" else "in/at"
                        } else {
                            if (surface == "で") "ở/bằng" else if (surface == "へ") "đến" else "vào/ở"
                        }
                        locationOrTimeTokens.add("$prep $phrase")
                    }
                    pendingTokens.clear()
                }
                "から" -> {
                    val phrase = pendingTokens.mapNotNull { cleanFirstMeaning(it, isEnglish) }.joinToString(" ")
                    if (phrase.isNotBlank()) {
                        val prep = if (isEnglish) "from" else "từ"
                        locationOrTimeTokens.add("$prep $phrase")
                    }
                    pendingTokens.clear()
                }
                "まで" -> {
                    val phrase = pendingTokens.mapNotNull { cleanFirstMeaning(it, isEnglish) }.joinToString(" ")
                    if (phrase.isNotBlank()) {
                        val prep = if (isEnglish) "until/to" else "đến"
                        locationOrTimeTokens.add("$prep $phrase")
                    }
                    pendingTokens.clear()
                }
                "と" -> {
                    val phrase = pendingTokens.mapNotNull { cleanFirstMeaning(it, isEnglish) }.joinToString(" ")
                    if (phrase.isNotBlank()) {
                        val conj = if (isEnglish) "and/with" else "và/cùng"
                        otherTokens.add("$conj $phrase")
                    }
                    pendingTokens.clear()
                }
                else -> {
                    if (pos in listOf("動詞", "形容詞", "形容動詞")) {
                        val vMeaning = cleanFirstMeaning(token, isEnglish)
                        if (!vMeaning.isNullOrBlank()) {
                            verbOrAdjTokens.add(vMeaning)
                        }
                    } else if (pos in listOf("名詞", "副詞", "連体詞", "感動詞")) {
                        pendingTokens.add(token)
                    }
                }
            }
        }

        // Bổ sung các token danh từ còn sót chưa gặp trợ từ
        if (pendingTokens.isNotEmpty()) {
            val remainPhrase = pendingTokens.mapNotNull { cleanFirstMeaning(it, isEnglish) }.joinToString(" ")
            if (remainPhrase.isNotBlank()) {
                if (topicTokens.isEmpty()) topicTokens.add(remainPhrase)
                else objectTokens.add(remainPhrase)
            }
        }

        // Kiểm tra thì/thể ngữ pháp đặc biệt
        val sentenceStr = sentence
        var aspectPrefix = ""
        var aspectSuffix = ""
        if (!isEnglish) {
            when {
                sentenceStr.contains("ています") || sentenceStr.contains("ている") -> aspectPrefix = "đang "
                sentenceStr.contains("てください") -> aspectPrefix = "xin hãy "
                sentenceStr.contains("たい") -> aspectPrefix = "muốn "
                sentenceStr.contains("なければならない") -> aspectPrefix = "phải "
                sentenceStr.contains("ことができる") -> aspectPrefix = "có thể "
                sentenceStr.contains("たことがある") -> aspectPrefix = "đã từng "
                sentenceStr.contains("ました") || sentenceStr.contains("た") -> aspectPrefix = "đã "
                sentenceStr.contains("ない") || sentenceStr.contains("ません") -> aspectPrefix = "không "
            }
        } else {
            when {
                sentenceStr.contains("てください") -> aspectPrefix = "please "
                sentenceStr.contains("たい") -> aspectPrefix = "want to "
                sentenceStr.contains("なければならない") -> aspectPrefix = "must "
                sentenceStr.contains("ことができる") -> aspectPrefix = "can "
                sentenceStr.contains("たことがある") -> aspectPrefix = "have experienced "
                sentenceStr.contains("ません") || sentenceStr.contains("ない") -> aspectPrefix = "do not "
            }
        }

        val resultParts = mutableListOf<String>()

        // 1. Chủ ngữ (S)
        if (topicTokens.isNotEmpty()) {
            resultParts.add(topicTokens.joinToString(", "))
        }

        // 2. Vị ngữ / Động từ (V) theo cấu trúc ngữ pháp Việt Nam: S + V + O
        val verbString = verbOrAdjTokens.joinToString(", ")
        if (verbString.isNotBlank()) {
            resultParts.add("$aspectPrefix$verbString$aspectSuffix".trim())
        } else if (aspectPrefix.isNotBlank()) {
            resultParts.add(aspectPrefix.trim())
        }

        // 3. Tân ngữ (O)
        if (objectTokens.isNotEmpty()) {
            resultParts.add(objectTokens.joinToString(", "))
        }

        // 4. Trạng ngữ thời gian/địa điểm
        if (locationOrTimeTokens.isNotEmpty()) {
            resultParts.add(locationOrTimeTokens.joinToString(", "))
        }

        // 5. Thành phần bổ sung
        if (otherTokens.isNotEmpty()) {
            resultParts.add(otherTokens.joinToString(", "))
        }

        val joined = resultParts.filter { it.isNotBlank() }.joinToString(" ")
        return if (joined.isNotBlank()) {
            joined.replaceFirstChar { it.uppercase() } + if (joined.endsWith(".") || joined.endsWith("!") || joined.endsWith("?")) "" else "."
        } else {
            val fallback = tokens.mapNotNull { cleanFirstMeaning(it, isEnglish) }.distinct().joinToString(" ")
            if (fallback.isNotBlank()) fallback.replaceFirstChar { it.uppercase() } + "." else sentence
        }
    }

    private fun cleanFirstMeaning(token: TokenAnalysis, isEnglish: Boolean): String? {
        val raw = if (isEnglish) token.meaningEn ?: token.meaningVi else token.meaningVi ?: token.meaningEn
        return cleanFirstPhrase(raw)
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

    private fun lookupWordExtraInfo(
        surface: String,
        baseForm: String,
        pos: String,
        meaningVi: String?
    ): Triple<String?, String?, List<ExampleSentence>> {
        val examples = mutableListOf<ExampleSentence>()
        var usageVi: String? = null
        var usageEn: String? = null

        // Tạo gợi ý ngữ cảnh sử dụng dựa trên loại từ và ý nghĩa
        when (pos) {
            "動詞" -> {
                usageVi = "Động từ hành động/trạng thái. Thường kết hợp với trợ từ を (chỉ tân ngữ), に/へ (chỉ đích đến), で (chỉ địa điểm/phương tiện)."
                usageEn = "Action or state verb. Commonly used with particles を (direct object), に/へ (destination), or で (location/method)."
            }
            "名詞" -> {
                usageVi = "Danh từ. Có thể làm chủ ngữ với は/が, tân ngữ với を, hoặc bổ ngữ thời gian/địa điểm với に/で."
                usageEn = "Noun. Can act as a topic/subject with は/が, an object with を, or a time/place complement with に/で."
            }
            "形容詞" -> {
                usageVi = "Tính từ đuôi い. Bổ nghĩa trực tiếp cho danh từ đứng sau, hoặc làm vị ngữ đứng cuối câu khẳng định/phủ định."
                usageEn = "I-Adjective. Modifies the following noun directly or serves as the predicate at the end of a sentence."
            }
            "形容動詞" -> {
                usageVi = "Tính từ đuôi な. Cần thêm 'な' khi bổ nghĩa cho danh từ đứng sau (VD: ...な人)."
                usageEn = "Na-Adjective. Requires 'な' when directly modifying a following noun."
            }
            "副詞" -> {
                usageVi = "Phó từ. Dùng để bổ nghĩa cho động từ, tính từ hoặc toàn bộ câu để chỉ mức độ, tần suất, cách thức."
                usageEn = "Adverb. Modifies verbs, adjectives, or entire clauses to indicate degree, frequency, or manner."
            }
        }

        // Tìm ví dụ từ Kanji metadata nếu từ vựng chứa Kanji
        val firstKanji = surface.firstOrNull { isKanjiChar(it) } ?: baseForm.firstOrNull { isKanjiChar(it) }
        if (firstKanji != null) {
            try {
                val jsonString = context.assets.open("metadata/$firstKanji.json").bufferedReader().use { it.readText() }
                val obj = JSONObject(jsonString)
                val vArr = obj.optJSONArray("vocabulary")
                if (vArr != null) {
                    for (i in 0 until vArr.length()) {
                        val item = vArr.optJSONObject(i) ?: continue
                        val word = item.optString("word", "")
                        val r = item.optString("reading", "")
                        val mVi = item.optString("meaning", "")
                        val mEn = item.optString("meaning_en", "")

                        // Nếu là từ liên quan khác với surface, lấy làm ví dụ
                        if (word.isNotBlank() && word != surface && examples.size < 2) {
                            examples.add(
                                ExampleSentence(
                                    japanese = word,
                                    reading = r,
                                    translationVi = mVi,
                                    translationEn = if (mEn.isNotBlank()) mEn else mVi
                                )
                            )
                        }
                    }
                }

                // Nếu có explanation trong kanji, làm giàu thêm usage
                val exp = obj.optJSONObject("explanation")?.optString("text", "")
                if (!exp.isNullOrBlank() && (surface.length == 1 || baseForm.length == 1)) {
                    usageVi = exp
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // Tạo câu ví dụ mẫu ngữ cảnh nếu chưa có ví dụ
        if (examples.isEmpty()) {
            val cleanM = cleanFirstPhrase(meaningVi) ?: surface
            when (pos) {
                "動詞" -> {
                    examples.add(
                        ExampleSentence(
                            japanese = "毎日${baseForm}。",
                            reading = "まいにち${baseForm}",
                            translationVi = "Mỗi ngày đều $cleanM.",
                            translationEn = "I $cleanM every day."
                        )
                    )
                }
                "名詞" -> {
                    examples.add(
                        ExampleSentence(
                            japanese = "これは${surface}です。",
                            reading = "これは${surface}です",
                            translationVi = "Đây là $cleanM.",
                            translationEn = "This is a/an $cleanM."
                        )
                    )
                }
                "形容詞", "形容動詞" -> {
                    examples.add(
                        ExampleSentence(
                            japanese = "とても${surface}です。",
                            reading = "とても${surface}です",
                            translationVi = "Rất $cleanM.",
                            translationEn = "It is very $cleanM."
                        )
                    )
                }
            }
        }

        return Triple(usageVi, usageEn, examples)
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
