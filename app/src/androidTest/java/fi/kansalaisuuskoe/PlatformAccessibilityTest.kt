package fi.kansalaisuuskoe

import android.app.UiAutomation
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.text.Spanned
import android.text.style.LocaleSpan
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import fi.kansalaisuuskoe.data.*
import fi.kansalaisuuskoe.domain.StudyLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the real Android accessibility tree, not only Compose's test semantics. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
class PlatformAccessibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<CitizenshipApplication>()
    private lateinit var automation: UiAutomation

    private fun node(label: String): AccessibilityNodeInfo? {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        automation.rootInActiveWindow?.let(pending::add)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (current.text?.toString()?.contains(label) == true || current.contentDescription?.toString() == label) return current
            for (index in 0 until current.childCount) current.getChild(index)?.let(pending::add)
        }
        return null
    }
    private fun await(label: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            node(label)?.let { return it }
            SystemClock.sleep(100)
        }
        throw AssertionError("Missing platform accessibility node: $label")
    }
    private fun interactiveNode(label: String): AccessibilityNodeInfo {
        var target = await(label)
        while (!target.isClickable) target = requireNotNull(target.parent)
        return target
    }
    private fun click(label: String) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        var target = interactiveNode(label)
        // Accessibility actions return before Compose processes the resulting state change.
        while (!target.isEnabled && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100)
            target = interactiveNode(label)
        }
        assertTrue("Disabled accessibility action: $label", target.isEnabled)
        assertTrue(target.isAccessibilityFocused || target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
        assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun assertSpeechLocale(label: String, language: String) {
        val text = await(label).text
        assertTrue("Locale annotations must reach Android's accessibility text", text is Spanned)
        val spans = (text as Spanned).getSpans(0, text.length, LocaleSpan::class.java)
        assertTrue("Missing $language speech locale", spans.any { it.locales[0].language == language })
    }

    @Test fun questionCanBeFocusedAnsweredAndReadThroughPlatformAccessibility() {
        // Keep a running TalkBack/Switch Access service active during this test.
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val previousFlags = automation.serviceInfo.flags
        // A standard CI emulator may have no screen reader enabled.
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        }
        runBlocking {
            app.container.database.clearAllTables(); app.container.learning.initialize(app)
            app.container.settings.setInterfaceLanguage(InterfaceLanguage.ENGLISH)
            app.container.settings.setStudyLanguage(StudyLanguage.FINNISH)
            app.container.settings.setExplanation(ExplanationPreference.ALSO_ENGLISH)
            app.container.settings.setTheme(ThemePreference.LIGHT)
            app.container.settings.setEasyFinnish(true)
        }
        val activity = instrumentation.startActivitySync(Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        try {
            click("Start learning")
            val question = await("Kuka säätää Suomen lait?")
            assertTrue(question.isHeading)
            assertTrue(question.isAccessibilityFocused || question.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
            assertSpeechLocale("Kuka säätää Suomen lait?", "fi")
            val option = interactiveNode("Eduskunta")
            assertTrue("Answer control must expose its choice state: ${option.className}", option.isCheckable)
            val bounds = Rect(); option.getBoundsInScreen(bounds)
            assertTrue(bounds.height() >= 56 * app.resources.displayMetrics.density)
            click("Eduskunta"); click("Check answer")
            await("Correct answer: Eduskunta")
            assertSpeechLocale("Perustuslain mukaan lainsäädäntövaltaa käyttää eduskunta. Hallitus valmistelee lakiesityksiä, mutta eduskunta päättää laeista.", "fi")
            assertSpeechLocale("Under the Constitution, Parliament exercises legislative power. The Government prepares bills, but Parliament decides on laws.", "en")
            assertEquals(1, runBlocking { app.container.learning.snapshot.first().attempts.size })
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            automation.serviceInfo = automation.serviceInfo.apply { flags = previousFlags }
        }
    }
}
