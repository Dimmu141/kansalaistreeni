package fi.kansalaisuuskoe.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.data.LearningRepository
import fi.kansalaisuuskoe.data.AssessmentRepository
import fi.kansalaisuuskoe.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class QuestionUiState(
    val loading: Boolean = true,
    val question: Question? = null,
    val position: Int = 0,
    val total: Int = 0,
    val selected: String? = null,
    val answered: Boolean = false,
    val saving: Boolean = false,
    val retryPending: Boolean = false,
    val finished: Boolean = false,
    val correctCount: Int = 0,
    val error: Int? = null,
    val contentStatus: ContentStatus = ContentStatus.DEMO,
    val extraPractice: Boolean = false,
    val nextReview: Long? = null,
    val recap: SessionRecap = SessionRecap(),
    val dailyReason: DailyQuestionReason? = null,
)
class QuestionViewModel(private val repository: LearningRepository, private val saved: SavedStateHandle, private val studyLanguage: StudyLanguage = StudyLanguage.FINNISH, private val assessments: AssessmentRepository? = null) : ViewModel() {
    private val mutable = MutableStateFlow(QuestionUiState())
    val state = mutable.asStateFlow()
    private var questions: List<Question> = emptyList()
    private var snapshot = LearningSnapshot()
    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                mutable.value = QuestionUiState()
                val request = PracticeRequest.fromArguments(saved["mode"], saved["topicId"], saved["assessmentId"])
                val assessment = (request as? PracticeRequest.Mistakes)?.let { requireNotNull(assessments?.get(it.assessmentId)) }
                val language = StudyLanguage.entries.firstOrNull { it.tag == (saved.get<String>("study_language") ?: assessment?.language) } ?: studyLanguage
                saved["study_language"] = language.tag
                snapshot = repository.snapshot.first { it.questions.isNotEmpty() }.forLanguage(language)
                val now = System.currentTimeMillis()
                val plan = if (request == PracticeRequest.Daily) DailyLearningPlanner.plan(snapshot, now) else null
                val ids = saved.get<ArrayList<String>>("question_ids") ?: ArrayList(
                    (when (request) {
                        is PracticeRequest.Mistakes -> Assessments.practiceMistakes(requireNotNull(assessment), snapshot.questions)
                        PracticeRequest.Review -> LearningEngine.due(snapshot, now)
                        PracticeRequest.Daily -> requireNotNull(plan).items.map { it.question }
                        is PracticeRequest.Topic -> LearningEngine.learningQueue(snapshot, now, request.id)
                    }).map { it.id },
                ).also {
                    saved["question_ids"] = it
                    if (plan != null) saved["daily_reasons"] = ArrayList(plan.items.map { item -> item.reason.name })
                }
                questions = ids.map { id -> requireNotNull(snapshot.questions.find { it.id == id }) }
                val revisions = saved.get<ArrayList<Int>>("queue_revisions")
                if (revisions != null && (saved.get<Int>("index") ?: 0) < questions.size) require(revisions == questions.map { it.revision }) { "Question revision changed; restart the session" }
                if (revisions == null) saved["queue_revisions"] = ArrayList(questions.map { it.revision })
                if (assessment != null) require(questions.all { it in Assessments.practiceMistakes(assessment, snapshot.questions) }) { "Assessment questions changed" }
                render()
                // Retry an interrupted database save using the same token.
                if (saved.get<String>("pending_option") != null && !mutable.value.answered) submitPending()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Diagnostics.error("Opening practice", e)
                mutable.value = QuestionUiState(loading = false, error = R.string.error_open_practice)
            }
        }
    }
    private fun render() {
        val index = saved.get<Int>("index") ?: 0
        mutable.value = QuestionUiState(
            loading = false, question = questions.getOrNull(index), position = index + 1, total = questions.size,
            selected = saved["selected"], answered = saved["answered"] ?: false,
            finished = index >= questions.size, correctCount = saved["correct_count"] ?: 0,
            contentStatus = questions.contentStatus(),
            extraPractice = saved["extra_practice"] ?: false, nextReview = saved["next_review"],
            recap = SessionRecaps.summarize(questions, snapshot.attempts, saved.get<ArrayList<String>>("session_attempt_ids").orEmpty(), snapshot.progress),
            dailyReason = saved.get<ArrayList<String>>("daily_reasons")?.getOrNull(index)?.let { name -> DailyQuestionReason.entries.find { it.name == name } },
        )
    }
    fun select(option: String) {
        if (mutable.value.answered || mutable.value.saving || saved.get<String>("pending_option") != null) return
        saved["selected"] = option
        mutable.value = mutable.value.copy(selected = option, error = null)
    }
    fun submit() {
        if (mutable.value.answered || mutable.value.saving) return
        val option = saved.get<String>("pending_option") ?: mutable.value.selected ?: return
        saved["pending_option"] = option
        if (saved.get<String>("attempt_token") == null) saved["attempt_token"] = UUID.randomUUID().toString()
        submitPending()
    }
    private fun submitPending() {
        val question = mutable.value.question ?: return
        val option = saved.get<String>("pending_option") ?: return
        val token = saved.get<String>("attempt_token") ?: return
        mutable.value = mutable.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val result = repository.answer(question, option, token, System.currentTimeMillis())
                val sessionIds = saved.get<ArrayList<String>>("session_attempt_ids") ?: arrayListOf()
                val alreadyCounted = result.attempt.id in sessionIds
                saved["session_attempt_ids"] = ArrayList((sessionIds + result.attempt.id).distinct())
                snapshot = snapshot.copy(
                    attempts = snapshot.attempts.filterNot { it.id == result.attempt.id } + result.attempt,
                    progress = snapshot.progress.filterNot { it.questionId == result.progress.questionId } + result.progress,
                )
                saved["answered"] = true
                saved["selected"] = option
                saved["correct_count"] = (saved.get<Int>("correct_count") ?: 0) + if (result.attempt.correct && !alreadyCounted) 1 else 0
                saved["extra_practice"] = result.attempt.correct && !result.eligibleForMastery
                saved["next_review"] = result.progress.nextReview
                saved.remove<String>("pending_option")
                mutable.value = mutable.value.copy(saving = false, retryPending = false, answered = true, selected = option, correctCount = saved["correct_count"] ?: 0, extraPractice = saved["extra_practice"] ?: false, nextReview = result.progress.nextReview)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Diagnostics.error("Saving an answer", e)
                mutable.value = mutable.value.copy(saving = false, retryPending = true, error = R.string.error_save_answer)
            }
        }
    }
    fun next() {
        if (!mutable.value.answered) return
        saved["index"] = (saved.get<Int>("index") ?: 0) + 1
        saved["answered"] = false
        saved.remove<String>("selected")
        saved.remove<String>("attempt_token")
        saved.remove<String>("pending_option")
        saved.remove<Boolean>("extra_practice")
        saved.remove<Long>("next_review")
        render()
    }
}
