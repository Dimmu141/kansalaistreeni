package fi.kansalaisuuskoe.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import fi.kansalaisuuskoe.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------- Interaction ----------

/** Press feedback from the design system: the control scales down while pressed. */
@Composable
internal fun Modifier.pressScale(interaction: InteractionSource, pressed: Float = 0.97f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, tween(KsMotion.PRESS), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

internal fun View.tapHaptic() { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }

/** Confirm or reject on reveal (API 30+); older devices stay silent rather than buzz ambiguously. */
internal fun View.verdictHaptic(correct: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) performHapticFeedback(if (correct) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT)
}

// ---------- Icons (rounded strokes, matching the launcher icon's check) ----------

private fun strokeIcon(name: String, width: Float, vararg paths: String, filled: Boolean = false) =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { data ->
            addPath(addPathNodes(data), fill = if (filled) SolidColor(Color.Black) else null, stroke = SolidColor(Color.Black),
                strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
    }.build()

internal object KsIcons {
    val Check = strokeIcon("Check", 3f, "M5 12.5l4.5 4.5L19 7.5")
    val Cross = strokeIcon("Cross", 3f, "M7 7l10 10M17 7L7 17")
    val Clock = strokeIcon("Clock", 2f, "M12 3.5a8.5 8.5 0 1 1 0 17a8.5 8.5 0 1 1 0-17z", "M12 7.5V12l3 2")
    val Flame = strokeIcon("Flame", 1.6f, "M12 3c1 4 5 5.5 5 10a5 5 0 0 1-10 0c0-2.5 1.5-3.5 2-5.5 1 1 1.5 2 1.5 3C12 8 12 5.5 12 3z", filled = true)
    val Star = strokeIcon("Star", 1.6f, "M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z", filled = true)
    val Document = strokeIcon("Document", 2f, "M7 3.5h7l4 4v13H7z", "M14 3.5v4h4", "M10 12h5M10 15.5h5")
}

// ---------- Buttons ----------

enum class KsTone { Primary, Tonal, Correct, Miss }

/** Pill button, 56dp tall, that scales on press. One primary per screen; Correct/Miss only inside the feedback sheet. */
@Composable
internal fun KsButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, tone: KsTone = KsTone.Primary) {
    val c = Ks.colors
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (container, content) = when (tone) {
        KsTone.Primary -> (if (pressed) c.brandPressed else c.brand) to c.onBrand
        KsTone.Tonal -> c.brandSoft to c.onBrandSoft
        KsTone.Correct -> c.correct to c.onCorrect
        KsTone.Miss -> c.miss to c.onMiss
    }
    val fill by animateColorAsState(container, tween(KsMotion.PRESS), label = "button")
    Button(
        onClick = { view.tapHaptic(); onClick() }, enabled = enabled, interactionSource = interaction, shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = fill, contentColor = content, disabledContainerColor = c.surface2, disabledContentColor = c.inkMuted),
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier.heightIn(min = 56.dp).pressScale(interaction),
    ) { Text(text, style = KsType.label, textAlign = TextAlign.Center) }
}

// ---------- Chips ----------

enum class ChipTone { Quiet, Brand, Birch }

