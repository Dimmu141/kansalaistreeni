# Practice assessments

Version 0.11 adds a mixed-topic self-check through Home or Topics → Practice assessment. It is our practice format, not an assertion about the official citizenship examination. Bundled questions remain DEMO, categories remain provisional, and there is no pass threshold, timer or prediction of official test success.

## Learner flow

The exercise uses the selected Finnish or Swedish study language. It draws at most 20 questions from installed material; the bundled demo has 12 questions per language. Topic groups and their questions are shuffled, then sampled in rounds so a large topic does not crowd out smaller topics. A topic with few questions can still be underrepresented; this is not an official coverage blueprint.

Choose an answer to save it automatically. Next remains disabled until the current choice is saved. Previous lets you change earlier answers. Back leaves without losing saved choices or position; resume from the assessment list after closing the app. One unfinished exercise per language is resumed instead of silently replaced. Finnish and Swedish exercises/history are separate.

Finish becomes available after every question has an answer. It freezes choices and the completion time, then reveals the correct answers, optional easier-Finnish/English explanations, sources, actual correct/total counts and topic results. Percentages describe this small exercise only. Reading or repeating the result does not update any learning score.

Practise mistakes starts an ordinary learning session for incorrect questions still compatible with the installed material. These practice answers do contribute to the usual learning accuracy, mastery, study streak and review scheduling. The original assessment result remains unchanged. Missing, revised, language-switched or natively changed questions are excluded with a visible count; their original explanations remain readable in the saved result. Supporting translations may change, and follow-up uses the current compatible question payload.

## Delete or discard

Open a completed result and choose **Delete result**, or choose **Discard unfinished assessment** beside Resume in the assessment list. The confirmation identifies the study language, date and score or saved-answer count, explains removal of that assessment and states that ordinary learning answers, statistics and review dates stay unchanged. Keep, Back or dismiss cancels without writes. Confirmation text scrolls at large text sizes; destructive actions remain separate from ordinary navigation.

Deleting removes only the selected assessment from local storage and future backups. It does not alter already exported files; deliberately restoring a backup containing that record can bring it back. Discarding an unfinished exercise enables a fresh assessment in the same language. Other assessments and languages remain untouched.

The repository compares the stored record with the reviewed snapshot inside the deletion transaction. Changed choices, position, completion or a restored replacement require a fresh confirmation instead of deleting unseen changes. A failed storage operation preserves the record and offers retry. Retrying an already committed deletion is harmless. Pending confirmation is kept only in memory: activity recreation or process death dismisses it and requires reviewing again, without automatic deletion. No database migration or backup-format change is needed.

## Persistence and boundaries

`domain/Assessment.kt` contains pure queue, choice, completion and scoring rules. `data/AssessmentRepository.kt` uses Room transactions and Moshi snapshots. Schema v5 adds an independent `assessments` table through migration 4→5. Existing learning history, scores, settings and material are preserved.

Each session stores its language, start/completion times, ordered question and topic snapshots, selected option IDs and position. Answer and position writes are serialized by the ViewModel and committed atomically. Double starts resume the same active session; repeat completion keeps its original timestamp. Invalid options or incomplete completion fail without writes. A failed save keeps the prior persisted choice and offers retry. Recovery after process death reads the database rather than retaining large question payloads in Android saved state.

Content updates do not rewrite these snapshots. New backups preserve the original snapshots; restoring a version 2 backup deliberately replaces saved assessment history, while a version 1 learning-only backup leaves it intact. Assessment results remain reproducible against their original answer keys even when the installed material changes. Assessment records never enter `attempts` or `question_progress`; existing summary calculations therefore remain unchanged. Follow-up practice pins the assessment language and uses the existing idempotent learning-answer path.

Since v0.13, **completed and unfinished assessments are included in new version 2 backups**, including question/topic snapshots, choices, position and timestamps. Restore previews disclose file counts and replacement of local results and unfinished assessments; confirmation commits material, learning history and assessments together. Older version 1 backups explicitly preserve local assessments. Restored unfinished sessions resume normally and results remain based on their original answer keys. See [backup and restore](BACKUP-RESTORE.md). Since v0.14, individual assessment deletion is available after confirmation. There is no automatic cloud synchronization.

## Verification

Domain tests cover balanced sampling, language filtering, arbitrary answer counts, answer changes, invalid inputs, deferred results, actual accuracy/topic totals, immutable completion and compatible mistake practice. Device tests cover saved-session reopening, concurrent starts, language separation, content/backup changes, unchanged learning data, legacy schema migration, real navigation, recreation, source/explanation expansion, saved answer edits, language-pinned follow-up and write-failure retry. Portability adds validation, roundtrip/reopening, stale-preview, atomic rollback, preview/recreation and legacy-preservation checks. Current runtime evidence and limitations are recorded in `VERIFICATION-0.14.0.md`; earlier assessment checks remain in `VERIFICATION-0.11.0.md`.
