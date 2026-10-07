# Learning backup and restore

Since v0.13, **Settings → Backup and restore** exports the installed study material, all saved Finnish/Swedish learning attempts and completed/unfinished practice assessments. New exports use backup schema version 2. Version 1 backups from earlier app releases remain supported.

## Use it

Choose **Save a backup**, select a location in Android's file picker and wait for **Backup saved**. The app takes a consistent material/history/assessment snapshot in one Room transaction. Cancelling makes no application-data changes. A failed write does not report success; delete any incomplete file and try another location.

Choose **Choose a backup to restore** and inspect its date, package/version, material label, question/topic counts, FI/SV learning-answer counts and completed/unfinished assessment counts. Version 2 previews explicitly disclose replacement of all local assessments, their choices and results, including an empty assessment list that will clear history. The preview shows the current and restored assessment counts. This is a snapshot replacement, not a merge. Cancel leaves data intact. **Restore backup** commits only the reviewed replacement.

Version 1 previews explain that the file has no assessments and that local assessment history will remain unchanged. An older file can restore material/learning attempts without deleting or overwriting an assessment begun since that backup.

Scores and review dates are recalculated from learning attempts under the current rules, using original timestamps and stable order for simultaneous answers. Assessment scores are recalculated against their original saved question snapshots; they never enter learning accuracy, mastery, streaks or review scheduling. Unfinished assessments retain their choices, position and original language and resume through the assessment list. Restored completed results remain immutable. Current matching question revisions contribute to learning mastery; older/removed learning history remains in lifetime answer statistics.

The file includes material, so another device needs no matching package beforehand. A deliberate restore can roll back package/question versions because it replaces their learning history together, after review. Material changes or new learning answers invalidate a preview. For version 2, assessment creation, choices, position or completion changes also invalidate it; select the file again. Storage failure rolls back the entire replacement and allows retry. Device interface/study/explanation languages, theme and easier-Finnish preferences stay unchanged. Temporary quiz/navigation state and derived learning-progress rows are excluded.

## Format and validation

The JSON envelope has `format: "fi.kansalaisuuskoe.learning-backup"`, `schemaVersion`, `exportedAt`, nested `content` using content schema v1, and an `attempts` list. Version 2 requires an `assessments` list, including `[]` for a deliberate empty replacement. Version 1 omits that list; a version 1 file that supplies assessments is rejected instead of silently ignoring them. Earlier app versions reject version 2 files and need an app update before restoring one.

Each assessment has `id`, `language`, `startedAt`, nullable `finishedAt` and `payload`. Its payload holds ordered `topics`/`questions` using the existing content DTOs, the chosen answer-ID map and `position`. Original explanations, vocabulary, sources, translations and provenance are preserved with the snapshots, independently of currently installed question revisions. No percentage or derived result is imported.

Limits are 16 MiB, 50,000 learning attempts and 1,000 assessments. Unsupported fields/schema versions, malformed UTF-8/content, duplicate IDs, incompatible learning answers and invalid languages/revisions/timestamps are rejected before writes. Assessment validation checks the original content, topic/answer references, positions, 1–20 question snapshots, complete answer coverage for finished records, completion chronology and at most one unfinished assessment per language. The 20-question bound is the app's practice limit, not an official exam specification.

Current learning correctness is checked against the restored answer key. Historical learning attempts for unavailable revisions cannot be checked against an old key because those question payloads were not retained. Their structure/language consistency is validated and they cannot affect current mastery. Assessment results do retain their own original answer key. A backup is user-controlled data, not a signed or authenticated record of learning or official material.

Export reconstructs the installed content package. Individual question provenance stays unchanged. The package envelope is conservatively DEMO for all-demo material and EDITORIAL otherwise because Room does not retain its original envelope status. Restoration hashes the canonical material package. Reselecting the original unchanged material after restore remains safe: same-version comparison uses complete persisted content, not JSON byte identity. Actual edits still need appropriate package/question revisions.

Authored lessons are included in installed study material starting in v0.15. Room migration 5→6 adds the lesson column without deleting existing data. Backups containing authored lessons require v0.15 or newer; older app versions reject unknown fields rather than preserve this addition. Old backups without lessons remain readable. Confirmation checks the material/history/assessment baseline inside the same transaction as replacement. Version 1 deliberately excludes assessments from that baseline because it cannot overwrite them.

## Privacy and recovery boundaries

Files are unencrypted and contain learning history, assessment choices and original question snapshots. Keep them private. The app has no backup server or network permission; a selected Android document provider may sync files independently. No persistent URI permission is retained. Application-data clearing/uninstallation still loses data unless a readable backup has been saved elsewhere. Android automatic backup/device transfer remains disabled.

A preview survives ordinary activity recreation through its ViewModel. After process death, reopen and review the file; nothing is restored automatically. Version 2 can recover assessments after reinstalling or moving devices. Version 1 cannot recover assessments absent from the file. Deleting an assessment changes local storage and subsequent exports, but does not edit earlier backup files. A reviewed v2 restore can bring a deleted record back. Deletion invalidates a previously opened v2 restore preview; a v1 restore leaves the deletion intact. Backups contain no trusted-content signature or human editorial approval. Human review and learner testing remain postponed.
