package fi.kansalaisuuskoe.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class ContentUpdateTest {
    @Test fun `supporting translation edits keep the learning revision`() {
        val q = content.questions.first()
        val translation = q.translations.getValue("en").copy(explanation = "Updated supporting explanation.")
        val next = content.copy(version = content.version + 1, questions = content.questions.map {
            if (it.id == q.id) it.copy(translations = mapOf("en" to translation)) else it
        })
        assertEquals(ContentChanges(0, 0, content.questions.size - 1, 0, 1), prepare(next).changes)
        assertEquals(q.revision, next.questions.first().revision)
    }
    @Test fun `translation removal is a supporting change`() {
        val next = content.copy(version = content.version + 1, questions = content.questions.map { it.copy(translations = emptyMap()) })
        assertEquals(ContentChanges(0, 0, 0, 0, content.questions.size), prepare(next).changes)
    }
    @Test fun `translation edits cannot disguise a changed answer key`() {
        val q = content.questions.first()
        val changed = q.copy(translations = emptyMap(), correctOptionId = q.options.first { it.id != q.correctOptionId }.id)
        rejects(ImportProblem.REVISION) { prepare(content.copy(version = content.version + 1, questions = listOf(changed))) }
    }
    @Test fun `translation only updates still require a package version`() {
        rejects(ImportProblem.VERSION) { prepare(content.copy(questions = content.questions.map { it.copy(translations = emptyMap()) })) }
    }
    private val content = ContentCodec.parse(File("src/main/assets/demo-content.json").readText())
    private val installed = InstalledContent(ContentMetadata(packageId = content.packageId, version = content.version, hash = "old"), content.topics, content.questions)
    private fun prepare(next: ContentPackageDto, hash: String = "new", history: Map<String, Int> = emptyMap()) = ContentUpdates.prepare(next, hash, installed, history)
    private fun rejects(problem: ImportProblem, block: () -> Unit) {
        try { block(); fail("Invalid update accepted") } catch (e: ContentImportException) { assertEquals(problem, e.problem) }
    }

    @Test fun `preview counts additions revisions removals and unchanged questions`() {
        val next = content.copy(version = content.version + 1, questions = content.questions.dropLast(1).mapIndexed { i, q ->
            if (i == 0) q.copy(revision = q.revision + 1, explanation = "Uusi selitys.") else q
        } + content.questions.last().copy(id = "new-question"))
        assertEquals(ContentChanges(1, 1, content.questions.size - 2, 1), prepare(next).changes)
    }
    @Test fun `topic only updates keep every question unchanged`() {
        val next = content.copy(version = content.version + 1, topics = content.topics.map { it.copy(title = "Uusi aihe") })
        assertEquals(ContentChanges(0, 0, content.questions.size, 0), prepare(next).changes)
    }
    @Test fun `unchanged material produces a no op preview`() {
        assertTrue(prepare(content, "old").alreadyInstalled)
    }
    @Test fun `different file hashes with identical stored material produce a no op`() {
        val result = prepare(content)
        assertTrue(result.alreadyInstalled)
        assertEquals(ContentChanges(0, 0, content.questions.size, 0), result.changes)
    }
    @Test fun `same version changes to question fields remain rejected even with the old hash`() {
        val q = content.questions.first()
        val variants = listOf(
            q.copy(text = "Changed question?"),
            q.copy(explanation = "Changed explanation."),
            q.copy(correctOptionId = q.options.first { it.id != q.correctOptionId }.id),
            q.copy(options = q.options.reversed()),
            q.copy(easyExplanation = "Changed easier explanation."),
            q.copy(source = q.source.copy(url = "https://example.org/changed-source")),
            q.copy(vocabulary = emptyList()),
            q.copy(translations = emptyMap()),
            q.copy(tags = q.tags + "new-tag"),
            q.copy(difficulty = "HARD"),
            q.copy(status = "EDITORIAL"),
            q.copy(revision = q.revision + 1),
        )
        variants.forEach { changed ->
            rejects(ImportProblem.VERSION) {
                prepare(content.copy(status = "EDITORIAL", questions = listOf(changed) + content.questions.drop(1)), "old")
            }
        }
    }
    @Test fun `topic labels translations and ordering require a package version`() {
        val t = content.topics.first()
        val variants = listOf(t.copy(title = "Changed title"), t.copy(description = "Changed description."),
            t.copy(order = t.order + 10), t.copy(translations = emptyMap()))
        variants.forEach { changed ->
            rejects(ImportProblem.VERSION) { prepare(content.copy(topics = listOf(changed) + content.topics.drop(1))) }
        }
    }
    @Test fun `topic array formatting order is irrelevant when explicit display order is unchanged`() {
        assertTrue(prepare(content.copy(topics = content.topics.reversed())).alreadyInstalled)
    }
    @Test fun `tied topic display order remains meaningful`() {
        val tied = content.copy(topics = content.topics.map { it.copy(order = 0) })
        val before = installed.copy(topics = tied.topics)
        rejects(ImportProblem.VERSION) {
            ContentUpdates.prepare(tied.copy(topics = tied.topics.reversed()), "new", before, emptyMap())
        }
    }
    @Test fun `question sequence changes require a new package version`() {
        rejects(ImportProblem.VERSION) { prepare(content.copy(questions = content.questions.reversed())) }
    }
    @Test fun `envelope labels cannot promote the provenance of installed questions`() {
        // Only question provenance is persisted and used in learning views. This is a no-op,
        // not an update or an authenticity check of the caller's package-level assertion.
        val result = prepare(content.copy(status = "OFFICIAL"))
        assertTrue(result.alreadyInstalled)
        assertTrue(result.content.questions.all { it.status == "DEMO" })
    }
    @Test fun `package downgrades are rejected`() {
        rejects(ImportProblem.VERSION) { prepare(content.copy(version = content.version - 1)) }
    }
    @Test fun `changed questions require revision even in a different package`() {
        rejects(ImportProblem.REVISION) { prepare(content.copy(packageId = "another-package", questions = content.questions.map { it.copy(explanation = "Muutettu") })) }
    }
    @Test fun `removed studied ids cannot reappear at a historical revision`() {
        val removed = installed.copy(questions = content.questions.drop(1))
        rejects(ImportProblem.REVISION) {
            ContentUpdates.prepare(content.copy(version = content.version + 1), "new", removed, mapOf(content.questions.first().id to 1))
        }
    }
    @Test fun `reintroduced question accepts a higher revision and is new to the current package`() {
        val first = content.questions.first()
        val removed = installed.copy(questions = content.questions.drop(1))
        val next = content.copy(version = content.version + 1, questions = listOf(first.copy(revision = 2)) + content.questions.drop(1))
        assertEquals(1, ContentUpdates.prepare(next, "new", removed, mapOf(first.id to 1)).changes.added)
    }
    @Test fun `changing package retains only identical shared questions`() {
        val next = content.copy(packageId = "replacement")
        val result = prepare(next)
        assertFalse(result.alreadyInstalled)
        assertEquals(ContentChanges(0, 0, content.questions.size, 0), result.changes)
    }
    @Test fun `utf8 content and optional byte order mark are read`() {
        assertEquals("kysymys ä", ContentFile.read(ByteArrayInputStream("\uFEFFkysymys ä".toByteArray())))
    }
    @Test fun `file limit is enforced on actual streamed bytes`() {
        assertEquals(ContentFile.MAX_BYTES, ContentFile.read(ByteArrayInputStream(ByteArray(ContentFile.MAX_BYTES) { 32 })).length)
        rejects(ImportProblem.TOO_LARGE) { ContentFile.read(ByteArrayInputStream(ByteArray(ContentFile.MAX_BYTES + 1) { 32 })) }
    }
    @Test fun `malformed utf8 is rejected instead of replacing characters`() {
        rejects(ImportProblem.INVALID) { ContentFile.read(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28))) }
    }
    @Test fun `a higher revision cannot change a question language`() {
        val q = content.questions.first()
        rejects(ImportProblem.REVISION) { prepare(content.copy(version = content.version + 1, questions = listOf(q.copy(revision = 2, language = "sv")))) }
    }
    @Test fun `removed ids cannot change their historical language`() {
        val q = content.questions.first()
        val empty = installed.copy(questions = emptyList())
        rejects(ImportProblem.REVISION) {
            ContentUpdates.prepare(content.copy(version = content.version + 1, questions = listOf(q.copy(revision = 2, language = "sv"))), "new", empty,
                mapOf(q.id to 1), mapOf(q.id to setOf("fi")))
        }
    }
}
