package fi.kansalaisuuskoe.domain

import org.junit.Assert.*
import org.junit.Test

class DailyLearningPlanTest {
    private val now = 1_791_280_000_000L
    private val q = Question("q", 1, "one", "Question?", listOf(AnswerOption("yes", "Yes"), AnswerOption("no", "No")), "yes",
        "Explanation", null, emptyList(), Source("Source", "https://example.org"), "fi", Difficulty.EASY, emptyList(), ContentStatus.DEMO)
    private val topics = (1..8).map { Topic("t$it", "Topic $it", "Description", it) }
    private fun questions(count: Int) = (1..count).map { q.copy(id = "q$it", topicId = "t${(it - 1) % 8 + 1}") }
    private fun progress(question: Question, mastery: Int = 25, next: Long = now + 1000, last: Long = now - 1000) =
        UserQuestionProgress(question.id, question.revision, attempts = 1, mastery = mastery, lastAnswered = last, nextReview = next)
    private fun plan(questions: List<Question>, progress: List<UserQuestionProgress> = emptyList()) =
        DailyLearningPlanner.plan(LearningSnapshot(topics, questions, progress), now)

    @Test fun `first session samples topics rather than consecutive items from one large topic`() {
        val qs = (1..12).map { q.copy(id = "large-$it", topicId = "t1") } + questions(6).drop(1)
        val selected = plan(qs)
        assertEquals(listOf("t1", "t2", "t3", "t4", "t5"), selected.items.map { it.question.topicId })
        assertEquals(5, selected.newCount); assertEquals(0, selected.reviewCount)
        assertTrue(selected.items.all { it.reason == DailyQuestionReason.NEW_TOPIC })
    }
    @Test fun `backlog reserves two new slots and exposes every remaining due item`() {
        val qs = questions(20)
        val old = qs.take(10).mapIndexed { i, question -> progress(question, next = now - 10_000 + i) }
        val selected = plan(qs, old)
        assertEquals(3, selected.reviewCount); assertEquals(2, selected.newCount)
        assertEquals(10, selected.totalDue); assertEquals(7, selected.remainingDue)
        assertEquals(qs.take(3), selected.items.take(3).map { it.question })
    }
    @Test fun `spare new slots return to overdue review without duplication`() {
        val qs = questions(8)
        val selected = plan(qs, qs.take(7).map { progress(it, next = now - 1) })
        assertEquals(4, selected.reviewCount); assertEquals(1, selected.newCount)
        assertEquals(3, selected.remainingDue)
        assertEquals(5, selected.items.map { it.question.id }.distinct().size)
    }
    @Test fun `review only session stays bounded while full due queue remains available`() {
        val qs = questions(12)
        val snapshot = LearningSnapshot(topics, qs, qs.map { progress(it, next = now - 1) })
        val selected = DailyLearningPlanner.plan(snapshot, now)
        assertEquals(5, selected.reviewCount); assertEquals(0, selected.newCount)
        assertEquals(7, selected.remainingDue)
        assertEquals(12, LearningEngine.due(snapshot, now).size)
    }
    @Test fun `oldest deadlines outrank weakness and equal deadlines prefer weaker questions`() {
        val qs = questions(3)
        val ps = listOf(progress(qs[0], 100, now - 2), progress(qs[1], 80, now - 1), progress(qs[2], 0, now - 1))
        assertEquals(listOf("q1", "q3", "q2"), plan(qs, ps).items.map { it.question.id })
    }
    @Test fun `weak familiar topic and unexplored topic each receive new exposure`() {
        val qs = listOf(q.copy(id = "known", topicId = "t1"), q.copy(id = "other-known", topicId = "t2")) +
            (1..8).map { q.copy(id = "weak-$it", topicId = "t1") } + q.copy(id = "also-weak", topicId = "t2") + q.copy(id = "unknown", topicId = "t3")
        val ps = listOf(progress(qs[0], 0), progress(qs[1], 25))
        val selected = plan(qs, ps)
        assertEquals(listOf("weak-1", "unknown", "also-weak"), selected.items.take(3).map { it.question.id })
        assertEquals("t1", selected.focusTopicId)
        assertEquals(DailyQuestionReason.WEAK_TOPIC, selected.items[0].reason)
        assertEquals(DailyQuestionReason.NEW_TOPIC, selected.items[1].reason)
    }
    @Test fun `unseen coverage does not falsely make a successful topic weaker`() {
        val qs = listOf(q.copy(id = "strong", topicId = "t1"), q.copy(id = "weak", topicId = "t2")) +
            (1..10).map { q.copy(id = "strong-new-$it", topicId = "t1") } + q.copy(id = "weak-new", topicId = "t2")
        val selected = plan(qs, listOf(progress(qs[0], 100), progress(qs[1], 25)))
        assertEquals("weak-new", selected.items.first().question.id)
        assertEquals(DailyQuestionReason.NEW_QUESTION, selected.items[1].reason)
        assertEquals("t2", selected.focusTopicId)
    }
    @Test fun `ties favour less recently practised topics then explicit topic order`() {
        val qs = listOf(q.copy(id = "a", topicId = "t1"), q.copy(id = "b", topicId = "t2"), q.copy(id = "a-new", topicId = "t1"), q.copy(id = "b-new", topicId = "t2"))
        assertEquals("b-new", plan(qs, listOf(progress(qs[0], last = now), progress(qs[1], last = now - 100))).items.first().question.id)
        assertEquals("a-new", plan(qs, listOf(progress(qs[0]), progress(qs[1]))).items.first().question.id)
    }
    @Test fun `weak early questions never fill an otherwise empty queue`() {
        val selected = plan(listOf(q), listOf(progress(q, 0, now + 600_000)))
        assertTrue(selected.items.isEmpty())
        assertEquals(now + 600_000, selected.nextReviewAt)
        assertNull(selected.focusTopicId)
    }
    @Test fun `revised and removed progress cannot affect topic weakness or eligibility`() {
        val qs = listOf(q.copy(topicId = "t1", revision = 2))
        val ps = listOf(progress(q.copy(topicId = "t1"), 100, now - 1), progress(q.copy(id = "removed"), 0, now - 100))
        val selected = plan(qs, ps)
        assertEquals(0, selected.totalDue)
        assertEquals(DailyQuestionReason.NEW_TOPIC, selected.items.single().reason)
        assertNull(selected.nextReviewAt)
    }
    @Test fun `missing topic metadata small packages and empty packages are safe`() {
        assertTrue(plan(emptyList()).items.isEmpty())
        val snapshot = LearningSnapshot(questions = listOf(q))
        assertEquals(listOf(q), DailyLearningPlanner.plan(snapshot, now).items.map { it.question })
    }
    @Test fun `language scope excludes other language review and weakness`() {
        val sv = q.copy(id = "sv", language = "sv", topicId = "t1")
        val snapshot = LearningSnapshot(topics, listOf(q, sv), listOf(progress(q, 0, now - 1)))
        val selected = DailyLearningPlanner.plan(snapshot.forLanguage(StudyLanguage.SWEDISH), now)
        assertEquals(listOf(sv), selected.items.map { it.question })
        assertEquals(0, selected.totalDue)
        assertNull(selected.focusTopicId)
    }
    @Test fun `planning is deterministic read only and shares the actual lesson policy`() {
        val snapshot = LearningSnapshot(topics, questions(16))
        val original = snapshot.copy()
        val selected = DailyLearningPlanner.plan(snapshot, now)
        assertEquals(selected, DailyLearningPlanner.plan(snapshot, now))
        assertEquals(selected.items.map { it.question }, LearningEngine.recommendedQueue(snapshot, now))
        assertEquals(original, snapshot)
    }
    @Test fun `successive short sessions cover new material without early repeats`() {
        val qs = questions(24)
        var snapshot = LearningSnapshot(topics, qs)
        val seen = mutableSetOf<String>()
        repeat(5) {
            val selected = DailyLearningPlanner.plan(snapshot, now)
            selected.items.forEach { item ->
                assertTrue(seen.add(item.question.id))
                snapshot = snapshot.copy(progress = snapshot.progress + LearningEngine.answer(item.question, item.question.correctOptionId, null, now))
            }
        }
        assertEquals(24, seen.size)
        assertTrue(DailyLearningPlanner.plan(snapshot, now).items.isEmpty())
    }
}
