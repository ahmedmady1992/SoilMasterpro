package com.example.soillab.fielddensity

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soillab.R
import com.example.soillab.data.*
import com.example.soillab.ui.components.*
import com.example.soillab.ui.theme.Green500
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

// --- UI State ---
data class SandConeUiState(
    val testInfo: TestInfo = TestInfo(),
    val calibrationData: SandConeCalibrationData = SandConeCalibrationData(),
    val fieldTestData: SandConeFieldData = SandConeFieldData(),
    val availableProctorReports: List<ProctorReport> = emptyList(),
    val selectedProctorReport: ProctorReport? = null,
    val result: SandConeResult? = null,
    val isLoading: Boolean = false,
    val currentReportId: String? = null
)

// --- ViewModel ---
class SandConeViewModel(private val repository: IReportRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SandConeUiState())
    val uiState = _uiState.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage = _userMessage.asSharedFlow()

    init {
        loadProctorReports()
    }

    private fun loadProctorReports() {
        viewModelScope.launch {
            repository.proctorReports.collect { reports ->
                _uiState.update { it.copy(availableProctorReports = reports) }
            }
        }
    }

    fun onTestInfoChange(newInfo: TestInfo) = _uiState.update { it.copy(testInfo = newInfo) }
    fun onCalibrationDataChange(newData: SandConeCalibrationData) {
        _uiState.update { it.copy(calibrationData = newData) }
        calculate()
    }
    fun onFieldDataChange(newData: SandConeFieldData) {
        _uiState.update { it.copy(fieldTestData = newData) }
        calculate()
    }

    fun onProctorReportSelected(report: ProctorReport?) {
        _uiState.update { it.copy(selectedProctorReport = report) }
        calculate()
    }

    fun calculate() {
        val state = _uiState.value
        val mdd = state.selectedProctorReport?.result?.maxDryDensity?.toDouble()
        val result = SandConeCalculator.calculate(
            state.calibrationData,
            state.fieldTestData,
            mdd
        )
        _uiState.update { it.copy(result = result) }
    }

    fun loadExampleData(context: Context) {
        viewModelScope.launch {
            val example = SandConeExampleDataGenerator.generate()
            _uiState.update {
                it.copy(
                    calibrationData = example.calibrationData,
                    fieldTestData = example.fieldTestData,
                    testInfo = it.testInfo.copy(sampleDescription = context.getString(example.descriptionResId)),
                    result = null
                )
            }
            calculate()
            _userMessage.emit(context.getString(R.string.example_data_loaded))
        }
    }

    fun saveReport(context: Context) {
        val state = _uiState.value
        if (state.result == null || state.testInfo.boreholeNo.isBlank()) {
            viewModelScope.launch { _userMessage.emit(context.getString(R.string.error_fill_info_and_compute)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val reportId = withContext(Dispatchers.IO) {
                    val report = SandConeReport(
                        id = state.currentReportId ?: UUID.randomUUID().toString(),
                        testInfo = state.testInfo,
                        calibrationData = state.calibrationData,
                        fieldTestData = state.fieldTestData,
                        selectedProctorId = state.selectedProctorReport?.id,
                        result = state.result
                    )
                    repository.saveSandConeReport(report)
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

    fun loadReportForEditing(reportId: String?) {
        if (reportId == null) {
            _uiState.value = SandConeUiState()
            loadProctorReports() // Ensure proctor list is still loaded
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getSandConeReportById(reportId)?.let { report ->
                val proctorReport = report.selectedProctorId?.let { pId ->
                    repository.getProctorReportById(pId)
                }
                _uiState.update {
                    it.copy(
                        testInfo = report.testInfo,
                        calibrationData = report.calibrationData,
                        fieldTestData = report.fieldTestData,
                        result = report.result,
                        selectedProctorReport = proctorReport,
                        currentReportId = report.id,
                        isLoading = false
                    )
                }
            } ?: _uiState.update { it.copy(isLoading = false) }
        }
    }
}

// --- Main Screen Composable ---
@Composable
fun SandConeScreen(
    viewModel: SandConeViewModel,
    reportIdToLoad: String?,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(reportIdToLoad) {
        viewModel.loadReportForEditing(reportIdToLoad)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        TestInfoSection(uiState.testInfo, viewModel::onTestInfoChange)

        SandConeCalibrationPanel(
            data = uiState.calibrationData,
            onDataChange = viewModel::onCalibrationDataChange
        )

        SandConeFieldTestPanel(
            data = uiState.fieldTestData,
            onDataChange = viewModel::onFieldDataChange,
            proctorReports = uiState.availableProctorReports,
            selectedReport = uiState.selectedProctorReport,
            onReportSelected = viewModel::onProctorReportSelected
        )

        ActionButtons(
            onCompute = viewModel::calculate,
            onLoadExample = { viewModel.loadExampleData(context) }
        )

        AnimatedVisibility(visible = uiState.result != null) {
            uiState.result?.let {
                SandConeResultsPanel(result = it)
            }
        }
    }
}

@Composable
fun SandConeCalibrationPanel(
    data: SandConeCalibrationData,
    onDataChange: (SandConeCalibrationData) -> Unit
) {
    DataPanel(title = stringResource(R.string.sandcone_calibration_title)) {
        NeuralInput(
            value = data.sandDensity,
            onValueChange = { onDataChange(data.copy(sandDensity = it)) },
            label = stringResource(R.string.sandcone_sand_density_g_cm3)
        )
        NeuralInput(
            value = data.coneWeight,
            onValueChange = { onDataChange(data.copy(coneWeight = it)) },
            label = stringResource(R.string.sandcone_cone_weight_g)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandConeFieldTestPanel(
    data: SandConeFieldData,
    onDataChange: (SandConeFieldData) -> Unit,
    proctorReports: List<ProctorReport>,
    selectedReport: ProctorReport?,
    onReportSelected: (ProctorReport?) -> Unit
) {
    var proctorMenuExpanded by remember { mutableStateOf(false) }

    DataPanel(title = stringResource(R.string.sandcone_field_test_title)) {
        NeuralInput(
            value = data.initialWeight,
            onValueChange = { onDataChange(data.copy(initialWeight = it)) },
            label = stringResource(R.string.sandcone_initial_weight_g)
        )
        NeuralInput(
            value = data.finalWeight,
            onValueChange = { onDataChange(data.copy(finalWeight = it)) },
            label = stringResource(R.string.sandcone_final_weight_g)
        )
        NeuralInput(
            value = data.wetSoilWeight,
            onValueChange = { onDataChange(data.copy(wetSoilWeight = it)) },
            label = stringResource(R.string.sandcone_wet_soil_weight_g)
        )
        NeuralInput(
            value = data.moistureContent,
            onValueChange = { onDataChange(data.copy(moistureContent = it)) },
            label = stringResource(R.string.proctor_moisture_content_percent)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.sandcone_proctor_link_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

        ExposedDropdownMenuBox(
            expanded = proctorMenuExpanded,
            onExpandedChange = { proctorMenuExpanded = !proctorMenuExpanded },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            OutlinedTextField(
                value = selectedReport?.parameters?.testInfo?.let { "${it.boreholeNo} / ${it.sampleNo}" } ?: stringResource(R.string.sandcone_select_proctor),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.sandcone_select_proctor_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = proctorMenuExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            )
            ExposedDropdownMenu(
                expanded = proctorMenuExpanded,
                onDismissRequest = { proctorMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sandcone_no_proctor)) },
                    onClick = {
                        onReportSelected(null)
                        proctorMenuExpanded = false
                    }
                )
                proctorReports.forEach { report ->
                    DropdownMenuItem(
                        text = { Text("${report.parameters.testInfo.boreholeNo} / ${report.parameters.testInfo.sampleNo} (MDD: ${String.format("%.3f", report.result.maxDryDensity)})") },
                        onClick = {
                            onReportSelected(report)
                            proctorMenuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SandConeResultsPanel(result: SandConeResult) {
    DataPanel(title = stringResource(R.string.sandcone_results_title)) {
        ResultDisplay(stringResource(R.string.sandcone_sand_in_hole_g), String.format(Locale.US, "%.1f", result.sandInHoleWeight))
        ResultDisplay(stringResource(R.string.sandcone_hole_volume_cm3), String.format(Locale.US, "%.1f", result.holeVolume))
        ResultDisplay(stringResource(R.string.sandcone_wet_density_g_cm3), String.format(Locale.US, "%.3f", result.wetDensity), isPrimary = true)
        ResultDisplay(stringResource(R.string.sandcone_dry_density_g_cm3), String.format(Locale.US, "%.3f", result.dryDensity), isPrimary = true)

        result.proctorMDD?.let { mdd ->
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ResultDisplay(stringResource(R.string.sandcone_mdd_g_cm3), String.format(Locale.US, "%.3f", mdd))

            result.compactionPercentage?.let { compaction ->
                val isPass = compaction >= (result.requiredCompaction ?: 95.0)
                val color = if (isPass) Green500 else MaterialTheme.colorScheme.error

                ResultDisplay(
                    title = stringResource(R.string.sandcone_compaction_percent),
                    value = "${String.format(Locale.US, "%.1f", compaction)}%",
                    isPrimary = true,
                    valueColor = color
                )
                Text(
                    text = if(isPass) "(${stringResource(R.string.pass)})" else "(${stringResource(R.string.fail)})",
                    color = color,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}