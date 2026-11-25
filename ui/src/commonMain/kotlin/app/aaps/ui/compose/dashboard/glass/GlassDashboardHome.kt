package app.aaps.ui.compose.dashboard.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.dashboard.AimiGlassDashboardData
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.source.CgmWarmupStatus
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.*
import app.aaps.core.ui.compose.glass.GlassColors
import app.aaps.core.ui.compose.glass.isGlassDarkMode
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.dashboard.GlassCgmLinkStatus
import app.aaps.ui.compose.notificationsSheet.NotificationBottomSheet
import app.aaps.ui.compose.notificationsSheet.NotificationFab
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.graphs.GraphsSection
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val DEFAULT_RANGE_HOURS = 6
private val NOTIFICATION_FAB_BOTTOM = 72.dp
private val NOTIFICATION_FAB_CLEARANCE = 136.dp

@Composable
fun GlassDashboardHome(
    graphViewModel: GraphViewModel,
    chipsViewModel: ChipsViewModel,
    statusViewModel: StatusViewModel,
    notifications: List<AapsNotification>,
    onDismissNotification: (AapsNotification) -> Unit,
    onNotificationActionClick: (AapsNotification) -> Unit,
    availablePluginClassNames: Set<String>,
    bgSourcePluginClassName: String?,
    commandsAllowed: Boolean,
    onNavigate: (NavigationRequest) -> Unit,
    onOpenActions: () -> Unit,
    aimiDashboardData: Flow<AimiGlassDashboardData>,
    modifier: Modifier = Modifier,
    cgmWarmupStatus: StateFlow<CgmWarmupStatus?>? = null,
    homeViewModel: GlassHomeViewModel = metroViewModel(),
) {
    val preferences = LocalPreferences.current
    val profileUtil = LocalProfileUtil.current
    val dateUtil = LocalDateUtil.current
    val isDark = isGlassDarkMode()

    LaunchedEffect(Unit) {
        if (!preferences.get(BooleanNonKey.GlassTopGridPillsMigrated)) {
            val current = parseSelectedGlassPills(preferences.get(StringNonKey.GlassSelectedPills)).toSet()
            preferences.put(StringNonKey.GlassSelectedPills, serializeSelectedGlassPills(withTopGridDefaultsMerged(current)))
            preferences.put(BooleanNonKey.GlassTopGridPillsMigrated, true)
        }
    }
    val selectedPillsRaw by preferences.observe(StringNonKey.GlassSelectedPills).collectAsStateWithLifecycle()
    val selectedPills = remember(selectedPillsRaw) { parseSelectedGlassPills(selectedPillsRaw) }
    val selectedToolsRaw by preferences.observe(StringNonKey.GlassSelectedTools).collectAsStateWithLifecycle()
    val showIobChart by preferences.observe(BooleanNonKey.GlassShowIobChart).collectAsStateWithLifecycle()
    val selectedTools = remember(selectedToolsRaw) { parseSelectedGlassTools(selectedToolsRaw).toSet() }
    val bgInfoState by graphViewModel.bgInfoState.collectAsStateWithLifecycle()
    val nowMs by graphViewModel.nowTimestamp.collectAsStateWithLifecycle()
    val noCgmWarmup = remember { MutableStateFlow<CgmWarmupStatus?>(null) }
    val cgmWarmup by (cgmWarmupStatus ?: noCgmWarmup).collectAsStateWithLifecycle()
    val iobState by chipsViewModel.iobUiState.collectAsStateWithLifecycle()
    val statusLights by statusViewModel.uiState.collectAsStateWithLifecycle()
    val homeStatus by homeViewModel.status.collectAsStateWithLifecycle()
    val aimiData by aimiDashboardData.collectAsStateWithLifecycle(initialValue = AimiGlassDashboardData())
    val healthStoreStepsToday = aimiData.safety?.stepsToday
    LaunchedEffect(healthStoreStepsToday) { homeViewModel.onHealthStoreStepsToday(healthStoreStepsToday) }
    val treatmentData by graphViewModel.treatmentGraphFlow.collectAsStateWithLifecycle()
    val bgReadings by graphViewModel.bgReadingsFlow.collectAsStateWithLifecycle()
    val iobData by graphViewModel.iobGraphFlow.collectAsStateWithLifecycle()
    val basalData by graphViewModel.basalGraphFlow.collectAsStateWithLifecycle()
    val chartConfig by graphViewModel.chartConfigFlow.collectAsStateWithLifecycle()
    val predictions by graphViewModel.predictionsFlow.collectAsStateWithLifecycle()
    var rangeHours by remember { mutableIntStateOf(DEFAULT_RANGE_HOURS) }
    val bgInfo = bgInfoState.bgInfo
    val deltaValue = bgInfo?.shortAvgDeltaText?.takeIf { bgInfo.shortAvgDelta?.isFinite() == true }
        ?: bgInfo?.deltaText ?: stringResource(CoreUiStrings.value_unavailable_short)
    val deltaText = stringResource(UiStrings.dashboard_glass_delta, deltaValue)
    val iobTotal = iobState.iobTotal.takeIf { iobState.text.isNotEmpty() }
    val iobText = iobTotal?.let {
        if (it >= 0) stringResource(InterfacesStrings.format_insulin_units, it)
        else stringResource(CoreUiStrings.format_insulin_units_signed, it)
    }
    val timeAgo = bgInfo?.let { dateUtil.minAgoShort(it.timestamp) } ?: ""

    val state = buildGlassUiState(
        bgInfo = bgInfo,
        timeAgo = timeAgo,
        deltaText = deltaText,
        unitText = profileUtil.units.displayLabel,
        iobText = iobText,
        bolusIobText = homeStatus.bolusIobText,
        basalIobText = homeStatus.basalIobText,
        cobText = homeStatus.activeCobText,
        lights = statusLights,
        homeStatus = homeStatus,
        treatmentData = treatmentData,
        warmup = cgmWarmup,
        nowMs = nowMs,
    )
    val chartState = remember(rangeHours, nowMs, bgReadings, iobData, basalData, treatmentData, chartConfig, predictions, iobTotal) {
        buildGlassChartState(
            rangeHours = rangeHours, nowEpochMs = nowMs, bgReadings = bgReadings, iobPoints = iobData.iob,
            iobPredictionPoints = iobData.predictions, basalData = basalData, boluses = treatmentData.boluses,
            carbs = treatmentData.carbs, chartConfig = chartConfig,
            mgdlToDisplayUnits = { profileUtil.fromMgdlToUnits(it) }, predictions = predictions, iobTotalU = iobTotal,
        )
    }
    val clientRole = LocalClientRole.current
    val commands = remember(onNavigate, commandsAllowed, clientRole) { GlassNavigationCommands(onNavigate, commandsAllowed, clientRole) }

    CompositionLocalProvider(LocalGlassHeroCommands provides commands) {
        GlassOverviewContent(
            graphViewModel = graphViewModel,
            state = state, chartState = chartState, isDark = isDark, nowMs = nowMs,
            selectedPills = selectedPills,
            onTogglePill = { id, checked ->
                val next = selectedPills.toMutableSet()
                if (checked) next.add(id) else next.remove(id)
                preferences.put(StringNonKey.GlassSelectedPills, serializeSelectedGlassPills(next))
            },
            showIobChart = showIobChart,
            onShowIobChartChange = { preferences.put(BooleanNonKey.GlassShowIobChart, it) },
            selectedTools = selectedTools,
            onToggleTool = { action, checked ->
                val next = selectedTools.toMutableSet()
                if (checked) next.add(action) else next.remove(action)
                preferences.put(StringNonKey.GlassSelectedTools, serializeSelectedGlassTools(next))
            },
            availablePluginClassNames = availablePluginClassNames,
            onToolAction = { action -> dispatchGlassToolAction(action, onNavigate, onOpenActions, bgSourcePluginClassName) },
            notifications = notifications, onDismissNotification = onDismissNotification,
            onNotificationActionClick = onNotificationActionClick, pumpStatus = homeStatus.pumpStatus, modifier = modifier,
        )
    }
}
@Composable
internal fun GlassOverviewContent(
    graphViewModel: GraphViewModel,
    state: GlassUiState, chartState: GlassChartState, isDark: Boolean, nowMs: Long,
    selectedPills: List<GlassPillId>, onTogglePill: (GlassPillId, Boolean) -> Unit,
    showIobChart: Boolean, onShowIobChartChange: (Boolean) -> Unit, selectedTools: Set<DashboardV2ToolAction>,
    onToggleTool: (DashboardV2ToolAction, Boolean) -> Unit, availablePluginClassNames: Set<String>,
    onToolAction: (DashboardV2ToolAction) -> Unit,
    notifications: List<AapsNotification>, onDismissNotification: (AapsNotification) -> Unit,
    onNotificationActionClick: (AapsNotification) -> Unit, pumpStatus: GlassPumpStatusLine? = null, modifier: Modifier = Modifier,
) {
    val commands = LocalGlassHeroCommands.current
    var showTools by remember { mutableStateOf(false) }
    var showPersonalize by remember { mutableStateOf(false) }
    var showNotificationSheet by remember { mutableStateOf(false) }
    LaunchedEffect(showTools) { if (!showTools) showPersonalize = false }

    val stale = GlassCgmLinkStatus.isStale(state.isGlucoseActual, state.glucoseTimestampMs, nowMs)
    val zone = classifyGlassZone(chartState.currentBgValue, stale, chartState.lowLine, chartState.highLine, chartState.lowLine < 20f)
    val zonePalette = zone.zoneColors()

    val screenBg = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF141820),
                Color(0xFF0C0F14),
                Color(0xFF12161E)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFFF1F5F9),
                Color(0xFFE2E8F0),
                Color(0xFFF8FAFC)
            )
        )
    }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = showTools,
        onBackCompleted = { if (showPersonalize) showPersonalize = false else showTools = false },
    )

    Box(modifier = modifier.fillMaxSize().background(screenBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusAgoraCard(
                state = state,
                pumpStatus = pumpStatus,
                isDark = isDark,
                nowMs = nowMs,
                commands = commands,
                onOpenTools = { showTools = true },
                selectedPills = selectedPills,
                currentBgValue = chartState.currentBgValue,
                lowLine = chartState.lowLine,
                highLine = chartState.highLine,
                isMmol = chartState.lowLine < 20f,
                onOpenMealAdvisor = { onToolAction(DashboardV2ToolAction.MEAL_ADVISOR) },
                onOpenAimiContext = { onToolAction(DashboardV2ToolAction.AIMI_CONTEXT) },
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSurface(isDark = isDark, radius = 20.dp, accent = zonePalette.glow)
                    .padding(vertical = 2.dp)
            ) {
                val glassScheme = MaterialTheme.colorScheme.copy(
                    background = Color.Transparent,
                    onBackground = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                    surface = Color.Transparent,
                    onSurface = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                    surfaceVariant = Color.Transparent,
                    onSurfaceVariant = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                    surfaceContainerLowest = Color.Transparent,
                    surfaceContainerLow = Color.Transparent,
                    surfaceContainer = Color.Transparent,
                    surfaceContainerHigh = Color.Transparent,
                    surfaceContainerHighest = Color.Transparent,
                )

                MaterialTheme(colorScheme = glassScheme) {
                    app.aaps.ui.compose.overview.graphs.GraphsSection(
                        graphViewModel = graphViewModel,
                        isSimpleMode = false,
                        iobText = state.iobText,
                        cobText = state.activeCobText,
                        transparentBgBackdrop = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (notifications.isNotEmpty()) Spacer(modifier = Modifier.height(NOTIFICATION_FAB_CLEARANCE))
        }

        NotificationFab(
            notificationCount = notifications.size, highestLevel = notifications.minByOrNull { it.level.ordinal }?.level,
            onClick = { showNotificationSheet = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = NOTIFICATION_FAB_BOTTOM),
        )

        if (showTools) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(GlassColors.screenBgTop(isDark), GlassColors.screenBgBottom(isDark), GlassColors.screenBgTop(isDark))))) {
                val accent = glassOverlayAccent(isDark)
                if (showPersonalize) {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(UiStrings.dashboard_glass_personalize_title), color = GlassColors.textBright(isDark), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            OverlayTextButton(stringResource(CoreUiStrings.ok), accent, FontWeight.Bold) { showPersonalize = false }
                        }
                        GlassPersonalizeScreen(selectedPills.toSet(), onTogglePill, showIobChart, onShowIobChartChange, selectedTools, onToggleTool, isDark, Modifier.fillMaxSize())
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                            OverlayTextButton(stringResource(UiStrings.dashboard_glass_personalize_button), accent, FontWeight.SemiBold) { showPersonalize = true }
                        }
                        DashboardV2ToolsScreen(availablePluginClassNames, { showTools = false; onToolAction(it) }, Modifier.fillMaxSize(), selectedTools)
                    }
                }
            }
        }
    }
    if (showNotificationSheet && notifications.isNotEmpty()) {
        NotificationBottomSheet(notifications, { showNotificationSheet = false }, onDismissNotification, onNotificationActionClick)
    }
}

@Composable
private fun OverlayTextButton(text: String, color: Color, fontWeight: FontWeight, onClick: () -> Unit) {
    Text(text, color = color, fontWeight = fontWeight, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = text, role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp))
}
