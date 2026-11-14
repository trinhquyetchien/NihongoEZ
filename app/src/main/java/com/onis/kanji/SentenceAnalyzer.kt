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
    val romaji: String = "",
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
        loadEnglishDictionary()
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

    private fun loadEnglishDictionary() {
        try {
            val jsonStr = context.assets.open("dictionary_en.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(jsonStr)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                vocabCacheEn[key] = obj.getString(key)
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
                val meaningVi = obj.optString("meaningVi", obj.optString("meaning", ""))
                val meaningEn = obj.optString("meaningEn", obj.optString("meaning_en", meaningVi))
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
                    val hasKanji = containsKanji(surface)
                    val reading = if (token.reading != "*") katakanaToHiragana(token.reading) else {
                        if (!hasKanji) katakanaToHiragana(surface) else ""
                    }
                    val romaji = toRomaji(if (reading.isNotBlank()) reading else surface)
                    val rawPos = token.partOfSpeechLevel1

                    val posVi = mapPosToVietnamese(rawPos)
                    val posEn = mapPosToEnglish(rawPos)

                    var (meaningVi, meaningEn, amHan) = lookupMeanings(surface, baseForm)
                    if (meaningVi.isNullOrBlank()) {
                        meaningVi = posVi
                    }
                    if (meaningEn.isNullOrBlank()) {
                        meaningEn = posEn
                    }
                    val (usageVi, usageEn, examples) = lookupWordExtraInfo(surface, baseForm, rawPos, meaningVi)

                    TokenAnalysis(
                        surface = surface,
                        baseForm = baseForm,
                        reading = reading,
                        romaji = romaji,
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

        // 1. Nhận diện các liên từ mở đầu (ví dụ: そして, しかし, だから, でも...)
        val firstToken = tokens.firstOrNull()
        var leadingConnector: String? = null
        val contentTokens = tokens.toMutableList()
        if (firstToken != null) {
            val s = firstToken.surface
            when (s) {
                "そして" -> { leadingConnector = if (isEnglish) "And then," else "Và rồi,"; contentTokens.removeAt(0) }
                "しかし", "でも" -> { leadingConnector = if (isEnglish) "However," else "Tuy nhiên,"; contentTokens.removeAt(0) }
                "だから", "ですから" -> { leadingConnector = if (isEnglish) "Therefore," else "Vì vậy,"; contentTokens.removeAt(0) }
                "また" -> { leadingConnector = if (isEnglish) "Also," else "Hơn nữa,"; contentTokens.removeAt(0) }
                "たとえば" -> { leadingConnector = if (isEnglish) "For example," else "Ví dụ như,"; contentTokens.removeAt(0) }
            }
        }

        // 2. Phân loại thành phần câu tiếng Nhật (SOV sang SVO)
        val timeTokens = mutableListOf<String>()
        val topicTokens = mutableListOf<String>()
        val objectTokens = mutableListOf<String>()
        val partnerTokens = mutableListOf<String>()
        val locationOrDestTokens = mutableListOf<String>()
        val verbOrAdjTokens = mutableListOf<String>()
        val otherTokens = mutableListOf<String>()

        val timeWords = setOf(
            "今日", "きょう", "明日", "あした", "あす", "昨日", "きのう",
            "毎日", "まいにち", "今", "いま", "朝", "あさ", "今朝", "けさ",
            "昼", "ひる", "夜", "よる", "今夜", "こんや", "毎晩", "まいばん",
            "先週", "せんしゅう", "今週", "こんしゅう", "来週", "らいしゅう",
            "先月", "せんげつ", "今月", "こんげつ", "来月", "らいげつ",
            "去年", "きょねん", "今年", "ことし", "来年", "らいねん",
            "月曜日", "火曜日", "水曜日", "木曜日", "金曜日", "土曜日", "日曜日",
            "いつも", "時々", "ときどき", "たまに", "よく", "最近", "さいきん"
        )

        var pendingTokens = mutableListOf<TokenAnalysis>()

        for (i in contentTokens.indices) {
            val token = contentTokens[i]
            val surface = token.surface
            val baseForm = token.baseForm
            val pos = token.partOfSpeech

            when (surface) {
                "は" -> {
                    val isTime = pendingTokens.any { it.surface in timeWords || it.baseForm in timeWords }
                    val phrase = pendingTokens.mapNotNull { cleanFirstMeaning(it, isEnglish) }.joinToString(" ")
                    if (phrase.isNotBlank()) {
                        if (isTime) timeTokens.add(phrase) else topicTokens.add(phrase)
                    }
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
                "に" -> {
                    val isTime = pendingTokens.any { it.surface in timeWords || it.baseForm in timeWords }
                    val phrase = pendingTokens.mapNotNull { cleanFirstMeaning(it, isEnglish) }.joinToString(" ")
                    if (phrase.isNotBlank()) {
                        if (isTime) {
                            val prep = if (isEnglish) "at/on" else "vào lúc"
                            timeTokens.add("$prep $phrase")
                        } else {
                            val prep = if (isEnglish) "at/to" else "ở/đến"
                            locationOrDestTokens.add("$prep $phrase")
                        }
                    }
                    pendingTokens.clear()
                }
                "で" -> {
                    val phrase = pendingTokens.mapNotNull { cleanFirstMeaning(it, isEnglish) }.joinToString(" ")
                    if (phrase.isNotBlank()) {
                        val prep = if (isEnglish) "at/by" else "ở/bằng"
                        locationOrDestTokens.add("$prep $phrase")
                    }
                    pendingTokens.clear()
                }
                "へ" -> {
                    val phrase = pendingTokens.mapNotNull { cleanFirstMeaning(it, isEnglish) }.joinToString(" ")
                    if (phrase.isNotBlank()) {
                        val prep = if (isEnglish) "to" else "đến"
                        locationOrDestTokens.add("$prep $phrase")
                    }
                    pendingTokens.clear()
                }
                "から" -> {
                    val phrase = pendingTokens.mapNotNull { cleanFirstMeaning(it, isEnglish) }.joinToString(" ")
                    if (phrase.isNotBlank()) {
                        val prep = if (isEnglish) "from" else "từ"
                        locationOrDestTokens.add("$prep $phrase")
                    }
                    pendingTokens.clear()
                }
                "まで" -> {
                    val phrase = pendingTokens.mapNotNull { cleanFirstMeaning(it, isEnglish) }.joinToString(" ")
                    if (phrase.isNotBlank()) {
                        val prep = if (isEnglish) "until/to" else "đến"
                        locationOrDestTokens.add("$prep $phrase")
                    }
                    pendingTokens.clear()
                }
                "と" -> {
                    val phrase = pendingTokens.mapNotNull { cleanFirstMeaning(it, isEnglish) }.joinToString(" ")
                    if (phrase.isNotBlank()) {
                        val conj = if (isEnglish) "with" else "cùng"
                        partnerTokens.add("$conj $phrase")
                    }
                    pendingTokens.clear()
                }
                "も" -> {
                    val phrase = pendingTokens.mapNotNull { cleanFirstMeaning(it, isEnglish) }.joinToString(" ")
                    if (phrase.isNotBlank()) {
                        val also = if (isEnglish) "also $phrase" else "cũng $phrase"
                        if (topicTokens.isEmpty()) topicTokens.add(also) else objectTokens.add(also)
                    }
                    pendingTokens.clear()
                }
                else -> {
                    if (pos in listOf("動詞", "形容詞", "形容動詞")) {
                        // Nếu có pendingTokens trước động từ mà chưa có trợ từ
                        if (pendingTokens.isNotEmpty()) {
                            val phrase = pendingTokens.mapNotNull { cleanFirstMeaning(it, isEnglish) }.joinToString(" ")
                            if (phrase.isNotBlank()) {
                                val isTime = pendingTokens.any { it.surface in timeWords || it.baseForm in timeWords }
                                if (isTime) timeTokens.add(phrase)
                                else if (topicTokens.isEmpty()) topicTokens.add(phrase)
                                else objectTokens.add(phrase)
                            }
                            pendingTokens.clear()
                        }
                        val vMeaning = cleanFirstMeaning(token, isEnglish)
                        if (!vMeaning.isNullOrBlank()) {
                            verbOrAdjTokens.add(vMeaning)
                        }
                    } else if (pos in listOf("名詞", "副詞", "連体詞", "感動詞")) {
                        if (surface in timeWords || baseForm in timeWords) {
                            val timePhrase = cleanFirstMeaning(token, isEnglish)
                            if (!timePhrase.isNullOrBlank()) {
                                timeTokens.add(timePhrase)
                            }
                        } else {
                            pendingTokens.add(token)
                        }
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

        // 3. Xử lý thì/thể và thái độ động từ (De-inflection Aspect/Mood)
        var aspectPrefix = ""
        var aspectSuffix = ""
        val s = sentence

        if (!isEnglish) {
            when {
                s.contains("てはいけない") || s.contains("ちゃいけない") -> aspectPrefix = "không được "
                s.contains("てもいい") || s.contains("てもよい") -> aspectPrefix = "có thể "
                s.contains("てください") || s.contains("てくれ") -> aspectPrefix = "xin hãy "
                s.contains("ないでください") -> aspectPrefix = "xin đừng "
                s.contains("たい") || s.contains("たくない") -> {
                    aspectPrefix = if (s.contains("たくない")) "không muốn " else "muốn "
                }
                s.contains("たことがある") -> aspectPrefix = "đã từng "
                s.contains("たことがない") -> aspectPrefix = "chưa từng "
                s.contains("ることができる") || s.contains("ことができる") || s.contains("ことができます") -> aspectPrefix = "có thể "
                s.contains("ことができない") || s.contains("ことができません") -> aspectPrefix = "không thể "
                s.contains("ほうがいい") -> aspectPrefix = "nên "
                s.contains("つもり") -> aspectPrefix = "dự định "
                s.contains("やすい") -> aspectSuffix = " dễ dàng"
                s.contains("にくい") -> aspectSuffix = " khó khăn"
                s.contains("すぎる") || s.contains("すぎます") || s.contains("すぎた") -> aspectSuffix = " quá mức"
                s.contains("ている") || s.contains("ています") -> aspectPrefix = "đang "
                s.contains("てしまう") || s.contains("てしまった") || s.contains("ちゃった") -> aspectPrefix = "lỡ "
                s.contains("てみる") || s.contains("てみます") -> aspectPrefix = "thử "
                s.contains("かもしれない") || s.contains("かもしれません") -> aspectSuffix = " có lẽ"
                s.contains("はずだ") || s.contains("はずです") -> aspectPrefix = "chắc chắn "
                s.contains("わけがない") -> aspectPrefix = "tuyệt đối không thể "
                s.contains("なければならない") || s.contains("なくてはいけない") || s.contains("なきゃ") -> aspectPrefix = "phải "
                s.contains("ました") || s.contains("だった") || s.contains("でした") -> aspectPrefix = "đã "
                s.contains("ない") || s.contains("ません") -> aspectPrefix = "không "
            }
        } else {
            when {
                s.contains("てはいけない") -> aspectPrefix = "must not "
                s.contains("てもいい") -> aspectPrefix = "may "
                s.contains("てください") -> aspectPrefix = "please "
                s.contains("ないでください") -> aspectPrefix = "please do not "
                s.contains("たい") -> aspectPrefix = if (s.contains("たくない")) "do not want to " else "want to "
                s.contains("たことがある") -> aspectPrefix = "have experienced "
                s.contains("たことがない") -> aspectPrefix = "have never "
                s.contains("ることができる") || s.contains("ことができる") -> aspectPrefix = "can "
                s.contains("ほうがいい") -> aspectPrefix = "had better "
                s.contains("つもり") -> aspectPrefix = "plan to "
                s.contains("ている") || s.contains("ています") -> aspectPrefix = "is/are currently "
                s.contains("なければならない") -> aspectPrefix = "must "
                s.contains("ない") || s.contains("ません") -> aspectPrefix = "do not "
            }
        }

        val resultParts = mutableListOf<String>()
        if (!leadingConnector.isNullOrBlank()) {
            resultParts.add(leadingConnector)
        }

        // 1. Trạng ngữ thời gian (đầu câu)
        if (timeTokens.isNotEmpty()) {
            resultParts.add(timeTokens.joinToString(", "))
        }

        // 2. Chủ ngữ (S)
        if (topicTokens.isNotEmpty()) {
            resultParts.add(topicTokens.joinToString(", "))
        }

        // 3. Vị ngữ / Động từ (V) theo cấu trúc ngữ pháp Việt Nam: S + V + O
        val verbString = verbOrAdjTokens.joinToString(", ")
        if (verbString.isNotBlank()) {
            resultParts.add("$aspectPrefix$verbString$aspectSuffix".trim())
        } else if (aspectPrefix.isNotBlank()) {
            resultParts.add(aspectPrefix.trim())
        }

        // 4. Tân ngữ (O)
        if (objectTokens.isNotEmpty()) {
            resultParts.add(objectTokens.joinToString(", "))
        }

        // 5. Bạn đồng hành ("với / cùng ...")
        if (partnerTokens.isNotEmpty()) {
            resultParts.add(partnerTokens.joinToString(", "))
        }

        // 6. Trạng ngữ nơi chốn / phương tiện / đích đến
        if (locationOrDestTokens.isNotEmpty()) {
            resultParts.add(locationOrDestTokens.joinToString(", "))
        }

        // 7. Thành phần bổ sung
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
            val reading = if (!hasK) katakanaToHiragana(s) else ""
            val romaji = toRomaji(if (reading.isNotBlank()) reading else s)
            var (meaningVi, meaningEn, amHan) = lookupMeanings(s, s)
            val posVi = if (hasK) "Danh từ" else "Ký hiệu"
            val posEn = if (hasK) "Noun" else "Symbol"
            if (meaningVi.isNullOrBlank()) meaningVi = posVi
            if (meaningEn.isNullOrBlank()) meaningEn = posEn
            TokenAnalysis(
                surface = s,
                baseForm = s,
                reading = reading,
                romaji = romaji,
                partOfSpeech = if (hasK) "名詞" else "記号",
                partOfSpeechVi = posVi,
                partOfSpeechEn = posEn,
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

    fun toRomaji(kana: String): String {
        if (kana.isBlank()) return ""
        val hira = katakanaToHiragana(kana)
        val sb = StringBuilder()
        var i = 0
        val n = hira.length

        val multiMap = mapOf(
            "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
            "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
            "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
            "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
            "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
            "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
            "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
            "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
            "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
            "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
            "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo"
        )

        val singleMap = mapOf(
            'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
            'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
            'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
            'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
            'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
            'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
            'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
            'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
            'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
            'わ' to "wa", 'を' to "wo", 'ん' to "n",
            'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
            'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
            'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
            'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
            'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po"
        )

        while (i < n) {
            if (i + 1 < n) {
                val pair = hira.substring(i, i + 2)
                if (multiMap.containsKey(pair)) {
                    sb.append(multiMap[pair])
                    i += 2
                    continue
                }
            }
            val c = hira[i]
            if (c == 'っ' && i + 1 < n) {
                val nextPair = if (i + 2 < n) hira.substring(i + 1, i + 3) else ""
                val nextR = multiMap[nextPair] ?: singleMap[hira[i + 1]] ?: ""
                if (nextR.isNotEmpty()) {
                    sb.append(nextR[0])
                }
                i++
                continue
            }
            if (singleMap.containsKey(c)) {
                sb.append(singleMap[c])
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }
}
