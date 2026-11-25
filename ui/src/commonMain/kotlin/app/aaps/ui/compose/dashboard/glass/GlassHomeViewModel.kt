package app.aaps.ui.compose.dashboard.glass

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.HR
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SC
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.dashboard.AimiGlassDashboardData
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.extensions.isInProgress
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.pump.tickerFlow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class GlassHomeViewModel(
    private val persistenceLayer: PersistenceLayer,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val iobCobCalculator: IobCobCalculator,
    private val loop: Loop,
    private val dateUtil: DateUtil,
    private val rh: TextResolver,
    private val decimalFormatter: DecimalFormatter,
    private val aapsLogger: AAPSLogger,
    private val activePlugin: ActivePlugin,
    private val preferences: Preferences,
) : ViewModel() {

    private val _status = MutableStateFlow(GlassHomeStatus())
    val status: StateFlow<GlassHomeStatus> = _status.asStateFlow()

    private val healthStoreStepsToday = MutableStateFlow<AimiGlassDashboardData.StepsToday?>(null)

    fun onHealthStoreStepsToday(stepsToday: AimiGlassDashboardData.StepsToday?) {
        healthStoreStepsToday.value = stepsToday
    }

    private var yesterdayDayStart: Long? = null
    private var yesterdayInRangePct: Double? = null

    init {
        _status.subscriptionCount
            .map { it > 0 }
            .distinctUntilChanged()
            .flatMapLatest { visible -> if (visible) refreshTriggers() else emptyFlow() }
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    private fun refreshTriggers(): Flow<Unit> = merge(
        tickerFlow(30_000L),
        persistenceLayer.observeChanges(GV::class).map { },
        persistenceLayer.observeChanges(TT::class).map { },
        persistenceLayer.observeChanges(TB::class).map { },
        persistenceLayer.observeChanges(RM::class).map { },
        persistenceLayer.observeChanges(HR::class).map { },
        persistenceLayer.observeChanges(SC::class).map { },
        persistenceLayer.observeChanges(TE::class).map { },
        persistenceLayer.observeChanges(PS::class).map { },
        activePlugin.activePump.reservoirLevel.map { },
        persistenceLayer.databaseClearedFlow.map { },
        healthStoreStepsToday.map { },
    )

    private var refreshJob: Job? = null

    private fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                _status.value = withContext(aapsIoDispatcher) { buildStatus() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Glass home status failed", e)
            }
        }
    }
    private suspend fun buildStatus(): GlassHomeStatus {
        val now = dateUtil.now()
        val profile = profileFunction.getProfile()
        val profileName = profileFunction.getProfileName().takeIf { it.isNotBlank() } ?: NO_VALUE

        val activeTemporaryTarget = persistenceLayer.getTemporaryTargetActiveAt(now)
        val targetRange = DashboardV2StatusValueResolver.resolveTargetRangeMgdl(
            temporaryLow = activeTemporaryTarget?.lowTarget,
            temporaryHigh = activeTemporaryTarget?.highTarget,
            profileLow = profile?.getTargetLowMgdl(now),
            profileHigh = profile?.getTargetHighMgdl(now),
            aimiTargetBg = loop.lastRun?.request?.targetBG,
        )
        val targetText = targetRange?.let { range ->
            profileUtil.toTargetRangeString(low = range.low, high = range.high, sourceUnits = GlucoseUnit.MGDL)
        }

        // IOB Detalhado (Bolus e Basal)
        val iobTotal = iobCobCalculator.calculateIobFromBolus()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended()
        val bolusIobValue = iobTotal.iob
        val basalIobValue = basalIob.basaliob
        val bolusIobText = rh.gs(InterfacesStrings.format_insulin_units, bolusIobValue)
        val basalIobText = rh.gs(CoreUiStrings.format_insulin_units_signed, basalIobValue)

        // Carboidratos Ativos (COB com valor pendente entre parênteses)
        val cobInfo = iobCobCalculator.getCobInfo("GlassHomeViewModel")
        val activeCobText = when {
            cobInfo.displayCob != null && cobInfo.futureCarbs > 0.0 ->
                "${decimalFormatter.to0Decimal(cobInfo.displayCob!!)} g (${decimalFormatter.to0Decimal(cobInfo.futureCarbs)} g)"
            cobInfo.displayCob != null ->
                "${decimalFormatter.to0Decimal(cobInfo.displayCob!!)} g"
            cobInfo.futureCarbs > 0.0 ->
                "0 g (${decimalFormatter.to0Decimal(cobInfo.futureCarbs)} g)"
            else -> "0 g"
        }

        // Sensibilidade (Autosens, ISF Perfil, ISF Variável, Autosens Algoritmo e ISF Médio)
        val request = loop.lastRun?.request
        val autosensRatio = request?.autosensResult?.ratio ?: 1.0
        val profileIsfMgdl = profile?.getIsfMgdl("GlassHomeViewModel")
        val variableIsfMgdl = profileIsfMgdl?.let { it / autosensRatio }
        val algoRatio = autosensRatio
        val avgIsfMgdl = if (profileIsfMgdl != null && variableIsfMgdl != null) {
            (profileIsfMgdl + variableIsfMgdl) / 2.0
        } else {
            profileIsfMgdl
        }

        val units = profileFunction.getUnits()
        val unitSuffix = if (units == GlucoseUnit.MGDL) "mg/dL/U" else "mmol/L/U"
        val profileIsfStr = profileIsfMgdl?.takeIf { it > 0 && it.isFinite() }?.let { profileUtil.fromMgdlToStringInUnits(it) } ?: NO_VALUE
        val variableIsfStr = variableIsfMgdl?.takeIf { it > 0 && it.isFinite() }?.let { profileUtil.fromMgdlToStringInUnits(it) } ?: profileIsfStr
        val avgIsfStr = avgIsfMgdl?.takeIf { it > 0 && it.isFinite() }?.let { profileUtil.fromMgdlToStringInUnits(it) } ?: profileIsfStr

        val trendArrowSymbol = when {
            variableIsfMgdl != null && profileIsfMgdl != null && variableIsfMgdl > profileIsfMgdl + 0.05 -> "↗"
            variableIsfMgdl != null && profileIsfMgdl != null && variableIsfMgdl < profileIsfMgdl - 0.05 -> "↘"
            else -> "→"
        }
        val isfInlineText = if (profileIsfStr != NO_VALUE) "$profileIsfStr $trendArrowSymbol $variableIsfStr" else NO_VALUE
        val isfText = if (profileIsfStr != NO_VALUE) "$profileIsfStr $unitSuffix" else NO_VALUE
        val autosensRatioText = "${(autosensRatio * 100).roundToInt()}%"
        val algorithmAutosensText = "${(algoRatio * 100).roundToInt()}%"

        // Alvo Temporário + Tempo Restante
        val tempTargetText = if (activeTemporaryTarget != null) {
            profileUtil.toTargetRangeString(
                low = activeTemporaryTarget.lowTarget,
                high = activeTemporaryTarget.highTarget,
                sourceUnits = GlucoseUnit.MGDL
            )
        } else {
            targetText ?: NO_VALUE
        }
        val tempTargetRemainingText = if (activeTemporaryTarget != null) {
            val remMin = ((activeTemporaryTarget.end - now) / 60_000L).coerceAtLeast(0L)
            if (remMin >= 60) "${remMin / 60}h${(remMin % 60).toString().padStart(2, '0')}m" else "${remMin}m"
        } else {
            ""
        }
        val activeTempBasal = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)?.takeIf { it.isInProgress(dateUtil) }
        val effectiveBasalRateUh = DashboardV2StatusValueResolver.resolveEffectiveBasalRateUh(
            temporaryBasal = activeTempBasal?.let { DashboardV2StatusValueResolver.TemporaryBasal(rate = it.rate, isAbsolute = it.isAbsolute) },
            scheduledBasalRateUh = profile?.getBasal(now),
        )
        val effectiveBasalText = effectiveBasalRateUh?.let { rh.gs(CoreUiStrings.pump_base_basal_rate, it) }

        val loopMode = persistenceLayer.getRunningModeActiveAt(now).mode

        val dayStart = dateUtil.beginOfDay(now)
        val todayMgdl = persistenceLayer.getBgReadingsDataFromTimeToTime(dayStart, now, true).map { it.value }
        if (yesterdayDayStart != dayStart) {
            val yesterdayMgdl = persistenceLayer.getBgReadingsDataFromTimeToTime(dateUtil.beginOfDay(dayStart - 1), dayStart, true).map { it.value }
            yesterdayInRangePct = GlassHomeStats.inRangePct(yesterdayMgdl)
            yesterdayDayStart = dayStart
        }
        val tir = GlassHomeStats.tir(
            todayMgdl = todayMgdl,
            yesterdayInRangePct = yesterdayInRangePct,
            hoursSinceDayStart = (now - dayStart) / 3_600_000.0,
        )

        val steps = GlassHomeStats.chooseStepsToday(
            healthStoreSteps = healthStoreStepsToday.value?.takeIf { it.dayStartMs == dayStart }?.steps,
            databaseSteps = GlassHomeStats.stepsToday(persistenceLayer.getStepsCountFromTimeToTime(dayStart, now)),
        )
        val stepsText = when {
            steps == null -> NO_VALUE
            steps > 1.0   -> decimalFormatter.to0Decimal(steps)
            else          -> decimalFormatter.to0Decimal(0.0)
        }
        val lastHr = persistenceLayer.getHeartRatesFromTimeToTime(now - HR_WINDOW_MS - HR_OVERLAP_MS, now)
            .maxByOrNull { it.timestamp }?.beatsPerMinute?.takeIf { it > 0 }
        val hrText = lastHr?.let { decimalFormatter.to0Decimal(it) } ?: NO_VALUE

        return GlassHomeStatus(
            pumpStatus = buildPumpStatus(now),
            targetText = targetText,
            isTempTargetActive = activeTemporaryTarget != null,
            effectiveBasalText = effectiveBasalText,
            loopStatusText = rh.gs(loopModeLabel(loopMode)),
            loopIsRunning = !loopMode.pausesLoopExecution(),
            stepsText = stepsText,
            hrText = hrText,
            tir = tir,
            profileName = profileName,
            isfText = isfText,
            isfInlineText = isfInlineText,
            autosensRatioText = autosensRatioText,
            profileIsfText = profileIsfStr,
            variableIsfText = variableIsfStr,
            algorithmAutosensText = algorithmAutosensText,
            averageIsfText = avgIsfStr,
            bolusIobText = bolusIobText,
            basalIobText = basalIobText,
            activeCobText = activeCobText,
            tempTargetText = tempTargetText,
            tempTargetRemainingText = tempTargetRemainingText,
        )
    }

    private suspend fun buildPumpStatus(now: Long): GlassPumpStatusLine {
        val pump = activePlugin.activePump
        val reservoirUnits = pump.reservoirLevel.value?.cU ?: 0.0
        val reservoirText =
            if (reservoirUnits > 0) rh.gs(InterfacesStrings.format_insulin_units, reservoirUnits)
            else rh.gs(CoreUiStrings.value_unavailable_short)
        val siteAgeMs = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.CANNULA_CHANGE)?.let { now - it.timestamp }
        val sensorAgeMs = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)?.let { now - it.timestamp }
        val unreachable = GlassPumpStatusRules.isPumpUnreachable(
            alertEnabled = preferences.get(BooleanKey.AlertPumpUnreachable),
            lastConnectionMs = pump.lastDataTime.value,
            thresholdMinutes = preferences.get(IntKey.AlertsPumpUnreachableThreshold),
            nowMs = now,
        )
        return GlassPumpStatusLine(
            reservoirText = reservoirText,
            unreachableText = if (unreachable) rh.gs(CoreUiStrings.pump_unreachable) else null,
            siteAgeText = formatDeviceAge(rh, siteAgeMs),
            siteAgeWarning = GlassPumpStatusRules.siteAgeWarning(siteAgeMs),
            sensorAgeText = formatDeviceAge(rh, sensorAgeMs),
            sensorAgeWarning = GlassPumpStatusRules.sensorAgeWarning(sensorAgeMs),
        )
    }

    private companion object {
        const val NO_VALUE = "--"
        const val HR_WINDOW_MS = 3 * 60 * 60 * 1000L
        const val HR_OVERLAP_MS = 15 * 60 * 1000L
    }
}

