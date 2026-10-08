package fi.kansalaisuuskoe.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ContentImportState(
    val installed: InstalledContent? = null,
    val preview: PreparedContent? = null,
    val busy: Boolean = false,
    val error: Int? = null,
    val imported: Boolean = false,
)

class ContentImportViewModel(private val repository: LearningRepository) : ViewModel() {
    private val mutable = MutableStateFlow(ContentImportState())
    val state = mutable.asStateFlow()
    init { refresh() }

    fun refresh() = run {
        if (mutable.value.busy) return@run
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { mutable.value = mutable.value.copy(installed = repository.installedContent()) }
            catch (e: Exception) { failure(e) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }

    // The selected document is read once; no URI permission or content is persisted before confirmation.
    fun selectDocument(read: () -> String) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, preview = null, error = null, imported = false)
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) { read() }
                val preview = repository.prepareImport(json)
                mutable.value = mutable.value.copy(preview = preview, installed = repository.installedContent())
            } catch (e: Exception) { failure(e) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }

    fun discard() {
        if (!mutable.value.busy) mutable.value = mutable.value.copy(preview = null, error = null, imported = false)
    }

    fun confirm() {
        val preview = mutable.value.preview ?: return
        if (mutable.value.busy || preview.alreadyInstalled) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                repository.confirmImport(preview)
                mutable.value = ContentImportState(installed = InstalledContent(
                    ContentMetadata(packageId = preview.content.packageId, version = preview.content.version, hash = preview.hash),
                    preview.content.topics, preview.content.questions, preview.content.objectives), imported = true, busy = true)
            } catch (e: Exception) {
                failure(e)
                // A changed database needs a new review. Storage errors keep the preview for retry.
                if (e is ContentImportException) {
                    mutable.value = mutable.value.copy(preview = null, installed = null)
                    try { mutable.value = mutable.value.copy(installed = repository.installedContent()) }
                    catch (readFailure: Exception) { failure(readFailure) }
                }
            } finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }

    private fun failure(error: Exception) {
        if (error is CancellationException) throw error
        Diagnostics.error("Study material import", error)
        val message = when ((error as? ContentImportException)?.problem) {
            ImportProblem.TOO_LARGE -> R.string.import_too_large
            ImportProblem.INVALID -> R.string.import_invalid
            ImportProblem.VERSION -> R.string.import_version_error
            ImportProblem.REVISION -> R.string.import_revision_error
            ImportProblem.STALE -> R.string.import_stale
            null -> R.string.import_storage_error
        }
        mutable.value = mutable.value.copy(error = message)
    }
}
