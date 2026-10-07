package fi.kansalaisuuskoe.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList

/** Preserve the study language in mixed-language accessibility text. */
internal fun localizedText(text: String, language: String) = AnnotatedString(
    text, spanStyle = SpanStyle(localeList = LocaleList(Locale(language))),
)

@Composable
internal fun StudyText(text: String, language: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(localizedText(text, language), style = style, modifier = modifier)
}
