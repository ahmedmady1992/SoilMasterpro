package com.example.soillab.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soillab.R
import com.example.soillab.data.*
import com.example.soillab.ui.theme.Yellow500
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

// --- Enums for Sorting and Filtering ---
enum class FilterType { ALL, ATTERBERG, CBR, SIEVE, PROCTOR, GS, FIELD_DENSITY, AGG_QUALITY }
enum class SortOption { DATE, BOREHOLE }
enum class SortDirection { DESC, ASC }

data class SortState(val option: SortOption = SortOption.DATE, val direction: SortDirection = SortDirection.DESC)

data class ReportsUiState(
    val atterbergReports: List<AtterbergReportSummary> = emptyList(),
    val cbrReports: List<CBRReport> = emptyList(),
    val sieveReports: List<SieveAnalysisReport> = emptyList(),
    val proctorReports: List<ProctorReport> = emptyList(),
    val gsReports: List<SpecificGravityReport> = emptyList(),
    val sandConeReports: List<SandConeReport> = emptyList(),
    val laAbrasionReports: List<LAAbrasionReport> = emptyList(),
    val flakinessReports: List<FlakinessReport> = emptyList(),
    val isLoading: Boolean = true,
    val searchTerm: String = "",
    val filterType: FilterType = FilterType.ALL,
    val sortState: SortState = SortState()
)

