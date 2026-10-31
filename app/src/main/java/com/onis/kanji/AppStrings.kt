package com.onis.kanji

enum class AppLanguage {
    VIETNAMESE,
    ENGLISH
}

object AppStrings {
    fun appTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Onis Kanji"
        AppLanguage.ENGLISH -> "Onis Kanji"
    }

    fun tabDraw(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Nhận diện Kanji"
        AppLanguage.ENGLISH -> "Kanji Draw"
    }

    fun tabAnalyze(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Phân tích câu"
        AppLanguage.ENGLISH -> "Sentence Analyzer"
    }

    fun analyzeHeader(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Phân tích Từ & Ngữ pháp"
        AppLanguage.ENGLISH -> "Word & Grammar Analysis"
    }

    fun inputLabel(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Nhập câu hoặc văn bản tiếng Nhật"
        AppLanguage.ENGLISH -> "Enter Japanese text or sentence"
    }

    fun clearBtn(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Xóa"
        AppLanguage.ENGLISH -> "Clear"
    }

    fun analyzeBtn(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Phân tích"
        AppLanguage.ENGLISH -> "Analyze"
    }

    fun viewModeAll(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Toàn bộ văn bản"
        AppLanguage.ENGLISH -> "Full Text"
    }

    fun viewModeSentence(lang: AppLanguage, count: Int) = when (lang) {
        AppLanguage.VIETNAMESE -> "Từng câu ($count)"
        AppLanguage.ENGLISH -> "By Sentence ($count)"
    }

    fun sentenceChip(lang: AppLanguage, index: Int) = when (lang) {
        AppLanguage.VIETNAMESE -> "Câu $index"
        AppLanguage.ENGLISH -> "Sentence $index"
    }

    fun originalWithFurigana(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Nguyên văn (Kèm Furigana):"
        AppLanguage.ENGLISH -> "Original Text (with Furigana):"
    }

    fun translationHeader(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Bản dịch nghĩa:"
        AppLanguage.ENGLISH -> "Translation:"
    }

    fun selectedWordHeader(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Từ được chọn:"
        AppLanguage.ENGLISH -> "Selected Word:"
    }

    fun closeBtn(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "✕ Đóng"
        AppLanguage.ENGLISH -> "✕ Close"
    }

    fun grammarHeader(lang: AppLanguage, count: Int) = when (lang) {
        AppLanguage.VIETNAMESE -> "Cấu trúc ngữ pháp ($count)"
        AppLanguage.ENGLISH -> "Grammar Structures ($count)"
    }

    fun vocabHeader(lang: AppLanguage, count: Int) = when (lang) {
        AppLanguage.VIETNAMESE -> "Từ vựng chi tiết ($count)"
        AppLanguage.ENGLISH -> "Detailed Vocabulary ($count)"
    }

    fun baseForm(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Dạng gốc:"
        AppLanguage.ENGLISH -> "Base form:"
    }

    fun relatedVocab(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Từ vựng liên quan"
        AppLanguage.ENGLISH -> "Related Vocabulary"
    }

    fun detailsHeader(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Giải thích chi tiết"
        AppLanguage.ENGLISH -> "Detailed Explanation"
    }

    fun strokeOrder(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Thứ tự nét viết"
        AppLanguage.ENGLISH -> "Stroke Order"
    }

    fun descImage(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Ảnh mô tả"
        AppLanguage.ENGLISH -> "Illustration"
    }

    fun sinoVietnamese(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Âm Hán Việt"
        AppLanguage.ENGLISH -> "Sino-Vietnamese Reading"
    }

    fun meaningLabel(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Ý nghĩa"
        AppLanguage.ENGLISH -> "Meaning"
    }

    fun readingLabel(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Cách đọc"
        AppLanguage.ENGLISH -> "Readings"
    }

    fun recognizeBtn(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Nhận diện"
        AppLanguage.ENGLISH -> "Recognize"
    }

    fun recognitionResult(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Kết quả nhận diện"
        AppLanguage.ENGLISH -> "Recognition Results"
    }

    fun strokeCount(lang: AppLanguage, count: Int) = when (lang) {
        AppLanguage.VIETNAMESE -> "$count nét"
        AppLanguage.ENGLISH -> "$count strokes"
    }

    fun wordUsage(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Cách dùng & Ngữ cảnh"
        AppLanguage.ENGLISH -> "Usage & Context"
    }

    fun exampleSentences(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Ví dụ minh họa"
        AppLanguage.ENGLISH -> "Example Sentences"
    }

    fun onReadingsLabel(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Âm On (Katakana)"
        AppLanguage.ENGLISH -> "On Readings (Katakana)"
    }

    fun kunReadingsLabel(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Âm Kun (Hiragana)"
        AppLanguage.ENGLISH -> "Kun Readings (Hiragana)"
    }

    fun mnemonic(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Mẹo nhớ (Mnemonic)"
        AppLanguage.ENGLISH -> "Mnemonic Tips"
    }

    fun drawDialogTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Vẽ chữ Hán để chèn vào câu"
        AppLanguage.ENGLISH -> "Draw Kanji to insert into text"
    }

    fun clearCanvas(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Xóa nét"
        AppLanguage.ENGLISH -> "Clear"
    }

    fun undoStroke(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Hoàn tác"
        AppLanguage.ENGLISH -> "Undo"
    }

    fun tapToInsert(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "Chạm để chèn:"
        AppLanguage.ENGLISH -> "Tap to insert:"
    }

    fun tapWordToInspect(lang: AppLanguage) = when (lang) {
        AppLanguage.VIETNAMESE -> "💡 Chạm vào từng từ trong câu để xem chi tiết cách đọc & ý nghĩa"
        AppLanguage.ENGLISH -> "💡 Tap any word in the text to inspect reading & detailed meaning"
    }
}