@Composable
internal fun KsChip(text: String, tone: ChipTone = ChipTone.Quiet, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    val c = Ks.colors
    val (fill, ink) = when (tone) {
        ChipTone.Quiet -> c.surface2 to c.inkMuted
        ChipTone.Brand -> c.brandSoft to c.onBrandSoft
        ChipTone.Birch -> c.birchSoft to c.onBirchSoft
    }
    Surface(color = fill, contentColor = ink, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Row(Modifier.heightIn(min = 32.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            icon?.let { Icon(it, null, Modifier.size(16.dp), tint = if (tone == ChipTone.Birch) c.birch else ink) }
            Text(text, style = KsType.caption.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

// ---------- Cards ----------

/** A `surface-1` card with a hairline and a soft lift; tappable cards scale to 0.985 on press. */
@Composable
internal fun KsCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Ks.colors
    val body: @Composable () -> Unit = { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth().pressScale(interaction, 0.985f), interactionSource = interaction,
            color = c.surface1, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, c.line), shadowElevation = 1.dp, content = body)
    } else {
        Surface(modifier = modifier.fillMaxWidth(), color = c.surface1, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, c.line), shadowElevation = 1.dp, content = body)
    }
}

// ---------- Progress ----------

/** Rounded progress track; 0 % reads as an empty `surface-2` track. */
@Composable
fun ProgressBar(fraction: Float, height: Dp = 8.dp, color: Color = Ks.colors.brand) {
    val value = fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(value, tween(KsMotion.PROGRESS, easing = FastOutSlowInEasing), label = "progress")
    val shape = RoundedCornerShape(height / 2)
    Box(Modifier.fillMaxWidth().height(height).clip(shape).background(Ks.colors.surface2).progressSemantics(value)) {
        Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(shape).background(color))
    }
}

enum class Segment { Upcoming, Current, Correct, Miss }

/** Short sessions show one segment per question: a mini scoreboard. */
@Composable
internal fun ProgressSegments(segments: List<Segment>, modifier: Modifier = Modifier) {
    val c = Ks.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        segments.forEach { segment ->
            val color by animateColorAsState(when (segment) {
                Segment.Upcoming -> c.surface2; Segment.Current -> c.brand; Segment.Correct -> c.correct; Segment.Miss -> c.miss
            }, tween(KsMotion.PROGRESS), label = "segment")
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
    }
}

/** The mastery ring: fills from 0 on first composition while the number counts up in sync. */
@Composable
internal fun MasteryRing(percent: Int, modifier: Modifier = Modifier, size: Dp = 112.dp) {
    val c = Ks.colors
    val progress = remember { Animatable(0f) }
    LaunchedEffect(percent) { progress.animateTo(percent.coerceIn(0, 100).toFloat(), tween(KsMotion.PROGRESS, delayMillis = 60, easing = FastOutSlowInEasing)) }
    val ringColor = if (percent >= 100) c.birch else c.brand
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(c.surface1, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (progress.value > 0f) drawArc(ringColor, -90f, 360f * progress.value / 100f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(stringResource(R.string.percent_value, progress.value.roundToInt()), style = KsType.display.copy(fontSize = 30.sp, lineHeight = 30.sp), color = c.brand)
    }
}

// ---------- Verdict ----------

/** A solid disc with a check (correct) or cross (miss) that pops in and draws its stroke. */
@Composable
internal fun VerdictBadge(correct: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp, animate: Boolean = true) {
    val c = Ks.colors
    val scale = remember { Animatable(if (animate) 0.4f else 1f) }
    val draw = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(correct) {
        if (animate) {
            launch { scale.animateTo(1f, tween(KsMotion.REVEAL, easing = CubicBezierEasing(.2f, .9f, .3f, 1.4f))) }
            draw.animateTo(1f, tween(KsMotion.REVEAL, delayMillis = 80))
        }
    }
    val disc = if (correct) c.correct else c.miss
    val mark = if (correct) c.onCorrect else c.onMiss
    Canvas(modifier.size(size).graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
        drawCircle(disc)
        val u = this.size.width / 24f
        val lines = if (correct) listOf(listOf(Offset(7f, 12.5f), Offset(10.5f, 16f), Offset(17.5f, 8.5f)))
        else listOf(listOf(Offset(8f, 8f), Offset(16f, 16f)), listOf(Offset(16f, 8f), Offset(8f, 16f)))
        lines.forEach { points ->
            val full = Path().apply { moveTo(points[0].x * u, points[0].y * u); points.drop(1).forEach { lineTo(it.x * u, it.y * u) } }
            val measure = PathMeasure().apply { setPath(full, false) }
            val part = Path().also { measure.getSegment(0f, measure.length * draw.value, it, true) }
            drawPath(part, mark, style = Stroke(width = 2.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