class ReportsViewModel(private val repository: IReportRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState = _uiState.asStateFlow()

    private val _searchTerm = MutableStateFlow("")
    private val _filterType = MutableStateFlow(FilterType.ALL)
    private val _sortState = MutableStateFlow(SortState())

    init {
        viewModelScope.launch {
            combine(
                repository.atterbergReports,
                repository.cbrReports,
                repository.sieveAnalysisReports,
                repository.proctorReports,
                repository.specificGravityReports,
                repository.sandConeReports,
                repository.laAbrasionReports,
                repository.flakinessReports,
                _searchTerm,
                _filterType,
                _sortState
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val atterbergList = values[0] as List<AtterbergReport>
                @Suppress("UNCHECKED_CAST")
                val cbrList = values[1] as List<CBRReport>
                @Suppress("UNCHECKED_CAST")
                val sieveList = values[2] as List<SieveAnalysisReport>
                @Suppress("UNCHECKED_CAST")
                val proctorList = values[3] as List<ProctorReport>
                @Suppress("UNCHECKED_CAST")
                val gsList = values[4] as List<SpecificGravityReport>
                @Suppress("UNCHECKED_CAST")
                val sandConeList = values[5] as List<SandConeReport>
                @Suppress("UNCHECKED_CAST")
                val laAbrasionList = values[6] as List<LAAbrasionReport>
                @Suppress("UNCHECKED_CAST")
                val flakinessList = values[7] as List<FlakinessReport>
                val term = values[8] as String
                val filter = values[9] as FilterType
                val sort = values[10] as SortState

                val atterbergSummaries = atterbergList.map { it.summary }

                // 1. Filtering
                val filteredAtterberg = if (filter == FilterType.ALL || filter == FilterType.ATTERBERG) atterbergSummaries else emptyList()
                val filteredCbr = if (filter == FilterType.ALL || filter == FilterType.CBR) cbrList else emptyList()
                val filteredSieve = if (filter == FilterType.ALL || filter == FilterType.SIEVE) sieveList else emptyList()
                val filteredProctor = if (filter == FilterType.ALL || filter == FilterType.PROCTOR) proctorList else emptyList()
                val filteredGs = if (filter == FilterType.ALL || filter == FilterType.GS) gsList else emptyList()
                val filteredSandCone = if (filter == FilterType.ALL || filter == FilterType.FIELD_DENSITY) sandConeList else emptyList()
                val filteredLaAbrasion = if (filter == FilterType.ALL || filter == FilterType.AGG_QUALITY) laAbrasionList else emptyList()
                val filteredFlakiness = if (filter == FilterType.ALL || filter == FilterType.AGG_QUALITY) flakinessList else emptyList()

                // 2. Searching
                val searchedAtterberg = filteredAtterberg.filter { it.boreholeNo.contains(term, true) || it.sampleNo.contains(term, true) }
                val searchedCbr = filteredCbr.filter { it.testParameters.testInfo.boreholeNo.contains(term, true) }
                val searchedSieve = filteredSieve.filter { it.testInfo.boreholeNo.contains(term, true) }
                val searchedProctor = filteredProctor.filter { it.parameters.testInfo.boreholeNo.contains(term, true) }
                val searchedGs = filteredGs.filter { it.testInfo.boreholeNo.contains(term, true) }
                val searchedSandCone = filteredSandCone.filter { it.testInfo.boreholeNo.contains(term, true) }
                val searchedLaAbrasion = filteredLaAbrasion.filter { it.testInfo.boreholeNo.contains(term, true) }
                val searchedFlakiness = filteredFlakiness.filter { it.testInfo.boreholeNo.contains(term, true) }

                // 3. Sorting
                val sortedAtterberg = sortAtterberg(searchedAtterberg, sort)
                val sortedCbr = sortCbr(searchedCbr, sort)
                val sortedSieve = sortSieve(searchedSieve, sort)
                val sortedProctor = sortProctor(searchedProctor, sort)
                val sortedGs = sortGs(searchedGs, sort)
                val sortedSandCone = sortSandCone(searchedSandCone, sort)
                val sortedLaAbrasion = sortLaAbrasion(searchedLaAbrasion, sort)
                val sortedFlakiness = sortFlakiness(searchedFlakiness, sort)

                ReportsUiState(
                    atterbergReports = sortedAtterberg,
                    cbrReports = sortedCbr,
                    sieveReports = sortedSieve,
                    proctorReports = sortedProctor,
                    gsReports = sortedGs,
                    sandConeReports = sortedSandCone,
                    laAbrasionReports = sortedLaAbrasion,
                    flakinessReports = sortedFlakiness,
                    isLoading = false,
                    searchTerm = term,
                    filterType = filter,
                    sortState = sort
                )
            }.collect { combinedState ->
                _uiState.value = combinedState
            }
        }
    }

    private fun sortAtterberg(list: List<AtterbergReportSummary>, sort: SortState): List<AtterbergReportSummary> {
        return when (sort.option) {
            SortOption.DATE -> if (sort.direction == SortDirection.DESC) list.sortedByDescending { it.testDate } else list.sortedBy { it.testDate }
            SortOption.BOREHOLE -> if (sort.direction == SortDirection.DESC) list.sortedByDescending { it.boreholeNo } else list.sortedBy { it.boreholeNo }
        }
    }

    private fun sortCbr(list: List<CBRReport>, sort: SortState): List<CBRReport> {
        return when (sort.option) {
            SortOption.DATE -> if (sort.direction == SortDirection.DESC) list.sortedByDescending { it.testParameters.testInfo.testDate } else list.sortedBy { it.testParameters.testInfo.testDate }
            SortOption.BOREHOLE -> if (sort.direction == SortDirection.DESC) list.sortedByDescending { it.testParameters.testInfo.boreholeNo } else list.sortedBy { it.testParameters.testInfo.boreholeNo }
        }
    }

    private fun sortSieve(list: List<SieveAnalysisReport>, sort: SortState): List<SieveAnalysisReport> {
        return when (sort.option) {
            SortOption.DATE -> if (sort.direction == SortDirection.DESC) list.sortedByDescending { it.testInfo.testDate } else list.sortedBy { it.testInfo.testDate }
            SortOption.BOREHOLE -> if (sort.direction == SortDirection.DESC) list.sortedByDescending { it.testInfo.boreholeNo } else list.sortedBy { it.testInfo.boreholeNo }
        }
    }

    private fun sortProctor(list: List<ProctorReport>, sort: SortState): List<ProctorReport> {
        return when (sort.option) {
            SortOption.DATE -> if (sort.direction == SortDirection.DESC) list.sortedByDescending { it.parameters.testInfo.testDate } else list.sortedBy { it.parameters.testInfo.testDate }
            SortOption.BOREHOLE -> if (sort.direction == SortDirection.DESC) list.sortedByDescending { it.parameters.testInfo.boreholeNo } else list.sortedBy { it.parameters.testInfo.boreholeNo }
        }
    }

    private fun sortGs(list: List<SpecificGravityReport>, sort: SortState): List<SpecificGravityReport> {
        return when (sort.option) {
            SortOption.DATE -> if (sort.direction == SortDirection.DESC) list.sortedByDescending { it.testInfo.testDate } else list.sortedBy { it.testInfo.testDate }
            SortOption.BOREHOLE -> if (sort.direction == SortDirection.DESC) list.sortedByDescending { it.testInfo.boreholeNo } else list.sortedBy { it.testInfo.boreholeNo }
        }
    }

    private fun sortSandCone(list: List<SandConeReport>, sort: SortState): List<SandConeReport> {
        return when (sort.option) {
            SortOption.DATE -> if (sort.direction == SortDirection.DESC) list.sortedByDescending { it.testInfo.testDate } else list.sortedBy { it.testInfo.testDate }
            SortOption.BOREHOLE -> if (sort.direction == SortDirection.DESC) list.sortedByDescending { it.testInfo.boreholeNo } else list.sortedBy { it.testInfo.boreholeNo }
        }
    }

    private fun sortLaAbrasion(list: List<LAAbrasionReport>, sort: SortState): List<LAAbrasionReport> {
        return when (sort.option) {
            SortOption.DATE -> if (sort.direction == SortDirection.DESC) list.sortedByDescending { it.testInfo.testDate } else list.sortedBy { it.testInfo.testDate }
            SortOption.BOREHOLE -> if (sort.direction == SortDirection.DESC) list.sortedByDescending { it.testInfo.boreholeNo } else list.sortedBy { it.testInfo.boreholeNo }
        }
    }

    private fun sortFlakiness(list: List<FlakinessReport>, sort: SortState): List<FlakinessReport> {
        return when (sort.option) {
            SortOption.DATE -> if (sort.direction == SortDirection.DESC) list.sortedByDescending { it.testInfo.testDate } else list.sortedBy { it.testInfo.testDate }
            SortOption.BOREHOLE -> if (sort.direction == SortDirection.DESC) list.sortedByDescending { it.testInfo.boreholeNo } else list.sortedBy { it.testInfo.boreholeNo }
        }
    }

    fun onSearchTermChange(term: String) { _searchTerm.value = term }
    fun onFilterChange(filter: FilterType) { _filterType.value = filter }
    fun onSortChange(sort: SortState) { _sortState.value = sort }
    fun deleteAtterbergReport(reportId: String) { viewModelScope.launch { repository.deleteAtterbergReport(reportId) } }
    fun deleteCBRReport(reportId: String) { viewModelScope.launch { repository.deleteCBRReport(reportId) } }
    fun deleteSieveAnalysisReport(reportId: String) { viewModelScope.launch { repository.deleteSieveAnalysisReport(reportId) } }
    fun deleteProctorReport(reportId: String) { viewModelScope.launch { repository.deleteProctorReport(reportId) } }
    fun deleteSpecificGravityReport(reportId: String) { viewModelScope.launch { repository.deleteSpecificGravityReport(reportId) } }
    fun deleteSandConeReport(reportId: String) { viewModelScope.launch { repository.deleteSandConeReport(reportId) } }
    fun deleteLAAbrasionReport(reportId: String) { viewModelScope.launch { repository.deleteLAAbrasionReport(reportId) } }
    fun deleteFlakinessReport(reportId: String) { viewModelScope.launch { repository.deleteFlakinessReport(reportId) } }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsHubScreen(
    viewModel: ReportsViewModel,
    onLoadAtterbergReport: (String) -> Unit,
    onLoadCBRReport: (String) -> Unit,
    onLoadSieveReport: (String) -> Unit,
    onLoadProctorReport: (String) -> Unit,
    onLoadSpecificGravityReport: (String) -> Unit,
    onLoadFieldDensityReport: (String) -> Unit,
    onLoadLAAbrasionReport: (String) -> Unit,
    onLoadFlakinessReport: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SearchAndFilterControls(
            searchTerm = uiState.searchTerm,
            onSearchTermChange = viewModel::onSearchTermChange,
            filterType = uiState.filterType,
            onFilterChange = viewModel::onFilterChange,
            sortState = uiState.sortState,
            onSortChange = viewModel::onSortChange
        )

        if (uiState.isLoading) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (uiState.atterbergReports.isEmpty() && uiState.cbrReports.isEmpty() && uiState.sieveReports.isEmpty() && uiState.proctorReports.isEmpty() && uiState.gsReports.isEmpty() && uiState.sandConeReports.isEmpty() && uiState.laAbrasionReports.isEmpty() && uiState.flakinessReports.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Filled.FindInPage, stringResource(R.string.no_reports_found), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(80.dp))
                    Text(stringResource(R.string.no_reports_found), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 16.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.atterbergReports.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.atterberg_reports)) }
                    items(uiState.atterbergReports, key = { "att_${it.id}" }) { report ->
                        AtterbergReportCard(report = report, onLoad = { onLoadAtterbergReport(report.id) }, onDelete = { viewModel.deleteAtterbergReport(report.id) })
                    }
                }
                if (uiState.cbrReports.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.cbr_reports)) }
                    items(uiState.cbrReports, key = { "cbr_${it.id}" }) { report ->
                        CBRReportCard(report = report, onLoad = { onLoadCBRReport(report.id) }, onDelete = { viewModel.deleteCBRReport(report.id) })
                    }
                }
                if (uiState.sieveReports.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.sieve_reports)) }
                    items(uiState.sieveReports, key = { "sieve_${it.id}" }) { report ->
                        SieveReportCard(report = report, onLoad = { onLoadSieveReport(report.id) }, onDelete = { viewModel.deleteSieveAnalysisReport(report.id) })
                    }
                }
                if (uiState.proctorReports.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.proctor_reports)) }
                    items(uiState.proctorReports, key = { "proctor_${it.id}" }) { report ->
                        ProctorReportCard(report = report, onLoad = { onLoadProctorReport(report.id) }, onDelete = { viewModel.deleteProctorReport(report.id) })
                    }
                }
                if (uiState.gsReports.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.gs_reports)) }
                    items(uiState.gsReports, key = { "gs_${it.id}" }) { report ->
                        SpecificGravityReportCard(report = report, onLoad = { onLoadSpecificGravityReport(report.id) }, onDelete = { viewModel.deleteSpecificGravityReport(report.id) })
                    }
                }
                if (uiState.sandConeReports.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.sandcone_reports)) }
                    items(uiState.sandConeReports, key = { "sandcone_${it.id}" }) { report ->
                        SandConeReportCard(report = report, onLoad = { onLoadFieldDensityReport(report.id) }, onDelete = { viewModel.deleteSandConeReport(report.id) })
                    }
                }
                if (uiState.laAbrasionReports.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.agg_quality_reports_la)) }
                    items(uiState.laAbrasionReports, key = { "la_${it.id}" }) { report ->
                        LAAbrasionReportCard(report = report, onLoad = { onLoadLAAbrasionReport(report.id) }, onDelete = { viewModel.deleteLAAbrasionReport(report.id) })
                    }
                }
                if (uiState.flakinessReports.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.agg_quality_reports_flakiness)) }
                    items(uiState.flakinessReports, key = { "flakiness_${it.id}" }) { report ->
                        FlakinessReportCard(report = report, onLoad = { onLoadFlakinessReport(report.id) }, onDelete = { viewModel.deleteFlakinessReport(report.id) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAndFilterControls(
    searchTerm: String,
    onSearchTermChange: (String) -> Unit,
    filterType: FilterType,
    onFilterChange: (FilterType) -> Unit,
    sortState: SortState,
    onSortChange: (SortState) -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = searchTerm,
            onValueChange = onSearchTermChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
            )
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                SegmentedButton(selected = filterType == FilterType.ALL, onClick = { onFilterChange(FilterType.ALL) }, shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.all)) }
                SegmentedButton(selected = filterType == FilterType.ATTERBERG, onClick = { onFilterChange(FilterType.ATTERBERG) }, shape = MaterialTheme.shapes.small) { Icon(Icons.Default.WaterDrop, contentDescription = null, modifier = Modifier.size(18.dp)) }
                SegmentedButton(selected = filterType == FilterType.CBR, onClick = { onFilterChange(FilterType.CBR) }, shape = MaterialTheme.shapes.small) { Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp)) }
                SegmentedButton(selected = filterType == FilterType.SIEVE, onClick = { onFilterChange(FilterType.SIEVE) }, shape = MaterialTheme.shapes.small) { Icon(Icons.Default.Grain, contentDescription = null, modifier = Modifier.size(18.dp)) }
                SegmentedButton(selected = filterType == FilterType.PROCTOR, onClick = { onFilterChange(FilterType.PROCTOR) }, shape = MaterialTheme.shapes.small) { Icon(Icons.Default.LineWeight, contentDescription = null, modifier = Modifier.size(18.dp)) }
                SegmentedButton(selected = filterType == FilterType.GS, onClick = { onFilterChange(FilterType.GS) }, shape = MaterialTheme.shapes.small) { Icon(Icons.Default.Iso, contentDescription = null, modifier = Modifier.size(18.dp)) }
                SegmentedButton(selected = filterType == FilterType.FIELD_DENSITY, onClick = { onFilterChange(FilterType.FIELD_DENSITY) }, shape = MaterialTheme.shapes.small) { Icon(Icons.Default.Grass, contentDescription = null, modifier = Modifier.size(18.dp)) }
                SegmentedButton(selected = filterType == FilterType.AGG_QUALITY, onClick = { onFilterChange(FilterType.AGG_QUALITY) }, shape = MaterialTheme.shapes.small) { Icon(Icons.Default.Diamond, contentDescription = null, modifier = Modifier.size(18.dp)) }
            }

            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(Icons.Default.Sort, stringResource(R.string.sort_options), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.date_newest_first)) }, onClick = { onSortChange(SortState(SortOption.DATE, SortDirection.DESC)); sortMenuExpanded = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.date_oldest_first)) }, onClick = { onSortChange(SortState(SortOption.DATE, SortDirection.ASC)); sortMenuExpanded = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.bh_no_az)) }, onClick = { onSortChange(SortState(SortOption.BOREHOLE, SortDirection.ASC)); sortMenuExpanded = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.bh_no_za)) }, onClick = { onSortChange(SortState(SortOption.BOREHOLE, SortDirection.DESC)); sortMenuExpanded = false })
                }
            }
        }
    }
}


