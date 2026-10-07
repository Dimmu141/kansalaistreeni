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

internal suspend fun LearningRepository.installRegressionContent() {
    val content = ContentCodec.parse(regressionContentJson()).copy(packageId = "regression-demo-v4")
    importContent(Moshi.Builder().build().adapter(ContentPackageDto::class.java).toJson(content))
}
