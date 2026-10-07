# Verification — v0.14.0 assessment history controls

Date: 2026-10-07. Dedicated Android 16/API 36 emulator: `emulator-5556`, `Citizenship_MVP`.

## Change boundary

Completed results have a Delete result action. The assessment list has Discard unfinished assessment beside Resume. Both use a shared confirmation showing language, date and score or saved-answer count. It explains removal from this device, preservation of ordinary learning data and the possibility of restoration from an existing backup. Confirmation text scrolls and destructive actions are separate from ordinary navigation.

Deletion checks the complete reviewed snapshot inside a Room transaction and removes only its assessment row. Changed/restored records require fresh review. A storage failure retains data and supports retry; a repeated committed delete is harmless. Pending confirmations are intentionally kept only in memory and are dismissed by recreation or process death. No deletion happens automatically on recovery.

No new dependencies, database migrations, content or learning rules are introduced. Room schema v5, backup format v2, DEMO package v4, mastery rules v2 and adaptive daily sessions are unchanged. Human content review and learner testing remain postponed.

## Targeted checks

Six new repository device tests passed: persistent deletion of only one completed record, other-language/history preservation, unfinished discard and fresh start, stale choices/position, same-ID replacement by a backup, backup-preview invalidation and storage-failure retry. Ordinary learning attempts and progress remain unchanged after deletion and reopening. Subsequent exports omit deleted records; v1 restore preserves deletion while a freshly reviewed older v2 backup can deliberately restore it.

Four new UI tests passed through real app navigation: completed cancellation/deletion and recreation requiring fresh confirmation, Swedish discard with Finnish interface and an empty fresh start, failure/retry on a completed result, and a record changed while confirmation is open. Error and retry are placed near the affected action. Stale rejection offers fresh deletion review rather than retrying old consent.

Targeted reports: `.verification/v14-deletion-repository-tests.xml`, `.verification/v14-deletion-flow-tests.xml`. The first repository invocation did not execute tests because JUnit rejected an inferred non-Unit fixture return type; both fixture signatures were corrected. Its report is retained in `.verification/v14-initial-repository-tests.xml`.

## Full gate

The final `scripts/verify.ps1 -Offline` gate passed: **143 JVM tests, 123 device tests and six Python content-gate regressions**, with no failures/errors/skips. The ten new device tests are included in the 123 total; repeated targeted runs do not add to that count. Full report: `.verification/v14-full-device-tests.xml`.

Source hygiene and JSON/XML syntax cover 82 files; FI/EN resources and formatting arguments match across 296 resources. Debug/release lint has zero errors, with only the existing `OldTargetApi` warnings and debug `AndroidGradlePluginVersion`. Debug APK, minified release APK and unsigned release AAB build successfully. Both APKs contain byte-identical bundled demo content and exclude disposable QA files. Seed SHA-256: `8d690e6a10a9a202cc0fd8de90147f5f2ffd549babb4ceda41a66ecc90c2b1a9`.

## Native minified-release checks

The final minified release was signed only with the local debug key as `.verification/release-v14-smoke.apk`. These checks used native Android controls and disposable data only on the dedicated emulator.

1. Restored a DEMO fixture containing a completed Finnish result, an unfinished Swedish assessment and one ordinary learning answer per language through Android's actual file picker and confirmation. Opened the Finnish result and verified the deletion confirmation identifies its 11/12 result, date, language and scope. Back dismissed confirmation with the result intact. Screenshot: `v14-release-result-confirmation.png`, `v14-release-back-cancelled.png`.
2. Inspected the confirmation at 1920×1200/density 240. Keep cancelled without deletion. Restored normal phone dimensions/density, confirmed deletion and returned to the empty Finnish history. Progress still showed one learning answer, accuracy 100%, material mastery 2% and no due reviews. Screenshots: `v14-release-confirmation-tablet.png`, `v14-release-result-deleted.png`, `v14-release-learning-after-delete.png`.
3. Switched to Swedish and observed its untouched unfinished assessment with two saved answers. At 200% Android text scale, the title wraps, the confirmation body scrolls to the backup explanation and both destructive/cancel controls stay reachable. Keep cancelled. Screenshots: `v14-release-sv-before-discard.png`, `v14-release-discard-large-text.png`, `v14-release-discard-large-scrolled.png`.
4. At normal text scale, opened discard confirmation, backgrounded the app, used `am kill`, verified the PID was absent and restored its saved task. PID changed from 3718 to 4995; confirmation was dismissed and the unfinished assessment still had two saved answers. Fresh confirmation then discarded it. Starting another Swedish assessment showed question 1 with zero saved answers. Evidence: `.verification/v14-process-death.txt`, `v14-release-confirmation-process-recovery.png`, `v14-release-unfinished-discarded.png`, `v14-release-fresh-sv-assessment.png`.
5. Discarded the disposable fresh assessment and exported through Android's actual Create document dialog. The file has zero assessments, both unchanged FI/SV learning attempts and unchanged material. The original backup copied back from Downloads remains byte-identical and still contains both original records. Evidence: `.verification/v14-native-export-check.txt`, `v14-release-export-after-delete.png`, `.verification/v14-native-after-deletion.json`.
6. No fatal exception appears in `.verification/v14-release-runtime.log`. Disposable application data and both v14 Downloads files were removed afterward. The final debug APK is installed and open on fresh Home, with Finnish defaults, zero learning/assessment history, normal phone dimensions/density and 100% text scale. Screenshot: `v14-final-fresh-home.png`.

Screenshots and comparison files above are retained in `.verification/`. No distribution signing or publication was performed.

## Review and limits

There is no Git repository in this folder. Review uses saved originals in `.verification/v14-before/`, direct diffs, final source, automated reports and rendered native screens. Changes are confined to single-record assessment persistence, ViewModel error/retry handling, result/list controls, bilingual copy, relevant tests, version and documentation.

Deletion does not edit previously exported backup files and there is no automatic cloud synchronization. Physical devices, other Android versions and a spoken TalkBack journey remain unverified in this increment. Distribution signing/publication and human approval remain pending.