@Composable
fun AtterbergReportCard(
    report: AtterbergReportSummary,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) { DeleteDialog(onConfirm = { onDelete(); showDeleteDialog = false }, onDismiss = { showDeleteDialog = false }) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = surfaceVariantColor.copy(alpha = 0.5f)),
        modifier = Modifier.border(1.dp, primaryColor.copy(alpha = 0.2f), MaterialTheme.shapes.large)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Science, "Data Record", tint = primaryColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.report_card_bh, report.boreholeNo, report.sampleNo), color = onBackgroundColor, style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.report_card_date, report.testDate), color = onSurfaceVariantColor.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = primaryColor.copy(alpha = 0.2f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DataChip(stringResource(R.string.report_card_class), report.soilClassification, color = primaryColor)
                DataChip(stringResource(R.string.report_card_ll), report.liquidLimit?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A", color = primaryColor)
                DataChip(stringResource(R.string.report_card_pi), report.plasticityIndex?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A", color = primaryColor)
            }
            Spacer(Modifier.height(20.dp))
            ActionRow(onLoad = onLoad, onDelete = { showDeleteDialog = true }, accentColor = primaryColor)
        }
    }
}

@Composable
fun CBRReportCard(report: CBRReport, onLoad: () -> Unit, onDelete: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) { DeleteDialog(onConfirm = { onDelete(); showDeleteDialog = false }, onDismiss = { showDeleteDialog = false }) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    val qualityColor = remember(report.result?.insights) {
        try { Color(android.graphics.Color.parseColor(report.result?.insights?.ratingColorHex ?: "#FFFFFF")) } catch (e: Exception) { primaryColor }
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = surfaceVariantColor.copy(alpha = 0.5f)),
        modifier = Modifier.border(1.dp, primaryColor.copy(alpha = 0.2f), MaterialTheme.shapes.large)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(qualityColor, CircleShape))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Compress, "Data Record", tint = primaryColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(report.testParameters.testInfo.boreholeNo, color = onBackgroundColor, style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.report_card_date, report.testParameters.testInfo.testDate), color = onSurfaceVariantColor.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 24.dp))
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = primaryColor.copy(alpha = 0.2f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DataChip(stringResource(R.string.report_card_final_cbr), report.result?.finalCbrValue?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A", color = qualityColor)
                report.result?.insights?.let { DataChip(stringResource(R.string.report_card_quality), stringResource(it.qualityRatingResId), color = qualityColor) }
            }
            Spacer(Modifier.height(20.dp))
            ActionRow(onLoad = onLoad, onDelete = { showDeleteDialog = true }, accentColor = primaryColor)
        }
    }
}

