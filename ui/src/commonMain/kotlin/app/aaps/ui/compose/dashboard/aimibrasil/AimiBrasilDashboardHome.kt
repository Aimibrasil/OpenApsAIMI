package app.aaps.ui.compose.dashboard.aimibrasil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.dashboard.AimiGlassDashboardData
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.source.CgmWarmupStatus
import app.aaps.core.keys.StringNonKey
import app.aaps.core.ui.compose.*
import androidx.compose.foundation.isSystemInDarkTheme
import app.aaps.core.ui.compose.navigation.NavigationRequest
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

private val NOTIFICATION_FAB_BOTTOM = 24.dp
private val NOTIFICATION_FAB_CLEARANCE = 88.dp

@Composable
fun AimiBrasilDashboardHome(
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
    homeViewModel: AimiBrHomeViewModel = metroViewModel(),
) {
    val profileUtil = LocalProfileUtil.current
    val dateUtil = LocalDateUtil.current
    val decimalFormatter = LocalDecimalFormatter.current
    val isDark = isSystemInDarkTheme()

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

    val bgInfo = bgInfoState.bgInfo
    val deltaValue = bgInfo?.shortAvgDeltaText?.takeIf { bgInfo.shortAvgDelta?.isFinite() == true }
        ?: bgInfo?.deltaText ?: "--"
    val deltaText = "Δ $deltaValue"
    val iobTotal = iobState.iobTotal.takeIf { iobState.text.isNotEmpty() }
    val iobText = iobTotal?.let {
        if (it >= 0) "${decimalFormatter.to2Decimal(it)} U"
        else "+${decimalFormatter.to2Decimal(it)} U".replace("+-", "-")
    }
    val timeAgo = bgInfo?.let { dateUtil.minAgoShort(it.timestamp) } ?: ""

    val state = buildAimiBrUiState(
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

    val isMmol = profileUtil.units == GlucoseUnit.MMOL
    val lowLine = if (isMmol) 3.9f else 70f
    val highLine = if (isMmol) 10.0f else 180f
    val currentBgValue = remember(state.currentBg) {
        state.currentBg.replace(",", ".").toFloatOrNull() ?: 0f
    }

    val clientRole = LocalClientRole.current
    val commands = remember(onNavigate, commandsAllowed, clientRole) {
        AimiBrNavigationCommands(onNavigate, commandsAllowed, clientRole)
    }

    CompositionLocalProvider(LocalAimiBrHeroCommands provides commands) {
        AimiBrasilOverviewContent(
            graphViewModel = graphViewModel,
            state = state,
            currentBgValue = currentBgValue,
            lowLine = lowLine,
            highLine = highLine,
            isMmol = isMmol,
            isDark = isDark,
            nowMs = nowMs,
            onOpenActions = onOpenActions,
            notifications = notifications,
            onDismissNotification = onDismissNotification,
            onNotificationActionClick = onNotificationActionClick,
            pumpStatus = homeStatus.pumpStatus,
            modifier = modifier,
        )
    }
}
@Composable
internal fun AimiBrasilOverviewContent(
    graphViewModel: GraphViewModel,
    state: AimiBrUiState,
    currentBgValue: Float,
    lowLine: Float,
    highLine: Float,
    isMmol: Boolean,
    isDark: Boolean,
    nowMs: Long,
    onOpenActions: () -> Unit,
    notifications: List<AapsNotification>,
    onDismissNotification: (AapsNotification) -> Unit,
    onNotificationActionClick: (AapsNotification) -> Unit,
    pumpStatus: AimiBrPumpStatusLine? = null,
    modifier: Modifier = Modifier,
) {
    val commands = LocalAimiBrHeroCommands.current
    val preferences = LocalPreferences.current
    var showNotificationSheet by remember { mutableStateOf(false) }

    val savedPrefsRaw by preferences.observe(StringNonKey.GlassSelectedPills).collectAsStateWithLifecycle()
    val visualStyle = remember(savedPrefsRaw) {
        val raw = savedPrefsRaw.substringAfter("AIMIBR_STYLE:", "").substringBefore(";")
        AimiBrVisualStyle.entries.firstOrNull { it.name == raw } ?: AimiBrVisualStyle.NEUTRAL
    }
    val activeMetrics = remember(savedPrefsRaw) {
        if ("AIMIBR_METRICS:" !in savedPrefsRaw) {
            AimiBrMetricItem.defaultOrder
        } else {
            val raw = savedPrefsRaw.substringAfter("AIMIBR_METRICS:", "").substringBefore(";")
            if (raw.isBlank()) emptyList()
            else raw.split(",").mapNotNull { id -> AimiBrMetricItem.entries.firstOrNull { it.id == id } }
        }
    }
    val isImmersive = remember(savedPrefsRaw) {
        savedPrefsRaw.substringAfter("AIMIBR_IMMERSIVE:", "false").substringBefore(";") == "true"
    }

    LaunchedEffect(isImmersive) {
        AimiBrImmersiveState.value = isImmersive
    }
    DisposableEffect(Unit) {
        onDispose { AimiBrImmersiveState.value = false }
    }

    fun saveAimiBrPrefs(
        newStyle: AimiBrVisualStyle = visualStyle,
        newMetrics: List<AimiBrMetricItem> = activeMetrics,
        newImmersive: Boolean = isImmersive,
    ) {
        val base = savedPrefsRaw.substringBefore("|AIMIBR_PREFS|")
        val metricsStr = newMetrics.joinToString(",") { it.id }
        preferences.put(
            StringNonKey.GlassSelectedPills,
            "$base|AIMIBR_PREFS|AIMIBR_STYLE:${newStyle.name};AIMIBR_METRICS:$metricsStr;AIMIBR_IMMERSIVE:$newImmersive;"
        )
    }

    val stale = GlassCgmLinkStatus.isStale(state.isGlucoseActual, state.glucoseTimestampMs, nowMs)
    val zone = classifyAimiBrZone(currentBgValue, stale, lowLine, highLine, isMmol)
    val zonePalette = zone.zoneColors()

    val screenBg = if (isDark) {
        Brush.verticalGradient(listOf(Color(0xFF0E121A), Color(0xFF080A0F), Color(0xFF0D1118)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1), Color(0xFFF1F5F9)))
    }

    Box(modifier = modifier.fillMaxSize().background(screenBg)) {
        if (visualStyle == AimiBrVisualStyle.LIQUID_GLASS) {
            AimiBrLiquidBackdrop(isDark = isDark, zoneGlow = zonePalette.glow)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AimiBrasilStatusCard(
                state = state,
                pumpStatus = pumpStatus,
                isDark = isDark,
                nowMs = nowMs,
                commands = commands,
                onOpenTools = onOpenActions,
                currentBgValue = currentBgValue,
                lowLine = lowLine,
                highLine = highLine,
                isMmol = isMmol,
                onOpenMealAdvisor = commands::openMealAdvisor,
                onOpenAimiContext = commands::openAimiContext,
                visualStyle = visualStyle,
                onChangeVisualStyle = { saveAimiBrPrefs(newStyle = it) },
                activeMetrics = activeMetrics,
                onChangeActiveMetrics = { saveAimiBrPrefs(newMetrics = it) },
                isImmersive = isImmersive,
                onToggleImmersive = { saveAimiBrPrefs(newImmersive = !isImmersive) },
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aimiBrSurface(isDark = isDark, style = visualStyle, zoneAccent = zonePalette.glow, radius = 20.dp)
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
                    GraphsSection(
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
            notificationCount = notifications.size,
            highestLevel = notifications.minByOrNull { it.level.ordinal }?.level,
            onClick = { showNotificationSheet = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = NOTIFICATION_FAB_BOTTOM),
        )
    }

    if (showNotificationSheet && notifications.isNotEmpty()) {
        NotificationBottomSheet(notifications, { showNotificationSheet = false }, onDismissNotification, onNotificationActionClick)
    }
}
