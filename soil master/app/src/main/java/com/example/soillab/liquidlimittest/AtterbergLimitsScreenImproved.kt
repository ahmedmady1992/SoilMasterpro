package com.example.soillab.liquidlimittest

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soillab.R
import com.example.soillab.data.*
import com.example.soillab.ui.components.*
import com.example.soillab.ui.theme.Green500
import com.example.soillab.ui.theme.Yellow500
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt


// --- UI State ---
data class AtterbergUiState(
    val llSamples: List<LiquidLimitSample> = emptyList(),
    val plSamples: List<PlasticLimitSample> = emptyList(),
    val llSampleInput: LiquidLimitSample = LiquidLimitSample(),
    val plSampleInput: PlasticLimitSample = PlasticLimitSample(),
    val testInfo: TestInfo = TestInfo(),
    val calculationResult: CalculationResult? = null,
    val isLoading: Boolean = false,
    val validationResult: ValidationResult? = null,
    val currentReportId: String? = null
)

// --- ViewModel ---
class AtterbergCoreViewModel(private val repository: IReportRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AtterbergUiState())
    val uiState = _uiState.asStateFlow()

    private val _userMessage = MutableSharedFlow<String?>()
    val userMessage = _userMessage.asSharedFlow()

    private val validator = AISoilDataValidator()
    private val classifier = AdvancedSoilClassifier()

    fun loadReportForEditing(reportId: String?) {
        if (reportId == null) {
            _uiState.value = AtterbergUiState()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getAtterbergReportById(reportId)?.let { report ->
                _uiState.update {
                    it.copy(
                        testInfo = report.testInfo,
                        llSamples = report.llSamples,
                        plSamples = report.plSamples,
                        calculationResult = report.calculationResult,
                        currentReportId = report.id,
                        isLoading = false
                    )
                }
            } ?: _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun saveReport(context: Context) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.calculationResult == null || state.testInfo.boreholeNo.isBlank()) {
                _userMessage.emit(context.getString(R.string.error_fill_info_and_compute))
                return@launch
            }
            _uiState.update { it.copy(isLoading = true) }
            val reportId = withContext(Dispatchers.IO) {
                val report = AtterbergReport(
                    id = state.currentReportId ?: UUID.randomUUID().toString(),
                    testInfo = state.testInfo,
                    llSamples = state.llSamples,
                    plSamples = state.plSamples,
                    calculationResult = state.calculationResult
                )
                repository.saveAtterbergReport(report)
                report.id
            }
            _uiState.update { it.copy(currentReportId = reportId, isLoading = false) }
            _userMessage.emit(context.getString(R.string.success_report_saved))
        }
    }

    fun onTestInfoChange(newInfo: TestInfo) { _uiState.update { it.copy(testInfo = newInfo) } }
    fun onLLBlowsChange(blows: String) { _uiState.update { it.copy(llSampleInput = it.llSampleInput.copy(blows = blows)) } }
    fun onLLWaterContentChange(wc: String) { _uiState.update { it.copy(llSampleInput = it.llSampleInput.copy(waterContent = wc)) } }
    fun onPLWaterContentChange(wc: String) { _uiState.update { it.copy(plSampleInput = it.plSampleInput.copy(waterContent = wc)) } }

    fun addLLSample() {
        val validation = validator.validateLiquidLimitSample(_uiState.value.llSampleInput)
        if (validation.isValid) {
            _uiState.update {
                val newList = it.llSamples + it.llSampleInput.copy(id = (it.llSamples.maxOfOrNull { s -> s.id } ?: 0) + 1)
                it.copy(llSamples = newList, llSampleInput = LiquidLimitSample())
            }
        } else {
            viewModelScope.launch { _userMessage.emit(validation.errors.firstOrNull()?.message) }
        }
    }

    fun removeLLSample(sample: LiquidLimitSample) { _uiState.update { it.copy(llSamples = it.llSamples - sample) } }
    fun addPLSample() {
        val validation = validator.validatePlasticLimitSample(_uiState.value.plSampleInput)
        if (validation.isValid) {
            _uiState.update {
                val newList = it.plSamples + it.plSampleInput.copy(id = (it.plSamples.maxOfOrNull { s -> s.id } ?: 0) + 1)
                it.copy(plSamples = newList, plSampleInput = PlasticLimitSample())
            }
        } else {
            viewModelScope.launch { _userMessage.emit(validation.errors.firstOrNull()?.message) }
        }
    }
    fun removePLSample(sample: PlasticLimitSample) { _uiState.update { it.copy(plSamples = it.plSamples - sample) } }

    fun computeResults() {
        val llData = _uiState.value.llSamples
        val plData = _uiState.value.plSamples
        if (llData.isEmpty() || plData.isEmpty()) {
            viewModelScope.launch { _userMessage.emit("Please add at least one LL and one PL sample.") }
            return
        }

        val plValue = plData.mapNotNull { it.waterContent.toFloatOrNull() }.average().toFloat()

        // One-point method if only one LL sample
        if (llData.size == 1) {
            val sample = llData.first()
            val n = sample.blows.toIntOrNull()
            val w = sample.waterContent.toFloatOrNull()
            if (n == null || w == null) return

            val ll = w * (n / 25.0).pow(0.121)
            val pi = ll - plValue
            val result = CalculationResult(
                liquidLimit = ll.toFloat(),
                plasticLimit = plValue,
                plasticityIndex = pi.toFloat(),
                points = llData.mapNotNull { DataPoint(it.blows.toFloatOrNull() ?: 0f, it.waterContent.toFloatOrNull() ?: 0f) },
                calculationMethod = "One-Point Method (ASTM D4318)"
            )
            val classification = classifier.classifySoilAdvanced(result.liquidLimit, result.plasticityIndex, emptyList(), null)
            _uiState.update { it.copy(calculationResult = result.copy(soilClassification = classification.basicClassification.symbol, advancedAnalysis = classification)) }
            return
        }

        // Multi-point method (linear regression on log scale)
        val points = llData.mapNotNull {
            val blows = it.blows.toFloatOrNull()
            val wc = it.waterContent.toFloatOrNull()
            if (blows != null && wc != null) Pair(log10(blows), wc) else null
        }
        if (points.size < 2) return

        val (slope, intercept, rSquared) = linearRegression(points)
        val ll = (slope * log10(25f)) + intercept
        val pi = ll - plValue

        val bestFitLine = listOf(
            Entry(10f, (slope * log10(10f) + intercept)),
            Entry(40f, (slope * log10(40f) + intercept))
        )

        val result = CalculationResult(
            liquidLimit = ll,
            plasticLimit = plValue,
            plasticityIndex = pi,
            points = llData.mapNotNull { DataPoint(it.blows.toFloatOrNull() ?: 0f, it.waterContent.toFloatOrNull() ?: 0f) },
            bestFitLine = bestFitLine,
            correlationCoefficient = rSquared,
            calculationMethod = "Multi-Point Method (ASTM D4318)"
        )
        val classification = classifier.classifySoilAdvanced(result.liquidLimit, result.plasticityIndex, points.map { it.first to it.second }, rSquared)
        _uiState.update { it.copy(calculationResult = result.copy(soilClassification = classification.basicClassification.symbol, advancedAnalysis = classification)) }
    }

    private fun linearRegression(points: List<Pair<Float, Float>>): Triple<Float, Float, Float> {
        val n = points.size
        val sumX = points.sumOf { it.first.toDouble() }
        val sumY = points.sumOf { it.second.toDouble() }
        val sumXY = points.sumOf { (it.first * it.second).toDouble() }
        val sumX2 = points.sumOf { it.first.pow(2).toDouble() }
        val sumY2 = points.sumOf { it.second.pow(2).toDouble() }

        val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        val intercept = (sumY - slope * sumX) / n

        val r = (n * sumXY - sumX * sumY) / sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY))
        val rSquared = r * r

        return Triple(slope.toFloat(), intercept.toFloat(), rSquared.toFloat())
    }

    fun loadExampleData(context: Context) {
        viewModelScope.launch {
            val example = AtterbergExampleDataGenerator.generate()
            _uiState.update {
                it.copy(
                    llSamples = example.llSamples,
                    plSamples = example.plSamples,
                    testInfo = it.testInfo.copy(sampleDescription = context.getString(example.descriptionResId)),
                    calculationResult = null
                )
            }
            _userMessage.emit(context.getString(R.string.example_data_loaded))
        }
    }
}