@Composable
fun SieveReportCard(report: SieveAnalysisReport, onLoad: () -> Unit, onDelete: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) { DeleteDialog(onConfirm = { onDelete(); showDeleteDialog = false }, onDismiss = { showDeleteDialog = false }) }

    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = surfaceVariantColor.copy(alpha = 0.5f)),
        modifier = Modifier.border(1.dp, Yellow500.copy(alpha = 0.2f), MaterialTheme.shapes.large)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Grain, "Data Record", tint = Yellow500, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(report.testInfo.boreholeNo, color = onBackgroundColor, style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.report_card_date, report.testInfo.testDate), color = onSurfaceVariantColor.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = Yellow500.copy(alpha = 0.2f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                report.result?.classification?.let {
                    DataChip("AASHTO", it.aashto.groupName, color = Yellow500)
                    DataChip("USCS", it.uscs.groupName, color = Yellow500)
                }
            }
            Spacer(Modifier.height(20.dp))
            ActionRow(onLoad = onLoad, onDelete = { showDeleteDialog = true }, accentColor = Yellow500)
        }
    }
}

@Composable
fun ProctorReportCard(report: ProctorReport, onLoad: () -> Unit, onDelete: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) { DeleteDialog(onConfirm = { onDelete(); showDeleteDialog = false }, onDismiss = { showDeleteDialog = false }) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = surfaceVariantColor.copy(alpha = 0.5f)),
        modifier = Modifier.border(1.dp, primaryColor.copy(alpha = 0.2f), MaterialTheme.shapes.large)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LineWeight, "Data Record", tint = primaryColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(report.parameters.testInfo.boreholeNo, color = onBackgroundColor, style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.report_card_date, report.parameters.testInfo.testDate), color = onSurfaceVariantColor.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = primaryColor.copy(alpha = 0.2f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DataChip(
                    label = stringResource(R.string.report_card_mdd),
                    value = String.format(Locale.US, "%.3f", report.result.maxDryDensity),
                    color = primaryColor
                )
                DataChip(
                    label = stringResource(R.string.report_card_omc),
                    value = "${String.format(Locale.US, "%.1f", report.result.optimumMoistureContent)}%",
                    color = primaryColor
                )
            }
            Spacer(Modifier.height(20.dp))
            ActionRow(onLoad = onLoad, onDelete = { showDeleteDialog = true }, accentColor = primaryColor)
        }
    }
}

