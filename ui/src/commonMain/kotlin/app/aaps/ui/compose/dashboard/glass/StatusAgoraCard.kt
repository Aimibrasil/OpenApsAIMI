package app.aaps.ui.compose.dashboard.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.core.ui.compose.glass.GlassColors
import app.aaps.core.ui.extensions.directionToIcon
import app.aaps.ui.compose.dashboard.GlassCgmLinkStatus
import kotlin.math.roundToInt

internal enum class GlassGlucoseZone(val titlePt: String) {
    MUITO_BAIXO("MUITO BAIXO"),
    BAIXO("BAIXO"),
    ESTAVEL("ESTÁVEL"),
    ALTO("ALTO"),
    MUITO_ALTO("MUITO ALTO"),
    SEM_DADOS("SEM DADOS")
}

internal data class GlassZoneColors(val core: Color, val deep: Color, val glow: Color, val border: Color)

internal fun GlassGlucoseZone.zoneColors(): GlassZoneColors = when (this) {
    GlassGlucoseZone.MUITO_BAIXO -> GlassZoneColors(Color(0xFFD32F2F), Color(0xFF7F0000), Color(0xFFFF1744), Color(0xFFFF8A80))
    GlassGlucoseZone.BAIXO -> GlassZoneColors(Color(0xFFE53935), Color(0xFF8E0000), Color(0xFFFF5252), Color(0xFFFFCDD2))
    GlassGlucoseZone.ESTAVEL -> GlassZoneColors(Color(0xFF1B8A5A), Color(0xFF0A472C), Color(0xFF00E676), Color(0xFFA5D6A7))
    GlassGlucoseZone.ALTO -> GlassZoneColors(Color(0xFFD48817), Color(0xFF7A4900), Color(0xFFFFB300), Color(0xFFFFE082))
    GlassGlucoseZone.MUITO_ALTO -> GlassZoneColors(Color(0xFFE65100), Color(0xFF872600), Color(0xFFFF9100), Color(0xFFFFCC80))
    GlassGlucoseZone.SEM_DADOS -> GlassZoneColors(Color(0xFF455A64), Color(0xFF1C313A), Color(0xFF90A4AE), Color(0xFFCFD8DC))
}

internal fun classifyGlassZone(bg: Float, stale: Boolean, low: Float, high: Float, isMmol: Boolean): GlassGlucoseZone {
    if (stale || bg <= 0f || !bg.isFinite()) return GlassGlucoseZone.SEM_DADOS
    val vLow = if (isMmol) 0.8f else 15f
    val vHigh = if (isMmol) 2.2f else 40f
    return when {
        bg < low - vLow -> GlassGlucoseZone.MUITO_BAIXO
        bg < low -> GlassGlucoseZone.BAIXO
        bg <= high -> GlassGlucoseZone.ESTAVEL
        bg <= high + vHigh -> GlassGlucoseZone.ALTO
        else -> GlassGlucoseZone.MUITO_ALTO
    }
}

