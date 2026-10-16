package fi.kansalaisuuskoe.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.domain.Question
import fi.kansalaisuuskoe.domain.QuestionFormat
import kotlin.math.PI
import kotlin.math.sin

/** True/false answers are labelled in the interface language; other answers use the study-language option text. */
@Composable
internal fun answerLabel(question: Question, optionId: String): String =
    if (question.format == QuestionFormat.TRUE_FALSE) {
        stringResource(if (optionId == QuestionFormat.TRUE_ID) R.string.true_false_true else R.string.true_false_false)
    } else question.options.first { it.id == optionId }.text

@Composable
internal fun AnswerText(question: Question, optionId: String, style: TextStyle) {
    if (question.format == QuestionFormat.TRUE_FALSE) Text(answerLabel(question, optionId), style = style)
    else StudyText(answerLabel(question, optionId), question.language, style)
}

private enum class OptionState { Resting, Selected, Correct, Miss, Dimmed }

/** Shared accessible options (design system AnswerOption / TrueFalse); assessments always keep feedback false until completion. */
@Composable
internal fun AnswerChoices(question: Question, selected: String?, enabled: Boolean, select: (String) -> Unit, feedback: Boolean = false) {
    if (question.format == QuestionFormat.TRUE_FALSE) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.true_false_prompt), style = KsType.caption, color = Ks.colors.inkMuted)
            // A statement with two large tiles in a fixed order, whatever the file's option order.
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(QuestionFormat.TRUE_ID, QuestionFormat.FALSE_ID).forEach { id ->
                    Choice(question, id, selected, enabled, select, feedback, Modifier.weight(1f).heightIn(min = 112.dp), tile = true)
                }
            }
        }
        return
    }
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        question.options.forEach { option ->
            Choice(question, option.id, selected, enabled, select, feedback, Modifier.fillMaxWidth().heightIn(min = 60.dp), tile = false)
        }
    }
}

@Composable
private fun Choice(question: Question, optionId: String, selected: String?, enabled: Boolean, select: (String) -> Unit,
                   feedback: Boolean, modifier: Modifier, tile: Boolean) {
    val c = Ks.colors
    val view = LocalView.current
    val isSelected = selected == optionId
    val state = when {
        feedback && optionId == question.correctOptionId -> OptionState.Correct
        feedback && isSelected -> OptionState.Miss
        feedback -> OptionState.Dimmed
        isSelected -> OptionState.Selected
        else -> OptionState.Resting
    }
    // Animate only a reveal seen on screen, not one restored with the screen.
    var shown by remember(question.id) { mutableStateOf(state) }
    val revealed = shown != state && (state == OptionState.Correct || state == OptionState.Miss)
    // 0 → 1 over the reveal: a correct pick pops 1 → 1.025 → 1, a miss shakes ±6dp and settles.
    val reveal = remember(question.id) { Animatable(1f) }
    LaunchedEffect(state) {
        if (revealed) {
            if (isSelected) view.verdictHaptic(state == OptionState.Correct)
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(KsMotion.REVEAL, easing = LinearEasing))
        }
        shown = state
    }
    val t = reveal.value
    val scale = if (state == OptionState.Correct) 1f + 0.025f * sin(PI.toFloat() * t) else 1f
    val offset = if (state == OptionState.Miss) -6f * sin(4 * PI.toFloat() * t) * (1f - t) else 0f

    val fill by animateColorAsState(when (state) {
        OptionState.Selected -> c.brandSoft; OptionState.Correct -> c.correctSoft; OptionState.Miss -> c.missSoft; else -> c.surface1
    }, tween(KsMotion.SELECT), label = "fill")
    val stroke by animateColorAsState(when (state) {
        OptionState.Selected -> c.brand; OptionState.Correct -> c.correct; OptionState.Miss -> c.miss; else -> c.line
    }, tween(KsMotion.SELECT), label = "border")
    val ink = when (state) { OptionState.Correct -> c.onCorrectSoft; OptionState.Miss -> c.onMissSoft; else -> c.ink }
    val width by animateDpAsState(if (state == OptionState.Resting || state == OptionState.Dimmed) 1.5.dp else 2.dp, tween(KsMotion.SELECT), label = "width")
    val opacity by animateFloatAsState(if (state == OptionState.Dimmed) 0.55f else 1f, tween(KsMotion.SELECT), label = "dim")
    val interaction = remember { MutableInteractionSource() }
    val lifted = state == OptionState.Resting || state == OptionState.Selected

    Surface(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset * density }
            .pressScale(interaction, 0.98f)
            .alpha(opacity)
            .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, interactionSource = interaction, indication = null,
                onClick = { view.tapHaptic(); select(optionId) }),
        color = fill, contentColor = ink, shape = MaterialTheme.shapes.medium,
        border = BorderStroke(width, stroke), shadowElevation = if (lifted) 1.dp else 0.dp,
    ) {
        val tag = when (state) {
            // Text labels keep feedback understandable without colour.
            OptionState.Correct -> stringResource(R.string.correct_answer_label)
            OptionState.Miss -> stringResource(R.string.your_answer)
            else -> null
        }
        val locale = LocalConfiguration.current.locales[0]
        val tagColor = if (state == OptionState.Correct) c.correct else c.miss
        if (tile) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                val (disc, glyph) = when (state) {
                    OptionState.Selected -> c.brand to c.onBrand
                    OptionState.Correct -> c.correct to c.onCorrect
                    OptionState.Miss -> c.miss to c.onMiss
                    else -> c.surface2 to c.inkMuted
                }
                val discColor by animateColorAsState(disc, tween(KsMotion.SELECT), label = "glyph")
                Box(Modifier.size(36.dp).background(discColor, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (optionId == QuestionFormat.TRUE_ID) KsIcons.Check else KsIcons.Cross, null, Modifier.size(20.dp), tint = glyph)
                }
                Text(answerLabel(question, optionId), style = KsType.verdict.copy(fontSize = 20.sp, lineHeight = 26.sp), textAlign = TextAlign.Center)
                tag?.let { Text(it.uppercase(locale), style = KsType.overline, color = tagColor, textAlign = TextAlign.Center) }
            }
        } else {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    StudyText(question.options.first { it.id == optionId }.text, question.language, KsType.option)
                    tag?.let { Text(it.uppercase(locale), style = KsType.overline, color = tagColor) }
                }
                when (state) {
                    OptionState.Correct, OptionState.Miss -> VerdictBadge(state == OptionState.Correct, animate = revealed)
                    else -> RadioMark(state == OptionState.Selected)
                }
            }
        }
    }
}

/** The resting ring, or a filled dot while selected. */
@Composable
private fun RadioMark(selected: Boolean) {
    val c = Ks.colors
    val ring by animateColorAsState(if (selected) c.brand else c.lineStrong, tween(KsMotion.SELECT), label = "ring")
    val dot by animateDpAsState(if (selected) 12.dp else 0.dp, tween(KsMotion.SELECT, easing = CubicBezierEasing(.2f, .9f, .3f, 1.3f)), label = "dot")
    Box(Modifier.size(24.dp).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(dot).background(c.brand, CircleShape))
    }
}
