package app.aaps.ui.compose.dashboard.glass

import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.ui.compose.navigation.GlassDestination
import app.aaps.core.ui.compose.navigation.NavigationRequest

internal class GlassNavigationCommands(
    private val onNavigate: (NavigationRequest) -> Unit,
    private val commandsAllowed: Boolean,
    private val clientRole: ClientRole,
) : GlassHeroCommands {

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
    override fun openPump() = glass(GlassDestination.PUMP_DETAIL)
    override fun openCannula() = command(GlassDestination.CANNULA_DETAIL)
    override fun openBattery() = glass(GlassDestination.BATTERY_DETAIL)
    override fun openBasal() = command(GlassDestination.BASAL_DETAIL)
    override fun openSensorInsert() = command(GlassDestination.SENSOR_INSERT_DETAIL)
    override fun openSensorQuality() = glass(GlassDestination.SENSOR_QUALITY)
    override fun openStatsScreen() = element(ElementType.STATISTICS)
    override fun openTreatmentsScreen() = element(ElementType.TREATMENTS)
}

internal fun dispatchGlassToolAction(
    action: DashboardV2ToolAction,
    onNavigate: (NavigationRequest) -> Unit,
    onOpenActions: () -> Unit,
    bgSourcePluginClassName: String?,
) {
    when (val destination = action.destination) {
        DashboardV2ToolDestination.Actions        -> onOpenActions()
        DashboardV2ToolDestination.ActiveBgSource -> bgSourcePluginClassName?.let { onNavigate(NavigationRequest.Plugin(it)) }
        is DashboardV2ToolDestination.Navigate    -> onNavigate(destination.request)
    }
}