// --- Main Screen Composable ---
@Composable
fun AtterbergLimitsScreenImproved(
    viewModel: AtterbergCoreViewModel,
    reportIdToLoad: String?,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(reportIdToLoad) {
        viewModel.loadReportForEditing(reportIdToLoad)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            TestInfoSection(uiState.testInfo, viewModel::onTestInfoChange)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LiquidLimitInput(
                    modifier = Modifier.weight(1.5f),
                    sampleInput = uiState.llSampleInput,
                    samples = uiState.llSamples,
                    onBlowsChange = viewModel::onLLBlowsChange,
                    onWaterContentChange = viewModel::onLLWaterContentChange,
                    onAddSample = viewModel::addLLSample,
                    onRemoveSample = viewModel::removeLLSample
                )
                PlasticLimitInput(
                    modifier = Modifier.weight(1f),
                    sampleInput = uiState.plSampleInput,
                    samples = uiState.plSamples,
                    onWaterContentChange = viewModel::onPLWaterContentChange,
                    onAddSample = viewModel::addPLSample,
                    onRemoveSample = viewModel::removePLSample
                )
            }

            ActionButtons(onCompute = viewModel::computeResults, onLoadExample = { viewModel.loadExampleData(context) })

            // Replaced .let with if (result != null) to fix invocation error
            if (uiState.calculationResult != null) {
                Column {
                    ResultDashboard(uiState.calculationResult!!)
                }
            } else if (!uiState.isLoading) {
                InfoPanel(stringResource(R.string.info_awaiting_data_atterberg))
            }

            Spacer(Modifier.height(32.dp))
        }

        AnimatedVisibility(
            visible = uiState.isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}


