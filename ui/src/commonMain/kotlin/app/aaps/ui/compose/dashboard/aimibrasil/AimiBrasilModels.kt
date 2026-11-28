package app.aaps.ui.compose.dashboard.aimibrasil

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.TreatmentGraphData
import app.aaps.core.interfaces.source.CgmWarmupStatus
import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.navigation.GlassDestination
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.ui.compose.overview.statusLights.StatusUiState

private const val NO_VALUE = "--"

internal enum class AimiBrGlucoseZone(val titleEn: String) {
    VERY_LOW("VERY LOW"),
    LOW("LOW"),
    IN_RANGE("IN RANGE"),
    HIGH("HIGH"),
    VERY_HIGH("VERY HIGH"),
    NO_DATA("NO DATA")
}

internal data class AimiBrZoneColors(val core: Color, val deep: Color, val glow: Color, val border: Color)

internal fun AimiBrGlucoseZone.zoneColors(): AimiBrZoneColors = when (this) {
    AimiBrGlucoseZone.VERY_LOW -> AimiBrZoneColors(Color(0xFFD32F2F), Color(0xFF7F0000), Color(0xFFFF1744), Color(0xFFFF8A80))
    AimiBrGlucoseZone.LOW -> AimiBrZoneColors(Color(0xFFE53935), Color(0xFF8E0000), Color(0xFFFF5252), Color(0xFFFFCDD2))
    AimiBrGlucoseZone.IN_RANGE -> AimiBrZoneColors(Color(0xFF1B8A5A), Color(0xFF0A472C), Color(0xFF00E676), Color(0xFFA5D6A7))
    AimiBrGlucoseZone.HIGH -> AimiBrZoneColors(Color(0xFFD48817), Color(0xFF7A4900), Color(0xFFFFB300), Color(0xFFFFE082))
    AimiBrGlucoseZone.VERY_HIGH -> AimiBrZoneColors(Color(0xFFE65100), Color(0xFF872600), Color(0xFFFF9100), Color(0xFFFFCC80))
    AimiBrGlucoseZone.NO_DATA -> AimiBrZoneColors(Color(0xFF455A64), Color(0xFF1C313A), Color(0xFF90A4AE), Color(0xFFCFD8DC))
}

internal fun classifyAimiBrZone(bg: Float, stale: Boolean, low: Float, high: Float, isMmol: Boolean): AimiBrGlucoseZone {
    if (stale || bg <= 0f || !bg.isFinite()) return AimiBrGlucoseZone.NO_DATA
    val vLow = if (isMmol) 0.8f else 15f
    val vHigh = if (isMmol) 2.2f else 40f
    return when {
        bg < low - vLow -> AimiBrGlucoseZone.VERY_LOW
        bg < low -> AimiBrGlucoseZone.LOW
        bg <= high -> AimiBrGlucoseZone.IN_RANGE
        bg <= high + vHigh -> AimiBrGlucoseZone.HIGH
        else -> AimiBrGlucoseZone.VERY_HIGH
    }
}

data class AimiBrUiState(
    val currentBg: String = "--",
    val unit: String = "mg/dL",
    val glucoseRange: BgRange? = null,
    val deltaText: String = "",
    val trendArrow: TrendArrow? = null,
    val timeAgo: String = "--",
    val insulinAge: String = "--",
    val insulinAgeStatus: StatusLevel = StatusLevel.UNSPECIFIED,
    val insulinLabel: String? = null,
    val cannulaAge: String = "--",
    val cannulaAgeStatus: StatusLevel = StatusLevel.UNSPECIFIED,
    val cannulaLabel: String? = null,
    val batteryAge: String = "--",
    val batteryAgeStatus: StatusLevel = StatusLevel.UNSPECIFIED,
    val batteryLabel: String? = null,
    val sensorAge: String = "--",
    val sensorAgeStatus: StatusLevel = StatusLevel.UNSPECIFIED,
    val sensorLabel: String? = null,
    val loopMode: RM.Mode = RM.Mode.CLOSED_LOOP,
    val loopStatusText: String? = null,
    val loopIsRunning: Boolean = true,
    val iobText: String = "--",
    val bolusIobText: String = "--",
    val basalIobText: String = "--",
    val isTempTargetActive: Boolean = false,
    val targetText: String = "--",
    val basalPercentText: String = "--",
    val stepsText: String = "--",
    val hrText: String = "--",
    val lastBolusText: String = "--",
    val lastCarbsText: String = "--",
    val activeCobText: String = "--",
    val profileName: String = "--",
    val isfText: String = "--",
    val isfInlineText: String = "--",
    val autosensRatioText: String = "--",
    val profileIsfText: String = "--",
    val variableIsfText: String = "--",
    val algorithmAutosensText: String = "--",
    val averageIsfText: String = "--",
    val tempTargetText: String = "--",
    val tempTargetRemainingText: String = "",
    val tir: AimiBrTir? = null,
    val warmup: CgmWarmupStatus? = null,
    val isGlucoseActual: Boolean = true,
    val glucoseTimestampMs: Long? = null,
)