@Composable
fun SpecificGravityReportCard(report: SpecificGravityReport, onLoad: () -> Unit, onDelete: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) { DeleteDialog(onConfirm = { onDelete(); showDeleteDialog = false }, onDismiss = { showDeleteDialog = false }) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = surfaceVariantColor.copy(alpha = 0.5f)),
        modifier = Modifier.border(1.dp, primaryColor.copy(alpha = 0.2f), MaterialTheme.shapes.large)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Iso, "Data Record", tint = primaryColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(report.testInfo.boreholeNo, color = onBackgroundColor, style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.report_card_date, report.testInfo.testDate), color = onSurfaceVariantColor.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = primaryColor.copy(alpha = 0.2f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DataChip(
                    label = stringResource(R.string.report_card_gs),
                    value = String.format(Locale.US, "%.3f", report.result.specificGravity),
                    color = primaryColor
                )
            }
            Spacer(Modifier.height(20.dp))
            ActionRow(onLoad = onLoad, onDelete = { showDeleteDialog = true }, accentColor = primaryColor)
        }
    }
}

@Composable
fun SandConeReportCard(report: SandConeReport, onLoad: () -> Unit, onDelete: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) { DeleteDialog(onConfirm = { onDelete(); showDeleteDialog = false }, onDismiss = { showDeleteDialog = false }) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val compaction = report.result.compactionPercentage
    val isPass = compaction != null && compaction >= (report.result.requiredCompaction)
    val compactionColor = if(compaction == null) onSurfaceVariantColor else if(isPass) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error


    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = surfaceVariantColor.copy(alpha = 0.5f)),
        modifier = Modifier.border(1.dp, primaryColor.copy(alpha = 0.2f), MaterialTheme.shapes.large)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Grass, "Data Record", tint = primaryColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(report.testInfo.boreholeNo, color = onBackgroundColor, style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.report_card_date, report.testInfo.testDate), color = onSurfaceVariantColor.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = primaryColor.copy(alpha = 0.2f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DataChip(
                    label = stringResource(R.string.sandcone_dry_density_g_cm3),
                    value = String.format(Locale.US, "%.3f", report.result.dryDensity),
                    color = primaryColor
                )
                DataChip(
                    label = stringResource(R.string.report_card_compaction),
                    value = compaction?.let { "${String.format(Locale.US, "%.1f", it)}%" } ?: "N/A",
                    color = compactionColor
                )
            }
            Spacer(Modifier.height(20.dp))
            ActionRow(onLoad = onLoad, onDelete = { showDeleteDialog = true }, accentColor = primaryColor)
        }
    }
}

