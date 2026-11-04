package fi.kansalaisuuskoe

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.kansalaisuuskoe.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Google Play requires a privacy policy link inside the app, not only in the store listing. */
@RunWith(AndroidJUnit4::class)
class PrivacyPolicyLinkTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CitizenshipApplication

    @Before fun reset(): Unit = runBlocking {
        app.container.database.clearAllTables(); app.container.learning.initialize(app)
        app.container.settings.setInterfaceLanguage(InterfaceLanguage.ENGLISH)
    }

    @Test fun settingsOpensThePublishedPolicyInTheBrowser() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val filter = IntentFilter(Intent.ACTION_VIEW).apply { addDataScheme("https") }
        val monitor = instrumentation.addMonitor(filter, Instrumentation.ActivityResult(Activity.RESULT_OK, null), true)
        try {
            rule.waitUntil(15_000) { rule.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("Settings").performClick()
            rule.onNode(hasScrollAction()).performScrollToNode(hasText("Privacy policy"))
            rule.onNodeWithText("Privacy policy").assertIsDisplayed().performClick()
            rule.waitUntil(5_000) { monitor.hits > 0 }
            assertEquals(1, monitor.hits)
            assertEquals("https://kansalaistreeni.vercel.app/tietosuoja", app.getString(R.string.privacy_policy_url))
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}
