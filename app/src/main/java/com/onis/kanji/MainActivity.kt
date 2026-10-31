package com.onis.kanji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.onis.kanji.ui.theme.KanjiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val KANJI_EXPORT_SIZE = 128
private const val KANJI_EXPORT_PADDING = 12f
private const val KANJI_EXPORT_STROKE_WIDTH = 10f
private const val KANJI_CANVAS_STROKE_WIDTH = 16f

enum class AppTab {
    DRAW_KANJI,
    TRANSLATE_ANALYZE
}

enum class AnalysisViewMode {
    ALL,      // Toàn bộ
    SENTENCE  // Từng câu
}

class KanjiViewModel : ViewModel() {
    var selectedTab by mutableStateOf(AppTab.DRAW_KANJI)

    // App Preferences: Language & Theme
    var appLanguage by mutableStateOf(AppLanguage.VIETNAMESE)
    var isDarkTheme by mutableStateOf(false)

    // Canvas State
    var points by mutableStateOf<List<List<Offset>>>(emptyList())
    var currentStroke by mutableStateOf<List<Offset>>(emptyList())
    var predictions by mutableStateOf<List<KanjiPrediction>>(emptyList())
    var isProcessing by mutableStateOf(false)
    var selectedKanji by mutableStateOf<String?>(null)

    // Translate & Sentence Analyzer State
    var inputText by mutableStateOf("")
    var fullResult by mutableStateOf<FullAnalysisResult?>(null)
    var isAnalyzing by mutableStateOf(false)
    var viewMode by mutableStateOf(AnalysisViewMode.ALL)
    var selectedSentenceIndex by mutableStateOf(0)

    // Drawing Dialog for Input Bar
    var showDrawDialog by mutableStateOf(false)
    var dialogPoints by mutableStateOf<List<List<Offset>>>(emptyList())
    var dialogCurrentStroke by mutableStateOf<List<Offset>>(emptyList())
    var dialogPredictions by mutableStateOf<List<KanjiPrediction>>(emptyList())
    var isDialogProcessing by mutableStateOf(false)

    // Quick Kanji Detail Modal
    var quickKanjiDetail by mutableStateOf<KanjiPrediction?>(null)