internal fun Modifier.glassSurface(isDark: Boolean, radius: Dp = 18.dp, accent: Color? = null): Modifier {
    val shape = RoundedCornerShape(radius)
    val bg = if (isDark) {
        listOf(
            Color(0xFF232832).copy(alpha = 0.86f),
            Color(0xFF14171D).copy(alpha = 0.90f)
        )
    } else {
        listOf(
            Color.White.copy(alpha = 0.94f),
            Color(0xFFF1F5F9).copy(alpha = 0.88f)
        )
    }
    val border = if (accent != null) {
        listOf(accent.copy(0.45f), Color.White.copy(if (isDark) 0.08f else 0.35f))
    } else if (isDark) {
        listOf(Color.White.copy(0.22f), Color.White.copy(0.05f))
    } else {
        listOf(Color.White.copy(0.95f), Color(0xFF94A3B8).copy(0.35f))
    }
    return this
        .clip(shape)
        .background(Brush.verticalGradient(bg))
        .border(BorderStroke(1.dp, Brush.verticalGradient(border)), shape)
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StatusAgoraCard(
    state: GlassUiState,
    pumpStatus: GlassPumpStatusLine? = null,
    isDark: Boolean,
    nowMs: Long,
    commands: GlassHeroCommands,
    onOpenTools: () -> Unit,
    selectedPills: List<GlassPillId>,
    currentBgValue: Float = state.currentBg.replace(",", ".").toFloatOrNull() ?: 0f,
    lowLine: Float = 70f,
    highLine: Float = 180f,
    isMmol: Boolean = false,
    onOpenMealAdvisor: () -> Unit = onOpenTools,
    onOpenAimiContext: () -> Unit = commands::openLoopDashboard,
) {
    var topExpanded by rememberSaveable { mutableStateOf(true) }
    var showIobDialog by remember { mutableStateOf(false) }
    var showSensDialog by remember { mutableStateOf(false) }

    val stale = GlassCgmLinkStatus.isStale(state.isGlucoseActual, state.glucoseTimestampMs, nowMs)
    val zone = classifyGlassZone(currentBgValue, stale, lowLine, highLine, isMmol)
    val pal = zone.zoneColors()

    val coreAnim by animateColorAsState(pal.core, tween(500), label = "core")
    val deepAnim by animateColorAsState(pal.deep, tween(500), label = "deep")
    val glowAnim by animateColorAsState(pal.glow, tween(500), label = "glow")

    val txtPrimary = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val txtMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    val bombaAgeValue = pumpStatus?.siteAgeText?.takeIf { it.isNotBlank() } ?: state.cannulaAge.ifBlank { "--" }
    val insulinaUnitsValue = pumpStatus?.reservoirText?.takeIf { it.isNotBlank() } ?: state.insulinAge.ifBlank { "--" }
    val sensorAgeValue = pumpStatus?.sensorAgeText?.takeIf { it.isNotBlank() } ?: state.sensorAge.ifBlank { "--" }

    val numericTimeAgo = remember(state.timeAgo) {
        val digits = state.timeAgo.filter { it.isDigit() }
        when {
            state.timeAgo.contains("-") || state.timeAgo.isBlank() -> "0m"
            digits.isNotEmpty() -> "${digits}m"
            else -> state.timeAgo
        }
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(isDark = isDark, radius = 18.dp)
                .padding(horizontal = 12.dp, vertical = 9.dp),
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
                        label = "Bomba",
                        value = bombaAgeValue,
                        valueColor = pumpTint,
                        labelColor = txtMuted,
                        onClick = commands::openCannula
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
                        label = "Insulina",
                        value = insulinaUnitsValue,
                        valueColor = insulinTint,
                        labelColor = txtMuted,
                        onClick = commands::openPump
                    )
                    DeviceBadgeCustom(
                        iconContent = { RoundWhiteSensorGraphic() },
                        label = "Sensor",
                        value = sensorAgeValue,
                        valueColor = sensorTint,
                        labelColor = txtMuted,
                        onClick = commands::openSensorQuality,
                        onLong = commands::openSensorInsert
                    )
                }
            } else {
                Text(
                    text = "Bomba: $bombaAgeValue • Insulina: $insulinaUnitsValue • Sensor: $sensorAgeValue",
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
        // CARTÃO PRINCIPAL DE TELEMETRIA VIDRO LÍQUIDO GRAFITE
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(isDark = isDark, radius = 22.dp)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. CABEÇALHO 50% / 50%: ESTRELA + PERFIL (Abre Perfis) | ÍCONE LOOP + STATUS DO LOOP (Abre Modo do Loop)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = state.profileName,
                        fontSize = 14.5.sp,
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
                        tint = if (state.loopIsRunning) GlassColors.emerald else Color(0xFFEF4444),
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = state.loopStatusText ?: "Loop Fechado",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = txtPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // CORPO DO CARTÃO: ESFERA À ESQUERDA + LISTA AO CENTRO + TIR & BOTÕES À DIREITA
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Esfera de Glicemia -> Abre Painel do Loop
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(124.dp)
                        .clip(CircleShape)
                        .border(2.dp, pal.border.copy(alpha = 0.85f), CircleShape)
                        .clickable { commands.openLoopDashboard() }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(coreAnim, deepAnim),
                                center = Offset(size.width * 0.35f, size.height * 0.30f),
                                radius = size.minDimension * 0.78f
                            )
                        )
                        drawCircle(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.38f), Color.Transparent),
                                startY = 0f,
                                endY = size.height * 0.55f
                            ),
                            radius = size.minDimension * 0.44f,
                            center = Offset(size.width * 0.5f, size.height * 0.44f)
                        )
                        drawCircle(
                            color = glowAnim.copy(alpha = 0.45f),
                            radius = size.minDimension * 0.47f,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = numericTimeAgo,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (stale) Color(0xFFFFCDD2) else Color.White.copy(alpha = 0.92f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.currentBg,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                textDecoration = if (stale) TextDecoration.LineThrough else null,
                                letterSpacing = (-1.2).sp,
                                lineHeight = 36.sp
                            )
                            state.trendArrow?.let {
                                Icon(
                                    imageVector = it.directionToIcon(),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Text(
                            text = "Δ ${state.deltaText} ${state.unit}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.95f)
                        )
                    }
                }
                // COLUNA CENTRAL: TELEMETRIA COMPACTA
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
                            Text(state.stepsText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = txtPrimary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Default.Favorite, null, tint = Color(0xFFF43F5E), modifier = Modifier.size(13.dp))
                            Text("${state.hrText} bpm", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = txtPrimary)
                        }
                    }

                    // IOB -> Abre janela com IOB de Bolus, IOB Basal e Total
                    TelemetryRow(
                        icon = Icons.Default.WaterDrop,
                        iconTint = Color(0xFF38BDF8),
                        label = "IOB",
                        value = state.iobText,
                        txtColor = txtPrimary,
                        onClick = { showIobDialog = true }
                    )
                    TelemetryRow(Icons.Default.Vaccines, Color(0xFF60A5FA), "Últ. Bolus", state.lastBolusText, txtPrimary, commands::openInsulin)
                    TelemetryRow(Icons.Default.Medication, Color(0xFF818CF8), "Basal", state.basalPercentText, txtPrimary, commands::openBasal)

                    // Carbo Ativo (reduzindo conforme absorção + pendente entre parênteses)
                    TelemetryRow(
                        icon = Icons.Default.LocalDining,
                        iconTint = Color(0xFFF59E0B),
                        label = "Carbo",
                        value = state.activeCobText,
                        txtColor = txtPrimary,
                        onClick = commands::openInsulin
                    )

                    // Sensibilidade inline (24,0 ↗ 28,5) -> Abre diálogo detalhado
                    TelemetryRow(
                        icon = Icons.Default.Speed,
                        iconTint = Color(0xFF2DD4BF),
                        label = "Sens.",
                        value = state.isfInlineText,
                        txtColor = txtPrimary,
                        onClick = { showSensDialog = true }
                    )

                    // Alvo Temporário sem quebra + destaque quando ativo -> Abre Alvo Temporário Completo
                    val targetDisplay = if (state.isTempTargetActive && state.tempTargetRemainingText.isNotBlank()) {
                        "${state.tempTargetText} (${state.tempTargetRemainingText})"
                    } else {
                        state.tempTargetText
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (state.isTempTargetActive) Color(0xFFF59E0B).copy(alpha = 0.18f) else Color.Transparent
                            )
                            .border(
                                width = if (state.isTempTargetActive) 1.dp else 0.dp,
                                color = if (state.isTempTargetActive) Color(0xFFF59E0B).copy(alpha = 0.55f) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { commands.openFullTempTarget() }
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrackChanges,
                            contentDescription = null,
                            tint = if (state.isTempTargetActive) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Alvo:",
                            fontSize = 10.sp,
                            color = if (state.isTempTargetActive) Color(0xFFD97706) else txtPrimary.copy(alpha = 0.72f),
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = targetDisplay,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (state.isTempTargetActive) (if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)) else txtPrimary,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // COLUNA DIREITA: BARRA TIR + BOTÕES AIMI + CALCULADORA
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tirPct = state.tir?.inRangePct?.roundToInt() ?: 0
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TIR", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = txtMuted)
                        Spacer(Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .width(11.dp)
                                .height(132.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isDark) Color(0xFF1E242E) else Color(0xFFE2E8F0)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight((tirPct / 100f).coerceIn(0.05f, 1f))
                                    .background(GlassColors.emerald)
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (state.tir != null) "$tirPct%" else "--",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = txtMuted
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        GlassSquareBtn(Icons.Default.CameraAlt, "Sugestão\nAIMI", Color(0xFF38BDF8), isDark, onOpenMealAdvisor)
                        GlassSquareBtn(Icons.Default.LocationOn, "Contexto", Color(0xFF94A3B8), isDark, onOpenAimiContext)
                        GlassSquareBtn(Icons.Default.Calculate, "Calc.", Color(0xFF22C55E), isDark, commands::openCalculator)
                        GlassSquareBtn(Icons.Default.Settings, "AIMI", Color(0xFFF59E0B), isDark, onOpenTools)
                    }
                }
            }
        }
    }
    // JANELA MODAL DE IOB (Bolus, Basal e Total)
    if (showIobDialog) {
        AlertDialog(
            onDismissRequest = { showIobDialog = false },
            title = {
                Text(
                    text = "Insulina Ativa (IOB)",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("IOB de Bolus: ${state.bolusIobText}", fontSize = 15.sp)
                    Text("IOB Basal: ${state.basalIobText}", fontSize = 15.sp)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "IOB Total: ${state.iobText}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showIobDialog = false }) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // JANELA MODAL DE SENSIBILIDADE (5 campos iguais ao original)
    if (showSensDialog) {
        AlertDialog(
            onDismissRequest = { showSensDialog = false },
            title = {
                Text(
                    text = "Sensibilidade",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("Valor do Autosens: ${state.autosensRatioText}", fontSize = 14.sp)
                    Text("ISF do perfil: ${state.profileIsfText}", fontSize = 14.sp)
                    Text("ISF variável: ${state.variableIsfText}", fontSize = 14.sp)
                    Text("Autosens no algoritmo: ${state.algorithmAutosensText}", fontSize = 14.sp)
                    Text(
                        text = "ISF médio para a calculadora e a absorção de carboidratos: ${state.averageIsfText}",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSensDialog = false }) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSensDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
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
private fun TelemetryRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    txtColor: Color,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 1.dp, horizontal = 2.dp)
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(12.dp))
        Text("$label:", fontSize = 10.sp, color = txtColor.copy(alpha = 0.72f), maxLines = 1, softWrap = false)
        Text(value, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = txtColor, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GlassSquareBtn(icon: ImageVector, label: String, accent: Color, isDark: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(54.dp)
            .height(36.dp)
            .glassSurface(isDark = isDark, radius = 11.dp)
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        Icon(icon, label, tint = accent, modifier = Modifier.size(14.dp))
        Text(
            text = label,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
            lineHeight = 8.sp,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
