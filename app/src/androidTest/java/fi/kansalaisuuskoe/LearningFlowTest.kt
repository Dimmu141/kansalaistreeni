package fi.kansalaisuuskoe

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.kansalaisuuskoe.domain.LearningEngine
import fi.kansalaisuuskoe.data.ThemePreference
import fi.kansalaisuuskoe.data.InterfaceLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LearningFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun text(value: String) = rule.onNodeWithText(value)
    private fun await(value: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(value: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(value))
        text(value).performClick()
    }
    private val repository get() = (rule.activity.application as CitizenshipApplication).container.learning

    @Before fun resetDemoState() = runBlocking {
        val app = rule.activity.application as CitizenshipApplication
        app.container.database.clearAllTables()
        repository.initialize(app)
        repository.installRegressionContent()
        app.container.settings.setTheme(ThemePreference.LIGHT)
        app.container.settings.setEasyFinnish(true)
        app.container.settings.setInterfaceLanguage(InterfaceLanguage.FINNISH)
        app.container.settings.setStudyLanguage(fi.kansalaisuuskoe.domain.StudyLanguage.FINNISH)
        app.container.settings.setExplanation(fi.kansalaisuuskoe.data.ExplanationPreference.STUDY_LANGUAGE)
    }

    @Test fun learnerCanAnswerRestoreReviewAndPersistSettings() {
        await("Oma oppimispolkusi")
        val initialAttempts = runBlocking { repository.snapshot.first().attempts.size }
        text("Aiheet").performClick()
        await("Mitä haluat oppia?")
        click("Yhteiskunta ja demokratia"); click("Harjoittele tätä aihetta")
        await("Kuka käyttää Suomessa lainsäädäntövaltaa?")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Tarkista vastaus"))
        text("Tarkista vastaus").assertIsNotEnabled()
        text("Eduskunta").performClick()
        click("Tarkista vastaus")
        await("Oikein")
        text("Oikein").performScrollTo().assertIsDisplayed()
        click("Seuraava kysymys")
        await("Kuinka monta kansanedustajaa Suomen eduskunnassa on?")
        text("Kuinka monta kansanedustajaa Suomen eduskunnassa on?").assertIsDisplayed()
        text("150").performClick()
        click("Tarkista vastaus")
        await("Ei aivan – opitaan tästä")
        rule.activityRule.scenario.recreate()
        await("Ei aivan – opitaan tästä")
        assertEquals(initialAttempts + 2, runBlocking { repository.snapshot.first().attempts.size })
        click("Näytä yhteenveto")
        await("Harjoitus valmis")
        text("Oikein 1 / 2. Vastauksesi ja edistymisesi on tallennettu tähän laitteeseen.").assertIsDisplayed()
        click("Palaa oppimispolulle")
        await("Oma oppimispolkusi")

        runBlocking {
            val q = repository.snapshot.first().questions.first { it.id == "demo-emergency" }
            repository.answer(q, "b", UUID.randomUUID().toString(), System.currentTimeMillis() - 660_000)
        }
        text("Kertaus").performClick()
        await("Aloita kertaus · 1 kysymys")
        click("Aloita kertaus · 1 kysymys")
        await("Mihin numeroon soitat kiireellisessä hätätilanteessa Suomessa?")
        text("112").performClick()
        click("Tarkista vastaus")
        await("Oikein")
        click("Näytä yhteenveto")
        click("Palaa oppimispolulle")
        await("Oma oppimispolkusi")
        assertEquals(0, runBlocking { LearningEngine.due(repository.snapshot.first(), System.currentTimeMillis()).size })

        text("Tilastot").performClick()
        await("Näin harjoittelusi etenee")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Oikeiden vastausten osuus"))
        text("Oikeiden vastausten osuus").assertIsDisplayed()
        rule.onNodeWithContentDescription("Asetukset").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Ulkoasu"))
        click("Tumma")
        rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Helpot suomenkieliset selitykset"))
        rule.onNodeWithContentDescription("Helpot suomenkieliset selitykset").assertIsDisplayed().performClick()
        rule.waitUntil(15_000) { !runBlocking { (rule.activity.application as CitizenshipApplication).container.settings.settings.first().easyFinnish } }
        rule.activityRule.scenario.recreate()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Ulkoasu"))
        text("Tumma").assertIsSelected()
        rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Helpot suomenkieliset selitykset"))
        rule.onNodeWithContentDescription("Helpot suomenkieliset selitykset").assertIsOff()
        rule.onNodeWithContentDescription("Takaisin").performClick()
        text("Aiheet").performClick()
        click("Työelämä"); click("Harjoittele tätä aihetta")
        await("Mikä asiakirja sisältää työntekijän ja työnantajan sopimat työehdot?")
        text("Työsopimus").performClick()
        click("Tarkista vastaus")
        await("Oikein")
        text("Helpolla suomella").assertDoesNotExist()
    }
    @Test fun earlyPracticeExplainsScoreAndRestoresFeedback() {
        await("Oma oppimispolkusi")
        val first = runBlocking {
            val q = repository.snapshot.first().questions.first { it.id == "demo-parliament" }
            repository.answer(q, q.correctOptionId, "first", System.currentTimeMillis())
        }
        text("Aiheet").performClick()
        click("Yhteiskunta ja demokratia"); click("Harjoittele tätä aihetta")
        // The unseen question is presented before the previously answered one.
        await("Kuinka monta kansanedustajaa Suomen eduskunnassa on?")
        text("200").performClick()
        click("Tarkista vastaus")
        click("Seuraava kysymys")
        await("Kuka käyttää Suomessa lainsäädäntövaltaa?")
        text("Eduskunta").performClick()
        click("Tarkista vastaus")
        await("Lisäharjoittelu tallennettu")
        text("Lisäharjoittelu tallennettu").performScrollTo().assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        await("Lisäharjoittelu tallennettu")
        val p = runBlocking { repository.snapshot.first().progress.first { it.questionId == first.attempt.questionId } }
        assertEquals(25, p.mastery)
        assertEquals(first.progress.nextReview, p.nextReview)
        assertEquals(2, p.attempts)
    }
    @Test fun homeOffersTopicsWhenAllMaterialIsUpToDate() {
        await("Oma oppimispolkusi")
        runBlocking {
            val now = System.currentTimeMillis()
            repository.snapshot.first().questions.filter { it.language == "fi" }.forEach { repository.answer(it, it.correctOptionId, "first-${it.id}", now) }
        }
        await("Harjoittele aiheita")
        click("Harjoittele aiheita")
        await("Mitä haluat oppia?")
        assertEquals(12, runBlocking { repository.snapshot.first().attempts.size })
    }
}
