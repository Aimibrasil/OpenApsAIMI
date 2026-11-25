package app.aaps.ui.compose.dashboard.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import kotlinx.datetime.TimeZone
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.time.Clock

private const val AXIS_VALUE_TEXT_PX = 28f
private const val AXIS_HOUR_TEXT_PX = 26f

private fun DrawScope.drawCenteredText(
    textMeasurer: TextMeasurer, text: String, centerX: Float, baselineY: Float, color: Color, textSizePx: Float,
) {
    val layout = textMeasurer.measure(text, TextStyle(color = color, fontSize = textSizePx.toSp()), density = this)
    drawText(layout, topLeft = Offset(centerX - layout.size.width / 2f, baselineY - layout.firstBaseline))
}

@Composable
internal fun BgChartCard(
    readings: List<BgReadingPoint>,
    treatments: List<TreatmentPoint>,
    timeRangeHours: Int,
    currentBgValue: Float,
    lowLine: Float,
    highLine: Float,
    predictions: List<PredictionPoint>,
    historyFraction: Float,
    axisMinValue: Float,
    axisHeadroom: Float,
    basalReadings: List<BasalReadingPoint> = emptyList(),
    profileBasalReadings: List<BasalReadingPoint> = emptyList(),
    maxBasalRateUh: Float = 1f,
    formatValue: (Float) -> String,
    isDark: Boolean,
    accentColor: Color? = null,
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableFloatStateOf(0f) }
    var touchedReading by remember { mutableStateOf<BgReadingPoint?>(null) }
    var selectedTreatment by remember { mutableStateOf<TreatmentPoint?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val timeZone = TimeZone.currentSystemDefault()

    LaunchedEffect(timeRangeHours) {
        zoom = 1f
        panOffset = 0f
        touchedReading = null
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .glassSurface(isDark = isDark, radius = 18.dp, accent = accentColor)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(CoreUiStrings.bg_label), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B))
                    if (zoom > 1.05f || panOffset > 5f) {
                        Text("• ${glassFixedDecimals(zoom, 1)}x", fontSize = 10.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold)
                    }
                }
                val pt = touchedReading
                if (pt != null) {
                    val timeStr = glassClockLabel(Clock.System.now().toEpochMilliseconds(), (timeRangeHours * (1f - pt.progress) * 60).toLong(), timeZone)
                    val bgColor = if (pt.value < lowLine) Color(0xFFEF4444) else if (pt.value > highLine) Color(0xFFF59E0B) else Color(0xFF22C55E)
                    Text("$timeStr • ${formatValue(pt.value)}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = bgColor)
                } else {
                    val curColor = if (readings.isEmpty()) Color(0xFF94A3B8) else if (currentBgValue < lowLine) Color(0xFFEF4444) else if (currentBgValue > highLine) Color(0xFFF59E0B) else Color(0xFF22C55E)
                    Text(if (readings.isEmpty()) "--" else formatValue(currentBgValue), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = curColor)
                }
            }

            Spacer(Modifier.height(2.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(readings, treatments, timeRangeHours, zoom, panOffset) {
                        detectTapGestures(
                            onDoubleTap = { zoom = 1f; panOffset = 0f; touchedReading = null },
                            onTap = { pos ->
                                val chartW = size.width - 65f
                                val p = ((pos.x + panOffset) / (chartW * zoom * historyFraction)).coerceIn(0f, 1f)
                                touchedReading = readings.minByOrNull { abs(it.progress - p) }
                                val nearT = treatments.minByOrNull { abs(it.progress - p) }
                                selectedTreatment = if (nearT != null && abs(nearT.progress - p) < (0.04f / zoom)) nearT else null
                            }
                        )
                    }
                    .pointerInput(zoom) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            val chartW = size.width - 65f
                            val newZoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                            val maxPan = (chartW * newZoom - chartW).coerceAtLeast(0f)
                            zoom = newZoom
                            panOffset = (panOffset - pan.x).coerceIn(0f, maxPan)
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val paddingRight = 65f
                val chartW = w - paddingRight
                val chartH = h - 30f
                val scaledW = chartW * zoom

                fun historyX(progress: Float): Float = (progress * scaledW * historyFraction) - panOffset
                fun predictionX(progress: Float): Float = ((historyFraction + progress * (1f - historyFraction)) * scaledW) - panOffset

                val minY = axisMinValue
                val maxY = maxOf(readings.maxByOrNull { it.value }?.value ?: highLine, predictions.maxByOrNull { it.value }?.value ?: highLine, highLine) + axisHeadroom
                fun mapY(bg: Float): Float = chartH - ((bg.coerceIn(minY, maxY) - minY) / (maxY - minY)) * chartH
                val yHigh = mapY(highLine)
                val yLow = mapY(lowLine)

                clipRect(0f, 0f, chartW, h) {
                    drawRect(Brush.verticalGradient(listOf(Color(0xFF22C55E).copy(0.22f), Color(0xFF22C55E).copy(0.05f)), yHigh, yLow), Offset(0f, yHigh), Size(chartW, yLow - yHigh))
                    val lineStroke = if (isDark) Color(0x2EFFFFFF) else Color(0x26000000)
                    drawLine(lineStroke, Offset(0f, yHigh), Offset(chartW, yHigh), 1f)
                    drawLine(lineStroke, Offset(0f, yLow), Offset(chartW, yLow), 1f)

                    val nowX = historyX(1f)
                    if (nowX in 0f..chartW) drawLine(if (isDark) Color(0x55FFFFFF) else Color(0x40000000), Offset(nowX, 0f), Offset(nowX, chartH), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))

                    if (readings.size >= 2) {
                        fun zColor(bg: Float) = if (bg < lowLine) Color(0xFFEF4444) else if (bg > highLine) Color(0xFFF59E0B) else Color(0xFF22C55E)
                        for (i in 1 until readings.size) {
                            val prev = readings[i - 1]; val curr = readings[i]
                            val pX = historyX(prev.progress); val cX = historyX(curr.progress)
                            if (cX < 0f || pX > chartW) continue
                            val midX = (pX + cX) / 2f
                            val seg = Path().apply { moveTo(pX, mapY(prev.value)); cubicTo(midX, mapY(prev.value), midX, mapY(curr.value), cX, mapY(curr.value)) }
                            drawPath(seg, zColor((prev.value + curr.value) / 2f), style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        val lastX = historyX(readings.last().progress); val lastY = mapY(readings.last().value)
                        if (lastX in 0f..chartW) { drawCircle(zColor(readings.last().value), 10f, Offset(lastX, lastY)); drawCircle(Color.White, 4.5f, Offset(lastX, lastY)) }
                    }

                    predictions.groupBy { it.type }.forEach { (type, pts) ->
                        val c = when (type) { PredictionType.IOB -> Color(0xFF64B5F6); PredictionType.COB -> Color(0xFFFFB74D); PredictionType.A_COB -> Color(0xFFFFB74D).copy(0.55f); PredictionType.UAM -> Color(0xFFE6D39A); PredictionType.ZT -> Color(0xFF4DD4D4) }
                        val sorted = pts.sortedBy { it.progress }
                        for (i in 1 until sorted.size) {
                            val pX = predictionX(sorted[i - 1].progress); val cX = predictionX(sorted[i].progress)
                            if (cX >= 0f && pX <= chartW) drawLine(c, Offset(pX, mapY(sorted[i - 1].value)), Offset(cX, mapY(sorted[i].value)), 5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                        }
                    }

                    val hourColor = if (isDark) Color(0x82FFFFFF) else Color(0x96475569)
                    val nowMs = Clock.System.now().toEpochMilliseconds()
                    val ticks = (6 * zoom).toInt().coerceIn(6, 24)
                    for (i in 0 until ticks) {
                        val prog = i.toFloat() / (ticks - 1); val x = historyX(prog)
                        if (x in 15f..(chartW - 15f)) drawCenteredText(textMeasurer, glassHourLabel(nowMs, (timeRangeHours * (1f - prog) * 60).toLong(), timeZone), x, h - 4f, hourColor, AXIS_HOUR_TEXT_PX)
                    }

                    touchedReading?.let { pt ->
                        val ptX = historyX(pt.progress); val ptY = mapY(pt.value)
                        if (ptX in 0f..chartW) {
                            drawLine(Color.White.copy(0.45f), Offset(ptX, 0f), Offset(ptX, chartH), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                            drawCircle(Color(0xFFF59E0B), 10f, Offset(ptX, ptY)); drawCircle(Color.White, 4f, Offset(ptX, ptY))
                        }
                    }
                }
                val axisColor = if (isDark) Color(0xA0FFFFFF) else Color(0xB4475569)
                drawCenteredText(textMeasurer, formatValue(highLine), chartW + paddingRight / 2f, yHigh + 8f, axisColor, AXIS_VALUE_TEXT_PX)
                drawCenteredText(textMeasurer, formatValue(lowLine), chartW + paddingRight / 2f, yLow + 8f, axisColor, AXIS_VALUE_TEXT_PX)
            }
        }
    }
    selectedTreatment?.let { t ->
        val labelColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
        val valueColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
        AlertDialog(
            onDismissRequest = { selectedTreatment = null },
            confirmButton = { TextButton(onClick = { selectedTreatment = null }) { Text(stringResource(CoreUiStrings.ok), color = Color(0xFF38BDF8)) } },
            title = { Text(stringResource(UiStrings.dashboard_glass_treatment_details_title), fontWeight = FontWeight.Bold, color = valueColor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("Tipo:", color = labelColor, fontSize = 13.sp); Text(if (t.isCarb) "Carboidrato" else "Bolus / SMB", color = valueColor, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("Data/Hora:", color = labelColor, fontSize = 13.sp); Text("${glassDateLabel(t.timestamp, timeZone)} ${glassClockLabel(t.timestamp, timeZone)}", color = valueColor, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("Quantidade:", color = labelColor, fontSize = 13.sp); Text(t.label, color = if (t.isCarb) Color(0xFF22C55E) else Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            },
            containerColor = if (isDark) Color(0xE60F172A) else Color(0xF8FFFFFF),
            shape = RoundedCornerShape(18.dp)
        )
    }
}
@Composable
internal fun IobChartCard(
    iobReadings: List<IobReadingPoint>,
    currentIob: Float,
    timeRangeHours: Int,
    historyFraction: Float,
    isDark: Boolean,
    predictions: List<IobReadingPoint> = emptyList(),
    accentColor: Color? = null,
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableFloatStateOf(0f) }
    var touchedIob by remember { mutableStateOf<IobReadingPoint?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val timeZone = TimeZone.currentSystemDefault()

    LaunchedEffect(timeRangeHours) { zoom = 1f; panOffset = 0f; touchedIob = null }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(138.dp)
            .glassSurface(isDark = isDark, radius = 18.dp, accent = accentColor)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("IOB • Insulina Ativa", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B))
                val pt = touchedIob
                val labelText = if (pt != null) {
                    val tStr = glassClockLabel(Clock.System.now().toEpochMilliseconds(), (timeRangeHours * (1f - pt.progress) * 60).toLong(), timeZone)
                    "$tStr • ${stringResource(InterfacesStrings.format_insulin_units, pt.iob.toDouble())}"
                } else {
                    stringResource(InterfacesStrings.format_insulin_units, currentIob.toDouble())
                }
                Text(labelText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
            }
            Spacer(Modifier.height(2.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(iobReadings, timeRangeHours, zoom, panOffset) {
                        detectTapGestures(
                            onDoubleTap = { zoom = 1f; panOffset = 0f; touchedIob = null },
                            onTap = { pos ->
                                val chartW = size.width - 65f
                                val p = ((pos.x + panOffset) / (chartW * zoom * historyFraction)).coerceIn(0f, 1f)
                                touchedIob = iobReadings.minByOrNull { abs(it.progress - p) }
                            }
                        )
                    }
                    .pointerInput(zoom) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            val chartW = size.width - 65f
                            val newZoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                            val maxPan = (chartW * newZoom - chartW).coerceAtLeast(0f)
                            zoom = newZoom
                            panOffset = (panOffset - pan.x).coerceIn(0f, maxPan)
                        }
                    }
            ) {
                val w = size.width; val h = size.height; val paddingRight = 65f; val chartW = w - paddingRight; val chartH = h - 24f; val scaledW = chartW * zoom
                fun historyX(prog: Float) = (prog * scaledW * historyFraction) - panOffset
                fun predX(prog: Float) = ((historyFraction + prog * (1f - historyFraction)) * scaledW) - panOffset
                val peak = maxOf(iobReadings.maxByOrNull { it.iob }?.iob ?: 0f, predictions.maxByOrNull { it.iob }?.iob ?: 0f, currentIob).coerceAtLeast(0f)
                val maxIob = if (peak == 0f) 1f else if (peak % 1f == 0f) peak + 1f else ceil(peak)
                fun mapY(v: Float) = chartH - (v.coerceIn(0f, maxIob) / maxIob) * chartH

                clipRect(0f, 0f, chartW, h) {
                    val grid = if (isDark) Color(0x1FFFFFFF) else Color(0x1F000000)
                    drawLine(grid, Offset(0f, chartH), Offset(chartW, chartH), 1f)
                    drawLine(grid, Offset(0f, 0f), Offset(chartW, 0f), 1f)
                    if (iobReadings.size >= 2) {
                        val path = Path(); val area = Path()
                        val fx = historyX(iobReadings.first().progress); val fy = mapY(iobReadings.first().iob)
                        path.moveTo(fx, fy); area.moveTo(fx, chartH); area.lineTo(fx, fy)
                        for (i in 1 until iobReadings.size) {
                            val p = iobReadings[i - 1]; val c = iobReadings[i]
                            val px = historyX(p.progress); val cx = historyX(c.progress); val mid = (px + cx) / 2f
                            path.cubicTo(mid, mapY(p.iob), mid, mapY(c.iob), cx, mapY(c.iob))
                            area.cubicTo(mid, mapY(p.iob), mid, mapY(c.iob), cx, mapY(c.iob))
                        }
                        area.lineTo(historyX(iobReadings.last().progress), chartH); area.close()
                        drawPath(area, Brush.verticalGradient(listOf(Color(0xFF38BDF8).copy(0.32f), Color.Transparent), 0f, chartH))
                        drawPath(path, Color(0xFF38BDF8), style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    val sorted = predictions.sortedBy { it.progress }
                    for (i in 1 until sorted.size) {
                        drawLine(Color(0xFF38BDF8), Offset(predX(sorted[i - 1].progress), mapY(sorted[i - 1].iob)), Offset(predX(sorted[i].progress), mapY(sorted[i].iob)), 5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                    }
                    val hourColor = if (isDark) Color(0x82FFFFFF) else Color(0x96475569)
                    val nowMs = Clock.System.now().toEpochMilliseconds()
                    for (i in 0 until 6) {
                        val prog = i / 5f; val x = historyX(prog)
                        if (x in 15f..(chartW - 15f)) drawCenteredText(textMeasurer, glassHourLabel(nowMs, (timeRangeHours * (1f - prog) * 60).toLong(), timeZone), x, h - 4f, hourColor, AXIS_HOUR_TEXT_PX)
                    }
                }
                val axisColor = if (isDark) Color(0xA0FFFFFF) else Color(0xB4475569)
                drawCenteredText(textMeasurer, glassFixedDecimals(maxIob, 1), chartW + paddingRight / 2f, 12f, axisColor, AXIS_VALUE_TEXT_PX)
                drawCenteredText(textMeasurer, "0", chartW + paddingRight / 2f, chartH + 4f, axisColor, AXIS_VALUE_TEXT_PX)
            }
        }
    }
}
@Composable
internal fun BolusBasalChartCard(
    treatments: List<TreatmentPoint>,
    basalReadings: List<BasalReadingPoint>,
    profileBasalReadings: List<BasalReadingPoint>,
    maxBasalRateUh: Float,
    timeRangeHours: Int,
    historyFraction: Float,
    isDark: Boolean,
    accentColor: Color? = null,
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableFloatStateOf(0f) }
    var touchedTreatment by remember { mutableStateOf<TreatmentPoint?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val timeZone = TimeZone.currentSystemDefault()

    LaunchedEffect(timeRangeHours) { zoom = 1f; panOffset = 0f; touchedTreatment = null }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(138.dp)
            .glassSurface(isDark = isDark, radius = 18.dp, accent = accentColor)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Bolus, Carbo & Basal", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B))
                val pt = touchedTreatment
                if (pt != null) {
                    val tStr = glassClockLabel(pt.timestamp, timeZone)
                    val c = if (pt.isCarb) Color(0xFFF59E0B) else Color(0xFF38BDF8)
                    Text("$tStr • ${pt.label}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c)
                } else {
                    val lastBolus = treatments.filter { !it.isCarb }.maxByOrNull { it.timestamp }?.label ?: "--"
                    Text("Últ. Bolus: $lastBolus", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF818CF8))
                }
            }

            Spacer(Modifier.height(2.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(treatments, timeRangeHours, zoom, panOffset) {
                        detectTapGestures(
                            onDoubleTap = { zoom = 1f; panOffset = 0f; touchedTreatment = null },
                            onTap = { pos ->
                                val chartW = size.width - 65f
                                val p = ((pos.x + panOffset) / (chartW * zoom * historyFraction)).coerceIn(0f, 1f)
                                touchedTreatment = treatments.minByOrNull { abs(it.progress - p) }
                            }
                        )
                    }
                    .pointerInput(zoom) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            val chartW = size.width - 65f
                            val newZoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                            val maxPan = (chartW * newZoom - chartW).coerceAtLeast(0f)
                            zoom = newZoom
                            panOffset = (panOffset - pan.x).coerceIn(0f, maxPan)
                        }
                    }
            ) {
                val w = size.width; val h = size.height; val paddingRight = 65f; val chartW = w - paddingRight; val chartH = h - 24f; val scaledW = chartW * zoom
                fun historyX(prog: Float) = (prog * scaledW * historyFraction) - panOffset
                val maxB = maxBasalRateUh.coerceAtLeast(0.5f)
                fun mapBasalY(rate: Float) = chartH - (rate.coerceIn(0f, maxB) / maxB) * (chartH * 0.65f)

                clipRect(0f, 0f, chartW, h) {
                    val grid = if (isDark) Color(0x1FFFFFFF) else Color(0x1F000000)
                    drawLine(grid, Offset(0f, chartH), Offset(chartW, chartH), 1f)

                    if (profileBasalReadings.isNotEmpty()) {
                        val path = Path()
                        var y = mapBasalY(profileBasalReadings.first().rateUh)
                        path.moveTo(historyX(profileBasalReadings.first().progress), y)
                        for (i in 1 until profileBasalReadings.size) {
                            val nx = historyX(profileBasalReadings[i].progress)
                            path.lineTo(nx, y)
                            y = mapBasalY(profileBasalReadings[i].rateUh)
                            path.lineTo(nx, y)
                        }
                        path.lineTo(historyX(1f), y)
                        drawPath(path, Color(0xFF94A3B8).copy(0.55f), style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))))
                    }

                    if (basalReadings.isNotEmpty()) {
                        val path = Path()
                        var y = mapBasalY(basalReadings.first().rateUh)
                        path.moveTo(historyX(basalReadings.first().progress), y)
                        for (i in 1 until basalReadings.size) {
                            val nx = historyX(basalReadings[i].progress)
                            path.lineTo(nx, y)
                            y = mapBasalY(basalReadings[i].rateUh)
                            path.lineTo(nx, y)
                        }
                        path.lineTo(historyX(1f), y)
                        drawPath(path, Color(0xFF818CF8), style = Stroke(3.5f))
                    }

                    treatments.forEach { t ->
                        val tx = historyX(t.progress)
                        if (tx in 0f..chartW) {
                            val color = if (t.isCarb) Color(0xFFF59E0B) else Color(0xFF38BDF8)
                            val topY = if (t.isCarb) chartH * 0.25f else chartH * 0.12f
                            drawLine(color.copy(0.65f), Offset(tx, chartH), Offset(tx, topY), 4f, cap = StrokeCap.Round)
                            drawCircle(color, 7f, Offset(tx, topY))
                            drawCircle(Color.White, 3f, Offset(tx, topY))
                        }
                    }

                    val hourColor = if (isDark) Color(0x82FFFFFF) else Color(0x96475569)
                    val nowMs = Clock.System.now().toEpochMilliseconds()
                    for (i in 0 until 6) {
                        val prog = i / 5f; val x = historyX(prog)
                        if (x in 15f..(chartW - 15f)) drawCenteredText(textMeasurer, glassHourLabel(nowMs, (timeRangeHours * (1f - prog) * 60).toLong(), timeZone), x, h - 4f, hourColor, AXIS_HOUR_TEXT_PX)
                    }
                }
                val axisColor = if (isDark) Color(0xA0FFFFFF) else Color(0xB4475569)
                drawCenteredText(textMeasurer, "${glassFixedDecimals(maxB, 1)}U", chartW + paddingRight / 2f, 14f, axisColor, AXIS_VALUE_TEXT_PX)
                drawCenteredText(textMeasurer, "0", chartW + paddingRight / 2f, chartH + 4f, axisColor, AXIS_VALUE_TEXT_PX)
            }
        }
    }
}
