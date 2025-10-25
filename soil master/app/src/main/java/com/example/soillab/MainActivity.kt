package com.example.soillab

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.soillab.aggquality.AggregateQualityScreen
import com.example.soillab.aggquality.AggregateQualityViewModel
import com.example.soillab.aggregatehub.AggregateHubScreen
import com.example.soillab.cbr.CBRTestScreenImproved
import com.example.soillab.cbr.CBRViewModel
import com.example.soillab.data.GsTestType
import com.example.soillab.data.SampleType
import com.example.soillab.di.AppContainer
import com.example.soillab.di.AppViewModelFactory
import com.example.soillab.fielddensity.SandConeScreen
import com.example.soillab.fielddensity.SandConeViewModel
import com.example.soillab.liquidlimittest.AtterbergCoreViewModel
import com.example.soillab.liquidlimittest.AtterbergLimitsScreenImproved
import com.example.soillab.proctor.ProctorScreenImproved
import com.example.soillab.proctor.ProctorViewModel
import com.example.soillab.reports.ReportsHubScreen
import com.example.soillab.reports.ReportsViewModel
import com.example.soillab.sieveanalysis.SieveAnalysisScreenImproved
import com.example.soillab.sieveanalysis.SieveAnalysisViewModel
import com.example.soillab.specificgravity.SpecificGravityScreen
import com.example.soillab.specificgravity.SpecificGravityViewModel
import com.example.soillab.ui.BootScreen
import com.example.soillab.ui.HubScreen
import com.example.soillab.ui.SettingsScreen
import com.example.soillab.ui.theme.SoilLabTheme
import com.example.soillab.util.LanguageViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

// --- معرفات شاشات التطبيق ---
enum class AppScreen {
    BOOT_SEQUENCE,
    DASHBOARD,
    ATTERBERG_LIMITS_TEST,
    REPORTS_HUB,
    CBR_TEST,
    SETTINGS,
    SIEVE_ANALYSIS,
    PROJECTS,
    PROCTOR_TEST,
    SPECIFIC_GRAVITY,
    FIELD_DENSITY,
    AGGREGATE_QUALITY,
    AGGREGATE_HUB // Added new hub screen
}

// --- ViewModel المركزي (العقل المدبر للتنقل) ---
class AppCoordinatorViewModel : ViewModel() {
    private val _currentScreen = MutableStateFlow(AppScreen.BOOT_SEQUENCE)
    val currentScreen = _currentScreen.asStateFlow()

    private val _reportToEditId = MutableStateFlow<String?>(null)
    val reportToEditId = _reportToEditId.asStateFlow()

    private val _navArgs = MutableStateFlow<Any?>(null)
    val navArgs = _navArgs.asStateFlow()

    fun navigateTo(screen: AppScreen, reportId: String? = null, args: Any? = null) {
        _reportToEditId.value = reportId
        _navArgs.value = args // Store args
        _currentScreen.value = screen
    }

    fun clearNavArgs() {
        _navArgs.value = null
    }

    fun onTestFinished() {
        _reportToEditId.value = null
        navigateTo(AppScreen.REPORTS_HUB)
    }

    fun goBack() {
        when (_currentScreen.value) {
            AppScreen.ATTERBERG_LIMITS_TEST, AppScreen.CBR_TEST, AppScreen.SIEVE_ANALYSIS, AppScreen.PROCTOR_TEST, AppScreen.SPECIFIC_GRAVITY, AppScreen.FIELD_DENSITY, AppScreen.AGGREGATE_QUALITY, AppScreen.AGGREGATE_HUB -> navigateTo(AppScreen.DASHBOARD)
            AppScreen.REPORTS_HUB, AppScreen.PROJECTS, AppScreen.SETTINGS -> navigateTo(AppScreen.DASHBOARD)
            else -> {} // Do nothing on dashboard or boot
        }
    }
}

// --- نقطة الدخول الرئيسية للتطبيق ---
class MainActivity : ComponentActivity() {
    private lateinit var appContainer: AppContainer
    private val languageViewModel: LanguageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This MUST be called before setContent

        appContainer = AppContainer(applicationContext)

