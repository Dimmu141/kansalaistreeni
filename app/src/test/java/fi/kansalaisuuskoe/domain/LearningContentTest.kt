package fi.kansalaisuuskoe.domain

import org.junit.Assert.*
import org.junit.Test

class LearningContentTest {
    private val topic = Topic("society", "Society", "Description", 0)
    private val question = Question("q", 1, topic.id, "Question", listOf(AnswerOption("a", "Yes")), "a", "Parliament makes laws.", "Easy", listOf(VocabularyItem("eduskunta", "parliament", "riksdagen")), Source("Parliament", "https://example.org"), "fi", Difficulty.EASY, emptyList(), ContentStatus.DEMO)
    private val snapshot = LearningSnapshot(listOf(topic), listOf(question))

    @Test fun `lessons include only matching topic and study language`() {
        val mixed = snapshot.copy(questions = listOf(question, question.copy(id = "sv", language = "sv"), question.copy(id = "other", topicId = "other")))
        assertEquals(listOf(question), LearningContent.lesson(mixed, topic.id, StudyLanguage.FINNISH)?.ideas)
        assertEquals(listOf("sv"), LearningContent.lesson(mixed, topic.id, StudyLanguage.SWEDISH)?.ideas?.map { it.id })
    }
    @Test fun `missing topics and unavailable languages do not produce fake lessons`() {
        assertNull(LearningContent.lesson(snapshot, "missing", StudyLanguage.FINNISH))
        assertNull(LearningContent.lesson(snapshot, topic.id, StudyLanguage.SWEDISH))
    }
    @Test fun `lessons reflect imported explanations and sources`() {
        val revised = question.copy(revision = 2, explanation = "Revised explanation", source = Source("New reference", "https://example.org/new"))
        assertEquals(revised, LearningContent.lesson(snapshot.copy(questions = listOf(revised)), topic.id, StudyLanguage.FINNISH)?.ideas?.single())
    }
    @Test fun `duplicate words retain all contexts without duplicate question entries`() {
        val repeated = question.copy(id = "second", vocabulary = question.vocabulary + question.vocabulary)
        val words = LearningContent.vocabulary(snapshot.copy(questions = listOf(question, repeated)), StudyLanguage.FINNISH)
        assertEquals(1, words.size)
        assertEquals(listOf("q", "second"), words.single().contexts.map { it.id })
        assertEquals("q:0", words.single().id)
    }
    @Test fun `different meanings are not merged`() {
        val words = LearningContent.vocabulary(snapshot.copy(questions = listOf(question.copy(vocabulary = question.vocabulary + VocabularyItem("eduskunta", "another meaning", "riksdagen")))), StudyLanguage.FINNISH)
        assertEquals(2, words.size)
    }
    @Test fun `Swedish vocabulary uses Swedish terms and omits missing translations`() {
        val sv = question.copy(language = "sv", vocabulary = question.vocabulary + VocabularyItem("puuttuva", "missing"))
        val words = LearningContent.vocabulary(snapshot.copy(questions = listOf(question, sv)), StudyLanguage.SWEDISH)
        assertEquals("riksdagen", words.single().term)
        assertEquals("eduskunta", words.single().otherTerm)
        assertEquals("sv", words.single().language)
    }
    @Test fun `search covers meanings alternate terms and explanation with all query tokens`() {
        val words = LearningContent.vocabulary(snapshot, StudyLanguage.FINNISH)
        assertEquals(words, LearningContent.search(words, "PARLIAMENT laws"))
        assertEquals(words, LearningContent.search(words, "riksdagen"))
        assertTrue(LearningContent.search(words, "parliament missing").isEmpty())
    }
    @Test fun `search normalizes equivalent unicode and whitespace`() {
        val q = question.copy(vocabulary = listOf(VocabularyItem("työ", "work", "arbete")))
        val words = LearningContent.vocabulary(snapshot.copy(questions = listOf(q)), StudyLanguage.FINNISH)
        assertEquals(words, LearningContent.search(words, "  TYO\u0308  "))
    }
    @Test fun `topic filter limits both contexts and searched explanation`() {
        val other = question.copy(id = "other", topicId = "history", explanation = "Independence")
        val words = LearningContent.vocabulary(snapshot.copy(questions = listOf(question, other)), StudyLanguage.FINNISH)
        assertEquals(listOf(question), LearningContent.search(words, "", topic.id).single().contexts)
        assertTrue(LearningContent.search(words, "independence", topic.id).isEmpty())
        assertEquals(listOf(other), LearningContent.search(words, "independence", "history").single().contexts)
        assertTrue(LearningContent.search(words, "", "missing").isEmpty())
    }
    @Test fun `reading views preserve history and scores`() {
        val history = snapshot.copy(progress = listOf(UserQuestionProgress(question.id, 1, mastery = 25)), attempts = listOf(QuestionAttempt("a", question.id, 1, topic.id, "a", true, 1000)))
        val before = history.copy()
        LearningContent.lesson(history, topic.id, StudyLanguage.FINNISH)
        LearningContent.search(LearningContent.vocabulary(history, StudyLanguage.FINNISH), "parliament")
        assertEquals(before, history)
    }
}