@Composable
fun LiquidLimitInput(
    modifier: Modifier = Modifier,
    sampleInput: LiquidLimitSample,
    samples: List<LiquidLimitSample>,
    onBlowsChange: (String) -> Unit,
    onWaterContentChange: (String) -> Unit,
    onAddSample: () -> Unit,
    onRemoveSample: (LiquidLimitSample) -> Unit
) {
    DataPanel(title = stringResource(R.string.liquid_limit_input)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuralInput(sampleInput.blows, onBlowsChange, stringResource(R.string.blows), Modifier.weight(1f), keyboardType = KeyboardType.Number)
            NeuralInput(sampleInput.waterContent, onWaterContentChange, stringResource(R.string.water_content_percent), Modifier.weight(1f))
            Button(onClick = onAddSample, modifier = Modifier.padding(top = 20.dp), enabled = sampleInput.blows.isNotBlank() && sampleInput.waterContent.isNotBlank()) {
                Icon(Icons.Default.Add, stringResource(R.string.add_ll_sample))
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
            itemsIndexed(samples) { _, sample ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("N=${sample.blows}, w=${sample.waterContent}%", color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemoveSample(sample) }) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun PlasticLimitInput(
    modifier: Modifier = Modifier,
    sampleInput: PlasticLimitSample,
    samples: List<PlasticLimitSample>,
    onWaterContentChange: (String) -> Unit,
    onAddSample: () -> Unit,
    onRemoveSample: (PlasticLimitSample) -> Unit
) {
    DataPanel(title = stringResource(R.string.plastic_limit_input)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuralInput(sampleInput.waterContent, onWaterContentChange, stringResource(R.string.water_content_percent), Modifier.weight(1f))
            Button(onClick = onAddSample, modifier = Modifier.padding(top = 20.dp), enabled = sampleInput.waterContent.isNotBlank()) {
                Icon(Icons.Default.Add, stringResource(R.string.add_pl_sample))
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
            itemsIndexed(samples) { _, sample ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("w=${sample.waterContent}%", color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemoveSample(sample) }) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ResultDashboard(result: CalculationResult) {
    Column {
        DataPanel(title = stringResource(R.string.analysis_results)) {
            ResultDisplay(stringResource(R.string.liquid_limit), result.liquidLimit?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A", isPrimary = true)
            ResultDisplay(stringResource(R.string.plastic_limit), result.plasticLimit?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A", isPrimary = true)
            ResultDisplay(stringResource(R.string.plasticity_index), result.plasticityIndex?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A", isPrimary = true)
            ResultDisplay(stringResource(R.string.soil_classification), result.soilClassification, isPrimary = true)
            result.correlationCoefficient?.let {
                ResultDisplay(stringResource(R.string.correlation_r2), String.format(Locale.US, "%.3f", it))
            }
        }

        FlowCurveChart(result.points, result.bestFitLine)
        PlasticityChart(result.liquidLimit, result.plasticityIndex)

        result.advancedAnalysis?.let { AdvancedInsightsPanel(it) }
    }
}

@Composable
fun AdvancedInsightsPanel(analysis: AdvancedClassificationResult) {
    DataPanel(title = stringResource(R.string.advanced_geo_insights)) {
        Text(stringResource(R.string.behavior_analysis), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        InsightCard(Icons.Default.Info, "Swell Potential", analysis.behaviorAnalysis.swellPotential.name)
        InsightCard(Icons.Default.Info, "Compressibility", analysis.behaviorAnalysis.compressibility.name)

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.recommendations), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        analysis.constructionRecommendations.forEach {
            InsightCard(Icons.Default.Checklist, stringResource(it.categoryResId), stringResource(it.recommendationResId))
        }
    }
}

@Composable
fun FlowCurveChart(points: List<DataPoint>, bestFitLine: List<Entry>) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current

    AndroidView(
        factory = {
            LineChart(context).apply {
                description.text = context.getString(R.string.flow_curve_analysis)
                description.textColor = onSurfaceVariant.toArgb()
                setBackgroundColor(Color.Transparent.toArgb())
                setNoDataText("No data for flow curve.")
                setNoDataTextColor(onSurfaceVariant.toArgb())

                xAxis.apply {
                    textColor = onSurfaceVariant.toArgb()
                    position = XAxis.XAxisPosition.BOTTOM
                    isGranularityEnabled = true
                    granularity = 1f
                    valueFormatter = LogAxisValueFormatter() // Use log scale formatter
                }
                axisLeft.textColor = onSurfaceVariant.toArgb()
                axisRight.isEnabled = false
                legend.textColor = onSurfaceVariant.toArgb()
            }
        },
        update = { chart ->
            val entries = points.map { Entry(it.blows, it.waterContent) }
            val pointDataSet = LineDataSet(entries, "Data Points").apply {
                color = primaryColor.toArgb()
                setCircleColor(primaryColor.toArgb())
                circleRadius = 4f
                setDrawValues(false)
                lineWidth = 0f
            }

            val lineDataSet = LineDataSet(bestFitLine, "Flow Curve").apply {
                color = primaryColor.toArgb()
                lineWidth = 2f
                setDrawCircles(false)
                setDrawValues(false)
                enableDashedLine(10f, 5f, 0f)
            }

            val llLine = LimitLine(25f, "LL (25 blows)").apply {
                lineColor = onSurfaceVariant.toArgb()
                lineWidth = 1.5f
                enableDashedLine(10f, 10f, 0f)
                textColor = onSurfaceVariant.toArgb()
                labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
            }
            chart.xAxis.removeAllLimitLines()
            chart.xAxis.addLimitLine(llLine)

            chart.data = LineData(listOf(pointDataSet, lineDataSet))
            chart.invalidate()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(vertical = 16.dp)
    )
}

@Composable
fun PlasticityChart(ll: Float?, pi: Float?) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current

    AndroidView(
        factory = {
            LineChart(context).apply { // CORRECTED: Changed from ScatterChart to LineChart
                description.text = context.getString(R.string.plasticity_chart_analysis)
                description.textColor = onSurfaceVariant.toArgb()
                setBackgroundColor(Color.Transparent.toArgb())
                setNoDataText("No data for plasticity chart.")
                setNoDataTextColor(onSurfaceVariant.toArgb())

                xAxis.apply {
                    textColor = onSurfaceVariant.toArgb()
                    position = XAxis.XAxisPosition.BOTTOM
                    axisMinimum = 0f
                    axisMaximum = 100f
                    addLimitLine(LimitLine(50f, "LL=50").apply {
                        lineColor = onSurfaceVariant.copy(alpha = 0.5f).toArgb()
                        lineWidth = 1f
                        enableDashedLine(10f, 5f, 0f)
                    })
                }
                axisLeft.apply {
                    textColor = onSurfaceVariant.toArgb()
                    axisMinimum = 0f
                    axisMaximum = 60f
                }
                axisRight.isEnabled = false
                legend.textColor = onSurfaceVariant.toArgb()
            }
        },
        update = { chart ->
            val aLineEntries = (20..100).map { Entry(it.toFloat(), 0.73f * (it - 20)) }
            val aLineDataSet = LineDataSet(aLineEntries, "A-Line").apply {
                color = onSurfaceVariant.toArgb()
                lineWidth = 2f
                setDrawCircles(false)
                setDrawValues(false)
            }

            val dataSets = mutableListOf<ILineDataSet>(aLineDataSet)

            if (ll != null && pi != null) {
                val pointEntry = listOf(Entry(ll, pi))
                // CORRECTED: Changed from ScatterDataSet to LineDataSet
                val pointDataSet = LineDataSet(pointEntry, "Your Soil").apply {
                    color = primaryColor.toArgb()
                    setCircleColor(primaryColor.toArgb())
                    circleRadius = 6f // Make point more visible
                    lineWidth = 0f // Hide the line, show only point
                }
                dataSets.add(pointDataSet)
            }

            // CORRECTED: Changed from ScatterData to LineData
            chart.data = LineData(dataSets)
            chart.invalidate()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(vertical = 16.dp)
    )
}

// Custom formatter for log axis
class LogAxisValueFormatter : com.github.mikephil.charting.formatter.ValueFormatter() {
    override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
        return "%.0f".format(value)
    }
}
