package com.example.soillab.aggquality

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soillab.R
import com.example.soillab.data.*
import com.example.soillab.ui.components.*
import com.example.soillab.ui.theme.Green500
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

enum class AggTestType(val resId: Int) {
    LA_ABRASION(R.string.agg_la_abrasion_tab),
    FLAKINESS(R.string.agg_flakiness_tab)
}

// --- UI State ---
data class AggQualityUiState(
    val testInfo: TestInfo = TestInfo(),
    val laAbrasionData: LAAbrasionData = LAAbrasionData(),
    val laAbrasionResult: LAAbrasionResult? = null,
    val flakinessData: FlakinessData = FlakinessData(),
    val flakinessResult: FlakinessResult? = null,
    val isLoading: Boolean = false,
    val currentLaReportId: String? = null,
    val currentFlakinessReportId: String? = null,
    val selectedTestType: AggTestType = AggTestType.LA_ABRASION,
    val selectedTabIndex: Int = 0 // 0: Setup, 1: Data, 2: Results
)

// --- ViewModel ---
class AggregateQualityViewModel(private val repository: IReportRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AggQualityUiState())
    val uiState = _uiState.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage = _userMessage.asSharedFlow()

    fun onTestInfoChange(newInfo: TestInfo) = _uiState.update { it.copy(testInfo = newInfo) }
    fun onLaDataChange(newData: LAAbrasionData) = _uiState.update { it.copy(laAbrasionData = newData) }
    fun onFlakinessDataChange(newData: FlakinessData) = _uiState.update { it.copy(flakinessData = newData) }
    fun onTabSelected(index: Int) = _uiState.update { it.copy(selectedTabIndex = index) }
    fun onTestTypeSelected(type: AggTestType) = _uiState.update { it.copy(selectedTestType = type, selectedTabIndex = 0, laAbrasionResult = null, flakinessResult = null) }


    fun calculateLAAbrasion() {
        viewModelScope.launch {
            val result = AggregateQualityCalculator.calculateLAAbrasion(_uiState.value.laAbrasionData)
            if (result == null) {
                _userMessage.emit("Invalid data. Please check inputs.")
            } else {
                _uiState.update { it.copy(laAbrasionResult = result, selectedTabIndex = 2) }
            }
        }
    }

    fun calculateFlakiness() {
        viewModelScope.launch {
            val result = AggregateQualityCalculator.calculateFlakiness(_uiState.value.flakinessData)
            if (result == null) {
                _userMessage.emit("Invalid data. Please check inputs.")
            } else {
                _uiState.update { it.copy(flakinessResult = result, selectedTabIndex = 2) }
            }
        }
    }

    fun loadExampleData(context: Context) {
        viewModelScope.launch {
            if (_uiState.value.selectedTestType == AggTestType.LA_ABRASION) {
                _uiState.update { it.copy(laAbrasionData = LAAbrasionExampleDataGenerator.generate(), laAbrasionResult = null) }
            } else {
                _uiState.update { it.copy(flakinessData = FlakinessExampleDataGenerator.generate(), flakinessResult = null) }
            }
            _userMessage.emit(context.getString(R.string.example_data_loaded_no_compute))
        }
    }

    fun saveReport(context: Context) {
        val state = _uiState.value
        if (state.testInfo.boreholeNo.isBlank()) {
            viewModelScope.launch { _userMessage.emit(context.getString(R.string.error_fill_info_and_compute)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                if (state.selectedTestType == AggTestType.LA_ABRASION && state.laAbrasionResult != null) {
                    val reportId = withContext(Dispatchers.IO) {
                        val report = LAAbrasionReport(
                            id = state.currentLaReportId ?: UUID.randomUUID().toString(),
                            testInfo = state.testInfo,
                            data = state.laAbrasionData,
                            result = state.laAbrasionResult
                        )
                        repository.saveLAAbrasionReport(report)
                        report.id
                    }
                    _uiState.update { it.copy(currentLaReportId = reportId) }
                } else if (state.selectedTestType == AggTestType.FLAKINESS && state.flakinessResult != null) {
                    val reportId = withContext(Dispatchers.IO) {
                        val report = FlakinessReport(
                            id = state.currentFlakinessReportId ?: UUID.randomUUID().toString(),
                            testInfo = state.testInfo,
                            data = state.flakinessData,
                            result = state.flakinessResult
                        )
                        repository.saveFlakinessReport(report)
                        report.id
                    }
                    _uiState.update { it.copy(currentFlakinessReportId = reportId) }
                }
                _userMessage.emit(context.getString(R.string.success_report_saved))
            } catch (e: Exception) {
                _userMessage.emit("❌ Error saving report: ${e.message}")
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}

// --- Main Screen Composable ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AggregateQualityScreen(
    viewModel: AggregateQualityViewModel,
    reportIdToLoad: String?,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val tabs = listOf(R.string.agg_setup_tab, R.string.agg_data_tab, R.string.agg_results_tab)

    // Note: Loading reports is complex here as we don't know which type it is.
    // This screen is designed for creating NEW reports.
    // Loading will be handled by the ReportsHub.

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.agg_test_type_label),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AggTestType.values().forEach { type ->
                SegmentedButton(
                    selected = uiState.selectedTestType == type,
                    onClick = { viewModel.onTestTypeSelected(type) },
                    shape = SegmentedButtonDefaults.itemShape(index = type.ordinal, count = AggTestType.values().size)
                ) {
                    Text(stringResource(type.resId))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TabRow(selectedTabIndex = uiState.selectedTabIndex) {
            tabs.forEachIndexed { index, titleResId ->
                Tab(
                    selected = uiState.selectedTabIndex == index,
                    onClick = { viewModel.onTabSelected(index) },
                    text = { Text(stringResource(titleResId)) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(targetState = uiState.selectedTestType, label = "TestTypeSwitch") { testType ->
            when (testType) {
                AggTestType.LA_ABRASION -> LAAbrasionContent(viewModel, uiState, uiState.selectedTabIndex)
                AggTestType.FLAKINESS -> FlakinessContent(viewModel, uiState, uiState.selectedTabIndex)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun LAAbrasionContent(viewModel: AggregateQualityViewModel, uiState: AggQualityUiState, selectedTab: Int) {
    val context = LocalContext.current

    when(selectedTab) {
        0 -> LAAbrasionSetupTab(
            testInfo = uiState.testInfo,
            onTestInfoChange = viewModel::onTestInfoChange,
            grading = uiState.laAbrasionData.grading,
            onGradingChange = { newGrading -> viewModel.onLaDataChange(uiState.laAbrasionData.copy(grading = newGrading)) }
        )
        1 -> LAAbrasionDataTab(
            data = uiState.laAbrasionData,
            onDataChange = viewModel::onLaDataChange,
            onCompute = viewModel::calculateLAAbrasion,
            onLoadExample = { viewModel.loadExampleData(context) }
        )
        2 -> LAAbrasionResultTab(
            result = uiState.laAbrasionResult,
            data = uiState.laAbrasionData,
            onSpecLimitChange = { viewModel.onLaDataChange(uiState.laAbrasionData.copy(specLimit = it)) }
        )
    }
}

@Composable
fun LAAbrasionSetupTab(
    testInfo: TestInfo,
    onTestInfoChange: (TestInfo) -> Unit,
    grading: String,
    onGradingChange: (String) -> Unit
) {
    Column {
        TestInfoSection(testInfo, onTestInfoChange)
        DataPanel(title = stringResource(R.string.agg_la_setup_title)) {
            NeuralInput(
                value = grading,
                onValueChange = onGradingChange,
                label = stringResource(R.string.agg_la_grading_label),
                keyboardType = KeyboardType.Text
            )
        }
    }
}

@Composable
fun LAAbrasionDataTab(
    data: LAAbrasionData,
    onDataChange: (LAAbrasionData) -> Unit,
    onCompute: () -> Unit,
    onLoadExample: () -> Unit
) {
    Column {
        DataPanel(title = stringResource(R.string.agg_la_abrasion_title)) {
            NeuralInput(
                value = data.initialWeight,
                onValueChange = { onDataChange(data.copy(initialWeight = it)) },
                label = stringResource(R.string.agg_initial_weight_g)
            )
            NeuralInput(
                value = data.finalWeight,
                onValueChange = { onDataChange(data.copy(finalWeight = it)) },
                label = stringResource(R.string.agg_final_weight_g)
            )
        }
        ActionButtons(onCompute = onCompute, onLoadExample = onLoadExample)
    }
}

@Composable
fun LAAbrasionResultTab(
    result: LAAbrasionResult?,
    data: LAAbrasionData,
    onSpecLimitChange: (String) -> Unit
) {
    AnimatedVisibility(visible = result != null) {
        result?.let {
            DataPanel(title = stringResource(R.string.analysis_results)) {
                val specLimitValue = data.specLimit.toDoubleOrNull()
                val isPass = specLimitValue != null && result.percentLoss <= specLimitValue

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuralInput(
                        value = data.specLimit,
                        onValueChange = onSpecLimitChange,
                        label = stringResource(R.string.agg_la_spec_limit_percent),
                        modifier = Modifier.weight(1f)
                    )
                    if (specLimitValue != null) {
                        val (text, color) = if (isPass) {
                            Pair(stringResource(R.string.pass), Green500)
                        } else {
                            Pair(stringResource(R.string.fail), MaterialTheme.colorScheme.error)
                        }
                        Text(
                            text = text,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(color, MaterialTheme.shapes.medium)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                ResultDisplay(
                    title = stringResource(R.string.agg_initial_weight_g),
                    value = data.initialWeight + " g"
                )
                ResultDisplay(
                    title = stringResource(R.string.agg_final_weight_g),
                    value = data.finalWeight + " g"
                )
                ResultDisplay(
                    title = stringResource(R.string.agg_loss_weight_g),
                    value = String.format(Locale.US, "%.1f", result.lossWeight) + " g"
                )

                Spacer(modifier = Modifier.height(8.dp))

                ResultDisplay(
                    title = stringResource(R.string.agg_la_abrasion_loss),
                    value = "${String.format(Locale.US, "%.1f", result.percentLoss)}%",
                    isPrimary = true,
                    valueColor = if (specLimitValue == null) MaterialTheme.colorScheme.primary else if(isPass) Green500 else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun FlakinessContent(viewModel: AggregateQualityViewModel, uiState: AggQualityUiState, selectedTab: Int) {
    val context = LocalContext.current

    when(selectedTab) {
        0 -> TestInfoSection(uiState.testInfo, viewModel::onTestInfoChange)
        1 -> FlakinessDataTab(
            data = uiState.flakinessData,
            onDataChange = viewModel::onFlakinessDataChange,
            onCompute = viewModel::calculateFlakiness,
            onLoadExample = { viewModel.loadExampleData(context) }
        )
        2 -> FlakinessResultTab(
            result = uiState.flakinessResult,
            data = uiState.flakinessData,
            onFlakinessSpecChange = { viewModel.onFlakinessDataChange(uiState.flakinessData.copy(flakinessSpecLimit = it)) },
            onElongationSpecChange = { viewModel.onFlakinessDataChange(uiState.flakinessData.copy(elongationSpecLimit = it)) }
        )
    }
}

@Composable
fun FlakinessDataTab(
    data: FlakinessData,
    onDataChange: (FlakinessData) -> Unit,
    onCompute: () -> Unit,
    onLoadExample: () -> Unit
) {
    Column {
        DataPanel(title = stringResource(R.string.agg_flakiness_title)) {
            NeuralInput(
                value = data.initialWeight,
                onValueChange = { onDataChange(data.copy(initialWeight = it)) },
                label = stringResource(R.string.agg_initial_weight_g)
            )
            NeuralInput(
                value = data.flakyWeight,
                onValueChange = { onDataChange(data.copy(flakyWeight = it)) },
                label = stringResource(R.string.agg_flaky_weight_g)
            )
            NeuralInput(
                value = data.elongatedWeight,
                onValueChange = { onDataChange(data.copy(elongatedWeight = it)) },
                label = stringResource(R.string.agg_elongated_weight_g)
            )
        }
        ActionButtons(onCompute = onCompute, onLoadExample = onLoadExample)
    }
}

@Composable
fun FlakinessResultTab(
    result: FlakinessResult?,
    data: FlakinessData,
    onFlakinessSpecChange: (String) -> Unit,
    onElongationSpecChange: (String) -> Unit
) {
    AnimatedVisibility(visible = result != null) {
        result?.let {
            DataPanel(title = stringResource(R.string.analysis_results)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuralInput(
                        value = data.flakinessSpecLimit,
                        onValueChange = onFlakinessSpecChange,
                        label = stringResource(R.string.agg_flakiness_spec_limit),
                        modifier = Modifier.weight(1f)
                    )
                    NeuralInput(
                        value = data.elongationSpecLimit,
                        onValueChange = onElongationSpecChange,
                        label = stringResource(R.string.agg_elongation_spec_limit),
                        modifier = Modifier.weight(1f)
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                val fiSpec = data.flakinessSpecLimit.toDoubleOrNull()
                val fiPass = fiSpec != null && result.flakinessIndex <= fiSpec

                val eiSpec = data.elongationSpecLimit.toDoubleOrNull()
                val eiPass = eiSpec != null && result.elongationIndex <= eiSpec

                ResultDisplay(
                    title = stringResource(R.string.agg_flakiness_index),
                    value = "${String.format(Locale.US, "%.1f", result.flakinessIndex)}%",
                    isPrimary = true,
                    valueColor = if (fiSpec == null) MaterialTheme.colorScheme.primary else if(fiPass) Green500 else MaterialTheme.colorScheme.error
                )
                ResultDisplay(
                    title = stringResource(R.string.agg_elongation_index),
                    value = "${String.format(Locale.US, "%.1f", result.elongationIndex)}%",
                    isPrimary = true,
                    valueColor = if (eiSpec == null) MaterialTheme.colorScheme.primary else if(eiPass) Green500 else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

