package fi.kansalaisuuskoe.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.data.AssessmentRepository
import fi.kansalaisuuskoe.data.AssessmentChangedException
import fi.kansalaisuuskoe.domain.AssessmentSession
import fi.kansalaisuuskoe.domain.StudyLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AssessmentUiState(
    val loading: Boolean = true, val sessions: List<AssessmentSession> = emptyList(),
    val busy: Boolean = false, val error: Int? = null,
)

class AssessmentViewModel(private val repository: AssessmentRepository) : ViewModel() {
    private val mutable = MutableStateFlow(AssessmentUiState())
    val state = mutable.asStateFlow()
    private var observer: kotlinx.coroutines.Job? = null
    private var pending: (suspend () -> Unit)? = null
    private var pendingError = R.string.assessment_error
    init { load() }

    fun load() {
        if (observer?.isActive == true) return
        mutable.value = mutable.value.copy(loading = true, error = null)
        observer = viewModelScope.launch {
            try {
                repository.sessions.collect { mutable.value = mutable.value.copy(loading = false, sessions = it) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Diagnostics.error("Loading assessments", e)
                mutable.value = mutable.value.copy(loading = false, error = R.string.assessment_error)
            }
        }
    }

    private fun write(errorResource: Int = R.string.assessment_error, operation: suspend () -> Unit) {
        if (mutable.value.busy) return
        pending = operation
        pendingError = errorResource
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { operation(); pending = null }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                Diagnostics.error("Assessment change", e)
                if (e is AssessmentChangedException) pending = null
                mutable.value = mutable.value.copy(error = if (e is AssessmentChangedException) R.string.assessment_delete_stale else errorResource)
            } finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun retry() { pending?.let { write(pendingError, it) } ?: load() }
    fun start(language: StudyLanguage, length: Int, timed: Boolean, open: (String) -> Unit) =
        write { open(repository.start(language, length = length, timed = timed)) }
    fun select(id: String, questionId: String, optionId: String) = write { repository.select(id, questionId, optionId) }
    fun move(id: String, position: Int) = write { repository.move(id, position) }
    fun finish(id: String) = write { repository.finish(id) }
    fun delete(session: AssessmentSession, deleted: () -> Unit = {}) = write(R.string.assessment_delete_error) {
        repository.delete(session)
        deleted()
    }
}
