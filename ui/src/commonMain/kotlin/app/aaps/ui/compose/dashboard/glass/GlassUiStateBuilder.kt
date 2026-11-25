package app.aaps.ui.compose.dashboard.glass

import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.TreatmentGraphData
import app.aaps.core.interfaces.source.CgmWarmupStatus
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.ui.compose.overview.statusLights.StatusUiState

private const val NO_VALUE = "--"

internal fun buildGlassUiState(
    bgInfo: BgInfoData?,
    timeAgo: String,
    deltaText: String,
    unitText: String,
    iobText: String?,
    bolusIobText: String? = null,
    basalIobText: String? = null,
    cobText: String? = null,
    lights: StatusUiState?,
    homeStatus: GlassHomeStatus?,
    treatmentData: TreatmentGraphData?,
    warmup: CgmWarmupStatus?,
    nowMs: Long,
): GlassUiState {
    val defaults = GlassUiState()
    return GlassUiState(
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
