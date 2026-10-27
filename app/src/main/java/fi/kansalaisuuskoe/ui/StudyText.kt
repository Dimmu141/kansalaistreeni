package fi.kansalaisuuskoe.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList

/** Preserve the study language in mixed-language accessibility text. */
internal fun localizedText(text: String, language: String) = AnnotatedString(
    text, spanStyle = SpanStyle(localeList = LocaleList(Locale(language))),
)

/** Languages written right to left. */
private val RIGHT_TO_LEFT = setOf("ar", "fa", "prs", "ckb", "ur", "he")

/** Study text keeps its own direction whatever the interface: Finnish stays LTR in an Arabic screen and vice versa. */
internal fun textDirectionFor(language: String) =
    if (language.substringBefore('-') in RIGHT_TO_LEFT) TextDirection.Rtl else TextDirection.Ltr

@Composable
internal fun StudyText(text: String, language: String, style: TextStyle, modifier: Modifier = Modifier) {
    val direction = textDirectionFor(language)
    // Start/End follow the text's own direction. Finnish in an Arabic interface still lines up with the screen's start edge.
    val align = if (style.textAlign != TextAlign.Unspecified) style.textAlign
        else if (direction == TextDirection.Ltr && LocalLayoutDirection.current == LayoutDirection.Rtl) TextAlign.End else TextAlign.Start
    Text(localizedText(text, language), style = style.copy(textDirection = direction, textAlign = align), modifier = modifier)
}
