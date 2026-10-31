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

    /** Step 1 (interface language) and step 2 (exam language and support) lead to the disclaimer. */
    private fun throughLanguageSteps() {
        await("Choose your language"); rule.onNodeWithText("Continue").performClick()
        await("The test is in Finnish or Swedish")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Continue")); rule.onNodeWithText("Continue").performClick()
    }

    @Test fun firstRunAsksForLanguagesAndSaysTheTestIsInFinnishOrSwedish() {
        val examNote = "The citizenship test is taken in Finnish or Swedish, so questions in this app are always in Finnish or Swedish. " +
            "Choose the language you plan to take the test in. Your own language helps you understand the explanations and words."
        await("Choose your language")
        // Choosing an interface language also picks it as the support language.
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Русский · Russian")); rule.onNodeWithText("Русский · Russian").performClick()
        await("Выберите язык")
        rule.waitUntil(10_000) { runBlocking { app.container.settings.settings.first().explanation == ExplanationPreference.ALSO_RUSSIAN } }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("English · английский")); rule.onNodeWithText("English · английский").performClick()
        await("Choose your language")
        rule.waitUntil(10_000) { runBlocking { app.container.settings.settings.first().explanation == ExplanationPreference.ALSO_ENGLISH } }
        rule.onNodeWithText("Continue").performClick()
        await("The test is in Finnish or Swedish"); rule.onNodeWithText(examNote).assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Swedish questions")); rule.onNodeWithText("Swedish questions").performClick()
        rule.waitUntil(10_000) { runBlocking { app.container.settings.settings.first().studyLanguage == StudyLanguage.SWEDISH } }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Also in English")); rule.onNodeWithText("Also in English").assertIsSelected()
        // Back returns to the previous step instead of leaving the app.
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        await("Choose your language")
        rule.onNodeWithText("Continue").performClick(); await("The test is in Finnish or Swedish")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Continue")); rule.onNodeWithText("Continue").performClick()
        await("Before you start"); rule.onNodeWithText("Start").performClick()
        rule.awaitDescription("Study language: Swedish")
    }

    @Test fun disclaimerShowsOnceThenOnlyAsChipAndInAbout() {
        throughLanguageSteps()
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
        throughLanguageSteps()
        await("Before you start"); rule.onNodeWithText("Start").performClick(); await("Your learning path")
        rule.onNodeWithContentDescription("Study language: Swedish").performClick()
        rule.waitUntil(10_000) { runBlocking { app.container.settings.settings.first().studyLanguage == StudyLanguage.SWEDISH } }
        rule.onNodeWithContentDescription("Study language: Swedish").assertIsSelected()
        rule.onNodeWithContentDescription("Study language: Finnish").performClick()
        rule.waitUntil(10_000) { runBlocking { app.container.settings.settings.first().studyLanguage == StudyLanguage.FINNISH } }
    }
}
