package fi.kansalaisuuskoe.ui

import android.content.res.Configuration
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.LayoutDirection
import fi.kansalaisuuskoe.data.InterfaceLanguage
import java.util.Locale

/** In-app preference across API 26+. Keep the navigation/session alive when switching. */
@Composable
fun InterfaceLocale(language: InterfaceLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val current = LocalConfiguration.current
    // Registry lookup falls back to Context. Preserve the Activity owner before replacing that context.
    val activityResultOwner = LocalActivityResultRegistryOwner.current
    val ownerProviders = listOfNotNull(activityResultOwner?.let { LocalActivityResultRegistryOwner provides it })
    val configuration = remember(current, language) {
        Configuration(current).apply { setLocale(Locale.forLanguageTag(language.tag)) }
    }
    val localized = remember(context, configuration) { context.createConfigurationContext(configuration) }
    // The Activity's direction follows the device locale; an Arabic interface on a Finnish phone must still mirror.
    val direction = if (configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(
        LocalConfiguration provides configuration,
        LocalLayoutDirection provides direction,
        LocalContext provides localized,
        LocalResources provides localized.resources,
        *ownerProviders.toTypedArray(),
        content = content,
    )
}
