package com.example.soillab.sieveanalysis

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import com.example.soillab.reports.DataChip
import com.example.soillab.ui.components.*
import com.example.soillab.ui.theme.Green500
import com.example.soillab.ui.theme.Yellow500
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

data class SieveUiState(
    val testInfo: TestInfo = TestInfo(),
    val parameters: ClassificationParameters = ClassificationParameters(),
    val sieves: List<Sieve> = Sieve.standardSet(), // Default to soil
    val sampleType: SampleType = SampleType.SOIL,
    val result: SieveAnalysisResult? = null,
    val isLoading: Boolean = false,
    val currentReportId: String? = null,
    val selectedSpec: Specification? = null,
    val customSpecs: List<Specification> = emptyList(),
    val showingCustomSpecDialog: Boolean = false
)

class SieveAnalysisViewModel(private val repository: IReportRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SieveUiState())
    val uiState = _uiState.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage = _userMessage.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.customSpecifications.collect { customSpecs ->
                _uiState.update { it.copy(customSpecs = customSpecs) }
            }
        }
    }

    // Function to handle navigation arguments
    fun initializeWithSampleType(sampleType: SampleType) {
        _uiState.update {
            val newSieves = if (sampleType == SampleType.AGGREGATE) Sieve.aggregateSet() else Sieve.standardSet()
            it.copy(
                sampleType = sampleType,
                sieves = newSieves,
                result = null, // Clear result when type changes
                parameters = ClassificationParameters(), // Clear parameters
                testInfo = TestInfo() // Clear test info
            )
        }
    }

    fun loadReportForEditing(reportId: String?) {
        if (reportId == null) {
            _uiState.update {
                it.copy(
                    testInfo = TestInfo(),
                    parameters = ClassificationParameters(),
                    sieves = Sieve.standardSet(),
                    sampleType = SampleType.SOIL,
                    result = null,
                    currentReportId = null
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getSieveAnalysisReportById(reportId)?.let { report ->
                _uiState.update {
                    it.copy(
                        testInfo = report.testInfo,
                        parameters = report.parameters,
                        sieves = report.sieves,
                        result = report.result,
                        sampleType = if (report.sieves.any { s -> s.name == "No. 8" }) SampleType.AGGREGATE else SampleType.SOIL,
                        currentReportId = report.id,
                        isLoading = false
                    )
                }
            } ?: _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun saveReport(context: Context) {
        val currentState = _uiState.value
        if (currentState.result == null || currentState.testInfo.boreholeNo.isBlank()) {
            viewModelScope.launch { _userMessage.emit(context.getString(R.string.error_fill_info_and_compute)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val reportId = withContext(Dispatchers.IO) {
                    val report = SieveAnalysisReport(
                        id = currentState.currentReportId ?: UUID.randomUUID().toString(),
                        testInfo = currentState.testInfo,
                        parameters = currentState.parameters,
                        sieves = currentState.sieves,
                        result = currentState.result
                    )
                    repository.saveSieveAnalysisReport(report)
                    report.id
                }
                _uiState.update { it.copy(currentReportId = reportId) }
                _userMessage.emit(context.getString(R.string.success_report_saved))
            } catch (e: Exception) {
                _userMessage.emit("❌ Error saving report: ${e.message}")
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // ADDED THIS FUNCTION
    fun onTestInfoChange(newInfo: TestInfo) {
        _uiState.update { it.copy(testInfo = newInfo) }
    }

    fun onParamsChange(newParams: ClassificationParameters) {
        _uiState.update { state ->
            state.copy(parameters = newParams)
        }
        if (_uiState.value.result != null) calculate() // Recalculate if params change
    }

    fun onSieveWeightChange(index: Int, weight: String) {
        _uiState.update { state ->
            val newSieves = state.sieves.mapIndexed { i, sieve ->
                if (i == index) sieve.copy(retainedWeight = weight) else sieve
            }
            state.copy(sieves = newSieves)
        }
    }

    fun onSampleTypeChange(sampleType: SampleType) {
        _uiState.update {
            it.copy(
                sampleType = sampleType,
                sieves = getSieveDataForType(sampleType),
                result = null // Clear result when type changes
            )
        }
    }

    fun calculate() {
        val state = _uiState.value
        val result = SieveAnalysisCalculator.calculate(state.sieves, state.parameters)
        val uscs = AASHTO_USCS_Classifier.classifyUSCS(result, state.parameters.liquidLimit.toFloatOrNull() ?: 0f, (state.parameters.liquidLimit.toFloatOrNull() ?: 0f) - (state.parameters.plasticLimit.toFloatOrNull() ?: 0f))
        val classification = AASHTO_USCS_Classifier.classify(result, state.parameters)
        val frost = AASHTO_USCS_Classifier.getFrostSusceptibility(result.percentFines)
        val predictions = AASHTO_USCS_Classifier.predictEngineeringProperties(result, state.parameters, uscs)

        _uiState.update {
            it.copy(
                result = result.copy(
                    classification = classification,
                    frostSusceptibility = frost,
                    predictedProperties = predictions
                )
            )
        }
    }

    private fun getSieveDataForType(sampleType: SampleType): List<Sieve> {
        return if (sampleType == SampleType.AGGREGATE) Sieve.aggregateSet() else Sieve.standardSet()
    }

    fun loadExampleData(context: Context) {
        viewModelScope.launch { _userMessage.emit(context.getString(R.string.example_data_loaded)) }
        // Logic to load example data
    }

    fun updateSpec(spec: Specification?) {
        _uiState.update { it.copy(selectedSpec = spec) }
    }

    fun saveCustomSpec(context: Context, name: String, limits: List<SpecificationLimit>) {
        viewModelScope.launch {
            if (name.isBlank()) {
                _userMessage.emit(context.getString(R.string.error_spec_name_empty))
                return@launch
            }
            if (limits.all { it.minPassing == 0f && it.maxPassing == 100f }) {
                _userMessage.emit(context.getString(R.string.error_spec_limits_empty))
                return@launch
            }
            val newSpec = Specification(
                id = UUID.randomUUID().toString(),
                name = name,
                limits = limits,
                isCustom = true
            )
            repository.saveCustomSpecification(newSpec)
            _uiState.update { it.copy(selectedSpec = newSpec, showingCustomSpecDialog = false) }
            _userMessage.emit(context.getString(R.string.success_spec_saved))
        }
    }

    fun deleteCustomSpec(specId: String) {
        viewModelScope.launch {
            repository.deleteCustomSpecification(specId)
            _uiState.update { it.copy(selectedSpec = null) }
        }
    }

    fun showCustomSpecDialog(show: Boolean) {
        _uiState.update { it.copy(showingCustomSpecDialog = show) }
    }
}

// --- Composables ---

@Composable
fun SieveAnalysisScreenImproved(
    viewModel: SieveAnalysisViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(R.string.agg_setup_tab, R.string.agg_data_tab, R.string.agg_results_tab)

    if (uiState.showingCustomSpecDialog) {
        CustomSpecDialog(
            sieves = uiState.sieves.filter { it.opening > 0 },
            onDismiss = { viewModel.showCustomSpecDialog(false) },
            onSave = { name, limits -> viewModel.saveCustomSpec(context, name, limits) }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, titleResId ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(stringResource(titleResId)) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedTabIndex) {
                    0 -> SieveSetupTab(viewModel, uiState)
                    1 -> SieveDataTab(viewModel, uiState)
                    2 -> SieveResultsTab(viewModel, uiState)
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = uiState.isLoading,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SieveSetupTab(viewModel: SieveAnalysisViewModel, uiState: SieveUiState) {
    var sampleTypeExpanded by remember { mutableStateOf(false) }

    Column {
        TestInfoSection(uiState.testInfo, viewModel::onTestInfoChange) // CORRECTED

        DataPanel(title = stringResource(R.string.sieve_classification_params)) {
            ExposedDropdownMenuBox(
                expanded = sampleTypeExpanded,
                onExpandedChange = { sampleTypeExpanded = !sampleTypeExpanded },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                OutlinedTextField(
                    value = stringResource(id = uiState.sampleType.displayNameResId),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.sample_type_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sampleTypeExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                )
                ExposedDropdownMenu(
                    expanded = sampleTypeExpanded,
                    onDismissRequest = { sampleTypeExpanded = false }
                ) {
                    SampleType.values().forEach { type ->
                        DropdownMenuItem(
                            text = { Text(stringResource(type.displayNameResId)) },
                            onClick = {
                                viewModel.onSampleTypeChange(type)
                                sampleTypeExpanded = false
                            }
                        )
                    }
                }
            }

            NeuralInput(
                value = uiState.parameters.initialWeight,
                onValueChange = { viewModel.onParamsChange(uiState.parameters.copy(initialWeight = it)) },
                label = stringResource(R.string.sieve_initial_weight_g)
            )
            AnimatedVisibility(visible = uiState.sampleType == SampleType.SOIL) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    NeuralInput(
                        value = uiState.parameters.liquidLimit,
                        onValueChange = { viewModel.onParamsChange(uiState.parameters.copy(liquidLimit = it)) },
                        label = stringResource(R.string.liquid_limit)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NeuralInput(
                        value = uiState.parameters.plasticLimit,
                        onValueChange = { viewModel.onParamsChange(uiState.parameters.copy(plasticLimit = it)) },
                        label = stringResource(R.string.plastic_limit)
                    )
                }
            }
        }
    }
}

@Composable
fun SieveDataTab(viewModel: SieveAnalysisViewModel, uiState: SieveUiState) {
    val context = LocalContext.current
    DataPanel(title = stringResource(R.string.sieve_retained_weights)) {
        uiState.sieves.forEachIndexed { index, sieve ->
            if(sieve.opening > 0) { // Don't show Pan
                NeuralInput(
                    value = sieve.retainedWeight,
                    onValueChange = { weight -> viewModel.onSieveWeightChange(index, weight) },
                    label = stringResource(R.string.sieve_retained_weight_g) + " on ${sieve.name}"
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
    ActionButtons(
        onCompute = { viewModel.calculate() },
        onLoadExample = { viewModel.loadExampleData(context) }
    )
}

@Composable
fun SieveResultsTab(viewModel: SieveAnalysisViewModel, uiState: SieveUiState) {
    Column { // CORRECTED: Wrapped in a Column
        AnimatedVisibility(visible = uiState.result == null) {
            InfoPanel(text = stringResource(R.string.info_sieve_automatic))
        }

        if (uiState.result != null) { // CORRECTED: Changed .let to if
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SpecificationPanel(viewModel, uiState)
                GradationChart(uiState.result!!.sieves, uiState.selectedSpec)
                SieveTable(uiState.result!!.sieves)
                ResultsDashboard(uiState.result!!)

                if (uiState.result!!.classification != null) { // CORRECTED: Changed .let to if
                    ClassificationPanel(uiState.result!!.classification!!)
                }
                if (uiState.result!!.predictedProperties != null) { // CORRECTED: Changed .let to if
                    PredictionPanel(uiState.result!!.predictedProperties!!)
                }
            }
        }
    }
}

@Composable
fun SieveTable(sieves: List<Sieve>) {
    DataPanel(title = "Gradation Data") {
        Column {
            // Header Row
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)).padding(vertical = 8.dp)) {
                Text(stringResource(R.string.sieve_header_name), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(stringResource(R.string.sieve_header_retained), modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(stringResource(R.string.sieve_header_passing), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            // Data Rows
            sieves.forEach { sieve ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(sieve.name, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text(String.format(Locale.US, "%.1f", sieve.cumulativeRetained), modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center)
                    Text(String.format(Locale.US, "%.1f", sieve.percentPassing), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun GradationChart(sieves: List<Sieve>, spec: Specification?) {
    val context = LocalContext.current
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error

    AndroidView(
        factory = {
            LineChart(context).apply {
                description.text = context.getString(R.string.sieve_gradation_curve)
                description.textColor = onSurfaceVariantColor.toArgb()
                setBackgroundColor(Color.Transparent.toArgb())
                setNoDataText("No data for chart.")
                setNoDataTextColor(onSurfaceVariantColor.toArgb())

                xAxis.apply {
                    textColor = onSurfaceVariantColor.toArgb()
                    position = XAxis.XAxisPosition.BOTTOM
                    isGranularityEnabled = true
                    valueFormatter = object : ValueFormatter() {
                        override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
                            // Convert from log10 back to original value for label
                            val originalValue = 10f.pow(value)
                            return if (originalValue < 1) "%.3f".format(originalValue) else "%.1f".format(originalValue)
                        }
                    }
                }
                axisLeft.apply {
                    textColor = onSurfaceVariantColor.toArgb()
                    axisMinimum = 0f
                    axisMaximum = 100f
                }
                axisRight.isEnabled = false
                legend.textColor = onSurfaceVariantColor.toArgb()
            }
        },
        update = { chart ->
            val entries = sieves.filter { it.opening > 0 }.map { Entry(log10(it.opening.toFloat()), it.percentPassing.toFloat()) }.sortedBy { it.x }
            val dataSet = LineDataSet(entries, "Gradation Curve").apply {
                color = primaryColor.toArgb()
                setCircleColor(primaryColor.toArgb())
                circleRadius = 4f
                lineWidth = 2f
            }

            val dataSets = mutableListOf<ILineDataSet>(dataSet)

            spec?.limits?.let { limits ->
                val minEntries = limits.mapNotNull { limit ->
                    val sieve = sieves.find { abs(it.opening - limit.sieveOpening) < 0.001 }
                    sieve?.let { Entry(log10(it.opening.toFloat()), limit.minPassing) }
                }.sortedBy { it.x }

                val maxEntries = limits.mapNotNull { limit ->
                    val sieve = sieves.find { abs(it.opening - limit.sieveOpening) < 0.001 }
                    sieve?.let { Entry(log10(it.opening.toFloat()), limit.maxPassing) }
                }.sortedBy { it.x }

                val minDataSet = LineDataSet(minEntries, "Spec. Min").apply {
                    color = errorColor.toArgb()
                    lineWidth = 1.5f
                    setDrawCircles(false)
                    enableDashedLine(10f, 5f, 0f)
                }
                val maxDataSet = LineDataSet(maxEntries, "Spec. Max").apply {
                    color = errorColor.toArgb()
                    lineWidth = 1.5f
                    setDrawCircles(false)
                    enableDashedLine(10f, 5f, 0f)
                }
                dataSets.add(minDataSet)
                dataSets.add(maxDataSet)
            }

            chart.data = LineData(dataSets)
            chart.xAxis.isGranularityEnabled = true
            chart.invalidate()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(vertical = 16.dp)
    )
}

@Composable
fun ResultsDashboard(result: SieveAnalysisResult) {
    DataPanel(title = stringResource(R.string.sieve_summary_properties)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            DataChip(stringResource(R.string.sieve_percent_gravel), String.format(Locale.US, "%.1f", result.percentGravel) + "%")
            DataChip(stringResource(R.string.sieve_percent_sand), String.format(Locale.US, "%.1f", result.percentSand) + "%")
            DataChip(stringResource(R.string.sieve_percent_fines), String.format(Locale.US, "%.1f", result.percentFines) + "%")
        }
        Divider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

        ResultDisplayWithInfo(stringResource(R.string.sieve_cu), result.cu?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A", R.string.info_cu_title, R.string.info_cu_content)
        ResultDisplayWithInfo(stringResource(R.string.sieve_cc), result.cc?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A", R.string.info_cc_title, R.string.info_cc_content)
        ResultDisplayWithInfo(stringResource(R.string.sieve_fineness_modulus), String.format(Locale.US, "%.2f", result.finenessModulus), R.string.info_fm_title, R.string.info_fm_content)
        result.estimatedPermeability?.let {
            ResultDisplay(stringResource(R.string.est_permeability), "${String.format(Locale.US, "%.2E", it)} cm/s")
        }
        result.frostSusceptibility?.let {
            ResultDisplay(stringResource(R.string.frost_susceptibility), stringResource(it.resId), valueColor = Color(android.graphics.Color.parseColor(it.colorHex)))
        }
        result.materialLossPercentage?.let {
            val isError = it > 2.0
            ResultDisplay(stringResource(R.string.sieve_material_loss), String.format(Locale.US, "%.2f", it) + "%", valueColor = if(isError) MaterialTheme.colorScheme.error else Green500)
        }
    }
}

@Composable
fun ClassificationPanel(classification: SoilClassificationResult) {
    DataPanel(title = stringResource(R.string.soil_classification)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            DataChip("AASHTO", classification.aashto.groupName)
            DataChip("USCS", classification.uscs.groupName)
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(classification.uscs.groupDescriptionResId), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.sieve_ai_commentary), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        InsightCard(Icons.Default.Info, "Commentary", stringResource(classification.aiCommentaryResId))
    }
}

@Composable
fun PredictionPanel(predictions: PredictedProperties) {
    DataPanel(title = stringResource(R.string.prediction_panel_title)) {
        predictions.predictedMdd?.let {
            ResultDisplayWithInfo(stringResource(R.string.predicted_mdd), "${String.format(Locale.US, "%.2f", it)} g/cm³", R.string.info_prediction_title, R.string.info_prediction_content)
        }
        predictions.predictedOmc?.let {
            ResultDisplayWithInfo(stringResource(R.string.predicted_omc), "${String.format(Locale.US, "%.1f", it)}%", R.string.info_prediction_title, R.string.info_prediction_content)
        }
        predictions.predictedCbr?.let {
            ResultDisplayWithInfo(stringResource(R.string.predicted_cbr), "~ ${String.format(Locale.US, "%.0f", it)}%", R.string.info_prediction_title, R.string.info_prediction_content)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecificationPanel(viewModel: SieveAnalysisViewModel, uiState: SieveUiState) {
    var specMenuExpanded by remember { mutableStateOf(false) }
    val predefinedSpecs = SpecificationRepository.getPredefinedSpecs()
    val allSpecs = predefinedSpecs + uiState.customSpecs
    val context = LocalContext.current

    DataPanel(title = stringResource(R.string.spec_compare_title)) {
        ExposedDropdownMenuBox(
            expanded = specMenuExpanded,
            onExpandedChange = { specMenuExpanded = !specMenuExpanded }
        ) {
            OutlinedTextField(
                value = uiState.selectedSpec?.name ?: (uiState.selectedSpec?.nameResId?.let { stringResource(it) } ?: stringResource(R.string.spec_select)),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.spec_select)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = specMenuExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            )
            ExposedDropdownMenu(
                expanded = specMenuExpanded,
                onDismissRequest = { specMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.spec_none)) },
                    onClick = { viewModel.updateSpec(null); specMenuExpanded = false }
                )
                allSpecs.forEach { spec ->
                    DropdownMenuItem(
                        text = { Text(spec.name.ifEmpty { stringResource(spec.nameResId) }) },
                        onClick = { viewModel.updateSpec(spec); specMenuExpanded = false }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.spec_custom), fontWeight = FontWeight.Bold) },
                    onClick = { viewModel.showCustomSpecDialog(true); specMenuExpanded = false }
                )
            }
        }
    }
}

@Composable
fun CustomSpecDialog(
    sieves: List<Sieve>,
    onDismiss: () -> Unit,
    onSave: (String, List<SpecificationLimit>) -> Unit
) {
    var specName by remember { mutableStateOf("") }
    val limits = remember { mutableStateMapOf<Double, Pair<String, String>>() }

    // Initialize map
    LaunchedEffect(sieves) {
        sieves.forEach { sieve ->
            limits[sieve.opening] = "0" to "100"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text(stringResource(R.string.custom_spec_panel_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                NeuralInput(
                    value = specName,
                    onValueChange = { specName = it },
                    label = stringResource(R.string.spec_name_label),
                    keyboardType = KeyboardType.Text
                )
                Spacer(Modifier.height(16.dp))
                sieves.forEach { sieve ->
                    val currentLimits = limits[sieve.opening] ?: ("0" to "100")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(sieve.name, modifier = Modifier.width(60.dp))
                        NeuralInput(
                            value = currentLimits.first,
                            onValueChange = { limits[sieve.opening] = it to currentLimits.second },
                            label = stringResource(R.string.min_passing),
                            modifier = Modifier.weight(1f)
                        )
                        NeuralInput(
                            value = currentLimits.second,
                            onValueChange = { limits[sieve.opening] = currentLimits.first to it },
                            label = stringResource(R.string.max_passing),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val specLimits = limits.map { (opening, pair) ->
                    SpecificationLimit(
                        sieveOpening = opening,
                        minPassing = pair.first.toFloatOrNull() ?: 0f,
                        maxPassing = pair.second.toFloatOrNull() ?: 100f
                    )
                }
                onSave(specName, specLimits)
            }) {
                Text(stringResource(R.string.save_spec))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

