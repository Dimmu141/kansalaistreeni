package fi.kansalaisuuskoe.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.data.BackupFile
import fi.kansalaisuuskoe.domain.contentStatus
import java.time.LocalDate

@Composable
internal fun BackupScreen(state: BackupState, model: BackupViewModel, currentAnswers: Int) {
    val resolver = LocalContext.current.contentResolver
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) model.export { json ->
            requireNotNull(resolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use { it.write(json) }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.select { requireNotNull(resolver.openInputStream(uri)).use(BackupFile::read) }
    }
    BackupBody(state, currentAnswers, { create.launch("kansalaisuuskoe-backup-${LocalDate.now()}.json") },
        { open.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, model::discard, model::restore)
}

@Composable
internal fun BackupBody(state: BackupState, currentAnswers: Int, export: () -> Unit, select: () -> Unit, discard: () -> Unit, restore: () -> Unit) {
    Page {
        item { Heading(stringResource(R.string.backup_title), stringResource(R.string.backup_intro)) }
        item { Text(stringResource(R.string.assessment_backup_note), style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { item { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } }
        if (state.saved || state.restored) item { Text(stringResource(if (state.saved) R.string.backup_saved else R.string.backup_restored), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().semanticsLabel(stringResource(R.string.backup_busy))) }
        val preview = state.preview
        if (preview == null) {
            item { Text(stringResource(R.string.backup_current_answers, currentAnswers)) }
            item { Text(stringResource(R.string.backup_privacy), style = MaterialTheme.typography.bodyMedium) }
            item { Button(export, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !state.busy) { Text(stringResource(R.string.backup_export)) } }
            item { OutlinedButton(select, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !state.busy) { Text(stringResource(R.string.backup_select)) } }
        } else {
            item { Heading(stringResource(R.string.backup_review)) }
            item { Text(stringResource(R.string.backup_date, formatDateTime(preview.backup.exportedAt))) }
            item { Text(stringResource(R.string.package_details, preview.backup.content.packageId, preview.backup.content.version)) }
            item { ContentNotice(preview.backup.content.questions.map { it.model() }.contentStatus(), compact = true) }
            item { Text(stringResource(R.string.backup_material_counts, preview.backup.content.topics.size, preview.backup.content.questions.size)) }
            item { Text(stringResource(R.string.backup_answer_counts, preview.finnishAnswers, preview.swedishAnswers)) }
            if (preview.replacesAssessments) {
                item { Text(stringResource(R.string.backup_assessment_counts, preview.completedAssessments, preview.unfinishedAssessments)) }
                item { Text(stringResource(R.string.backup_assessment_replacement, preview.currentAssessments, preview.backup.assessments.orEmpty().size), style = MaterialTheme.typography.bodyLarge) }
            } else {
                item { Text(stringResource(R.string.backup_legacy_assessments), style = MaterialTheme.typography.bodyMedium) }
            }
            item { Text(stringResource(R.string.backup_replacement, preview.currentAnswers, preview.backup.attempts.size), style = MaterialTheme.typography.bodyLarge) }
            item { Text(stringResource(R.string.backup_restore_note), style = MaterialTheme.typography.bodyMedium) }
            item { Button(restore, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !state.busy) { Text(stringResource(R.string.backup_confirm)) } }
            item { OutlinedButton(discard, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !state.busy) { Text(stringResource(R.string.backup_cancel)) } }
        }
    }
}