internal fun loopModeLabel(mode: RM.Mode): TextRef = when (mode) {
    RM.Mode.SUPER_BOLUS       -> CoreUiStrings.superbolus
    RM.Mode.DISCONNECTED_PUMP -> CoreUiStrings.disconnected
    RM.Mode.SUSPENDED_BY_PUMP -> InterfacesStrings.pumpsuspended
    RM.Mode.SUSPENDED_BY_USER -> InterfacesStrings.loopsuspended
    RM.Mode.SUSPENDED_BY_DST  -> CoreUiStrings.loop_suspended_by_dst
    RM.Mode.CLOSED_LOOP_LGS   -> CoreUiStrings.uel_lgs_loop_mode
    RM.Mode.CLOSED_LOOP       -> CoreUiStrings.closedloop
    RM.Mode.OPEN_LOOP         -> CoreUiStrings.openloop
    RM.Mode.DISABLED_LOOP     -> CoreUiStrings.disabled_loop
    RM.Mode.RESUME            -> CoreUiStrings.resumeloop
}

data class GlassHomeStatus(
    val pumpStatus: GlassPumpStatusLine? = null,
    val targetText: String? = null,
    val isTempTargetActive: Boolean = false,
    val effectiveBasalText: String? = null,
    val loopStatusText: String? = null,
    val loopIsRunning: Boolean = true,
    val stepsText: String = "--",
    val hrText: String = "--",
    val tir: GlassTir? = null,
    val profileName: String = "--",
    val isfText: String = "--",
    val isfInlineText: String = "--",
    val autosensRatioText: String = "--",
    val profileIsfText: String = "--",
    val variableIsfText: String = "--",
    val algorithmAutosensText: String = "--",
    val averageIsfText: String = "--",
    val bolusIobText: String = "--",
    val basalIobText: String = "--",
    val activeCobText: String = "--",
    val tempTargetText: String = "--",
    val tempTargetRemainingText: String = "",
)

