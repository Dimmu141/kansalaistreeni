package fi.kansalaisuuskoe

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.moshi.Moshi
import fi.kansalaisuuskoe.data.*
import fi.kansalaisuuskoe.ui.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room + ViewModel + screen; file-provider selection is separately checked on the emulator. */
@RunWith(AndroidJUnit4::class)
class ContentImportFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as CitizenshipApplication
    private val repository get() = app.container.learning
    private lateinit var model: ContentImportViewModel
    private lateinit var selectedJson: String
    private lateinit var original: ContentPackageDto
    private fun json(c: ContentPackageDto) = Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(c)
    private fun text(value: String) = rule.onNodeWithText(value)
    private fun await(value: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(value: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(value))
        text(value).performClick()
    }

    @Before fun setup() {
        runBlocking {
            app.container.database.clearAllTables()
            repository.initialize(app)
        }
        original = ContentCodec.parse(app.assets.open("demo-content.json").bufferedReader().use { it.readText() })
        selectedJson = json(original.copy(version = original.version + 1, questions = original.questions.mapIndexed { i, q ->
            if (i == 0) q.copy(revision = 2, explanation = "Päivitetty testiselitys.") else q
        }))
        renderScreen()
        await("Choose a content file")
        rule.waitUntil(15_000) { rule.onNodeWithText("Choose a content file").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not() }
    }

    /** MainActivity installs the full app on recreation; reattach this isolated test screen. */
    private fun renderScreen() {
        rule.activityRule.scenario.onActivity { activity ->
            model = ViewModelProvider(activity, object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ContentImportViewModel(repository) as T
                }
            })[ContentImportViewModel::class.java]
            activity.setContent {
                InterfaceLocale(InterfaceLanguage.ENGLISH) {
                    CitizenshipTheme(ThemePreference.LIGHT) {
                        val state by model.state.collectAsState()
                        ContentImportBody(state, { model.selectDocument { selectedJson } }, model::discard, model::confirm, model::refresh)
                    }
                }
            }
        }
    }

    @Test fun reviewAndCancelDoNotChangeProgressOrContent() {
        val q = runBlocking { repository.snapshot.first().questions.first() }
        runBlocking { repository.answer(q, q.correctOptionId, "before-preview", 1000) }
        val before = runBlocking { repository.snapshot.first() }
        click("Choose a content file")
        await("Review the update")
        click("Questions in this file")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Show answers and explanation"))
        rule.onAllNodesWithText("Show answers and explanation").onFirst().performClick()
        await("Hide details")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Päivitetty testiselitys."))
        text("Päivitetty testiselitys.").assertExists()
        click("Cancel update")
        await("Choose a content file")
        assertEquals(before, runBlocking { repository.snapshot.first() })
        assertEquals(original.version, runBlocking { repository.installedContent().metadata!!.version })
    }

    @Test fun confirmationUpdatesContentAndPreservesHistory() {
        val questions = runBlocking { repository.snapshot.first().questions }
        runBlocking { questions.take(2).forEachIndexed { i, q -> repository.answer(q, q.correctOptionId, "answer-$i", 1000) } }
        click("Choose a content file")
        await("Review the update")
        click("Replace study material")
        await("Study material updated. Your saved answer history is retained.")
        val saved = runBlocking { repository.snapshot.first() }
        assertEquals("Päivitetty testiselitys.", saved.questions.first().explanation)
        assertEquals(2, saved.attempts.size)
        assertEquals(1, fi.kansalaisuuskoe.domain.LearningEngine.summarize(saved, 1000).studiedCount)
        assertEquals(original.version + 1, runBlocking { repository.installedContent().metadata!!.version })
    }

    @Test fun invalidSelectionAndAlreadyInstalledFileCannotReplaceMaterial() {
        selectedJson = "invalid JSON"
        click("Choose a content file")
        await("This is not a valid content package for the app. Check the file format, questions and source links. Your study material has not changed.")
        assertEquals(original.version, runBlocking { repository.installedContent().metadata!!.version })
        selectedJson = app.assets.open("demo-content.json").bufferedReader().use { it.readText() }
        click("Choose a content file")
        await("Review the update")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("This study material is already installed. Nothing needs replacing."))
        text("This study material is already installed. Nothing needs replacing.").assertExists()
        text("Replace study material").assertIsNotEnabled()
        assertEquals(original.version, runBlocking { repository.installedContent().metadata!!.version })
    }

    @Test fun originalMaterialFileAfterBackupRestoreShowsNoReplacementAndKeepsBothLanguages() {
        val before = runBlocking {
            val questions = repository.snapshot.first().questions
            val fi = questions.first { it.language == "fi" }
            val sv = questions.first { it.language == "sv" }
            repository.answer(fi, fi.correctOptionId, "fi-restored", 1000)
            repository.answer(sv, sv.options.first { it.id != sv.correctOptionId }.id, "sv-restored", 2000)
            val saved = repository.exportBackup(3000)
            repository.restoreBackup(repository.prepareRestore(saved))
            repository.snapshot.first()
        }
        selectedJson = app.assets.open("demo-content.json").bufferedReader().use { it.readText() }
        assertNotEquals(ContentFile.hash(selectedJson), runBlocking { repository.installedContent().metadata!!.hash })
        click("Choose a content file")
        await("Review the update")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("This study material is already installed. Nothing needs replacing."))
        text("This study material is already installed. Nothing needs replacing.").assertExists()
        text("Replace study material").assertIsNotEnabled()
        rule.activityRule.scenario.recreate()
        renderScreen()
        assertTrue(model.state.value.preview!!.alreadyInstalled)
        // The restored list stays at the confirmation controls; its heading is off-screen.
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Replace study material"))
        text("Replace study material").assertIsNotEnabled()
        click("Cancel update")
        await("Choose a content file")
        assertEquals(before, runBlocking { repository.snapshot.first() })
    }

    @Test fun staleConfirmationRefreshesTheDisplayedCurrentPackage() {
        click("Choose a content file")
        await("Review the update")
        runBlocking { repository.importContent(json(original.copy(version = original.version + 2))) }
        click("Replace study material")
        await("Your study material changed after this preview. Choose the file again to review its effect on the current material.")
        await("${original.packageId} · version ${original.version + 2}")
        text("Choose a content file").assertExists()
        assertEquals(original.version + 2, runBlocking { repository.installedContent().metadata!!.version })
    }
}
