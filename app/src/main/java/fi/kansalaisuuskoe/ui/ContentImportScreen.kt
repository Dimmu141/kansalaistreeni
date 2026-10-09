package fi.kansalaisuuskoe.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import fi.kansalaisuuskoe.R
import fi.kansalaisuuskoe.data.ContentFile
import fi.kansalaisuuskoe.domain.ContentStatus
import fi.kansalaisuuskoe.domain.Coverage

@Composable
fun ContentImportScreen(state: ContentImportState, model: ContentImportViewModel, openCoverage: () -> Unit = {}) {
    val resolver = LocalContext.current.contentResolver
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.selectDocument {
            requireNotNull(resolver.openInputStream(uri)).use(ContentFile::read)
        }
    }
    ContentImportBody(state, { picker.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, model::discard, model::confirm, model::refresh, openCoverage)
}

@Composable
internal fun ContentImportBody(state: ContentImportState, select: () -> Unit, discard: () -> Unit, confirm: () -> Unit, retry: () -> Unit, openCoverage: () -> Unit = {}) {
    val preview = state.preview
    Page {
        item { Heading(stringResource(R.string.material_title), stringResource(R.string.material_intro)) }
        state.error?.let { item { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } }
        if (state.imported) item { Text(stringResource(R.string.import_success), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.titleMedium) }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().semanticsLabel(stringResource(R.string.import_busy))) }
        state.installed?.let { installed ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.material_current), style = MaterialTheme.typography.titleMedium)
                    installed.metadata?.let { SelectionContainer { Text(stringResource(R.string.package_details, it.packageId, it.version)) } }
                    Text(stringResource(R.string.material_counts, pluralStringResource(R.plurals.topic_count, installed.topics.size, installed.topics.size), pluralStringResource(R.plurals.question_count, installed.questions.size, installed.questions.size)))
                    Text(stringResource(R.string.import_languages, installed.questions.count { it.language == "fi" }, installed.questions.count { it.language == "sv" }))
                    TextButton(onClick = openCoverage, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.coverage_title)) }
                }
            }
        }
        if (state.installed == null && !state.busy) item { OutlinedButton(onClick = retry) { Text(stringResource(R.string.retry)) } }
        if (preview == null) {
            item { Text(stringResource(R.string.import_pick_note), style = MaterialTheme.typography.bodyMedium) }
            item { Button(onClick = select, enabled = !state.busy && state.installed != null, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.import_pick)) } }
            item { Text(stringResource(R.string.import_privacy), style = MaterialTheme.typography.bodySmall) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.official_material_snapshot), style = MaterialTheme.typography.bodyMedium)
                    SelectionContainer { Text("https://migri.fi/ukk-suomen-kansalaisuus", style = MaterialTheme.typography.bodySmall) }
                }
            }
        } else {
            item { Heading(stringResource(R.string.import_preview_title)) }
            item { SelectionContainer { Text(stringResource(R.string.package_details, preview.content.packageId, preview.content.version)) } }
            item { Text(stringResource(R.string.material_counts, pluralStringResource(R.plurals.topic_count, preview.content.topics.size, preview.content.topics.size), pluralStringResource(R.plurals.question_count, preview.content.questions.size, preview.content.questions.size))) }
            item { Text(stringResource(R.string.import_languages, preview.content.questions.count { it.language == "fi" }, preview.content.questions.count { it.language == "sv" })) }
            item { Text(stringResource(R.string.import_study_scope), style = MaterialTheme.typography.bodyMedium) }
            if (preview.content.objectives.isNotEmpty()) item {
                val objectives = Coverage.report(preview.content.topics.map { it.model() }, preview.content.objectives.map { it.model() }, preview.content.questions.map { it.model() }).flatMap { it.objectives }
                Text(stringResource(R.string.import_objectives, pluralStringResource(R.plurals.objective_count, objectives.size, objectives.size), thinSummary(objectives.count { it.thin })),
                    style = MaterialTheme.typography.bodyMedium)
            }
            item { Text(stringResource(R.string.import_provenance, stringResource(ContentStatus.valueOf(preview.content.status).labelResource())), style = MaterialTheme.typography.bodyMedium) }
            item { Text(stringResource(R.string.import_unverified), style = MaterialTheme.typography.bodyMedium) }
            item { HorizontalDivider() }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatRow(preview.changes.added.toString(), stringResource(R.string.import_added))
                    StatRow(preview.changes.revised.toString(), stringResource(R.string.import_revised))
                    StatRow(preview.changes.translated.toString(), stringResource(R.string.import_translated))
                    StatRow(preview.changes.reclassified.toString(), stringResource(R.string.import_reclassified))
                    StatRow(preview.changes.unchanged.toString(), stringResource(R.string.import_unchanged))
                    StatRow(preview.changes.removed.toString(), stringResource(R.string.import_removed))
                    StatRow(preview.changedLessonVariants.toString(), stringResource(R.string.import_changed_lessons))
                }
            }
            item { Text(stringResource(R.string.import_progress_note), style = MaterialTheme.typography.bodyMedium) }
            if (preview.baseline?.packageId != preview.content.packageId) item { Text(stringResource(R.string.import_replacement_note), style = MaterialTheme.typography.bodyMedium) }
            if (preview.alreadyInstalled) item { Text(stringResource(R.string.import_already_installed), style = MaterialTheme.typography.titleMedium) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = confirm, enabled = !state.busy && !preview.alreadyInstalled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.import_confirm)) }
                    OutlinedButton(onClick = discard, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.import_discard)) }
                }
            }
            if (preview.content.topics.any { it.lessons.isNotEmpty() }) item { Heading(stringResource(R.string.import_lessons_title)) }
            preview.content.topics.forEach { topic ->
                topic.lessons.forEach { (language, dto) ->
                    item(key = "lesson:${topic.id}:$language") {
                        var expanded by remember(topic.id, language, dto.revision) { mutableStateOf(false) }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val languageLabel = stringResource(when (language) {
                                "fi" -> R.string.language_finnish
                                "sv" -> R.string.language_swedish
                                else -> R.string.language_english
                            })
                            val label = stringResource(R.string.import_lesson, topic.title, languageLabel)
                            val expansion = stringResource(if (expanded) R.string.recap_expanded else R.string.recap_collapsed)
                            TextButton({ expanded = !expanded }, Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { stateDescription = expansion }) { Text(label) }
                            if (expanded) {
                                ContentNotice(ContentStatus.valueOf(dto.status), compact = true)
                                StudyText(dto.introduction, language, MaterialTheme.typography.bodyLarge)
                                dto.sections.forEach { section ->
                                    Heading(section.title)
                                    StudyText(section.body, language, MaterialTheme.typography.bodyLarge)
                                    section.easyBody?.let { StudyText(it, "fi", MaterialTheme.typography.bodyLarge) }
                                    section.sources.forEach { SourceReference(fi.kansalaisuuskoe.domain.Source(it.title, it.url, it.checkedAt)) }
                                }
                            }
                        }
                    }
                }
            }
            item { Heading(stringResource(R.string.import_questions_title), stringResource(R.string.import_questions_note)) }
            items(preview.content.questions, key = { it.id }) { question ->
                var expanded by remember(question.id, question.revision) { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(question.text, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(if (question.language == "sv") R.string.question_language_swedish else R.string.question_language), style = MaterialTheme.typography.labelLarge)
                    Text(question.source.title, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.import_question_status, question.revision, stringResource(ContentStatus.valueOf(question.status).labelResource())), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { expanded = !expanded }) { Text(stringResource(if (expanded) R.string.import_hide_details else R.string.import_show_details)) }
                    if (expanded) {
                        question.options.forEach { Text(it.text, style = MaterialTheme.typography.bodyMedium) }
                        Text(stringResource(R.string.correct_answer_value, question.options.first { it.id == question.correctOptionId }.text))
                        Text(question.explanation)
                        question.translations["en"]?.let {
                            Text(stringResource(R.string.explanation_english_title), style = MaterialTheme.typography.titleSmall)
                            StudyText(it.explanation, "en", MaterialTheme.typography.bodyMedium)
                        }
                        question.easyExplanation?.let { Text(it) }
                        SelectionContainer { Text(question.source.url, style = MaterialTheme.typography.bodySmall) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
