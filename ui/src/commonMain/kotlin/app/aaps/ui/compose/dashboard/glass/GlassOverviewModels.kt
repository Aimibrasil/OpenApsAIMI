package app.aaps.ui.compose.dashboard.glass

import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.source.CgmWarmupStatus
import app.aaps.core.ui.compose.StatusLevel

data class GlassUiState(
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
    val tir: GlassTir? = null,
    val warmup: CgmWarmupStatus? = null,
    val isGlucoseActual: Boolean = true,
    val glucoseTimestampMs: Long? = null,
)

data class GlassTir(
    val belowPct: Float,
    val inRangePct: Float,
    val abovePct: Float,
    val deltaVsYesterday: Int?,
)
