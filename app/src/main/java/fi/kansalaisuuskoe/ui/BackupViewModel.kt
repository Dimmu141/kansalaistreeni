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

data class BackupState(val preview: PreparedBackup? = null, val busy: Boolean = false, val error: Int? = null, val saved: Boolean = false, val restored: Boolean = false)

class BackupViewModel(private val repository: LearningRepository) : ViewModel() {
    private val mutable = MutableStateFlow(BackupState())
    val state = mutable.asStateFlow()

    fun export(write: (String) -> Unit) {
        if (mutable.value.busy) return
        mutable.value = BackupState(busy = true)
        viewModelScope.launch {
            try {
                val json = repository.exportBackup()
                withContext(Dispatchers.IO) { write(json) }
                mutable.value = BackupState(busy = true, saved = true)
            } catch (e: Exception) { failure(e, R.string.backup_write_error) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }

    fun select(read: () -> String) {
        if (mutable.value.busy) return
        mutable.value = BackupState(busy = true)
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) { read() }
                mutable.value = mutable.value.copy(preview = repository.prepareRestore(json))
            } catch (e: Exception) { failure(e, R.string.backup_read_error) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }

    fun discard() { if (!mutable.value.busy) mutable.value = BackupState() }

    fun restore() {
        val preview = mutable.value.preview ?: return
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                repository.restoreBackup(preview)
                mutable.value = BackupState(busy = true, restored = true)
            } catch (e: Exception) {
                failure(e, R.string.backup_restore_error)
                if (e is BackupException) mutable.value = mutable.value.copy(preview = null)
            } finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }

    private fun failure(error: Exception, fallback: Int) {
        if (error is CancellationException) throw error
        Diagnostics.error("Backup", error)
        mutable.value = mutable.value.copy(error = when ((error as? BackupException)?.problem) {
            BackupProblem.INVALID -> R.string.backup_invalid
            BackupProblem.TOO_LARGE -> R.string.backup_too_large
            BackupProblem.STALE -> R.string.backup_stale
            null -> fallback
        })
    }
}
