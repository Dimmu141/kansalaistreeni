package fi.kansalaisuuskoe

import android.view.KeyEvent
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.kansalaisuuskoe.data.InterfaceLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CitizenshipApplication
    private fun await(value: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }

    @Before fun setup() = runBlocking {
        app.container.database.clearAllTables()
        app.container.learning.initialize(app)
        app.container.settings.setInterfaceLanguage(InterfaceLanguage.FINNISH)
        app.container.settings.setStudyLanguage(fi.kansalaisuuskoe.domain.StudyLanguage.FINNISH)
        app.container.settings.setExplanation(fi.kansalaisuuskoe.data.ExplanationPreference.STUDY_LANGUAGE)
    }

    @Test fun realNavigationAndPickerWorkWithBothLocaleContexts() {
        for (language in InterfaceLanguage.entries) {
            runBlocking { app.container.settings.setInterfaceLanguage(language) }
            val (settingsLabel, materialLabel, pickLabel) = when (language) {
                InterfaceLanguage.FINNISH -> Triple("Asetukset", "Oppimateriaali", "Valitse sisältötiedosto")
                InterfaceLanguage.SWEDISH -> Triple("Inställningar", "Studiematerial", "Välj en innehållsfil")
                InterfaceLanguage.ENGLISH -> Triple("Settings", "Study material", "Choose a content file")
                InterfaceLanguage.RUSSIAN -> Triple("Настройки", "Учебные материалы", "Выбрать файл с материалами")
                InterfaceLanguage.ARABIC -> Triple("الإعدادات", "المواد الدراسية", "اختيار ملف المحتوى")
                InterfaceLanguage.PERSIAN -> Triple("تنظیمات", "مواد درسی", "انتخاب فایل محتوا")
                InterfaceLanguage.SOMALI -> Triple("Dejinta", "Agabka waxbarashada", "Dooro fayl waxyaabo")
                InterfaceLanguage.UKRAINIAN -> Triple("Налаштування", "Навчальні матеріали", "Вибрати файл із матеріалами")
            }
            rule.waitUntil(15_000) { rule.onAllNodesWithContentDescription(settingsLabel).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription(settingsLabel).performClick()
            rule.onNode(hasScrollAction()).performScrollToNode(hasText(materialLabel))
            rule.onNodeWithText(materialLabel).performClick()
            await(pickLabel)
            rule.activityRule.scenario.recreate()
            await(pickLabel)
            rule.waitUntil(10_000) { !rule.onNodeWithText(pickLabel).fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
            rule.onNodeWithText(pickLabel).performClick()
            // The system chooser is outside Compose; cancel it using a real back key.
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            rule.waitUntil(10_000) {
                val root = automation.rootInActiveWindow
                root?.packageName?.toString()?.contains("documentsui") == true && root.window?.isFocused == true
            }
            automation.waitForIdle(500, 5_000)
            val downTime = SystemClock.uptimeMillis()
            automation.injectInputEvent(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0), true)
            automation.injectInputEvent(KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0), true)
            rule.waitUntil(10_000) { automation.rootInActiveWindow?.packageName?.toString() == "fi.kansalaistreeni" }
            await(pickLabel)
            val back = when (language) { InterfaceLanguage.FINNISH -> "Takaisin"; InterfaceLanguage.SWEDISH -> "Tillbaka"; InterfaceLanguage.ENGLISH -> "Back"; InterfaceLanguage.RUSSIAN -> "Назад"; InterfaceLanguage.ARABIC -> "رجوع"; InterfaceLanguage.PERSIAN -> "بازگشت"; InterfaceLanguage.SOMALI -> "Dib u noqo"; InterfaceLanguage.UKRAINIAN -> "Назад" }
            rule.onNodeWithContentDescription(back).performClick()
            rule.onNodeWithContentDescription(back).performClick()
        }
    }
}
