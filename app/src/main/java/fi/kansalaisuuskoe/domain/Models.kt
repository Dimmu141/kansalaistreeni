package fi.kansalaisuuskoe.domain

enum class ContentStatus { DEMO, EDITORIAL, OFFICIALLY_SOURCED, OFFICIAL }
enum class Difficulty { EASY, MEDIUM, HARD }

/** TRUE_FALSE questions are statements with exactly the fixed option IDs [TRUE_ID] and [FALSE_ID]. */
enum class QuestionFormat {
    SINGLE_CHOICE, TRUE_FALSE;
    companion object {
        const val TRUE_ID = "true"
        const val FALSE_ID = "false"
    }
}
enum class StudyLanguage(val tag: String) { FINNISH("fi"), SWEDISH("sv") }
data class AnswerOption(val id: String, val text: String)
data class VocabularyItem(val term: String, val meaning: String, val swedish: String? = null)
/** [checkedAt] is the ISO date an editor last checked the cited page. */
data class Source(val title: String, val url: String, val checkedAt: String? = null)
data class Topic(val id: String, val title: String, val description: String, val order: Int, val translations: Map<String, TopicTranslation> = emptyMap(), val lessons: Map<String, AuthoredLesson> = emptyMap()) {
    fun titleFor(language: String) = translations[language]?.title ?: title
    fun descriptionFor(language: String) = translations[language]?.description ?: description
}
data class TopicTranslation(val title: String, val description: String)

/** Localized reading material. Reading alone never counts as a scored answer. */
data class AuthoredLesson(val id: String, val revision: Int, val introduction: String, val status: ContentStatus, val sections: List<LessonSection>)
data class LessonSection(val id: String, val title: String, val body: String, val easyBody: String?, val sources: List<Source>)
data class Question(
    val id: String,
    val revision: Int,
    val topicId: String,
    val text: String,
    val options: List<AnswerOption>,
    val correctOptionId: String,
    val explanation: String,
    val easyExplanation: String?,
    val vocabulary: List<VocabularyItem>,
    val source: Source,
    val language: String,
    val difficulty: Difficulty,
    val tags: List<String>,
    val status: ContentStatus,
    val translations: Map<String, QuestionTranslation> = emptyMap(),
    val format: QuestionFormat = QuestionFormat.SINGLE_CHOICE,
    val objectiveIds: List<String> = emptyList(),
    // ISO dates for facts that can change (officeholders, ages, amounts, memberships).
    val validFrom: String? = null,
    val reviewBy: String? = null,
) {
    /** Past its editorial review date: the learner is told to check the source. */
    fun reviewOverdue(today: java.time.LocalDate) = reviewBy?.let { java.time.LocalDate.parse(it) < today } == true
}
data class QuestionTranslation(
    val text: String,
    val options: List<AnswerOption>,
    val explanation: String,
)
data class QuestionAttempt(
    val id: String,
    val questionId: String,
    val revision: Int,
    val topicId: String,
    val selectedOptionId: String,
    val correct: Boolean,
    val answeredAt: Long,
    val language: String = "fi",
)
data class UserQuestionProgress(
    val questionId: String,
    val revision: Int,
    val attempts: Int = 0,
    val correctAnswers: Int = 0,
    val consecutiveCorrect: Int = 0,
    val mastery: Int = 0,
    val lastAnswered: Long? = null,
    val nextReview: Long? = null,
)
data class AnswerResult(
    val attempt: QuestionAttempt,
    val progress: UserQuestionProgress,
    val eligibleForMastery: Boolean,
)
enum class RecommendationKind { REVIEW, NEW_QUESTIONS, WAIT_FOR_REVIEW }
data class StudyRecommendation(val kind: RecommendationKind, val questionCount: Int, val nextReviewAt: Long?)
data class TopicProgress(
    val topic: Topic,
    val questionCount: Int,
    val studiedCount: Int,
    val mastery: Int,
    val dueCount: Int,
)
data class LearningSummary(
    val mastery: Int,
    val answered: Int,
    val accuracy: Int?,
    val studiedCount: Int,
    val totalQuestions: Int,
    val dueCount: Int,
    val streak: Int,
    val topics: List<TopicProgress>,
    val weakest: TopicProgress?,
    val strongest: TopicProgress?,
)
data class LearningSnapshot(
    val topics: List<Topic> = emptyList(),
    val questions: List<Question> = emptyList(),
    val progress: List<UserQuestionProgress> = emptyList(),
    val attempts: List<QuestionAttempt> = emptyList(),
)

/** Historical accuracy stays scoped even when an imported package removes a question. */
fun LearningSnapshot.forLanguage(language: StudyLanguage): LearningSnapshot {
    val available = questions.filter { it.language == language.tag }
    val ids = available.map { it.id }.toSet()
    val topicIds = available.map { it.topicId }.toSet()
    return copy(
        questions = available,
        topics = topics.filter { it.id in topicIds },
        progress = progress.filter { it.questionId in ids },
        attempts = attempts.filter { it.language == language.tag },
    )
}

fun List<Question>.contentStatus(): ContentStatus = when {
    isEmpty() || any { it.status == ContentStatus.DEMO } -> ContentStatus.DEMO
    all { it.status == ContentStatus.OFFICIAL } -> ContentStatus.OFFICIAL
    all { it.status == ContentStatus.OFFICIALLY_SOURCED } -> ContentStatus.OFFICIALLY_SOURCED
    else -> ContentStatus.EDITORIAL
}
