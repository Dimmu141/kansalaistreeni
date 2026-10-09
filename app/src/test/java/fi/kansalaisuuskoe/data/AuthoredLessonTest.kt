package fi.kansalaisuuskoe.data

import com.squareup.moshi.Moshi
import fi.kansalaisuuskoe.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AuthoredLessonTest {
    private val content = ContentCodec.parse(File("src/main/assets/demo-content.json").readText())
    private val topic = content.topics.first()
    private val lesson = topic.lessons.getValue("fi")
    private val installed = InstalledContent(ContentMetadata(packageId = content.packageId, version = content.version, hash = "old"), content.topics, content.questions, content.objectives)
    private fun withLesson(value: LessonDto, language: String = "fi") = content.copy(topics = listOf(topic.copy(lessons = topic.lessons + (language to value))) + content.topics.drop(1))
    private fun rejects(block: () -> Unit) { try { block(); fail("Invalid lesson accepted") } catch (_: IllegalArgumentException) { } }
    private fun prepare(next: ContentPackageDto) = ContentUpdates.prepare(next, "new", installed, emptyMap())

    @Test fun `every topic has aligned independently authored bilingual lessons and English support`() {
        content.topics.forEach { t ->
            assertEquals(setOf("fi", "sv", "en"), t.lessons.keys)
            val native = t.lessons.getValue("fi")
            assertTrue(native.sections.size in 3..5)
            t.lessons.forEach { (language, l) ->
                assertEquals(native.id, l.id)
                assertEquals(native.sections.map { it.id }, l.sections.map { it.id })
                assertEquals("EDITORIAL", l.status)
                assertTrue(l.sections.all { s -> s.sources.isNotEmpty() && (language != "fi" || s.easyBody != null) })
                assertTrue(l.sections.none { s -> content.questions.any { it.explanation == s.body } })
            }
        }
    }
    @Test fun `legacy packages without authored lessons parse with explanation fallback`() {
        val adapter = Moshi.Builder().build().adapter(ContentPackageDto::class.java)
        val json = adapter.toJson(content.copy(topics = content.topics.map { it.copy(lessons = emptyMap()) }))
            .replace(",\"lessons\":{}", "")
        val old = ContentCodec.parse(json)
        val snapshot = LearningSnapshot(old.topics.map { it.model() }, old.questions.map { it.model() })
        val reading = LearningContent.lesson(snapshot, topic.id, StudyLanguage.FINNISH)!!
        assertNull(reading.authored); assertEquals(25, reading.ideas.size)
    }
    @Test fun `unsupported blank duplicate and unsourced lesson content is rejected`() {
        val section = lesson.sections.first()
        listOf(lesson.copy(revision = 0), lesson.copy(introduction = ""), lesson.copy(sections = emptyList()),
            lesson.copy(sections = listOf(section, section)), lesson.copy(sections = listOf(section.copy(sources = emptyList()))),
            lesson.copy(sections = listOf(section.copy(body = " "))), lesson.copy(sections = listOf(section.copy(easyBody = " "))),
            lesson.copy(status = "OFFICIAL"), lesson.copy(sections = listOf(section.copy(sources = listOf(SourceDto("Bad", "https://user@source.example/"))))))
            .forEach { changed -> rejects { ContentCodec.validate(withLesson(changed)) } }
        rejects { ContentCodec.validate(withLesson(lesson, "de")) }
        rejects { ContentCodec.validate(withLesson(lesson, "sv")) } // Easy Finnish cannot masquerade as Swedish support.
    }
    @Test fun `misaligned supporting lesson sections are rejected`() {
        rejects { ContentCodec.validate(withLesson(topic.lessons.getValue("en").copy(sections = lesson.sections.reversed().map { it.copy(easyBody = null) }), "en")) }
    }
    @Test fun `lesson only update changes package without revising any questions`() {
        val next = withLesson(lesson.copy(revision = lesson.revision + 1, introduction = "Revised lesson introduction.")).copy(version = content.version + 1)
        assertEquals(ContentChanges(0, 0, content.questions.size, 0), prepare(next).changes)
        assertFalse(prepare(next).alreadyInstalled)
        assertEquals(1, prepare(next).changedLessonVariants)
    }
    @Test fun `lesson changes need new package and native lesson revisions`() {
        rejects { prepare(withLesson(lesson.copy(revision = lesson.revision + 1))) }
        rejects { prepare(withLesson(lesson.copy(introduction = "Unversioned change")).copy(version = content.version + 1)) }
        rejects { prepare(withLesson(lesson.copy(revision = 0)).copy(version = content.version + 1)) }
    }
    @Test fun `English support edits keep native lesson and question revisions`() {
        val next = withLesson(topic.lessons.getValue("en").copy(introduction = "Revised English support."), "en").copy(version = content.version + 1)
        assertEquals(ContentChanges(0, 0, content.questions.size, 0), prepare(next).changes)
        assertEquals(lesson, next.topics.first().lessons["fi"])
    }
    @Test fun `preview reports lesson additions removals and unchanged material`() {
        val removed = content.copy(version = content.version + 1, topics = content.topics.map { it.copy(lessons = emptyMap()) })
        assertEquals(content.topics.sumOf { it.lessons.size }, prepare(removed).changedLessonVariants)
        assertEquals(0, prepare(content).changedLessonVariants)
        val previous = installed.copy(topics = installed.topics.map { it.copy(lessons = emptyMap()) }, metadata = installed.metadata!!.copy(version = content.version - 1))
        assertEquals(content.topics.sumOf { it.lessons.size }, ContentUpdates.prepare(content, "new", previous, emptyMap()).changedLessonVariants)
    }
    @Test fun `missing native lesson never substitutes English or other study language`() {
        val nativeTopic = topic.model().copy(lessons = topic.model().lessons - "sv")
        val snapshot = LearningSnapshot(listOf(nativeTopic), content.questions.map { it.model() })
        val sv = LearningContent.lesson(snapshot, topic.id, StudyLanguage.SWEDISH)!!
        assertNull(sv.authored); assertTrue(sv.ideas.all { it.language == "sv" })
    }
    @Test fun `reading retains progress and provenance is conservative`() {
        val officialQuestions = content.questions.map { it.model().copy(status = ContentStatus.OFFICIAL) }
        val snapshot = LearningSnapshot(content.topics.map { it.model() }, officialQuestions, listOf(UserQuestionProgress(officialQuestions.first().id, 1, mastery = 25)))
        val before = snapshot.copy()
        val lesson = LearningContent.lesson(snapshot, topic.id, StudyLanguage.FINNISH)!!
        // The editorial lesson caps the label even when every question claims stronger provenance.
        assertEquals(ContentStatus.EDITORIAL, lesson.contentStatus())
        assertEquals(before, snapshot)
    }
}
