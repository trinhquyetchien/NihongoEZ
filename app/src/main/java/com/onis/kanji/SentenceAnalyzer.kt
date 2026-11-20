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
    private val reverseDictVi = mutableMapOf<String, MutableList<String>>()

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
            val nonAlphaRegex = Regex("[^\\p{L}\\p{Nd}\\s]")
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.getString(key)
                vocabCacheVi[key] = value

                // Xây dựng reverse dictionary cho dịch ngược
                val meaning = if (value.contains(':')) value.substringAfter(':').trim() else value
                val parts = meaning.replace(';', ',').split(',')
                for (p in parts) {
                    val clean = p.lowercase().replace(nonAlphaRegex, "").trim()
                    if (clean.length >= 2) {
                        val list = reverseDictVi.getOrPut(clean) { mutableListOf() }
                        if (!list.contains(key)) {
                            list.add(key)
                        }
                    }
                }
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

    // -------------------------------------------------------------------------
    // REVERSE TRANSLATION: VIETNAMESE / ENGLISH -> JAPANESE
    // -------------------------------------------------------------------------

    private val highPriorityWordsVi = mapOf(
        "tôi" to "私", "mình" to "私", "bạn" to "あなた", "anh ấy" to "彼", "cô ấy" to "彼女",
        "chúng tôi" to "私たち", "chúng ta" to "私たち", "các bạn" to "あなたたち", "họ" to "彼ら",
        "người" to "人", "thầy giáo" to "先生", "cô giáo" to "先生", "học sinh" to "学生", "bác sĩ" to "医者",
        "bố" to "父", "ba" to "父", "cha" to "父", "mẹ" to "母", "anh trai" to "兄", "chị gái" to "姉",
        "em trai" to "弟", "em gái" to "妹", "bạn bè" to "友達",
        // Thời gian
        "hôm nay" to "今日", "ngày mai" to "明日", "hôm qua" to "昨日", "bây giờ" to "今", "hiện tại" to "今",
        "mỗi ngày" to "毎日", "buổi sáng" to "朝", "buổi trưa" to "昼", "buổi tối" to "夜",
        "năm nay" to "今年", "năm sau" to "来年", "năm ngoái" to "去年",
        // Địa điểm
        "nhà" to "家", "trường học" to "学校", "trường" to "学校", "công ty" to "会社", "bệnh viện" to "病院",
        "ngân hàng" to "銀行", "nhà ga" to "駅", "ga" to "駅", "cửa hàng" to "店", "siêu thị" to "スーパー",
        "nhà hàng" to "レストラン", "quán ăn" to "食堂", "phòng" to "部屋", "công viên" to "公園",
        // Danh từ / Đồ vật
        "sách" to "本", "bút" to "ペン", "nước" to "水", "trà" to "お茶", "cà phê" to "コーヒー",
        "cơm" to "ご飯", "bánh mì" to "パン", "thịt" to "肉", "cá" to "魚", "táo" to "りんご",
        "hoa quả" to "果物", "trái cây" to "果物", "tiếng nhật" to "日本語", "tiếng việt" to "ベトナム語",
        "tiếng anh" to "英語", "xe hơi" to "車", "ô tô" to "車", "xe máy" to "バイク", "xe đạp" to "自転車",
        "phim" to "映画", "nhạc" to "音楽", "thư" to "手紙", "ảnh" to "写真", "tiền" to "お金",
        // Động từ
        "ăn" to "食べる", "uống" to "飲む", "đi" to "行く", "đến" to "来る", "về" to "帰る",
        "mua" to "買う", "bán" to "売る", "đọc" to "読む", "viết" to "書く", "nghe" to "聞く",
        "nói" to "話す", "xem" to "見る", "nhìn" to "見る", "học" to "勉強する", "làm" to "する",
        "ngủ" to "寝る", "thức dậy" to "起きる", "gặp" to "会う", "chơi" to "遊ぶ", "chờ" to "待つ",
        "chụp" to "撮る", "ở" to "いる",
        // Tính từ
        "đẹp" to "綺麗", "xinh" to "綺麗", "tốt" to "良い", "ngon" to "美味しい", "dở" to "まずい",
        "lớn" to "大きい", "to" to "大きい", "nhỏ" to "小さい", "bận" to "忙しい", "rảnh" to "暇",
        "nóng" to "暑い", "lạnh" to "寒い", "thích" to "好き", "ghét" to "嫌い", "mới" to "新しい", "cũ" to "古い"
    )

    private val timeVi = setOf(
        "hôm nay", "ngày mai", "hôm qua", "bây giờ", "hiện tại", "mỗi ngày", "buổi sáng", "buổi trưa", "buổi tối", "năm nay", "năm sau", "năm ngoái"
    )

    private val placeVi = setOf(
        "nhà", "trường học", "trường", "công ty", "bệnh viện", "ngân hàng", "nhà ga", "ga", "cửa hàng", "siêu thị", "nhà hàng", "quán ăn", "phòng", "công viên"
    )

    private val subjectVi = setOf(
        "tôi", "mình", "bạn", "anh ấy", "cô ấy", "chúng tôi", "chúng ta", "các bạn", "họ", "học sinh", "thầy giáo", "cô giáo", "bác sĩ", "bố", "ba", "cha", "mẹ", "anh trai", "chị gái", "em trai", "em gái", "người"
    )

    private val motionVerbsVi = setOf("đi", "đến", "về")
    private val transitiveVerbsVi = setOf("ăn", "uống", "mua", "bán", "đọc", "viết", "nghe", "nói", "xem", "nhìn", "học", "làm", "gặp", "chờ", "chụp")
    private val adjVi = setOf("đẹp", "xinh", "tốt", "ngon", "dở", "lớn", "to", "nhỏ", "bận", "rảnh", "nóng", "lạnh", "thích", "ghét", "mới", "cũ")

    // Mapping tiếng Anh cho chiều dịch EN -> JA
    private val highPriorityWordsEn = mapOf(
        "i" to "私", "me" to "私", "you" to "あなた", "he" to "彼", "she" to "彼女", "we" to "私たち", "they" to "彼ら",
        "today" to "今日", "tomorrow" to "明日", "yesterday" to "昨日", "now" to "今", "every day" to "毎日",
        "school" to "学校", "home" to "家", "house" to "家", "company" to "会社", "hospital" to "病院",
        "station" to "駅", "restaurant" to "レストラン", "room" to "部屋", "park" to "公園",
        "book" to "本", "water" to "水", "tea" to "お茶", "coffee" to "コーヒー", "rice" to "ご飯",
        "bread" to "パン", "apple" to "りんご", "meat" to "肉", "fish" to "魚", "movie" to "映画",
        "music" to "音楽", "letter" to "手紙", "photo" to "写真", "money" to "お金",
        "eat" to "食べる", "drink" to "飲む", "go" to "行く", "come" to "来る", "return" to "帰る",
        "buy" to "買う", "sell" to "売る", "read" to "読む", "write" to "書く", "listen" to "聞く",
        "speak" to "話す", "watch" to "見る", "see" to "見る", "study" to "勉強する", "do" to "する",
        "sleep" to "寝る", "wake up" to "起きる", "meet" to "会う",
        "beautiful" to "綺麗", "good" to "良い", "delicious" to "美味しい", "big" to "大きい",
        "small" to "小さい", "busy" to "忙しい", "free" to "暇", "hot" to "暑い", "cold" to "寒い",
        "like" to "好き", "hate" to "嫌い", "new" to "新しい", "old" to "古い"
    )

    private val politeVerbForms = mapOf(
        "食べる" to mapOf("pres" to "食べます", "past" to "食べました", "neg" to "食べません", "past_neg" to "食べませんでした", "want" to "食べたいです", "req" to "食べてください", "ing" to "食べています", "can" to "食べられます", "must" to "食べなければなりません"),
        "飲む" to mapOf("pres" to "飲みます", "past" to "飲みました", "neg" to "飲みません", "past_neg" to "飲みませんでした", "want" to "飲みたいです", "req" to "飲んでください", "ing" to "飲んでいます", "can" to "飲めます", "must" to "飲まなければなりません"),
        "行く" to mapOf("pres" to "行きます", "past" to "行きました", "neg" to "行きません", "past_neg" to "行きませんでした", "want" to "行きたいです", "req" to "行ってください", "ing" to "行っています", "can" to "行けます", "must" to "行かなければなりません"),
        "来る" to mapOf("pres" to "来ます", "past" to "来ました", "neg" to "来ません", "past_neg" to "来ませんでした", "want" to "来たいです", "req" to "来てください", "ing" to "来ています", "can" to "来られます", "must" to "来なければなりません"),
        "帰る" to mapOf("pres" to "帰ります", "past" to "帰りました", "neg" to "帰りません", "past_neg" to "帰りませんでした", "want" to "帰りたいです", "req" to "帰ってください", "ing" to "帰っています", "can" to "帰れます", "must" to "帰らなければなりません"),
        "買う" to mapOf("pres" to "買います", "past" to "買いました", "neg" to "買いません", "past_neg" to "買いませんでした", "want" to "買いたいです", "req" to "買ってください", "ing" to "買っています", "can" to "買えます", "must" to "買わなければなりません"),
        "読む" to mapOf("pres" to "読みます", "past" to "読みました", "neg" to "読みません", "past_neg" to "読みませんでした", "want" to "読みたいです", "req" to "読んでください", "ing" to "読んでいます", "can" to "読めます", "must" to "読まなければなりません"),
        "書く" to mapOf("pres" to "書きます", "past" to "書きました", "neg" to "書きません", "past_neg" to "書きませんでした", "want" to "書きたいです", "req" to "書いてください", "ing" to "書いています", "can" to "書けます", "must" to "書かなければなりません"),
        "聞く" to mapOf("pres" to "聞きます", "past" to "聞きました", "neg" to "聞きません", "past_neg" to "聞きませんでした", "want" to "聞きたいです", "req" to "聞いてください", "ing" to "聞いています", "can" to "聞けます", "must" to "聞かなければなりません"),
        "話す" to mapOf("pres" to "話します", "past" to "話しました", "neg" to "話しません", "past_neg" to "話しませんでした", "want" to "話したいです", "req" to "話してください", "ing" to "話しています", "can" to "話せます", "must" to "話さなければなりません"),
        "見る" to mapOf("pres" to "見ます", "past" to "見ました", "neg" to "見ません", "past_neg" to "見ませんでした", "want" to "見たいです", "req" to "見てください", "ing" to "見ています", "can" to "見られます", "must" to "見なければなりません"),
        "する" to mapOf("pres" to "します", "past" to "しました", "neg" to "しません", "past_neg" to "しませんでした", "want" to "したいです", "req" to "してください", "ing" to "しています", "can" to "できます", "must" to "しなければなりません"),
        "勉強する" to mapOf("pres" to "勉強します", "past" to "勉強しました", "neg" to "勉強しません", "past_neg" to "勉強しませんでした", "want" to "勉強したいです", "req" to "勉強してください", "ing" to "勉強しています", "can" to "勉強できます", "must" to "勉強しなければなりません"),
        "寝る" to mapOf("pres" to "寝ます", "past" to "寝ました", "neg" to "寝ません", "past_neg" to "寝ませんでした", "want" to "寝たいです", "req" to "寝てください", "ing" to "寝ています", "can" to "寝られます", "must" to "寝なければなりません"),
        "起きる" to mapOf("pres" to "起きます", "past" to "起きました", "neg" to "起きません", "past_neg" to "起きませんでした", "want" to "起きたいです", "req" to "起きてください", "ing" to "起きています", "can" to "起きられます", "must" to "起きなければなりません"),
        "会う" to mapOf("pres" to "会います", "past" to "会いました", "neg" to "会いません", "past_neg" to "会いませんでした", "want" to "会いたいです", "req" to "会ってください", "ing" to "会っています", "can" to "会えます", "must" to "会わなければなりません"),
        "待つ" to mapOf("pres" to "待ちます", "past" to "待ちました", "neg" to "待ちません", "past_neg" to "待ちませんでした", "want" to "待ちたいです", "req" to "待ってください", "ing" to "待っています", "can" to "待てます", "must" to "待たなければなりません")
    )

    fun translateTargetToJapanese(input: String, lang: AppLanguage): FullAnalysisResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return FullAnalysisResult(input, emptyList(), emptyList(), emptyList(), "", "")
        }

        val rawSentences = trimmed.split(Regex("(?<=[.!?\n])")).map { it.trim() }.filter { it.isNotEmpty() }
        val sentenceList = if (rawSentences.isEmpty()) listOf(trimmed) else rawSentences

        val japaneseSentences = mutableListOf<String>()
        for (s in sentenceList) {
            val jaSentence = if (lang == AppLanguage.VIETNAMESE) {
                translateSingleSentenceViToJa(s)
            } else {
                translateSingleSentenceEnToJa(s)
            }
            if (jaSentence.isNotBlank()) {
                japaneseSentences.add(jaSentence)
            }
        }

        val fullJapaneseText = japaneseSentences.joinToString(" ")
        // Đưa câu tiếng Nhật thu được qua bộ phân tích câu đầy đủ (Kuromoji + Grammar)
        val analysis = analyzeFull(fullJapaneseText)

        // Cập nhật câu gốc hiển thị là câu ban đầu của người dùng
        return analysis.copy(
            fullText = fullJapaneseText,
            fullTranslationVi = if (lang == AppLanguage.VIETNAMESE) trimmed else analysis.fullTranslationVi,
            fullTranslationEn = if (lang == AppLanguage.ENGLISH) trimmed else analysis.fullTranslationEn
        )
    }

    private fun translateSingleSentenceViToJa(sentence: String): String {
        val text = sentence.lowercase().trim()
        if (text.isEmpty()) return ""

        // 1. Nhận diện thể / thì (Aspect / Mood)
        var aspect = "pres"
        when {
            text.contains("xin hãy") || text.contains("hãy") || text.contains("làm ơn") -> aspect = "req"
            text.contains("muốn") -> aspect = "want"
            text.contains("không được") || text.contains("phải") -> aspect = "must"
            text.contains("có thể") -> aspect = "can"
            text.contains("đang") -> aspect = "ing"
            text.contains("đã") || text.contains("rồi") -> {
                aspect = if (text.contains("không") || text.contains("chưa")) "past_neg" else "past"
            }
            text.contains("không") || text.contains("chưa") -> aspect = "neg"
        }

        // Loại bỏ các từ chỉ thể / thì để bóc tách từ vựng chính xác
        val aspectKeywords = listOf(
            "chưa từng", "đã từng", "xin hãy", "không được", "có thể",
            "hãy", "làm ơn", "muốn", "phải", "đang", "không", "chưa", "đã", "rồi", "sẽ",
            "đến", "ở tại", "ở"
        )
        var cleaned = text
        for (kw in aspectKeywords) {
            cleaned = cleaned.replace(Regex("\\b$kw\\b"), " ")
        }
        val nonAlpha = Regex("[^\\p{L}\\p{Nd}\\s]")
        cleaned = cleaned.replace(nonAlpha, " ").replace(Regex("\\s+"), " ").trim()

        // 2. Longest-match segmentation từ vựng
        val words = cleaned.split(" ").filter { it.isNotBlank() }
        val n = words.size
        var i = 0
        val extractedTokens = mutableListOf<String>()

        while (i < n) {
            var matched = false
            val maxLen = minOf(4, n - i)
            for (l in maxLen downTo 1) {
                val phrase = words.subList(i, i + l).joinToString(" ")
                if (highPriorityWordsVi.containsKey(phrase)) {
                    extractedTokens.add(phrase)
                    i += l
                    matched = true
                    break
                }
            }
            if (!matched) {
                // Thử tra cứu từ điển mở rộng reverseDictVi
                for (l in maxLen downTo 1) {
                    val phrase = words.subList(i, i + l).joinToString(" ")
                    val foundList = reverseDictVi[phrase]
                    if (!foundList.isNullOrEmpty()) {
                        extractedTokens.add(phrase)
                        i += l
                        matched = true
                        break
                    }
                }
            }
            if (!matched) {
                i++
            }
        }

        // 3. Phân loại thành phần cú pháp SOV
        var timePart: String? = null
        var subjectPart: String? = null
        var placePart: String? = null
        var verbKey: String? = null
        var objectPart: String? = null
        var adjPart: String? = null

        for (phrase in extractedTokens) {
            val jaVal = highPriorityWordsVi[phrase] ?: reverseDictVi[phrase]?.firstOrNull() ?: continue
            when {
                phrase in timeVi && timePart == null -> timePart = jaVal
                phrase in subjectVi && subjectPart == null -> subjectPart = jaVal
                phrase in placeVi && placePart == null -> placePart = jaVal
                (phrase in transitiveVerbsVi || phrase in motionVerbsVi) && verbKey == null -> verbKey = phrase
                phrase in adjVi && adjPart == null -> adjPart = jaVal
                else -> {
                    if (objectPart == null && phrase !in subjectVi && phrase !in timeVi && phrase !in placeVi) {
                        objectPart = jaVal
                    }
                }
            }
        }

        // 4. Ghép câu theo trật tự SOV tiếng Nhật
        val parts = mutableListOf<String>()
        if (timePart != null) {
            parts.add(timePart)
        }
        if (subjectPart != null) {
            parts.add("${subjectPart}は")
        }
        if (placePart != null) {
            if (verbKey in motionVerbsVi) {
                parts.add("${placePart}へ")
            } else {
                parts.add("${placePart}で")
            }
        }
        if (objectPart != null) {
            if (verbKey == "gặp") {
                parts.add("${objectPart}に")
            } else {
                parts.add("${objectPart}を")
            }
        }

        if (verbKey != null) {
            val baseJa = highPriorityWordsVi[verbKey] ?: reverseDictVi[verbKey]?.firstOrNull() ?: verbKey
            val conjugated = politeVerbForms[baseJa]?.get(aspect)
                ?: if (baseJa.endsWith("する")) {
                    val stem = baseJa.dropLast(2)
                    stem + (politeVerbForms["する"]?.get(aspect) ?: "します")
                } else {
                    "${baseJa}ます"
                }
            parts.add(conjugated)
        } else if (adjPart != null) {
            parts.add("${adjPart}です")
        } else if (parts.isEmpty() && extractedTokens.isNotEmpty()) {
            // Trường hợp tra từ đơn lẻ
            val firstWord = extractedTokens.first()
            val ja = highPriorityWordsVi[firstWord] ?: reverseDictVi[firstWord]?.firstOrNull() ?: firstWord
            parts.add(ja)
        }

        val result = parts.joinToString("")
        return if (result.isNotBlank()) "$result。" else ""
    }

    private fun translateSingleSentenceEnToJa(sentence: String): String {
        val text = sentence.lowercase().trim()
        if (text.isEmpty()) return ""

        var aspect = "pres"
        when {
            text.contains("please") -> aspect = "req"
            text.contains("want to") -> aspect = "want"
            text.contains("must") || text.contains("have to") -> aspect = "must"
            text.contains("can") -> aspect = "can"
            text.contains("is ") || text.contains("are ") || text.contains("ing") -> aspect = "ing"
            text.contains("did not") || text.contains("didn't") -> aspect = "past_neg"
            text.contains("do not") || text.contains("don't") -> aspect = "neg"
            text.contains("did") || text.contains("ed ") -> aspect = "past"
        }

        val words = text.replace(Regex("[^a-zA-Z\\s]"), " ").split(" ").filter { it.isNotBlank() }
        val extractedTokens = mutableListOf<String>()
        var i = 0
        while (i < words.size) {
            var matched = false
            val maxLen = minOf(3, words.size - i)
            for (l in maxLen downTo 1) {
                val phrase = words.subList(i, i + l).joinToString(" ")
                if (highPriorityWordsEn.containsKey(phrase)) {
                    extractedTokens.add(phrase)
                    i += l
                    matched = true
                    break
                }
            }
            if (!matched) i++
        }

        var timePart: String? = null
        var subjectPart: String? = null
        var placePart: String? = null
        var verbPart: String? = null
        var objectPart: String? = null
        var adjPart: String? = null

        for (w in extractedTokens) {
            val ja = highPriorityWordsEn[w] ?: continue
            when (w) {
                "today", "tomorrow", "yesterday", "now", "every day" -> if (timePart == null) timePart = ja
                "i", "me", "you", "he", "she", "we", "they" -> if (subjectPart == null) subjectPart = ja
                "school", "home", "house", "company", "hospital", "station", "restaurant", "room", "park" -> if (placePart == null) placePart = ja
                "eat", "drink", "go", "come", "return", "buy", "sell", "read", "write", "listen", "speak", "watch", "see", "study", "do", "sleep", "wake up", "meet" -> if (verbPart == null) verbPart = ja
                "beautiful", "good", "delicious", "big", "small", "busy", "free", "hot", "cold", "like", "hate", "new", "old" -> if (adjPart == null) adjPart = ja
                else -> if (objectPart == null) objectPart = ja
            }
        }

        val parts = mutableListOf<String>()
        if (timePart != null) parts.add(timePart)
        if (subjectPart != null) parts.add("${subjectPart}は")
        if (placePart != null) {
            if (verbPart in listOf("行く", "来る", "帰る")) parts.add("${placePart}へ")
            else parts.add("${placePart}で")
        }
        if (objectPart != null) parts.add("${objectPart}を")
        if (verbPart != null) {
            val conj = politeVerbForms[verbPart]?.get(aspect) ?: "${verbPart}ます"
            parts.add(conj)
        } else if (adjPart != null) {
            parts.add("${adjPart}です")
        }

        val res = parts.joinToString("")
        return if (res.isNotBlank()) "$res。" else ""
    }
}