        setContent {
            val currentLanguage by languageViewModel.currentLanguage.collectAsState()
            val localizedContext = remember(currentLanguage) { this.wrapInLocale(currentLanguage) }

            CompositionLocalProvider(LocalContext provides localizedContext) {
                SoilLabTheme {
                    SoilLabApp(appContainer = appContainer, languageViewModel = languageViewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoilLabApp(appContainer: AppContainer, languageViewModel: LanguageViewModel) {
    val coordinatorViewModel: AppCoordinatorViewModel = viewModel()
    val currentScreen by coordinatorViewModel.currentScreen.collectAsState()
    val reportIdToEdit by coordinatorViewModel.reportToEditId.collectAsState()
    val context = LocalContext.current
    val factory = AppViewModelFactory(appContainer.reportRepository)

    // ViewModels for each screen
    val atterbergViewModel: AtterbergCoreViewModel = viewModel(factory = factory)
    val cbrViewModel: CBRViewModel = viewModel(factory = factory)
    val sieveViewModel: SieveAnalysisViewModel = viewModel(factory = factory)
    val reportsViewModel: ReportsViewModel = viewModel(factory = factory)
    val proctorViewModel: ProctorViewModel = viewModel(factory = factory)
    val gsViewModel: SpecificGravityViewModel = viewModel(factory = factory)
    val sandConeViewModel: SandConeViewModel = viewModel(factory = factory)
    val aggQualityViewModel: AggregateQualityViewModel = viewModel(factory = factory)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Centralized Snackbar observers for all ViewModels
    LaunchedEffect(Unit) {
        atterbergViewModel.userMessage.collect { message ->
            message?.let { scope.launch { snackbarHostState.showSnackbar(it) } }
        }
    }
    LaunchedEffect(Unit) {
        cbrViewModel.userMessage.collect { message ->
            message?.let { scope.launch { snackbarHostState.showSnackbar(it) } }
        }
    }
    LaunchedEffect(Unit) {
        sieveViewModel.userMessage.collect { message ->
            message?.let { scope.launch { snackbarHostState.showSnackbar(it) } }
        }
    }
    LaunchedEffect(Unit) {
        proctorViewModel.userMessage.collect { message ->
            message?.let { scope.launch { snackbarHostState.showSnackbar(it) } }
        }
    }
    LaunchedEffect(Unit) {
        gsViewModel.userMessage.collect { message ->
            message?.let { scope.launch { snackbarHostState.showSnackbar(it) } }
        }
    }
    LaunchedEffect(Unit) {
        sandConeViewModel.userMessage.collect { message ->
            message?.let { scope.launch { snackbarHostState.showSnackbar(it) } }
        }
    }
    LaunchedEffect(Unit) {
        aggQualityViewModel.userMessage.collect { message ->
            message?.let { scope.launch { snackbarHostState.showSnackbar(it) } }
        }
    }

    BackHandler(enabled = currentScreen != AppScreen.DASHBOARD && currentScreen != AppScreen.BOOT_SEQUENCE) {
        coordinatorViewModel.goBack()
    }

    LaunchedEffect(Unit) {
        delay(2500L) // Shortened boot time
        coordinatorViewModel.navigateTo(AppScreen.DASHBOARD)
    }

    Scaffold(
        topBar = {
            if (currentScreen != AppScreen.BOOT_SEQUENCE) {
                DynamicTopAppBar(
                    currentScreen = currentScreen,
                    onSave = {
                        when (currentScreen) {
                            AppScreen.ATTERBERG_LIMITS_TEST -> atterbergViewModel.saveReport(context)
                            AppScreen.CBR_TEST -> cbrViewModel.saveReport(context)
                            AppScreen.SIEVE_ANALYSIS -> sieveViewModel.saveReport(context)
                            AppScreen.PROCTOR_TEST -> proctorViewModel.saveReport(context)
                            AppScreen.SPECIFIC_GRAVITY -> gsViewModel.saveReport(context)
                            AppScreen.FIELD_DENSITY -> sandConeViewModel.saveReport(context)
                            AppScreen.AGGREGATE_QUALITY -> aggQualityViewModel.saveReport(context)
                            else -> {}
                        }
                    },
                    onNavigateBack = { coordinatorViewModel.goBack() },
                    onNavigateToSettings = { coordinatorViewModel.navigateTo(AppScreen.SETTINGS) }
                )
            }
        },
        bottomBar = {
            if (currentScreen != AppScreen.BOOT_SEQUENCE) {
                BottomNavigationBar(currentScreen = currentScreen) { screen, reportId ->
                    coordinatorViewModel.navigateTo(screen, reportId)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            Crossfade(targetState = currentScreen, animationSpec = tween(500), label = "screen_crossfade") { screen ->
                when (screen) {
                    AppScreen.BOOT_SEQUENCE -> BootScreen()
                    AppScreen.DASHBOARD -> HubScreen { dest, id ->
                        coordinatorViewModel.navigateTo(dest, id)
                    }
                    AppScreen.ATTERBERG_LIMITS_TEST -> AtterbergLimitsScreenImproved(
                        viewModel = atterbergViewModel,
                        reportIdToLoad = reportIdToEdit,
                        onNavigateBack = { coordinatorViewModel.goBack() }
                    )
                    AppScreen.REPORTS_HUB -> ReportsHubScreen(
                        viewModel = reportsViewModel,
                        onLoadAtterbergReport = { reportId: String -> coordinatorViewModel.navigateTo(AppScreen.ATTERBERG_LIMITS_TEST, reportId) },
                        onLoadCBRReport = { reportId: String -> coordinatorViewModel.navigateTo(AppScreen.CBR_TEST, reportId) },
                        onLoadSieveReport = { reportId: String -> coordinatorViewModel.navigateTo(AppScreen.SIEVE_ANALYSIS, reportId) },
                        onLoadProctorReport = { reportId: String -> coordinatorViewModel.navigateTo(AppScreen.PROCTOR_TEST, reportId) },
                        onLoadSpecificGravityReport = { reportId: String -> coordinatorViewModel.navigateTo(AppScreen.SPECIFIC_GRAVITY, reportId) },
                        onLoadFieldDensityReport = { reportId: String -> coordinatorViewModel.navigateTo(AppScreen.FIELD_DENSITY, reportId) },
                        onLoadLAAbrasionReport = { reportId: String -> coordinatorViewModel.navigateTo(AppScreen.AGGREGATE_QUALITY, reportId) }, // Can pass args here if needed
                        onLoadFlakinessReport = { reportId: String -> coordinatorViewModel.navigateTo(AppScreen.AGGREGATE_QUALITY, reportId) }, // Can pass args here if needed
                        onNavigateBack = { coordinatorViewModel.goBack() }
                    )
                    AppScreen.CBR_TEST -> CBRTestScreenImproved(
                        viewModel = cbrViewModel,
                        reportIdToLoad = reportIdToEdit,
                        onNavigateBack = { coordinatorViewModel.goBack() }
                    )
                    AppScreen.SIEVE_ANALYSIS -> {
                        LaunchedEffect(reportIdToEdit, coordinatorViewModel.navArgs.collectAsState().value) {
                            val args = coordinatorViewModel.navArgs.value
                            if (args is SampleType) {
                                sieveViewModel.initializeWithSampleType(args)
                                coordinatorViewModel.clearNavArgs()
                            } else {
                                sieveViewModel.loadReportForEditing(reportIdToEdit)
                            }
                        }
                        SieveAnalysisScreenImproved(
                            viewModel = sieveViewModel,
                            onNavigateBack = { coordinatorViewModel.goBack() }
                        )
                    }
                    AppScreen.PROCTOR_TEST -> ProctorScreenImproved(
                        viewModel = proctorViewModel,
                        reportIdToLoad = reportIdToEdit,
                        onNavigateBack = { coordinatorViewModel.goBack() }
                    )
                    AppScreen.SPECIFIC_GRAVITY -> {
                        LaunchedEffect(reportIdToEdit, coordinatorViewModel.navArgs.collectAsState().value) {
                            val args = coordinatorViewModel.navArgs.value
                            if (args is GsTestType) {
                                gsViewModel.initializeWithTestType(args)
                                coordinatorViewModel.clearNavArgs()
                            } else {
                                gsViewModel.loadReportForEditing(reportIdToEdit)
                            }
                        }
                        SpecificGravityScreen(
                            viewModel = gsViewModel,
                            // reportIdToLoad = reportIdToEdit, // <-- THIS WAS THE ERROR
                            onNavigateBack = { coordinatorViewModel.goBack() }
                        )
                    }
                    AppScreen.FIELD_DENSITY -> SandConeScreen(
                        viewModel = sandConeViewModel,
                        reportIdToLoad = reportIdToEdit,
                        onNavigateBack = { coordinatorViewModel.goBack() }
                    )
                    AppScreen.AGGREGATE_HUB -> AggregateHubScreen { screen: AppScreen, args: Any? ->
                        coordinatorViewModel.navigateTo(screen, null, args)
                    }
                    AppScreen.AGGREGATE_QUALITY -> AggregateQualityScreen(
                        viewModel = aggQualityViewModel,
                        reportIdToLoad = reportIdToEdit,
                        onNavigateBack = { coordinatorViewModel.goBack() }
                    )
                    AppScreen.PROJECTS -> {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text("Projects Screen (Future)", color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                    AppScreen.SETTINGS -> {
                        SettingsScreen(languageViewModel = languageViewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicTopAppBar(
    currentScreen: AppScreen,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val isTestScreen = currentScreen in listOf(
        AppScreen.ATTERBERG_LIMITS_TEST,
        AppScreen.CBR_TEST,
        AppScreen.SIEVE_ANALYSIS,
        AppScreen.PROCTOR_TEST,
        AppScreen.SPECIFIC_GRAVITY,
        AppScreen.FIELD_DENSITY,
        AppScreen.AGGREGATE_QUALITY
    )
    val isDashboard = currentScreen == AppScreen.DASHBOARD

    val title = when (currentScreen) {
        AppScreen.DASHBOARD -> "Soil Master"
        AppScreen.REPORTS_HUB -> stringResource(R.string.reports_hub_title)
        AppScreen.PROJECTS -> "Projects"
        AppScreen.SETTINGS -> "Settings"
        AppScreen.ATTERBERG_LIMITS_TEST -> stringResource(R.string.atterberg_title)
        AppScreen.CBR_TEST -> stringResource(R.string.cbr_title)
        AppScreen.SIEVE_ANALYSIS -> stringResource(R.string.sieve_title)
        AppScreen.PROCTOR_TEST -> stringResource(R.string.proctor_title)
        AppScreen.SPECIFIC_GRAVITY -> stringResource(R.string.gs_title)
        AppScreen.FIELD_DENSITY -> stringResource(R.string.sandcone_title)
        AppScreen.AGGREGATE_QUALITY -> stringResource(R.string.agg_quality_title)
        AppScreen.AGGREGATE_HUB -> stringResource(R.string.aggregate_tests_title)
        else -> ""
    }

    TopAppBar(
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (isDashboard) {
                Icon(
                    imageVector = Icons.Default.Grain,
                    contentDescription = "Logo",
                    modifier = Modifier.padding(start = 12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        },
        actions = {
            if (isDashboard) {
                IconButton(onClick = { /* TODO: Search */ }) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search")
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
            if (isTestScreen) {
                IconButton(onClick = onSave) {
                    Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
fun BottomNavigationBar(currentScreen: AppScreen, onNavigate: (screen: AppScreen, reportId: String?) -> Unit) {
    val items = listOf(
        BottomNavItem(AppScreen.DASHBOARD, Icons.Default.Dashboard, "Dashboard"),
        BottomNavItem(AppScreen.REPORTS_HUB, Icons.Default.Article, "Reports"),
        BottomNavItem(AppScreen.PROJECTS, Icons.Default.Folder, "Projects")
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title) },
                selected = currentScreen == item.screen ||
                        (currentScreen !in listOf(AppScreen.DASHBOARD, AppScreen.REPORTS_HUB, AppScreen.PROJECTS) && item.screen == AppScreen.DASHBOARD),
                onClick = { onNavigate(item.screen, null) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            )
        }
    }
}

data class BottomNavItem(val screen: AppScreen, val icon: ImageVector, val title: String)

fun Context.wrapInLocale(language: String): ContextWrapper {
    val config = resources.configuration
    val locale = Locale(language)
    Locale.setDefault(locale)
    config.setLocale(locale)
    val context = createConfigurationContext(config)
    return ContextWrapper(context)
}

