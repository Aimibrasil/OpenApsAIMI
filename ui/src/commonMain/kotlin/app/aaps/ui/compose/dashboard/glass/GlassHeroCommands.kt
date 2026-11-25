package app.aaps.ui.compose.dashboard.glass

import androidx.compose.runtime.compositionLocalOf

interface GlassHeroCommands {
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
}

object NoopGlassHeroCommands : GlassHeroCommands {
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
}

val LocalGlassHeroCommands = compositionLocalOf<GlassHeroCommands> { NoopGlassHeroCommands }
