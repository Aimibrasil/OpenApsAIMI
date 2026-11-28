package app.aaps.ui.compose.dashboard.aimibrasil

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.dashboard.GlassCgmLinkStatus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal enum class AimiBrMetricItem(val id: String) {
    STEPS_HR("steps_hr"),
    IOB("iob"),
    BASAL("basal"),
    CARBO("carbo"),
    SENS("sens"),
    TARGET("target"),
    LAST_BOLUS("last_bolus");

    val labelRes get() = when (this) {
        STEPS_HR -> UiStrings.aimibr_metric_steps_hr
        IOB -> UiStrings.aimibr_metric_iob
        BASAL -> UiStrings.aimibr_metric_basal
        CARBO -> UiStrings.aimibr_metric_carbs
        SENS -> UiStrings.aimibr_metric_sens
        TARGET -> UiStrings.aimibr_metric_target
        LAST_BOLUS -> UiStrings.aimibr_metric_last_bolus
    }

    companion object {
        val defaultOrder = listOf(STEPS_HR, IOB, BASAL, CARBO, SENS, TARGET)
    }
}

private fun TrendArrow?.toAngleDegrees(): Float? = when (this) {
    TrendArrow.TRIPLE_UP -> -75f
    TrendArrow.DOUBLE_UP -> -60f
    TrendArrow.SINGLE_UP -> -45f
    TrendArrow.FORTY_FIVE_UP -> -25f
    TrendArrow.FLAT -> 0f
    TrendArrow.FORTY_FIVE_DOWN -> 25f
    TrendArrow.SINGLE_DOWN -> 45f
    TrendArrow.DOUBLE_DOWN -> 60f
    TrendArrow.TRIPLE_DOWN -> 75f
    else -> null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AimiBrasilStatusCard(
    state: AimiBrUiState,
    pumpStatus: AimiBrPumpStatusLine? = null,
    isDark: Boolean,
    nowMs: Long,
    commands: AimiBrHeroCommands,
    onOpenTools: () -> Unit,
    currentBgValue: Float = state.currentBg.replace(",", ".").toFloatOrNull() ?: 0f,
    lowLine: Float = 70f,
    highLine: Float = 180f,
    isMmol: Boolean = false,
    onOpenMealAdvisor: () -> Unit = onOpenTools,
    onOpenAimiContext: () -> Unit = commands::openLoopDashboard,
    visualStyle: AimiBrVisualStyle = AimiBrVisualStyle.NEUTRAL,
    onChangeVisualStyle: (AimiBrVisualStyle) -> Unit = {},
    activeMetrics: List<AimiBrMetricItem> = AimiBrMetricItem.defaultOrder,
    onChangeActiveMetrics: (List<AimiBrMetricItem>) -> Unit = {},
    isImmersive: Boolean = false,
    onToggleImmersive: () -> Unit = {},
) {
    var topExpanded by rememberSaveable { mutableStateOf(true) }
    var showIobDialog by remember { mutableStateOf(false) }
    var showSensDialog by remember { mutableStateOf(false) }
    var showEditMetricsDialog by remember { mutableStateOf(false) }
    var showStyleDialog by remember { mutableStateOf(false) }

    val stale = GlassCgmLinkStatus.isStale(state.isGlucoseActual, state.glucoseTimestampMs, nowMs)
    val zone = classifyAimiBrZone(currentBgValue, stale, lowLine, highLine, isMmol)
    val pal = zone.zoneColors()

    val coreAnim by animateColorAsState(pal.core, tween(500), label = "core")
    val deepAnim by animateColorAsState(pal.deep, tween(500), label = "deep")
    val glowAnim by animateColorAsState(pal.glow, tween(500), label = "glow")

    val infiniteTransition = rememberInfiniteTransition(label = "carbBlink")
    val carbPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "carbPulseAlpha"
    )

    val txtPrimary = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val txtMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    val pumpLabelStr = stringResource(UiStrings.aimibr_pump_label)
    val insulinLabelStr = stringResource(UiStrings.aimibr_insulin_label)
    val sensorLabelStr = stringResource(UiStrings.aimibr_sensor_label)

    val pumpAgeValue = pumpStatus?.siteAgeText?.takeIf { it.isNotBlank() } ?: state.cannulaAge.ifBlank { "--" }
    val insulinUnitsValue = pumpStatus?.reservoirText?.takeIf { it.isNotBlank() } ?: state.insulinAge.ifBlank { "--" }
    val sensorAgeValue = pumpStatus?.sensorAgeText?.takeIf { it.isNotBlank() } ?: state.sensorAge.ifBlank { "--" }

    val numericTimeAgo = remember(state.timeAgo) {
        val digits = state.timeAgo.filter { it.isDigit() }
        when {
            state.timeAgo.contains("-") || state.timeAgo.isBlank() -> "0m"
            digits.isNotEmpty() -> "${digits}m"
            else -> state.timeAgo
        }
    }

    val cleanDelta = remember(state.deltaText) {
        state.deltaText.replace("Δ", "").trim()
    }

    val pumpTint = when {
        pumpStatus?.unreachableText != null -> Color(0xFFEF4444)
        pumpStatus?.siteAgeWarning == true -> Color(0xFFF59E0B)
        else -> Color(0xFF38BDF8)
    }
    val insulinTint = when {
        pumpStatus?.unreachableText != null -> Color(0xFFEF4444)
        else -> Color(0xFF38BDF8)
    }
    val sensorTint = when {
        pumpStatus?.sensorAgeWarning == true -> Color(0xFFF97316)
        else -> Color(0xFF22C55E)
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // BARRA SUPERIOR: BOMBA, INSULINA, SENSOR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .aimiBrSurface(isDark = isDark, style = visualStyle, zoneAccent = coreAnim, radius = 18.dp)
                .padding(
                    start = if (isImmersive) 42.dp else 12.dp,
                    end = if (isImmersive) 42.dp else 12.dp,
                    top = 9.dp,
                    bottom = 9.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (topExpanded) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DeviceBadgeCustom(
                        iconContent = { MedtrumPatchGraphic() },
                        label = pumpLabelStr,
                        value = pumpAgeValue,
                        valueColor = pumpTint,
                        labelColor = txtMuted,
                        onClick = commands::openPump,
                        onLong = commands::openCannula
                    )
                    DeviceBadgeCustom(
                        iconContent = {
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = insulinLabelStr,
                        value = insulinUnitsValue,
                        valueColor = insulinTint,
                        labelColor = txtMuted,
                        onClick = commands::openPump
                    )
                    DeviceBadgeCustom(
                        iconContent = { RoundWhiteSensorGraphic() },
                        label = sensorLabelStr,
                        value = sensorAgeValue,
                        valueColor = sensorTint,
                        labelColor = txtMuted,
                        onClick = commands::openSensorInsert,
                        onLong = commands::openSensorQuality
                    )
                }
            } else {
                Text(
                    text = "$pumpLabelStr: $pumpAgeValue • $insulinLabelStr: $insulinUnitsValue • $sensorLabelStr: $sensorAgeValue",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = txtMuted,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (topExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = txtMuted,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable { topExpanded = !topExpanded }
            )
        }
        // CARTÃO PRINCIPAL DE STATUS
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aimiBrSurface(isDark = isDark, style = visualStyle, zoneAccent = coreAnim, radius = 22.dp)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. PERFIL E MODO DO LOOP
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { commands.openProfile() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.StarOutline,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = state.profileName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = txtPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { commands.openLoop() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = if (state.loopIsRunning) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.size(22.dp)
                    )
                    val loopLabel = when (state.loopMode) {
                        app.aaps.core.data.model.RM.Mode.SUPER_BOLUS -> stringResource(UiStrings.aimibr_super_bolus)
                        app.aaps.core.data.model.RM.Mode.DISCONNECTED_PUMP -> stringResource(UiStrings.aimibr_disconnected)
                        app.aaps.core.data.model.RM.Mode.SUSPENDED_BY_PUMP -> stringResource(UiStrings.aimibr_pump_suspended)
                        app.aaps.core.data.model.RM.Mode.SUSPENDED_BY_USER,
                        app.aaps.core.data.model.RM.Mode.SUSPENDED_BY_DST -> stringResource(UiStrings.aimibr_loop_suspended)
                        app.aaps.core.data.model.RM.Mode.CLOSED_LOOP_LGS -> "${stringResource(UiStrings.aimibr_closed_loop)} (LGS)"
                        app.aaps.core.data.model.RM.Mode.OPEN_LOOP -> stringResource(UiStrings.aimibr_open_loop)
                        app.aaps.core.data.model.RM.Mode.DISABLED_LOOP -> stringResource(UiStrings.aimibr_disabled_loop)
                        else -> stringResource(UiStrings.aimibr_closed_loop)
                    }
                    Text(
                        text = loopLabel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = txtPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 2. CORPO CENTRAL: BARRA TIR CLICÁVEL + SELETOR '*' + ANEL DE GLICEMIA + BOTÕES LATERAIS
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val tirPct = state.tir?.inRangePct?.roundToInt() ?: 0
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { commands.openStatsScreen() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("TIR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = txtMuted)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(145.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isDark) Color(0xFF1E242E) else Color(0xFFE2E8F0))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight((tirPct / 100f).coerceIn(0.05f, 1f))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF10B981), Color(0xFF0E7A4D))
                                        )
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = if (state.tir != null) "$tirPct%" else "--",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = txtPrimary,
                            modifier = Modifier.clickable { commands.openStatsScreen() }
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    when (visualStyle) {
                                        AimiBrVisualStyle.NEUTRAL -> Color.White.copy(alpha = 0.12f)
                                        AimiBrVisualStyle.DYNAMIC_BG -> coreAnim.copy(alpha = 0.35f)
                                        AimiBrVisualStyle.LIQUID_GLASS -> Color(0xFF38BDF8).copy(alpha = 0.35f)
                                    }
                                )
                                .clickable { showStyleDialog = true }
                        ) {
                            Text(
                                text = "*",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = when (visualStyle) {
                                    AimiBrVisualStyle.NEUTRAL -> txtMuted
                                    AimiBrVisualStyle.DYNAMIC_BG -> glowAnim
                                    AimiBrVisualStyle.LIQUID_GLASS -> Color(0xFF38BDF8)
                                },
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
                // ANEL DE GLICEMIA GLASS COM ARCO E SETA EXTERNA DE TENDÊNCIA (CENTRO)
                val arrowAngle = state.trendArrow.toAngleDegrees()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(182.dp)
                        .clickable { commands.openLoopDashboard() }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val outerRadius = size.minDimension * 0.42f
                        val ringStroke = 14.dp.toPx()

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(glowAnim.copy(alpha = 0.22f), Color.Transparent),
                                center = center,
                                radius = size.minDimension * 0.50f
                            ),
                            radius = size.minDimension * 0.50f,
                            center = center
                        )

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    coreAnim.copy(alpha = 0.26f),
                                    deepAnim.copy(alpha = 0.48f)
                                ),
                                center = center,
                                radius = outerRadius
                            ),
                            radius = outerRadius,
                            center = center
                        )

                        drawCircle(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                                startY = center.y - outerRadius,
                                endY = center.y
                            ),
                            radius = outerRadius * 0.94f,
                            center = center
                        )

                        drawCircle(
                            color = coreAnim.copy(alpha = 0.45f),
                            radius = outerRadius,
                            center = center,
                            style = Stroke(width = ringStroke)
                        )

                        drawCircle(
                            color = Color.White.copy(alpha = 0.28f),
                            radius = outerRadius + ringStroke / 2f,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.18f),
                            radius = outerRadius - ringStroke / 2f,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )

                        if (!stale && arrowAngle != null) {
                            val sweep = 95f
                            val startAngle = arrowAngle - (sweep / 2f)
                            drawArc(
                                color = glowAnim,
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                                size = Size(outerRadius * 2f, outerRadius * 2f),
                                style = Stroke(width = ringStroke + 2.dp.toPx(), cap = StrokeCap.Round)
                            )

                            val rad = arrowAngle * (PI.toFloat() / 180f)
                            val tipDist = outerRadius + ringStroke * 1.18f
                            val baseDist = outerRadius + ringStroke * 0.35f
                            val spreadAngle = 14f * (PI.toFloat() / 180f)

                            val tip = Offset(
                                x = center.x + tipDist * cos(rad),
                                y = center.y + tipDist * sin(rad)
                            )
                            val baseLeft = Offset(
                                x = center.x + baseDist * cos(rad - spreadAngle),
                                y = center.y + baseDist * sin(rad - spreadAngle)
                            )
                            val baseRight = Offset(
                                x = center.x + baseDist * cos(rad + spreadAngle),
                                y = center.y + baseDist * sin(rad + spreadAngle)
                            )

                            val arrowPath = Path().apply {
                                moveTo(tip.x, tip.y)
                                lineTo(baseLeft.x, baseLeft.y)
                                lineTo(baseRight.x, baseRight.y)
                                close()
                            }
                            drawPath(path = arrowPath, color = glowAnim)
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = numericTimeAgo,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (stale) Color(0xFFFFCDD2) else Color.White.copy(alpha = 0.85f)
                        )
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = state.currentBg,
                                style = TextStyle(
                                    fontSize = 46.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-1.5).sp,
                                    lineHeight = 46.sp,
                                    drawStyle = Stroke(width = 6f, join = StrokeJoin.Round)
                                ),
                                color = Color.Black
                            )
                            Text(
                                text = state.currentBg,
                                fontSize = 46.sp,
                                fontWeight = FontWeight.Black,
                                color = if (stale) Color.White.copy(alpha = 0.6f) else glowAnim,
                                textDecoration = if (stale) TextDecoration.LineThrough else null,
                                letterSpacing = (-1.5).sp,
                                lineHeight = 46.sp
                            )
                        }
                        Text(
                            text = "Δ $cleanDelta ${state.unit}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.92f)
                        )
                    }
                }

                // BOTÕES LATERAIS DE AÇÃO (CALC EMBAIXO)
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(end = 2.dp)
                ) {
                    GlassWideActionBtn(Icons.Default.CameraAlt, stringResource(UiStrings.aimibr_advisor_btn), Color(0xFF38BDF8), isDark, visualStyle, coreAnim, onOpenMealAdvisor)
                    GlassWideActionBtn(Icons.Default.LocationOn, stringResource(UiStrings.aimibr_context_btn), Color(0xFF94A3B8), isDark, visualStyle, coreAnim, onOpenAimiContext)
                    GlassWideActionBtn(Icons.Default.Settings, "AIMI", Color(0xFFF59E0B), isDark, visualStyle, coreAnim, onOpenTools)
                    GlassWideActionBtn(Icons.Default.Calculate, stringResource(UiStrings.aimibr_calc_btn), Color(0xFF22C55E), isDark, visualStyle, coreAnim, commands::openCalculator)
                }
            }
            // 3. GRADE DE MÉTRICAS PERSONALIZÁVEL (ATÉ 3 POR LINHA, SALVA NAS PREFERÊNCIAS)
            val cols = when {
                activeMetrics.size <= 2 -> 2
                activeMetrics.size == 4 -> 2
                else -> 3
            }
            val rows = activeMetrics.chunked(cols)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rows.forEachIndexed { rowIndex, rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowItems.forEach { metric ->
                            AimiBrMetricCardItem(
                                item = metric,
                                state = state,
                                isDark = isDark,
                                visualStyle = visualStyle,
                                zoneAccent = coreAnim,
                                txtPrimary = txtPrimary,
                                txtMuted = txtMuted,
                                carbPulseAlpha = carbPulseAlpha,
                                compact = cols == 3,
                                modifier = Modifier.weight(1f),
                                onOpenStats = commands::openStatsScreen,
                                onOpenIob = { showIobDialog = true },
                                onOpenBasal = commands::openBasal,
                                onOpenCarbs = commands::openInsulin,
                                onOpenSens = { showSensDialog = true },
                                onOpenTarget = commands::openFullTempTarget,
                                onLongEdit = { showEditMetricsDialog = true }
                            )
                        }
                        if (rowIndex == rows.lastIndex) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(width = 30.dp, height = 46.dp)
                                    .aimiBrSurface(isDark = isDark, style = visualStyle, zoneAccent = coreAnim, radius = 12.dp)
                                    .clickable { showEditMetricsDialog = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(UiStrings.aimibr_customize_title),
                                    tint = txtMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
                if (rows.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .aimiBrSurface(isDark = isDark, style = visualStyle, zoneAccent = coreAnim, radius = 12.dp)
                            .clickable { showEditMetricsDialog = true }
                    ) {
                        Text(
                            text = stringResource(UiStrings.aimibr_add_buttons),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = txtMuted
                        )
                    }
                }
            }
        }
    }
    // VISUAL STYLE DIALOG (NEUTRAL, GLUCOSE ZONE COLOR, OR LIQUID GLASS)
    // VISUAL STYLE DIALOG (NEUTRAL, GLUCOSE ZONE COLOR, OR LIQUID GLASS)
    if (showStyleDialog) {
        AlertDialog(
            onDismissRequest = { showStyleDialog = false },
            title = {
                Text(stringResource(UiStrings.aimibr_style_dialog_title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AimiBrVisualStyle.entries.forEach { option ->
                        val selected = option == visualStyle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onChangeVisualStyle(option)
                                    showStyleDialog = false
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    onChangeVisualStyle(option)
                                    showStyleDialog = false
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(option.titleRes), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(stringResource(option.subtitleRes), fontSize = 11.5.sp, color = txtMuted)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggleImmersive() }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(UiStrings.aimibr_fullscreen_title),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(UiStrings.aimibr_fullscreen_sub),
                                fontSize = 11.5.sp,
                                color = txtMuted
                            )
                        }
                        Switch(
                            checked = isImmersive,
                            onCheckedChange = { onToggleImmersive() }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStyleDialog = false }) {
                    Text(stringResource(UiStrings.aimibr_close), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // CUSTOMIZE BUTTONS DIALOG (SAVED TO PREFERENCES)
    if (showEditMetricsDialog) {
        AlertDialog(
            onDismissRequest = { showEditMetricsDialog = false },
            title = {
                Text(stringResource(UiStrings.aimibr_customize_title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        stringResource(UiStrings.aimibr_customize_hint),
                        fontSize = 12.sp,
                        color = txtMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    val allOrdered = activeMetrics + AimiBrMetricItem.entries.filter { it !in activeMetrics }
                    allOrdered.forEach { item ->
                        val isChecked = item in activeMetrics
                        val idx = activeMetrics.indexOf(item)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onChangeActiveMetrics(if (isChecked) activeMetrics - item else activeMetrics + item)
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        onChangeActiveMetrics(if (checked) activeMetrics + item else activeMetrics - item)
                                    }
                                )
                                Text(stringResource(item.labelRes), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (isChecked) {
                                Row {
                                    IconButton(
                                        onClick = {
                                            if (idx > 0) {
                                                val list = activeMetrics.toMutableList()
                                                val moved = list.removeAt(idx)
                                                list.add(idx - 1, moved)
                                                onChangeActiveMetrics(list)
                                            }
                                        },
                                        enabled = idx > 0,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            if (idx < activeMetrics.lastIndex) {
                                                val list = activeMetrics.toMutableList()
                                                val moved = list.removeAt(idx)
                                                list.add(idx + 1, moved)
                                                onChangeActiveMetrics(list)
                                            }
                                        },
                                        enabled = idx < activeMetrics.lastIndex,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEditMetricsDialog = false }) {
                    Text(stringResource(UiStrings.aimibr_done), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // IOB MODAL DIALOG
    if (showIobDialog) {
        AlertDialog(
            onDismissRequest = { showIobDialog = false },
            title = {
                Text(stringResource(UiStrings.aimibr_iob_dialog_title), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(UiStrings.aimibr_bolus_iob_format, state.bolusIobText), fontSize = 15.sp)
                    Text(stringResource(UiStrings.aimibr_basal_iob_format, state.basalIobText), fontSize = 15.sp)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(UiStrings.aimibr_total_iob_format, state.iobText), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            },
            confirmButton = {
                TextButton(onClick = { showIobDialog = false }) { Text("OK", fontWeight = FontWeight.Bold) }
            }
        )
    }

    // SENSITIVITY MODAL DIALOG
    if (showSensDialog) {
        AlertDialog(
            onDismissRequest = { showSensDialog = false },
            title = {
                Text(stringResource(UiStrings.aimibr_sens_dialog_title), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(UiStrings.aimibr_autosens_ratio_format, state.autosensRatioText), fontSize = 14.sp)
                    Text(stringResource(UiStrings.aimibr_profile_isf_format, state.profileIsfText), fontSize = 14.sp)
                    Text(stringResource(UiStrings.aimibr_variable_isf_format, state.variableIsfText), fontSize = 14.sp)
                    Text(stringResource(UiStrings.aimibr_algo_autosens_format, state.algorithmAutosensText), fontSize = 14.sp)
                    Text(stringResource(UiStrings.aimibr_avg_isf_format, state.averageIsfText), fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                TextButton(onClick = { showSensDialog = false }) { Text("OK", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showSensDialog = false }) { Text(stringResource(UiStrings.aimibr_cancel)) }
            }
        )
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AimiBrMetricCardItem(
    item: AimiBrMetricItem,
    state: AimiBrUiState,
    isDark: Boolean,
    visualStyle: AimiBrVisualStyle,
    zoneAccent: Color,
    txtPrimary: Color,
    txtMuted: Color,
    carbPulseAlpha: Float,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onOpenStats: () -> Unit,
    onOpenIob: () -> Unit,
    onOpenBasal: () -> Unit,
    onOpenCarbs: () -> Unit,
    onOpenSens: () -> Unit,
    onOpenTarget: () -> Unit,
    onLongEdit: () -> Unit,
) {
    val fontSize = if (compact) 10.5.sp else 12.sp
    when (item) {
        AimiBrMetricItem.STEPS_HR -> {
            GlassGridMetricCard(
                modifier = modifier,
                isDark = isDark,
                visualStyle = visualStyle,
                zoneAccent = zoneAccent,
                leadingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                        Text(state.stepsText, fontSize = fontSize, fontWeight = FontWeight.Bold, color = txtPrimary, maxLines = 1)
                        Icon(Icons.Default.Favorite, null, tint = Color(0xFFF43F5E), modifier = Modifier.size(12.dp))
                        Text("| ${state.hrText}", fontSize = fontSize, fontWeight = FontWeight.Bold, color = txtPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                onClick = onOpenStats,
                onLongClick = onLongEdit
            )
        }
        AimiBrMetricItem.IOB -> {
            GlassGridMetricCard(
                modifier = modifier,
                isDark = isDark,
                visualStyle = visualStyle,
                zoneAccent = zoneAccent,
                icon = Icons.Default.WaterDrop,
                iconTint = Color(0xFF38BDF8),
                label = if (compact) "" else "IOB:",
                value = state.iobText,
                txtColor = txtPrimary,
                compact = compact,
                onClick = onOpenIob,
                onLongClick = onLongEdit
            )
        }
        AimiBrMetricItem.BASAL -> {
            GlassGridMetricCard(
                modifier = modifier,
                isDark = isDark,
                visualStyle = visualStyle,
                zoneAccent = zoneAccent,
                icon = Icons.Default.Medication,
                iconTint = Color(0xFF818CF8),
                label = if (compact) "" else "Basal:",
                value = state.basalPercentText,
                txtColor = txtPrimary,
                compact = compact,
                onClick = onOpenBasal,
                onLongClick = onLongEdit
            )
        }
        AimiBrMetricItem.CARBO -> {
            val hasCarbsReq = state.lastCarbsText.contains("Req", ignoreCase = true) && !state.lastCarbsText.contains("0")
            val carbBlinkColor = if (hasCarbsReq) Color(0xFFF59E0B).copy(alpha = carbPulseAlpha) else Color(0xFFF59E0B).copy(alpha = 0.35f)
            val carbBgAlpha = if (hasCarbsReq) carbPulseAlpha * 0.25f else 0.08f
            val carbsPrefix = stringResource(UiStrings.aimibr_carbs_prefix)
            Box(
                modifier = modifier
                    .height(46.dp)
                    .aimiBrSurface(isDark = isDark, style = visualStyle, zoneAccent = Color(0xFFF59E0B), radius = 12.dp)
                    .background(Color(0xFFF59E0B).copy(alpha = carbBgAlpha))
                    .border(
                        width = if (hasCarbsReq) 1.5.dp else 1.dp,
                        color = carbBlinkColor,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .combinedClickable(onClick = onOpenCarbs, onLongClick = onLongEdit)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.LocalDining, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(15.dp))
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = if (compact) state.activeCobText else "$carbsPrefix ${state.activeCobText}",
                            fontSize = fontSize,
                            fontWeight = FontWeight.ExtraBold,
                            color = txtPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 12.sp
                        )
                        Text(
                            text = "(${state.lastCarbsText})",
                            fontSize = 9.5.sp,
                            fontWeight = if (hasCarbsReq) FontWeight.Bold else FontWeight.Medium,
                            color = if (hasCarbsReq) Color(0xFFFBBF24) else txtMuted,
                            maxLines = 1,
                            lineHeight = 10.5.sp
                        )
                    }
                }
            }
        }
        AimiBrMetricItem.SENS -> {
            GlassGridMetricCard(
                modifier = modifier,
                isDark = isDark,
                visualStyle = visualStyle,
                zoneAccent = zoneAccent,
                icon = Icons.Default.Speed,
                iconTint = Color(0xFF2DD4BF),
                label = if (compact) "" else stringResource(UiStrings.aimibr_sens_prefix),
                value = state.isfInlineText,
                txtColor = txtPrimary,
                compact = compact,
                onClick = onOpenSens,
                onLongClick = onLongEdit
            )
        }
        AimiBrMetricItem.TARGET -> {
            val targetDisplay = if (state.isTempTargetActive && state.tempTargetRemainingText.isNotBlank()) {
                "${state.tempTargetText} (${state.tempTargetRemainingText})"
            } else {
                state.tempTargetText
            }
            GlassGridMetricCard(
                modifier = modifier,
                isDark = isDark,
                visualStyle = visualStyle,
                zoneAccent = if (state.isTempTargetActive) Color(0xFFF59E0B) else zoneAccent,
                icon = Icons.Default.TrackChanges,
                iconTint = if (state.isTempTargetActive) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                label = if (compact) "" else stringResource(UiStrings.aimibr_target_prefix),
                value = targetDisplay,
                txtColor = if (state.isTempTargetActive) Color(0xFFFBBF24) else txtPrimary,
                compact = compact,
                onClick = onOpenTarget,
                onLongClick = onLongEdit
            )
        }
        AimiBrMetricItem.LAST_BOLUS -> {
            GlassGridMetricCard(
                modifier = modifier,
                isDark = isDark,
                visualStyle = visualStyle,
                zoneAccent = zoneAccent,
                icon = Icons.Default.Vaccines,
                iconTint = Color(0xFF38BDF8),
                label = if (compact) "" else stringResource(UiStrings.aimibr_bolus_prefix),
                value = state.lastBolusText,
                txtColor = txtPrimary,
                compact = compact,
                onClick = onOpenCarbs,
                onLongClick = onLongEdit
            )
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlassGridMetricCard(
    modifier: Modifier = Modifier,
    isDark: Boolean,
    visualStyle: AimiBrVisualStyle,
    zoneAccent: Color,
    icon: ImageVector? = null,
    iconTint: Color = zoneAccent,
    label: String = "",
    value: String = "",
    txtColor: Color = Color.White,
    compact: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .aimiBrSurface(isDark = isDark, style = visualStyle, zoneAccent = zoneAccent, radius = 12.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = if (compact) 7.dp else 9.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (leadingContent != null) {
            leadingContent()
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (icon != null) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(15.dp))
                }
                if (label.isNotEmpty()) {
                    Text(label, fontSize = 11.sp, color = txtColor.copy(alpha = 0.75f), maxLines = 1, softWrap = false)
                }
                Text(value, fontSize = if (compact) 11.sp else 12.sp, fontWeight = FontWeight.ExtraBold, color = txtColor, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceBadgeCustom(
    iconContent: @Composable () -> Unit,
    label: String,
    value: String,
    valueColor: Color,
    labelColor: Color,
    onClick: () -> Unit,
    onLong: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        iconContent()
        Column {
            Text(label, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = labelColor, lineHeight = 10.sp)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, maxLines = 1, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun MedtrumPatchGraphic() {
    Canvas(modifier = Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = Color(0xFFF1F5F9),
            topLeft = Offset(w * 0.05f, h * 0.18f),
            size = Size(w * 0.90f, h * 0.64f),
            cornerRadius = CornerRadius(w * 0.28f, h * 0.28f)
        )
        drawRoundRect(
            color = Color(0xFF94A3B8),
            topLeft = Offset(w * 0.05f, h * 0.18f),
            size = Size(w * 0.90f, h * 0.64f),
            cornerRadius = CornerRadius(w * 0.28f, h * 0.28f),
            style = Stroke(width = 1.5f)
        )
        drawRoundRect(
            color = Color(0xFF64748B),
            topLeft = Offset(w * 0.42f, h * 0.24f),
            size = Size(w * 0.46f, h * 0.52f),
            cornerRadius = CornerRadius(w * 0.20f, h * 0.20f)
        )
        drawCircle(
            color = Color(0xFF38BDF8),
            radius = w * 0.09f,
            center = Offset(w * 0.25f, h * 0.50f)
        )
    }
}

@Composable
private fun RoundWhiteSensorGraphic() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = Color.White.copy(alpha = 0.25f), radius = size.minDimension * 0.48f, center = c)
        drawCircle(color = Color(0xFFF8FAFC), radius = size.minDimension * 0.38f, center = c)
        drawCircle(color = Color(0xFFCBD5E1), radius = size.minDimension * 0.38f, center = c, style = Stroke(1.5f))
        drawCircle(color = Color(0xFF94A3B8), radius = size.minDimension * 0.10f, center = c)
    }
}

@Composable
private fun GlassWideActionBtn(
    icon: ImageVector,
    label: String,
    accent: Color,
    isDark: Boolean,
    visualStyle: AimiBrVisualStyle,
    zoneAccent: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(74.dp)
            .height(44.dp)
            .aimiBrSurface(isDark = isDark, style = visualStyle, zoneAccent = zoneAccent, radius = 12.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Icon(icon, label, tint = accent, modifier = Modifier.size(17.dp))
        Spacer(Modifier.height(1.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
            lineHeight = 9.5.sp,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
