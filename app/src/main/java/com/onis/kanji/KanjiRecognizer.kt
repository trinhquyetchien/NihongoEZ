package com.onis.kanji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

data class KanjiVocab(
    val word: String,
    val reading: String,
    val meaning: String
)

data class KanjiDetails(
    val meaning_vi: String?,
    val meaning_en: String?,
    val am_han: String?,
    val on_readings: List<String>,
    val kun_readings: List<String>,
    val stroke_count: Int?,
    val examples: List<String>,
    val vocabulary: List<KanjiVocab>,
    val explanation: String?,
    val image_urls: List<String>,
    val svg_url: String?,
    val mnemonic_comments: List<String>
)

data class KanjiPrediction(
    val kanji: String, 
    val confidence: Float,
    val details: KanjiDetails?
)

class KanjiRecognizer(private val context: Context) {
    private var module: Module? = null
    private val labelMap = mutableMapOf<Int, String>()

    init {
        loadModel()
        loadLabelMap()
    }

    private fun loadModel() {
        try {
            val file = assetFilePath(context, "kanji_model.ptl")
            module = LiteModuleLoader.load(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadLabelMap() {
        try {
            val reader = BufferedReader(InputStreamReader(context.assets.open("label_map.csv")))
            // Skip header if exists
            var isFirstLine = true
            reader.forEachLine { line ->
                if (isFirstLine) {
                    isFirstLine = false
                    return@forEachLine
                }
                val parts = line.split(",")
                if (parts.size >= 2) {
                    val kanjiChar = parts[0]
                    val labelId = parts[1].trim().toIntOrNull()
                    if (labelId != null) {
                        labelMap[labelId] = kanjiChar
                    }
                }
            }
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun assetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        val assetManager = context.assets
        val prefs = context.getSharedPreferences("kanji_prefs", Context.MODE_PRIVATE)
        val currentVersion = 2 // Increment this when updating the model asset
        val savedVersion = prefs.getInt("model_version", 0)
        
        var shouldCopy = !file.exists() || savedVersion < currentVersion
        
        if (shouldCopy) {
            try {
                assetManager.open(assetName).use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        val buffer = ByteArray(4 * 1024)
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                        }
                        outputStream.flush()
                    }
                }
                prefs.edit().putInt("model_version", currentVersion).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return file.absolutePath
    }

    fun recognize(bitmap: Bitmap): List<KanjiPrediction> {
        val model = module ?: return emptyList()

        // 1. Preprocess the 128x128 bitmap into a 1x1x128x128 tensor
        // Extract grayscale values, normalize with mean 0.5, std 0.5
        val floatArray = FloatArray(128 * 128)
        val pixels = IntArray(128 * 128)
        bitmap.getPixels(pixels, 0, 128, 0, 0, 128, 128)
        
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = android.graphics.Color.red(p) / 255.0f
            val g = android.graphics.Color.green(p) / 255.0f
            val b = android.graphics.Color.blue(p) / 255.0f
            val gray = (r * 0.299f + g * 0.587f + b * 0.114f)
            floatArray[i] = (gray - 0.5f) / 0.5f
        }

        // 2. Dummy stroke features tensor (1x24 zeros)
        val inputTensor = Tensor.fromBlob(floatArray, longArrayOf(1, 1, 128, 128))
        val strokeFeatures = Tensor.fromBlob(FloatArray(24) { 0f }, longArrayOf(1, 24))

        // 3. Inference
        val output = model.forward(IValue.from(inputTensor), IValue.from(strokeFeatures)).toTensor()
        val scores = output.dataAsFloatArray

        // 4. Softmax and Top 5
        return getTopK(scores, 5)
    }

    private fun loadKanjiDetails(kanji: String): KanjiDetails? {
        try {
            val fileStream = context.assets.open("metadata/$kanji.json")
            val jsonString = fileStream.bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(jsonString)
            
            val reading = json.optJSONObject("reading")
            val onReadings = mutableListOf<String>()
            val kunReadings = mutableListOf<String>()
            var amHan: String? = null
            if (reading != null) {
                amHan = reading.optString("am_han", null)
                val onArr = reading.optJSONArray("on")
                if (onArr != null) {
                    for (i in 0 until onArr.length()) onReadings.add(onArr.getString(i))
                }
                val kunArr = reading.optJSONArray("kun")
                if (kunArr != null) {
                    for (i in 0 until kunArr.length()) kunReadings.add(kunArr.getString(i))
                }
            }

            val meaning = json.optJSONObject("meaning")
            var meaningVi: String? = null
            var meaningEn: String? = null
            if (meaning != null) {
                meaningVi = meaning.optString("vi", null)
                meaningEn = meaning.optString("en", null)
            }

            val stroke = json.optJSONObject("stroke")
            val strokeCount = if (stroke != null && stroke.has("count")) stroke.getInt("count") else null
            var svgUrl: String? = null
            if (stroke != null && stroke.has("svg") && !stroke.isNull("svg")) {
                val rawPath = stroke.getString("svg").replace("assets/", "")
                svgUrl = rawPath
            }

            val examples = mutableListOf<String>()
            val exArr = json.optJSONArray("examples")
            if (exArr != null) {
                for (i in 0 until exArr.length()) examples.add(exArr.getString(i))
            }

            val vocabList = mutableListOf<KanjiVocab>()
            val vocabArr = json.optJSONArray("vocabulary")
            if (vocabArr != null) {
                for (i in 0 until vocabArr.length()) {
                    val vObj = vocabArr.optJSONObject(i)
                    if (vObj != null) {
                        val word = vObj.optString("word", "")
                        val reading = vObj.optString("reading", "")
                        val meaning = vObj.optString("meaning", "")
                        if (word.isNotBlank()) {
                            vocabList.add(KanjiVocab(word = word, reading = reading, meaning = meaning))
                        }
                    }
                }
            }

            var explanation: String? = null
            val expObj = json.optJSONObject("explanation")
            if (expObj != null) {
                explanation = expObj.optString("text", null)
            }

            val imageUrls = mutableListOf<String>()
            val mnemonicComments = mutableListOf<String>()
            val comments = json.optJSONArray("comments")
            if (comments != null) {
                for (i in 0 until comments.length()) {
                    val c = comments.getJSONObject(i)
                    if (c.has("content_text") && !c.isNull("content_text")) {
                        var text = c.getString("content_text").trim()
                        // Remove "JLPT N1/2/3/4/5" from the text
                        text = text.replace(Regex("(?i)JLPT\\s*N[1-5]\\s*\\n?"), "").trim()
                        if (text.isNotBlank()) mnemonicComments.add(text)
                    }
                    if (c.has("image_path") && !c.isNull("image_path")) {
                        var path = c.getString("image_path").replace("assets/", "")
                        path = path.replace(".jpg", ".webp").replace(".png", ".webp")
                        imageUrls.add("file:///android_asset/$path")
                    } else if (c.has("image_url") && !c.isNull("image_url")) {
                        imageUrls.add(c.getString("image_url"))
                    }
                }
            }

            return KanjiDetails(
                meaning_vi = meaningVi,
                meaning_en = meaningEn,
                am_han = amHan,
                on_readings = onReadings,
                kun_readings = kunReadings,
                stroke_count = strokeCount,
                examples = examples,
                vocabulary = vocabList,
                explanation = explanation,
                image_urls = imageUrls,
                svg_url = svgUrl,
                mnemonic_comments = mnemonicComments
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun getTopK(scores: FloatArray, k: Int): List<KanjiPrediction> {
        // Softmax
        var maxScore = Float.NEGATIVE_INFINITY
        for (score in scores) {
            if (score > maxScore) maxScore = score
        }
        var sum = 0.0f
        val expScores = FloatArray(scores.size)
        for (i in scores.indices) {
            val exp = kotlin.math.exp((scores[i] - maxScore).toDouble()).toFloat()
            expScores[i] = exp
            sum += exp
        }

        val indexedProbs = expScores.mapIndexed { index, fl -> index to (fl / sum) }
        val topK = indexedProbs.sortedByDescending { it.second }.take(k)

        return topK.map { (index, prob) ->
            val charStr = labelMap[index] ?: "?"
            KanjiPrediction(
                kanji = charStr,
                confidence = prob,
                details = loadKanjiDetails(charStr)
            )
        }
    }
}