@Composable
fun LAAbrasionReportCard(report: LAAbrasionReport, onLoad: () -> Unit, onDelete: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) { DeleteDialog(onConfirm = { onDelete(); showDeleteDialog = false }, onDismiss = { showDeleteDialog = false }) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val specLimit = report.data.specLimit.toDoubleOrNull()
    val isPass = specLimit != null && report.result.percentLoss <= specLimit
    val lossColor = if (specLimit == null) primaryColor else if (isPass) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = surfaceVariantColor.copy(alpha = 0.5f)),
        modifier = Modifier.border(1.dp, primaryColor.copy(alpha = 0.2f), MaterialTheme.shapes.large)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Diamond, "Data Record", tint = primaryColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(report.testInfo.boreholeNo, color = onBackgroundColor, style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.report_card_date, report.testInfo.testDate), color = onSurfaceVariantColor.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = primaryColor.copy(alpha = 0.2f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DataChip(
                    label = stringResource(R.string.report_card_la_loss),
                    value = "${String.format(Locale.US, "%.1f", report.result.percentLoss)}%",
                    color = lossColor
                )
            }
            Spacer(Modifier.height(20.dp))
            ActionRow(onLoad = onLoad, onDelete = { showDeleteDialog = true }, accentColor = primaryColor)
        }
    }
}