internal object GlassHomeStats {
    const val TIR_DAY_READY_HOURS = 6.0
    private const val LOW_MGDL = 70.0
    private const val HIGH_MGDL = 180.0

    fun inRangePct(valuesMgdl: List<Double>): Double? =
        valuesMgdl.takeIf { it.isNotEmpty() }?.let { list -> list.count { it in LOW_MGDL..HIGH_MGDL } * 100.0 / list.size }

    fun tir(todayMgdl: List<Double>, yesterdayInRangePct: Double?, hoursSinceDayStart: Double): GlassTir? {
        if (todayMgdl.isEmpty() || hoursSinceDayStart < TIR_DAY_READY_HOURS) return null
        val count = todayMgdl.size.toDouble()
        val inRange = todayMgdl.count { it in LOW_MGDL..HIGH_MGDL } / count * 100.0
        val below = todayMgdl.count { it < LOW_MGDL } / count * 100.0
        val above = todayMgdl.count { it > HIGH_MGDL } / count * 100.0
        return GlassTir(
            belowPct = below.toFloat(),
            inRangePct = inRange.toFloat(),
            abovePct = above.toFloat(),
            deltaVsYesterday = yesterdayInRangePct?.let { (inRange - it).roundToInt() },
        )
    }

    fun stepsToday(rows: List<SC>): Double? {
        if (rows.isEmpty()) return null
        return rows
            .filter { it.device.isNotBlank() }
            .groupBy { it.device }
            .values
            .maxOfOrNull { deviceRows ->
                deviceRows
                    .groupBy { it.timestamp / FIVE_MIN_MS }
                    .values
                    .sumOf { slot -> slot.maxOf { it.steps5min.coerceAtLeast(0) } }
                    .toDouble()
            } ?: 0.0
    }

    fun chooseStepsToday(healthStoreSteps: Long?, databaseSteps: Double?): Double? = when {
        healthStoreSteps != null -> max(healthStoreSteps.toDouble().coerceAtLeast(0.0), (databaseSteps ?: 0.0).coerceAtLeast(0.0))
        else                     -> databaseSteps
    }

    private const val FIVE_MIN_MS = 5 * 60 * 1000L
}