data class AimiBrTir(
    val belowPct: Float,
    val inRangePct: Float,
    val abovePct: Float,
    val deltaVsYesterday: Int?,
)

data class AimiBrPumpStatusLine(
    val reservoirText: String,
    val unreachableText: String? = null,
    val siteAgeText: String? = null,
    val siteAgeWarning: Boolean = false,
    val sensorAgeText: String? = null,
    val sensorAgeWarning: Boolean = false,
)
internal interface AimiBrHeroCommands {
    fun openLoop()
    fun openLoopDashboard()
    fun openTarget()
    fun openFullTempTarget()
    fun openProfile()
    fun openCalculator()
    fun openInsulin()
    fun openPump()
    fun openCannula()
    fun openBattery()
    fun openBasal()
    fun openSensorInsert()
    fun openSensorQuality()
    fun openStatsScreen()
    fun openTreatmentsScreen()
    fun openMealAdvisor()
    fun openAimiContext()
}

internal object NoopAimiBrHeroCommands : AimiBrHeroCommands {
    override fun openLoop() {}
    override fun openLoopDashboard() {}
    override fun openTarget() {}
    override fun openFullTempTarget() {}
    override fun openProfile() {}
    override fun openCalculator() {}
    override fun openInsulin() {}
    override fun openPump() {}
    override fun openCannula() {}
    override fun openBattery() {}
    override fun openBasal() {}
    override fun openSensorInsert() {}
    override fun openSensorQuality() {}
    override fun openStatsScreen() {}
    override fun openTreatmentsScreen() {}
    override fun openMealAdvisor() {}
    override fun openAimiContext() {}
}

internal val LocalAimiBrHeroCommands = compositionLocalOf<AimiBrHeroCommands> { NoopAimiBrHeroCommands }
val AimiBrImmersiveState = androidx.compose.runtime.mutableStateOf(false)

internal class AimiBrNavigationCommands(
    private val onNavigate: (NavigationRequest) -> Unit,
    private val commandsAllowed: Boolean,
    private val clientRole: ClientRole,
) : AimiBrHeroCommands {

    private fun element(type: ElementType) = onNavigate(NavigationRequest.Element(type))
    private fun glass(destination: GlassDestination) = onNavigate(NavigationRequest.Glass(destination))
    private fun command(destination: GlassDestination) {
        if (destination.isAllowed(commandsAllowed, clientRole)) glass(destination)
    }

    override fun openLoop() = command(GlassDestination.LOOP_DETAIL)
    override fun openLoopDashboard() = glass(GlassDestination.LOOP_DASHBOARD)
    override fun openTarget() = command(GlassDestination.TARGET_DETAIL)
    override fun openFullTempTarget() = element(ElementType.TEMP_TARGET_MANAGEMENT)
    override fun openProfile() = element(ElementType.PROFILE_MANAGEMENT)
    override fun openCalculator() = element(ElementType.BOLUS_WIZARD)
    override fun openInsulin() = command(GlassDestination.INSULIN_DETAIL)
    override fun openPump() = element(ElementType.PUMP)
    override fun openCannula() = command(GlassDestination.CANNULA_DETAIL)
    override fun openBattery() = glass(GlassDestination.BATTERY_DETAIL)
    override fun openBasal() = command(GlassDestination.BASAL_DETAIL)
    override fun openSensorInsert() = element(ElementType.SENSOR_INSERT)
    override fun openSensorQuality() = glass(GlassDestination.SENSOR_QUALITY)
    override fun openStatsScreen() = element(ElementType.STATISTICS)
    override fun openTreatmentsScreen() = element(ElementType.TREATMENTS)
    override fun openMealAdvisor() = onNavigate(NavigationRequest.Plugin("app.aaps.plugins.aps.aimi.AimiMealAdvisorPlugin"))
    override fun openAimiContext() = onNavigate(NavigationRequest.Plugin("app.aaps.plugins.aps.aimi.AimiContextPlugin"))
}

