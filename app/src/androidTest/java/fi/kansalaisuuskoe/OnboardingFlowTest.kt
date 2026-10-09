package fi.kansalaisuuskoe

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.kansalaisuuskoe.data.*
import fi.kansalaisuuskoe.domain.StudyLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CitizenshipApplication
    private val disclaimer = "These practice questions were written for the app and are not official test questions. This app is not a government service."
    private fun await(value: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    @Before fun reset(): Unit = runBlocking {
        app.container.database.clearAllTables(); app.container.learning.initialize(app)
        app.container.settings.setInterfaceLanguage(InterfaceLanguage.ENGLISH)
        app.container.settings.setStudyLanguage(StudyLanguage.FINNISH)
        app.container.settings.setTheme(ThemePreference.LIGHT)
        app.container.settings.setOnboardingSeen(false)
    }
    @After fun restore(): Unit = runBlocking { app.container.settings.setOnboardingSeen(true) }

    @Test fun disclaimerShowsOnceThenOnlyAsChipAndInAbout() {
        await("Before you start"); rule.onNodeWithText(disclaimer).assertIsDisplayed()
        rule.onNodeWithText("Start").performClick()
        await("Your learning path")
        rule.onNodeWithText(disclaimer).assertDoesNotExist()
        rule.onNodeWithText("Not official material").assertIsDisplayed()
        assertTrue(runBlocking { app.container.settings.settings.first().onboardingSeen })
        rule.activityRule.scenario.recreate(); await("Your learning path")
        rule.onNodeWithText("Before you start").assertDoesNotExist()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(disclaimer))
        rule.onNodeWithText("About").assertIsDisplayed(); rule.onNodeWithText(disclaimer).assertIsDisplayed()
    }

    @Test fun appBarSwitchChangesTheStudyLanguage() {
        await("Before you start"); rule.onNodeWithText("Start").performClick(); await("Your learning path")
        rule.onNodeWithContentDescription("Study language: Swedish").performClick()
        rule.waitUntil(10_000) { runBlocking { app.container.settings.settings.first().studyLanguage == StudyLanguage.SWEDISH } }
        rule.onNodeWithContentDescription("Study language: Swedish").assertIsSelected()
        rule.onNodeWithContentDescription("Study language: Finnish").performClick()
        rule.waitUntil(10_000) { runBlocking { app.container.settings.settings.first().studyLanguage == StudyLanguage.FINNISH } }
    }
}