    // Quick Token Detail Modal (khi nhấn/bôi đen từ vựng)
    var selectedTokenForDetail by mutableStateOf<TokenAnalysis?>(null)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: KanjiViewModel = viewModel()
            KanjiTheme(darkTheme = viewModel.isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KanjiAppScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanjiAppScreen(viewModel: KanjiViewModel = viewModel()) {
    val context = LocalContext.current
    val recognizer = remember { KanjiRecognizer(context) }
    val analyzer = remember { SentenceAnalyzer(context) }
    val lang = viewModel.appLanguage

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = viewModel.selectedTab == AppTab.DRAW_KANJI,
                    onClick = { viewModel.selectedTab = AppTab.DRAW_KANJI },
                    icon = { Icon(Icons.Default.Create, contentDescription = AppStrings.tabDraw(lang)) },
                    label = { Text(AppStrings.tabDraw(lang)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = viewModel.selectedTab == AppTab.TRANSLATE_ANALYZE,
                    onClick = { viewModel.selectedTab = AppTab.TRANSLATE_ANALYZE },
                    icon = { Icon(Icons.Default.Search, contentDescription = AppStrings.tabAnalyze(lang)) },
                    label = { Text(AppStrings.tabAnalyze(lang)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (viewModel.selectedTab) {
                AppTab.DRAW_KANJI -> DrawKanjiScreen(viewModel, recognizer)
                AppTab.TRANSLATE_ANALYZE -> TranslateAnalyzeScreen(viewModel, recognizer, analyzer)
            }

            // Quick Kanji Detail Sheet
            viewModel.quickKanjiDetail?.let { candidate ->
                ModalBottomSheet(
                    onDismissRequest = { viewModel.quickKanjiDetail = null },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Text(
                                candidate.kanji,
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        item {
                            KanjiDetailsSection(candidate = candidate, lang = viewModel.appLanguage)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppHeaderControls(viewModel: KanjiViewModel) {
    val lang = viewModel.appLanguage
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "Onis Logo",
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(10.dp))
        Text(
            AppStrings.appTitle(lang),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.weight(1f))

        // Switch Theme: Light / Dark - Modern pill switch
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .clickable { viewModel.isDarkTheme = !viewModel.isDarkTheme }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    if (viewModel.isDarkTheme) "🌙" else "☀️",
                    fontSize = 13.sp
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (viewModel.isDarkTheme) "Tối" else "Sáng",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Switch Language: VI / EN - Modern pill switch
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .clickable {
                    viewModel.appLanguage = if (lang == AppLanguage.VIETNAMESE) AppLanguage.ENGLISH else AppLanguage.VIETNAMESE
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    if (lang == AppLanguage.VIETNAMESE) "🇻🇳" else "🇬🇧",
                    fontSize = 13.sp
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (lang == AppLanguage.VIETNAMESE) "VI" else "EN",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// TAB 1: NHẬN DIỆN KANJI QUA NÉT VẼ
// -------------------------------------------------------------------------
@Composable
fun DrawKanjiScreen(viewModel: KanjiViewModel, recognizer: KanjiRecognizer) {
    val coroutineScope = rememberCoroutineScope()
    val lang = viewModel.appLanguage

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(20.dp)
    ) {
        item {
            AppHeaderControls(viewModel = viewModel)
            Spacer(Modifier.height(14.dp))
        }

        item {
            // Canvas Area
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(8.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    viewModel.currentStroke = listOf(offset)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    viewModel.currentStroke = viewModel.currentStroke + change.position
                                },
                                onDragEnd = {
                                    if (viewModel.currentStroke.isNotEmpty()) {
                                        viewModel.points = viewModel.points + listOf(viewModel.currentStroke)
                                        viewModel.currentStroke = emptyList()
                                    }
                                }
                            )
                        }
                ) {
                    val gridPaint = androidx.compose.ui.graphics.Paint().apply {
                        color = Color(0xFFE0E0E0)
                        strokeWidth = 3f
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
                    }
                    drawContext.canvas.drawLine(Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), gridPaint)
                    drawContext.canvas.drawLine(Offset(0f, size.height / 2), Offset(size.width, size.height / 2), gridPaint)

                    val drawStroke = { stroke: List<Offset> ->
                        if (stroke.size == 1) {
                            drawCircle(
                                color = Color.Black,
                                radius = KANJI_CANVAS_STROKE_WIDTH / 2f,
                                center = stroke.first()
                            )
                        } else if (stroke.isNotEmpty()) {
                            val path = Path()
                            stroke.forEachIndexed { index, offset ->
                                if (index == 0) path.moveTo(offset.x, offset.y)
                                else path.lineTo(offset.x, offset.y)
                            }
                            drawPath(
                                path,
                                Color.Black,
                                style = Stroke(
                                    width = KANJI_CANVAS_STROKE_WIDTH,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    viewModel.points.forEach { drawStroke(it) }
                    drawStroke(viewModel.currentStroke)
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Nút Hoàn tác (Undo)
                OutlinedButton(
                    onClick = {
                        if (viewModel.points.isNotEmpty()) {
                            viewModel.points = viewModel.points.dropLast(1)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = viewModel.points.isNotEmpty()
                ) {
                    Text(
                        AppStrings.undoStroke(lang),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Nút Xóa (Clear)
                OutlinedButton(
                    onClick = {
                        viewModel.points = emptyList()
                        viewModel.currentStroke = emptyList()
                        viewModel.predictions = emptyList()
                        viewModel.selectedKanji = null
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        AppStrings.clearBtn(lang),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Nút Nhận diện
                Button(
                    onClick = {
                        if (viewModel.points.isEmpty() && viewModel.currentStroke.isEmpty()) return@Button
                        viewModel.isProcessing = true

                        val allStrokes = if (viewModel.currentStroke.isNotEmpty()) viewModel.points + listOf(viewModel.currentStroke) else viewModel.points

                        coroutineScope.launch {
                            val bitmap = withContext(Dispatchers.Default) {
                                renderStrokesToBitmap(allStrokes)
                            }
                            val result = withContext(Dispatchers.Default) {
                                recognizer.recognize(bitmap)
                            }
                            viewModel.predictions = result
                            if (result.isNotEmpty()) {
                                viewModel.selectedKanji = result.first().kanji
                            }
                            viewModel.isProcessing = false
                        }
                    },
                    modifier = Modifier.weight(1.3f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !viewModel.isProcessing
                ) {
                    if (viewModel.isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                    } else {
                        Text(
                            AppStrings.recognizeBtn(lang),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (viewModel.predictions.isNotEmpty()) {
            item {
                Spacer(Modifier.height(18.dp))
                Text(AppStrings.recognitionResult(lang), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(viewModel.predictions) { candidate ->
                        KanjiCandidateCard(
                            kanji = candidate.kanji,
                            confidence = candidate.confidence,
                            meaning = candidate.details?.meaning(lang) ?: "",
                            selected = viewModel.selectedKanji == candidate.kanji,
                            onClick = { viewModel.selectedKanji = candidate.kanji }
                        )
                    }
                }
            }

            viewModel.selectedKanji?.let { kanji ->
                val match = viewModel.predictions.find { it.kanji == kanji }
                if (match != null) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 2.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    kanji,
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    item {
                        KanjiDetailsSection(candidate = match, lang = lang)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// TAB 2: DỊCH & PHÂN TÍCH CÂU (NHẬP TAY HOẶC VẼ KANJI)
// -------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranslateAnalyzeScreen(
    viewModel: KanjiViewModel,
    recognizer: KanjiRecognizer,
    analyzer: SentenceAnalyzer
) {
    val coroutineScope = rememberCoroutineScope()
    val lang = viewModel.appLanguage

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(20.dp)
    ) {
        item {
            AppHeaderControls(viewModel = viewModel)
            Spacer(Modifier.height(14.dp))
        }

        item {
            Text(
                AppStrings.analyzeHeader(lang),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
        }

        item {
            // Thanh nhập văn bản có nút Vẽ Kanji
            OutlinedTextField(
                value = viewModel.inputText,
                onValueChange = { viewModel.inputText = it },
                label = { Text(AppStrings.inputLabel(lang)) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        viewModel.dialogPoints = emptyList()
                        viewModel.dialogPredictions = emptyList()
                        viewModel.showDrawDialog = true
                    }) {
                        Icon(Icons.Default.Create, contentDescription = AppStrings.drawDialogTitle(lang), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(12.dp))
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        viewModel.inputText = ""
                        viewModel.fullResult = null
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(AppStrings.clearBtn(lang))
                }

                Button(
                    onClick = {
                        if (viewModel.inputText.isBlank()) return@Button
                        viewModel.isAnalyzing = true
                        viewModel.selectedTokenForDetail = null
                        coroutineScope.launch {
                            try {
                                val res = withContext(Dispatchers.Default) {
                                    analyzer.analyzeFull(viewModel.inputText)
                                }
                                viewModel.fullResult = res
                                viewModel.selectedSentenceIndex = 0
                            } catch (e: Throwable) {
                                e.printStackTrace()
                            } finally {
                                viewModel.isAnalyzing = false
                            }
                        }
                    },
                    modifier = Modifier.weight(2f),
                    enabled = !viewModel.isAnalyzing && viewModel.inputText.isNotBlank()
                ) {
                    if (viewModel.isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text(AppStrings.analyzeBtn(lang))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        viewModel.fullResult?.let { result ->
            // Chọn Option: Dịch toàn bộ vs Dịch từng câu - Modern Segmented Control
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        // Nút Toàn bộ
                        val isAllSelected = viewModel.viewMode == AnalysisViewMode.ALL
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.viewMode = AnalysisViewMode.ALL },
                            color = if (isAllSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (isAllSelected) 2.dp else 0.dp
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
                            ) {
                                Text(
                                    AppStrings.viewModeAll(lang),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Nút Từng câu
                        val isSentenceSelected = viewModel.viewMode == AnalysisViewMode.SENTENCE
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.viewMode = AnalysisViewMode.SENTENCE },
                            color = if (isSentenceSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (isSentenceSelected) 2.dp else 0.dp
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
                            ) {
                                Text(
                                    AppStrings.viewModeSentence(lang, result.sentences.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSentenceSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSentenceSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // Nếu chọn mode từng câu -> Hiện danh sách chọn câu
            if (viewModel.viewMode == AnalysisViewMode.SENTENCE && result.sentences.size > 1) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(result.sentences.indices.toList()) { idx ->
                            InputChip(
                                selected = viewModel.selectedSentenceIndex == idx,
                                onClick = { viewModel.selectedSentenceIndex = idx },
                                label = { Text(AppStrings.sentenceChip(lang, idx + 1)) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // Hiển thị nguyên văn kèm Furigana phía trên chữ Kanji
            item {
                SelectionContainer {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                AppStrings.originalWithFurigana(lang),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(10.dp))

                            val tokensToRender = if (viewModel.viewMode == AnalysisViewMode.ALL) {
                                result.sentences.flatMap { it.tokens }
                            } else {
                                result.sentences.getOrNull(viewModel.selectedSentenceIndex)?.tokens ?: emptyList()
                            }

                            // FlowRow hiển thị từng token: Kanji có Furigana nhỏ ở trên, nhấn để xem thông tin từ
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                tokensToRender.forEach { token ->
                                    val isSelected = viewModel.selectedTokenForDetail?.surface == token.surface &&
                                            viewModel.selectedTokenForDetail?.baseForm == token.baseForm
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                            .clickable {
                                                viewModel.selectedTokenForDetail = if (isSelected) null else token
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        // Furigana
                                        if (token.hasKanji && token.reading.isNotBlank() && token.reading != token.surface) {
                                            Text(
                                                text = token.reading,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                lineHeight = 11.sp
                                            )
                                        } else {
                                            // Khoảng trống giữ hàng ngay ngắn
                                            Text(
                                                text = " ",
                                                fontSize = 11.sp,
                                                lineHeight = 11.sp
                                            )
                                        }
                                        // Surface text (Kanji / Kana)
                                        Text(
                                            text = token.surface,
                                            fontSize = 17.sp,
                                            fontWeight = if (token.hasKanji) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Text(
                                AppStrings.tapWordToInspect(lang),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // Bản dịch (Full text translation khi chọn ALL, hoặc Sentence translation khi chọn SENTENCE)
            item {
                val translationText = if (viewModel.viewMode == AnalysisViewMode.ALL) {
                    result.fullTranslation(lang)
                } else {
                    result.sentences.getOrNull(viewModel.selectedSentenceIndex)?.translation(lang) ?: ""
                }

                if (translationText.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    AppStrings.translationHeader(lang),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.weight(1f))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        if (lang == AppLanguage.VIETNAMESE) "Tiếng Việt" else "English",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = translationText,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }

            // Ô thông tin từ vựng khi người dùng chọn/bôi đen từ ở đoạn văn phía trên
            viewModel.selectedTokenForDetail?.let { selectedToken ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    AppStrings.selectedWordHeader(lang),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.weight(1f))
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { viewModel.selectedTokenForDetail = null }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        AppStrings.closeBtn(lang),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            SingleLineTokenCard(index = 1, token = selectedToken, lang = lang, showFullDetails = true)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Dữ liệu ngữ pháp và từ vựng hiển thị tùy theo viewMode (không trùng nhau)
            val grammarsToShow = if (viewModel.viewMode == AnalysisViewMode.ALL) {
                result.allUniqueGrammars
            } else {
                result.sentences.getOrNull(viewModel.selectedSentenceIndex)?.grammars ?: emptyList()
            }

            val tokensToShow = if (viewModel.viewMode == AnalysisViewMode.ALL) {
                result.allUniqueTokens
            } else {
                result.sentences.getOrNull(viewModel.selectedSentenceIndex)?.tokens
                    ?.filter { it.surface.isNotBlank() && !it.surface.all { c -> c in "。、！？,.!?\n\r\t " } }
                    ?.distinctBy { "${it.baseForm}_${it.surface}" } ?: emptyList()
            }

            // Phần 1: Ngữ pháp tìm thấy
            if (grammarsToShow.isNotEmpty()) {
                item {
                    SectionCardV2 {
                        Text(
                            AppStrings.grammarHeader(lang, grammarsToShow.size),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            grammarsToShow.forEach { g ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    g.level,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                g.pattern,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            g.meaning(lang),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Phần 2: Từ vựng không trùng lặp (Hiển thị chi tiết rõ ràng)
            if (tokensToShow.isNotEmpty()) {
                item {
                    SectionCardV2 {
                        Text(
                            AppStrings.vocabHeader(lang, tokensToShow.size),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            tokensToShow.forEachIndexed { index, token ->
                                SingleLineTokenCard(index = index + 1, token = token, lang = lang)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    // Modal Dialog Vẽ Kanji để chèn chữ vào thanh nhập
    if (viewModel.showDrawDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showDrawDialog = false },
            title = { Text(AppStrings.drawDialogTitle(lang)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier
                            .size(240.dp)
                            .shadow(4.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            viewModel.dialogCurrentStroke = listOf(offset)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            viewModel.dialogCurrentStroke = viewModel.dialogCurrentStroke + change.position
                                        },
                                        onDragEnd = {
                                            if (viewModel.dialogCurrentStroke.isNotEmpty()) {
                                                viewModel.dialogPoints = viewModel.dialogPoints + listOf(viewModel.dialogCurrentStroke)
                                                viewModel.dialogCurrentStroke = emptyList()
                                            }
                                        }
                                    )
                                }
                        ) {
                            val gridPaint = androidx.compose.ui.graphics.Paint().apply {
                                color = Color(0xFFE0E0E0)
                                strokeWidth = 2f
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                            }
                            drawLine(Color(0xFFE0E0E0), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 2f)
                            drawLine(Color(0xFFE0E0E0), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2f)

                            val drawStroke = { stroke: List<Offset> ->
                                if (stroke.size == 1) {
                                    drawCircle(
                                        color = Color.Black,
                                        radius = KANJI_CANVAS_STROKE_WIDTH / 2f,
                                        center = stroke.first()
                                    )
                                } else if (stroke.isNotEmpty()) {
                                    val path = Path()
                                    stroke.forEachIndexed { index, offset ->
                                        if (index == 0) path.moveTo(offset.x, offset.y)
                                        else path.lineTo(offset.x, offset.y)
                                    }
                                    drawPath(
                                        path,
                                        Color.Black,
                                        style = Stroke(width = KANJI_CANVAS_STROKE_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                }
                            }
                            viewModel.dialogPoints.forEach { drawStroke(it) }
                            drawStroke(viewModel.dialogCurrentStroke)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.dialogPoints = emptyList()
                                viewModel.dialogCurrentStroke = emptyList()
                                viewModel.dialogPredictions = emptyList()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(AppStrings.clearCanvas(lang))
                        }
                        Button(
                            onClick = {
                                if (viewModel.dialogPoints.isEmpty() && viewModel.dialogCurrentStroke.isEmpty()) return@Button
                                viewModel.isDialogProcessing = true
                                val strokes = if (viewModel.dialogCurrentStroke.isNotEmpty()) viewModel.dialogPoints + listOf(viewModel.dialogCurrentStroke) else viewModel.dialogPoints
                                coroutineScope.launch {
                                    val bmp = withContext(Dispatchers.Default) { renderStrokesToBitmap(strokes) }
                                    val preds = withContext(Dispatchers.Default) { recognizer.recognize(bmp) }
                                    viewModel.dialogPredictions = preds
                                    viewModel.isDialogProcessing = false
                                }
                            },
                            modifier = Modifier.weight(1.2f),
                            enabled = !viewModel.isDialogProcessing
                        ) {
                            if (viewModel.isDialogProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                            } else {
                                Text(AppStrings.recognizeBtn(lang))
                            }
                        }
                    }

                    if (viewModel.dialogPredictions.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(AppStrings.tapToInsert(lang), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(viewModel.dialogPredictions) { pred ->
                                Surface(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clickable {
                                            viewModel.inputText += pred.kanji
                                            viewModel.showDrawDialog = false
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            pred.kanji,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.showDrawDialog = false }) {
                    Text(AppStrings.closeBtn(lang))
                }
            }
        )
    }
}

// -------------------------------------------------------------------------
// COMPONENT TỪ VỰNG (HIỂN THỊ CHI TIẾT TỪ VỰNG, CÁCH DÙNG & VÍ DỤ MINH HỌA)
// -------------------------------------------------------------------------
@Composable
fun SingleLineTokenCard(
    index: Int,
    token: TokenAnalysis,
    lang: AppLanguage = AppLanguage.VIETNAMESE,
    showFullDetails: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                "$index.",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .width(26.dp)
                    .padding(top = 2.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Hàng 1: Từ (surface) + cách đọc (reading) + loại từ (POS tag)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        token.surface,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (token.reading.isNotBlank() && token.reading != token.surface) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "[${token.reading}]",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!token.amHan.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "(${token.amHan})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    val posLabel = token.pos(lang)
                    if (posLabel.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                posLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Hàng 2 (nếu có thể nguyên thể / baseForm khác surface): Base form
                if (token.baseForm.isNotBlank() && token.baseForm != "*" && token.baseForm != token.surface) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${AppStrings.baseForm(lang)} ${token.baseForm}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Hàng 3: Ý nghĩa từ vựng chi tiết
                val meaningText = token.meaning(lang)
                if (!meaningText.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        meaningText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Hàng 4 & 5 (khi chạm xem chi tiết từ vựng): Cách dùng (Usage) & Câu ví dụ kèm Furigana
                if (showFullDetails) {
                    val usage = token.usage(lang)
                    if (!usage.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    "💡 ${AppStrings.wordUsage(lang)}:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    usage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (token.examples.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "📝 ${AppStrings.exampleSentences(lang)}:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            token.examples.forEach { ex ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        // Câu tiếng Nhật
                                        Text(
                                            ex.japanese,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        // Cách đọc Hiragana
                                        if (ex.reading.isNotBlank() && ex.reading != ex.japanese) {
                                            Text(
                                                "[${ex.reading}]",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        // Bản dịch nghĩa
                                        val exTrans = if (lang == AppLanguage.ENGLISH) ex.translationEn else ex.translationVi
                                        if (exTrans.isNotBlank()) {
                                            Text(
                                                "→ $exTrans",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// REUSABLE COMPONENTS & UTILS
// -------------------------------------------------------------------------
fun renderStrokesToBitmap(strokes: List<List<Offset>>): Bitmap {
    val bmp = Bitmap.createBitmap(KANJI_EXPORT_SIZE, KANJI_EXPORT_SIZE, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = KANJI_EXPORT_STROKE_WIDTH
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    val allPoints = strokes.flatten()
    if (allPoints.isNotEmpty()) {
        val minX = allPoints.minOf { it.x }
        val maxX = allPoints.maxOf { it.x }
        val minY = allPoints.minOf { it.y }
        val maxY = allPoints.maxOf { it.y }

        val drawWidth = maxX - minX
        val drawHeight = maxY - minY
        val targetSize = KANJI_EXPORT_SIZE.toFloat()
        val scale = (targetSize - (KANJI_EXPORT_PADDING * 2f)) / maxOf(drawWidth, drawHeight, 1f)
        val offsetX = (targetSize - drawWidth * scale) / 2f
        val offsetY = (targetSize - drawHeight * scale) / 2f

        strokes.forEach { stroke ->
            if (stroke.size == 1) {
                val point = stroke.first()
                val px = (point.x - minX) * scale + offsetX
                val py = (point.y - minY) * scale + offsetY
                canvas.drawCircle(px, py, KANJI_EXPORT_STROKE_WIDTH / 2f, paint)
            } else {
                val androidPath = android.graphics.Path()
                stroke.forEachIndexed { index, offset ->
                    val px = (offset.x - minX) * scale + offsetX
                    val py = (offset.y - minY) * scale + offsetY
                    if (index == 0) androidPath.moveTo(px, py)
                    else androidPath.lineTo(px, py)
                }
                canvas.drawPath(androidPath, paint)
            }
        }
    }
    return bmp
}

@Composable
fun KanjiCandidateCard(kanji: String, confidence: Float, meaning: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(80.dp, 90.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                kanji,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (meaning.isNotBlank()) meaning.take(8) else "${(confidence * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SectionCardV2(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun InfoBlock(title: String, content: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(content, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun VocabularySection(vocabulary: List<KanjiVocab>, lang: AppLanguage = AppLanguage.VIETNAMESE) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            AppStrings.relatedVocab(lang),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            vocabulary.forEachIndexed { index, vocab ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .width(22.dp)
                                .padding(top = 2.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    vocab.word,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (vocab.reading.isNotBlank()) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "[${vocab.reading}]",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (vocab.meaning.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    vocab.meaning,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KanjiDetailsSection(candidate: KanjiPrediction, lang: AppLanguage = AppLanguage.VIETNAMESE) {
    val details = candidate.details ?: return

    Spacer(Modifier.height(16.dp))
    SectionCardV2 {
        Text(AppStrings.detailsHeader(lang), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoBlock(AppStrings.sinoVietnamese(lang), details.am_han ?: "-")
            InfoBlock(AppStrings.meaningLabel(lang), details.meaning(lang) ?: "-")

            if (details.on_readings.isNotEmpty() || details.kun_readings.isNotEmpty()) {
                val readings = mutableListOf<String>()
                if (details.on_readings.isNotEmpty()) readings.add("On: ${details.on_readings.joinToString()}")
                if (details.kun_readings.isNotEmpty()) readings.add("Kun: ${details.kun_readings.joinToString()}")
                InfoBlock(AppStrings.readingLabel(lang), readings.joinToString("\n"))
            }

            if (lang == AppLanguage.VIETNAMESE && !details.explanation.isNullOrBlank()) {
                InfoBlock(AppStrings.detailsHeader(lang), details.explanation)
            }

            if (details.vocabulary.isNotEmpty()) {
                VocabularySection(vocabulary = details.vocabulary, lang = lang)
            }

            if (lang == AppLanguage.VIETNAMESE && details.mnemonic_comments.isNotEmpty()) {
                InfoBlock(AppStrings.mnemonic(lang), details.mnemonic_comments.joinToString("\n\n") { "• $it" })
            }
        }
    }

    details.svg_url?.let { svgUrl ->
        Spacer(Modifier.height(16.dp))
        SectionCardV2 {
            Text(AppStrings.strokeOrder(lang), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            AnimatedKanjiSvg(
                svgUrl = svgUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
            )
        }
    }

    if (details.image_urls.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        SectionCardV2 {
            Text(AppStrings.descImage(lang), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                details.image_urls.forEach { imageUrl ->
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Kanji mnemonic image for ${candidate.kanji}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedKanjiSvg(svgUrl: String?, modifier: Modifier = Modifier) {
    if (svgUrl == null) {
        Box(modifier = modifier.background(Color.White), contentAlignment = Alignment.Center) {
            Text("SVG not available", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val context = LocalContext.current
    val svgImageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }

    var svgData by remember { mutableStateOf<ByteArray?>(null) }
    var svgError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(svgUrl) {
        if (!svgUrl.isNullOrBlank()) {
            try {
                svgData = withContext(Dispatchers.IO) {
                    val rawSvg = String(context.assets.open(svgUrl).readBytes(), Charsets.UTF_8)
                    val fixedSvg = if (!rawSvg.contains("xmlns:kvg")) {
                        rawSvg.replaceFirst("<svg ", "<svg xmlns:kvg=\"http://kanjivg.tagaini.net\" ")
                    } else rawSvg
                    fixedSvg.toByteArray(Charsets.UTF_8)
                }
            } catch (e: Exception) {
                svgError = e.message
                android.util.Log.e("AnimatedKanjiSvg", "Error loading SVG from assets: $svgUrl", e)
            }
        }
    }

    if (svgError != null) {
        Box(modifier = modifier.background(Color.White), contentAlignment = Alignment.Center) {
            Text("Lỗi: $svgError", color = Color.Red, modifier = Modifier.padding(8.dp))
        }
    } else if (svgData == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        SubcomposeAsyncImage(
            imageLoader = svgImageLoader,
            model = ImageRequest.Builder(context)
                .data(svgData)
                .crossfade(false)
                .build(),
            contentDescription = "Stroke order SVG",
            modifier = modifier.background(Color.White),
            contentScale = ContentScale.Fit,
            loading = {
                Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
            error = { state ->
                val err = state.result.throwable.message ?: "Unknown Coil Error"
                android.util.Log.e("AnimatedKanjiSvg", "Coil error: $err", state.result.throwable)
                Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                    Text("Không hiển thị được thứ tự nét: $err", color = Color.Red, modifier = Modifier.padding(8.dp))
                }
            }
        )
    }
}
