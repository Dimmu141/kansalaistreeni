package fi.kansalaisuuskoe

import androidx.test.platform.app.InstrumentationRegistry
import com.squareup.moshi.Moshi
import fi.kansalaisuuskoe.data.ContentCodec
import fi.kansalaisuuskoe.data.ContentPackageDto
import fi.kansalaisuuskoe.data.LearningRepository

/** Fixed v0.14 fixture keeps two-question recovery/recap journeys deterministic.
 * The authored-lesson tests and repository/import tests exercise the full new package.
 */
internal fun regressionContentJson() = InstrumentationRegistry.getInstrumentation().context.assets
    .open("demo-content-v4.json").bufferedReader().use { it.readText() }

/** The fixture reuses bundled question and lesson IDs. The bundled package is installed first and
 * may already carry later revisions, so the fixture's revisions are lifted to stay a valid update. */
internal fun regressionContent(): ContentPackageDto = ContentCodec.parse(regressionContentJson()).let { c ->
    c.copy(packageId = "regression-demo-v4",
        topics = c.topics.map { t -> t.copy(lessons = t.lessons.mapValues { (_, l) -> l.copy(revision = l.revision + REVISION_LIFT) }) },
        questions = c.questions.map { it.copy(revision = it.revision + REVISION_LIFT) })
}
private const val REVISION_LIFT = 10

internal suspend fun LearningRepository.installRegressionContent() {
    val content = regressionContent()
    importContent(Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(content))
}
