package com.example.soillab.specificgravity

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soillab.R
import com.example.soillab.data.*
import com.example.soillab.ui.components.ActionButtons
import com.example.soillab.ui.components.DataPanel
import com.example.soillab.ui.components.NeuralInput
import com.example.soillab.ui.components.ResultDisplay
import com.example.soillab.ui.components.TestInfoSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

// --- UI State ---
data class GsUiState(
    val testInfo: TestInfo = TestInfo(),
    val fineSoilData: GsFineSoilData = GsFineSoilData(),
    val coarseSoilData: GsCoarseSoilData = GsCoarseSoilData(),
    val result: GsResult? = null,
    val isLoading: Boolean = false,
    val currentReportId: String? = null,
    val selectedTab: Int = 0 // 0 for Fine, 1 for Coarse
)

// --- ViewModel ---
class SpecificGravityViewModel(private val repository: IReportRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(GsUiState())
    val uiState = _uiState.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage = _userMessage.asSharedFlow()

    fun onTestInfoChange(newInfo: TestInfo) = _uiState.update { it.copy(testInfo = newInfo) }
    fun onFineSoilDataChange(newData: GsFineSoilData) = _uiState.update { it.copy(fineSoilData = newData) }
    fun onCoarseSoilDataChange(newData: GsCoarseSoilData) = _uiState.update { it.copy(coarseSoilData = newData) }
    fun onTabSelected(index: Int) = _uiState.update { it.copy(selectedTab = index) }

    // NEW FUNCTION to handle navigation arguments
    fun initializeWithTestType(testType: GsTestType) {
        _uiState.update {
            it.copy(
                selectedTab = if (testType == GsTestType.COARSE_AGG) 1 else 0
            )
        }
    }

    fun calculateGs() {
        viewModelScope.launch {
            val state = _uiState.value
            val result = if (state.selectedTab == 0) {
                SpecificGravityCalculator.calculateFineSoil(state.fineSoilData)
            } else {
                SpecificGravityCalculator.calculateCoarseSoil(state.coarseSoilData)
            }

            if (result == null) {
                _userMessage.emit("Invalid data. Please check inputs.")
            } else {
                _uiState.update { it.copy(result = result) }
            }
        }
    }

    fun loadExampleData(context: Context) {
        viewModelScope.launch {
            if (_uiState.value.selectedTab == 0) {
                val example = GsExampleDataGenerator.generateFine()
                _uiState.update { it.copy(fineSoilData = example.data, testInfo = it.testInfo.copy(sampleDescription = context.getString(example.descriptionResId)), result = null) }
            } else {
                val example = GsExampleDataGenerator.generateCoarse()
                _uiState.update { it.copy(coarseSoilData = example.data, testInfo = it.testInfo.copy(sampleDescription = context.getString(example.descriptionResId)), result = null) }
            }
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
                val report = withContext(Dispatchers.IO) {
                    SpecificGravityReport(
                        id = state.currentReportId ?: UUID.randomUUID().toString(),
                        testInfo = state.testInfo,
                        testType = if(state.selectedTab == 0) GsTestType.FINE_SOIL else GsTestType.COARSE_AGG,
                        fineSoilData = if(state.selectedTab == 0) state.fineSoilData else null,
                        coarseSoilData = if(state.selectedTab == 1) state.coarseSoilData else null,
                        result = state.result
                    ).also { repository.saveSpecificGravityReport(it) }
                }
                _uiState.update { it.copy(currentReportId = report.id) }
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
            _uiState.update { it.copy(
                testInfo = TestInfo(),
                fineSoilData = GsFineSoilData(),
                coarseSoilData = GsCoarseSoilData(),
                result = null,
                currentReportId = null
            ) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getSpecificGravityReportById(reportId)?.let { report ->
                _uiState.update {
                    it.copy(
                        testInfo = report.testInfo,
                        fineSoilData = report.fineSoilData ?: GsFineSoilData(),
                        coarseSoilData = report.coarseSoilData ?: GsCoarseSoilData(),
                        result = report.result,
                        selectedTab = if(report.testType == GsTestType.FINE_SOIL) 0 else 1,
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
fun SpecificGravityScreen(
    viewModel: SpecificGravityViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // This screen is now argument-aware via the LaunchedEffect in MainActivity
    // No need to check reportIdToLoad here, MainActivity handles it

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        TestInfoSection(uiState.testInfo, viewModel::onTestInfoChange)

        TabRow(selectedTabIndex = uiState.selectedTab) {
            Tab(
                selected = uiState.selectedTab == 0,
                onClick = { viewModel.onTabSelected(0) },
                text = { Text(stringResource(R.string.gs_fine_soil_tab)) }
            )
            Tab(
                selected = uiState.selectedTab == 1,
                onClick = { viewModel.onTabSelected(1) },
                text = { Text(stringResource(R.string.gs_coarse_agg_tab)) }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        Crossfade(targetState = uiState.selectedTab, label = "tab_crossfade") { tabIndex ->
            when (tabIndex) {
                0 -> FineSoilInputPanel(uiState.fineSoilData, viewModel::onFineSoilDataChange)
                1 -> CoarseSoilInputPanel(uiState.coarseSoilData, viewModel::onCoarseSoilDataChange)
            }
        }

        ActionButtons(
            onCompute = viewModel::calculateGs,
            onLoadExample = { viewModel.loadExampleData(context) }
        )

        AnimatedVisibility(visible = uiState.result != null) {
            uiState.result?.let {
                DataPanel(title = stringResource(R.string.gs_results_title)) {
                    ResultDisplay(
                        title = stringResource(R.string.gs_specific_gravity),
                        value = String.format(Locale.US, "%.3f", it.specificGravity),
                        isPrimary = true
                    )
                }
            }
        }
    }
}

@Composable
fun FineSoilInputPanel(data: GsFineSoilData, onDataChange: (GsFineSoilData) -> Unit) {
    DataPanel(title = stringResource(R.string.gs_fine_soil_title)) {
        NeuralInput(data.pycnometerNumber, { onDataChange(data.copy(pycnometerNumber = it)) }, stringResource(R.string.gs_pycnometer_number), keyboardType = KeyboardType.Text)
        NeuralInput(data.massPycnometer, { onDataChange(data.copy(massPycnometer = it)) }, stringResource(R.string.gs_mass_pycnometer_g))
        NeuralInput(data.massPycnometerDrySoil, { onDataChange(data.copy(massPycnometerDrySoil = it)) }, stringResource(R.string.gs_mass_pycnometer_dry_soil_g))
        NeuralInput(data.massPycnometerSoilWater, { onDataChange(data.copy(massPycnometerSoilWater = it)) }, stringResource(R.string.gs_mass_pycnometer_soil_water_g))
        NeuralInput(data.massPycnometerWater, { onDataChange(data.copy(massPycnometerWater = it)) }, stringResource(R.string.gs_mass_pycnometer_water_g))
        NeuralInput(data.temperature, { onDataChange(data.copy(temperature = it)) }, stringResource(R.string.gs_test_temperature_c))
    }
}

@Composable
fun CoarseSoilInputPanel(data: GsCoarseSoilData, onDataChange: (GsCoarseSoilData) -> Unit) {
    DataPanel(title = stringResource(R.string.gs_coarse_agg_title)) {
        NeuralInput(data.massDry, { onDataChange(data.copy(massDry = it)) }, stringResource(R.string.gs_mass_dry_g))
        NeuralInput(data.massSSD, { onDataChange(data.copy(massSSD = it)) }, stringResource(R.string.gs_mass_ssd_g))
        NeuralInput(data.massSubmerged, { onDataChange(data.copy(massSubmerged = it)) }, stringResource(R.string.gs_mass_submerged_g))
    }
}

