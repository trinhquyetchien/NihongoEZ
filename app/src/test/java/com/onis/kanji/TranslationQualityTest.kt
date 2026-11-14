package com.onis.kanji

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TranslationQualityTest {

    @Test
    fun testNaturalTranslationCases() {
        // Kiểm tra ngân hàng ngữ pháp mới
        val grammarFile = File("src/main/assets/grammar_rules.json")
        assertTrue(grammarFile.exists())
        val grammarArr = JSONArray(grammarFile.readText(Charsets.UTF_8))
        assertTrue("Grammar bank should have > 60 rules", grammarArr.length() >= 60)

        // Kiểm tra từ điển làm sạch và từ điển tiếng Anh
        val dictViFile = File("src/main/assets/dictionary_compact.json")
        val dictEnFile = File("src/main/assets/dictionary_en.json")
        assertTrue(dictViFile.exists())
        assertTrue(dictEnFile.exists())

        val dictVi = JSONObject(dictViFile.readText(Charsets.UTF_8))
        val dictEn = JSONObject(dictEnFile.readText(Charsets.UTF_8))

        // Từ vựng quan trọng phải chuẩn nghĩa
        assertEquals("tôi", dictVi.getString("私"))
        assertEquals("ăn", dictVi.getString("食べる"))
        assertEquals("uống", dictVi.getString("飲む"))
        assertEquals("I", dictEn.getString("私"))
        assertEquals("eat", dictEn.getString("食べる"))

        // Kiểm tra nhận diện mẫu ngữ pháp phong phú
        val patterns = (0 until grammarArr.length()).map { grammarArr.getJSONObject(it).getString("pattern") }
        assertTrue(patterns.any { it.contains("てはいけない") })
        assertTrue(patterns.any { it.contains("てもいい") })
        assertTrue(patterns.any { it.contains("たことがある") })
        assertTrue(patterns.any { it.contains("わけがない") })
        assertTrue(patterns.any { it.contains("にもかかわらず") })
    }
}