internal fun buildAimiBrUiState(
    bgInfo: BgInfoData?,
    timeAgo: String,
    deltaText: String,
    unitText: String,
    iobText: String?,
    bolusIobText: String? = null,
    basalIobText: String? = null,
    cobText: String? = null,
    lights: StatusUiState?,
    homeStatus: AimiBrHomeStatus?,
    treatmentData: TreatmentGraphData?,
    warmup: CgmWarmupStatus?,
    nowMs: Long,
): AimiBrUiState {
    val defaults = AimiBrUiState()
    return AimiBrUiState(
        currentBg = bgInfo?.bgText ?: defaults.currentBg,
        unit = unitText,
        glucoseRange = bgInfo?.bgRange,
        deltaText = deltaText,
        trendArrow = bgInfo?.trendArrow,
        timeAgo = timeAgo.ifEmpty { defaults.timeAgo },
        insulinAge = lights?.insulinStatus?.age ?: NO_VALUE,
        insulinAgeStatus = lights?.insulinStatus?.ageStatus ?: StatusLevel.UNSPECIFIED,
        insulinLabel = lights?.insulinStatus?.label,
        cannulaAge = lights?.cannulaStatus?.age ?: NO_VALUE,
        cannulaAgeStatus = lights?.cannulaStatus?.ageStatus ?: StatusLevel.UNSPECIFIED,
        cannulaLabel = lights?.cannulaStatus?.label,
        batteryAge = lights?.batteryStatus?.age ?: NO_VALUE,
        batteryAgeStatus = lights?.batteryStatus?.ageStatus ?: StatusLevel.UNSPECIFIED,
        batteryLabel = lights?.batteryStatus?.label,
        sensorAge = lights?.sensorStatus?.age ?: NO_VALUE,
        sensorAgeStatus = lights?.sensorStatus?.ageStatus ?: StatusLevel.UNSPECIFIED,
        sensorLabel = lights?.sensorStatus?.label,
        loopMode = homeStatus?.loopMode ?: RM.Mode.CLOSED_LOOP,
        loopStatusText = homeStatus?.loopStatusText,
        loopIsRunning = homeStatus?.loopIsRunning ?: defaults.loopIsRunning,
        iobText = iobText ?: NO_VALUE,
        bolusIobText = bolusIobText ?: homeStatus?.bolusIobText ?: NO_VALUE,
        basalIobText = basalIobText ?: homeStatus?.basalIobText ?: NO_VALUE,
        isTempTargetActive = homeStatus?.isTempTargetActive ?: false,
        targetText = homeStatus?.targetText ?: NO_VALUE,
        basalPercentText = homeStatus?.effectiveBasalText ?: NO_VALUE,
        stepsText = homeStatus?.stepsText ?: NO_VALUE,
        hrText = homeStatus?.hrText ?: NO_VALUE,
        lastBolusText = treatmentData?.boluses
            ?.filter { it.isValid && it.timestamp <= nowMs }
            ?.maxByOrNull { it.timestamp }?.label ?: NO_VALUE,
        lastCarbsText = treatmentData?.carbs
            ?.filter { it.isValid && it.timestamp <= nowMs }
            ?.maxByOrNull { it.timestamp }?.label ?: NO_VALUE,
        activeCobText = cobText ?: homeStatus?.activeCobText ?: NO_VALUE,
        profileName = homeStatus?.profileName ?: NO_VALUE,
        isfText = homeStatus?.isfText ?: NO_VALUE,
        isfInlineText = homeStatus?.isfInlineText ?: homeStatus?.isfText ?: NO_VALUE,
        autosensRatioText = homeStatus?.autosensRatioText ?: NO_VALUE,
        profileIsfText = homeStatus?.profileIsfText ?: NO_VALUE,
        variableIsfText = homeStatus?.variableIsfText ?: NO_VALUE,
        algorithmAutosensText = homeStatus?.algorithmAutosensText ?: NO_VALUE,
        averageIsfText = homeStatus?.averageIsfText ?: NO_VALUE,
        tempTargetText = homeStatus?.tempTargetText ?: NO_VALUE,
        tempTargetRemainingText = homeStatus?.tempTargetRemainingText ?: "",
        tir = homeStatus?.tir,
        warmup = warmup,
        isGlucoseActual = bgInfo?.isOutdated?.not() ?: defaults.isGlucoseActual,
        glucoseTimestampMs = bgInfo?.timestamp,
    )
}