@Composable
fun FlakinessReportCard(report: FlakinessReport, onLoad: () -> Unit, onDelete: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) { DeleteDialog(onConfirm = { onDelete(); showDeleteDialog = false }, onDismiss = { showDeleteDialog = false }) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val specLimit = report.data.flakinessSpecLimit.toDoubleOrNull()
    val fiPass = specLimit != null && report.result.flakinessIndex <= specLimit
    val fiColor = if (specLimit == null) primaryColor else if (fiPass) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = surfaceVariantColor.copy(alpha = 0.5f)),
        modifier = Modifier.border(1.dp, primaryColor.copy(alpha = 0.2f), MaterialTheme.shapes.large)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CompareArrows, "Data Record", tint = primaryColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(report.testInfo.boreholeNo, color = onBackgroundColor, style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.report_card_date, report.testInfo.testDate), color = onSurfaceVariantColor.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = primaryColor.copy(alpha = 0.2f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DataChip(
                    label = stringResource(R.string.report_card_flakiness),
                    value = "${String.format(Locale.US, "%.1f", report.result.flakinessIndex)}%",
                    color = fiColor
                )
                DataChip(
                    label = stringResource(R.string.agg_elongation_index),
                    value = "${String.format(Locale.US, "%.1f", report.result.elongationIndex)}%",
                    color = primaryColor
                )
            }
            Spacer(Modifier.height(20.dp))
            ActionRow(onLoad = onLoad, onDelete = { showDeleteDialog = true }, accentColor = primaryColor)
        }
    }
}


@Composable
private fun ActionRow(onLoad: () -> Unit, onDelete: () -> Unit, accentColor: Color) {
    val errorColor = MaterialTheme.colorScheme.error
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = errorColor.copy(alpha = 0.8f))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onLoad,
            colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(alpha = 0.2f)),
            border = BorderStroke(1.dp, accentColor)
        ) {
            Text(stringResource(R.string.load_data), color = accentColor)
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
    )
}

@Composable
fun DataChip(label: String, value: String, color: Color = MaterialTheme.colorScheme.primary) {
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = onSurfaceVariantColor, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
        Text(value, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun DeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val errorColor = MaterialTheme.colorScheme.error

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surfaceVariantColor,
        title = { Text(stringResource(R.string.confirm_deletion), color = onBackgroundColor) },
        text = { Text(stringResource(R.string.confirm_deletion_message), color = onSurfaceVariantColor) },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = errorColor)) {
                Text(stringResource(R.string.purge_record), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = onSurfaceVariantColor)
            }
        }
    )
}

