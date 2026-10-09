package fi.kansalaisuuskoe

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.kansalaisuuskoe.data.*
import fi.kansalaisuuskoe.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyLearningFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CitizenshipApplication
    private val repository get() = app.container.learning
    private fun await(value: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(value: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(value))
        rule.onNodeWithText(value).performClick()
    }
    @Before fun before() = runBlocking {
        app.container.database.clearAllTables()
        repository.initialize(app)
        app.container.settings.setTheme(ThemePreference.LIGHT)
        app.container.settings.setInterfaceLanguage(InterfaceLanguage.FINNISH)
        app.container.settings.setStudyLanguage(StudyLanguage.FINNISH)
        app.container.settings.setExplanation(ExplanationPreference.STUDY_LANGUAGE)
        app.container.settings.setEasyFinnish(true)
    }

    @Test fun homeUsesFiveQuestionMixWhileFullBacklogRemainsAvailable() {
        await("Oma oppimispolkusi")
        val plan = runBlocking {
            val now = System.currentTimeMillis()
            repository.snapshot.first().questions.filter { it.language == "fi" }.take(7).forEachIndexed { index, q ->
                repository.answer(q, q.options.first { it.id != q.correctOptionId }.id, "backlog-$index", now - 700_000 + index)
            }
            DailyLearningPlanner.plan(repository.snapshot.first().forLanguage(StudyLanguage.FINNISH), now)
        }
        await("3 kertauskysymystä · 2 uutta kysymystä")
        rule.onNodeWithText("Ei virallista materiaalia").assertIsDisplayed()
        // The full backlog lives on the Review tab; Home keeps one primary action.
        rule.onNodeWithText("Kertaus").performClick()
        click("Aloita kertaus · 7 kysymystä")
        await("Kysymys 1 / 7")
        rule.onNodeWithContentDescription("Takaisin").performClick()
        rule.onNodeWithText("Koti").performClick()
        await("Oma oppimispolkusi")
        click("Jatka harjoittelua")
        plan.items.forEachIndexed { index, item ->
            await(item.question.text)
            rule.onNodeWithText("Kysymys ${index + 1} / 5").assertExists()
            if (index == 0) rule.onNodeWithText("Kertauskysymys · valittu kertausajan perusteella.").assertExists()
            click(rule.activity.answerLabel(item.question, item.question.correctOptionId))
            click("Tarkista vastaus")
            await("Oikein")
            click(if (index == 4) "Näytä yhteenveto" else "Seuraava kysymys")
        }
        await("Harjoitus valmis")
        assertEquals(12, runBlocking { repository.snapshot.first().attempts.size })
        click("Palaa oppimispolulle")
        await("Oma oppimispolkusi")
        rule.onNodeWithText("Kertaus").performClick()
        await("Aloita kertaus · 4 kysymystä")
    }

    @Test fun weakTopicFocusAndNewTopicReasonRemainReadableAfterRecreation() {
        await("Oma oppimispolkusi")
        val first = runBlocking {
            val q = repository.snapshot.first().questions.first { it.id == "demo-parliament" }
            val now = System.currentTimeMillis()
            repository.answer(q, q.options.first { it.id != q.correctOptionId }.id, "recent-mistake", now)
            DailyLearningPlanner.plan(repository.snapshot.first().forLanguage(StudyLanguage.FINNISH), now).items.first().question
        }
        assertEquals("democracy", first.topicId)
        await("Lisää harjoittelua aiheesta: Demokratia ja päätöksenteko.")
        click("Jatka harjoittelua")
        await(first.text)
        rule.onNodeWithText("Uusi kysymys aiheesta, joka kaipaa vielä harjoittelua.").assertIsDisplayed()
        click(rule.activity.answerLabel(first, first.correctOptionId)); click("Tarkista vastaus")
        await("Oikein")
        rule.activityRule.scenario.recreate()
        await("Oikein")
        click("Seuraava kysymys")
        await("Tutustu aiheeseen, jota et ole vielä harjoitellut.")
        assertEquals(2, runBlocking { repository.snapshot.first().attempts.size })
    }
}
