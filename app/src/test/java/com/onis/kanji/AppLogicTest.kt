package com.onis.kanji

import com.atilika.kuromoji.ipadic.Tokenizer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AppLogicTest {

    @Test
    fun testLabelMapLoading() {
        val labelMapFile = File("src/main/assets/label_map.csv")
        assertTrue("label_map.csv should exist", labelMapFile.exists())

        val labelMap = mutableMapOf<Int, String>()
        val reader = labelMapFile.bufferedReader(Charsets.UTF_8)
        var isFirstLine = true
        reader.forEachLine { line ->
            val cleanLine = line.trimStart('\uFEFF')
            if (isFirstLine) {
                isFirstLine = false
                return@forEachLine
            }
            val parts = cleanLine.split(",")
            if (parts.size >= 2) {
                val kanjiChar = parts[0].trim()
                val labelId = parts[1].trim().toIntOrNull()
                if (labelId != null) {
                    labelMap[labelId] = kanjiChar
                }
            }
        }
        reader.close()

        assertEquals("Should load all 3036 labels", 3036, labelMap.size)
        assertEquals("Label 0 should be あ", "あ", labelMap[0])
        assertEquals("Label 71 should be 一", "一", labelMap[71])
        assertEquals("Label 89 should be 中", "中", labelMap[89])
    }

    @Test
    fun testGrammarRulesAndCompactDictionary() {
        val grammarFile = File("src/main/assets/grammar_rules.json")
        assertTrue("grammar_rules.json should exist", grammarFile.exists())

        val jsonStr = grammarFile.readText(Charsets.UTF_8)
        val arr = JSONArray(jsonStr)
        assertTrue("Should have grammar rules", arr.length() > 0)

        val dictFile = File("src/main/assets/dictionary_compact.json")
        assertTrue("dictionary_compact.json should exist", dictFile.exists())
        val dictObj = JSONObject(dictFile.readText(Charsets.UTF_8))
        assertTrue("Dictionary should have vocabulary keys", dictObj.length() > 0)

        // Test Kuromoji tokenization
        val tokenizer = Tokenizer()
        val sentence = "私は日本語を勉強しています。明日学校へ行きます。"
        val tokens = tokenizer.tokenize(sentence)
        val surfaces = tokens.map { it.surface }
        assertTrue("Tokens should include 私", surfaces.contains("私"))
        assertTrue("Tokens should include 日本語", surfaces.contains("日本語"))
        assertTrue("Tokens should include 勉強", surfaces.contains("勉強"))

        // Test regex matching for grammar
        var matchedTeIru = false
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val regex = Regex(obj.getString("regex"))
            if (regex.containsMatchIn(sentence)) {
                if (obj.getString("pattern").contains("ています")) {
                    matchedTeIru = true
                }
            }
        }
        assertTrue("Should match ています grammar rule", matchedTeIru)
    }

    @Test
    fun testKanjiModelFilePresenceAndSize() {
        val modelFile = File("src/main/assets/kanji_model.ptl")
        assertTrue("kanji_model.ptl must exist in assets", modelFile.exists())
        assertTrue("kanji_model.ptl must not be empty (> 40MB)", modelFile.length() > 40 * 1024 * 1024)
    }

    @Test
    fun testMetadataPresence() {
        val metadataDir = File("src/main/assets/metadata")
        assertTrue("metadata directory must exist", metadataDir.exists() && metadataDir.isDirectory)
        val files = metadataDir.listFiles()
        assertNotNull(files)
        assertTrue("metadata should contain thousands of kanji files (> 2000)", files!!.size > 2000)

        val sunKanji = File(metadataDir, "日.json")
        assertTrue("日.json should exist", sunKanji.exists())
        val json = JSONObject(sunKanji.readText())
        assertEquals("NHẬT", json.getJSONObject("reading").getString("am_han"))
    }
}
