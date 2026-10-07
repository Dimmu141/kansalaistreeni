# Version 0.9.0 verification — 7 October 2026

## Implemented increment

Settings now offers learning backup and restore through Android's document picker. A versioned JSON snapshot contains installed study material and all Finnish/Swedish answer history, including removed and older question revisions. Device preferences, temporary session state and derived scores are excluded.

Restore validates the complete file, shows its date, material provenance, counts and explicit replacement warning, and commits only after confirmation. Material/history replace together in one Room transaction. Current learning rules rebuild mastery and review dates from the original answer timestamps. A material update or new answer after preview invalidates confirmation; storage failures roll back every database change and permit retry. Cancellation makes no learning-data changes. Preview survives activity recreation; process death discards it and requires selecting/reviewing the file again.

This increment adds no dependency, database migration, learning-rule change, networking permission or factual content. Room remains schema v4. The bundled demo JSON in the archived v0.8 and final minified v0.9 APKs is unchanged: 24 DEMO variants, 12 concepts, six provisional topics, package v4. The source-review ledger and its 6 October status snapshot are unchanged. Human review and learner-pilot documents are prepared, not completed.

## Automated verification

The full verification gate passed with the installed Android Studio JBR, offline Gradle dependencies and bundled Python runtime:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\scripts\verify.ps1 -Python 'C:\Users\dimmu\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -Offline
```

- **101 JVM tests**, zero failures/errors. Twelve new backup tests cover FI/SV round trips, schema/format, duplicate IDs, current answer-key consistency, languages/revisions, retired history, timestamps, attempt/file limits, strict UTF-8/BOM handling and rejection of supplied scores or ordinary content files.
- **78 device tests**, zero failures/errors/skips on Android 16/API 36. Seven new repository tests cover complete material/history replacement, reopening/initialization, recalculated progress/dates, removed/older history, stale answer/material previews, atomic rollback with a database trigger, empty-history replacement and invalid/read-only operations. Six new UI tests cover export success/failure/retry, preview/cancel, confirmation, invalid/stale files and restore storage failure/retry. Existing answer, migration, content import, locale, learning, vocabulary and accessibility tests still pass.
- After count wording and the shorter navigation title changed, JVM tests, lint, debug/release/AAB assembly and all **six backup UI tests** passed again. The final Settings subtitle-only correction was rebuilt with debug/release lint and all three artifacts; no subsequent logic change was made.
- **Six Python content-gate tests**, passed; the 24 demo variants still match their review fingerprints. Source hygiene and JSON/XML checks passed for **66 files**, with **244 matching FI/EN resource entries** and format-argument parity.
- Debug/release lint: zero errors, **seven existing debug warnings and one existing release warning**. Initial plural-wording warnings in new count labels were fixed in the text without suppression or a new baseline.
- Debug APK, minified unsigned release APK and unsigned release AAB built successfully. No production signing or publication occurred.

Full device results are archived in ignored `.verification/v09-full-device-tests.xml`; the later targeted UI report is `v09-final-backup-ui-tests.xml`. An initial targeted Gradle command failed because PowerShell split an unquoted instrumentation-class argument. Quoting the argument fixed invocation; this was not a failing app test. A local signing invocation needed JAVA_HOME set; the corrected command succeeded.

## Installed release and native file journey

The final minified release was signed only with the existing local debug key for emulator installation as `.verification/release-v09-smoke.apk`. Checks used the dedicated `Citizenship_MVP` emulator, `emulator-5556`, rather than a physical device or a learner's history.

- Installed archived v0.8 on a disposable fixture, recorded one correct Finnish and one incorrect Swedish answer, selected English interface/Swedish study, then upgraded to v0.9. Scores, history and language preferences survived the upgrade.
- Exported through the actual CreateDocument picker to Downloads as `citizenship-verification-v09.json`. Pulled/read the file: correct backup format, 24 questions, two answers across FI/SV. Success appeared only after the write completed. Screenshots: `.verification/v09-native-create-picker.png`, `v09-native-save-name.png`, `v09-release-export-success.png`; file: `v09-export.json`.
- Added a third answer after export. The OpenDocument preview reported current history 3, backup history 2, FI 1/SV 1, package v4 and the complete replacement warning. Cancel kept all three answers. Screenshots: `v09-native-restore-preview.png`, `v09-cancel-keeps-three-answers.png`.
- Installed the final release after the Settings privacy-copy correction, preserving the fixture. Inspected the preview at 200% system text scale on a 1080×2400 phone and a 1920×1200, density-240 landscape tablet. Text wraps, navigation remains readable, content width is bounded, and confirmation/cancellation controls remain reachable by scrolling. Activity recreation retained the reviewed preview. Screenshots: `v09-final-settings-copy.png`, `v09-backup-large-font-preview.png`, `v09-backup-large-font-confirmation.png`, `v09-backup-tablet-preview.png`.
- Backgrounded the preview, killed the process with `am kill`, confirmed no PID remained, and returned to the task. PID changed from 9984 to 10509. Preview was discarded, no automatic restore occurred, and all three answers remained. Screenshot: `v09-process-restart-discards-preview.png`.
- Selected/reviewed the backup again and explicitly restored. The screen reported two saved answers and recalculated mastery/dates. Exported again through the native picker as `citizenship-verification-v09-restored.json`; parsed comparison matched the original material and every answer ID, language, selected option, correctness, revision and timestamp. Screenshots: `v09-native-restore-success.png`, `v09-restored-export-success.png`; file: `v09-restored-export.json`.
- Repeated real background process death after restore. Two answers remained. Selecting a malformed JSON file displayed an invalid-file error and left restored data intact. Swedish Progress showed one saved answer, 0% accuracy/mastery and the original wrong answer due for review; English interface and Swedish study preferences remained. Screenshots: `v09-restored-history-after-process-restart.png`, `v09-invalid-file-keeps-restored-data.png`, `v09-restored-swedish-progress.png`.
- No fatal app exception appeared in the inspected final release log window, retained as `.verification/v09-release-runtime.log`.

After verification, only the dedicated emulator's disposable app fixture was cleared. The two QA-created Downloads backup files were removed by their exact paths; local evidence remains under `.verification`. The final debug APK is installed with fresh Finnish defaults, zero study history, normal phone dimensions/density and 100% text size. Screenshots: `v09-final-fresh-home.png`, `v09-final-finnish-backup.png`.

## Boundaries and decisions

Backups are plain, unencrypted JSON, limited to 16 MiB and 50,000 answers. A document provider chosen by the learner may sync the file independently. Restore is complete replacement, not merging. Device settings are deliberately excluded. See [BACKUP-RESTORE.md](BACKUP-RESTORE.md) for format and recovery details.

Older/removed answers cannot be checked against unavailable historical answer keys; they retain lifetime-history value but cannot inflate current mastery. Files do not authenticate learning or official provenance. Export preserves individual question status, but reconstructs a conservative package envelope. Canonical content hashing after restore means a differently formatted original same-version material file can be rejected by the existing strict material importer; restored material already works without reimporting it.

Only the API 36 emulator was available. Physical devices, other Android versions, spoken TalkBack review, actual power loss during commit, and real document-provider/storage outages were not verified. Repository rollback and UI storage retry were tested through injected failures. Human civic/language review, reuse decisions and learner sessions remain pending; [HUMAN-CONTENT-REVIEW.md](HUMAN-CONTENT-REVIEW.md) and [LEARNER-PILOT.md](LEARNER-PILOT.md) provide the next work, not invented approvals or findings.

The mastery percentage remains a practice heuristic, not an exam-pass prediction. No official exam format, timing, pass threshold or completeness claim was added. There is no Git repository here; final review used direct source inspection, meaningful tests, archived APK/content comparison and rendered emulator journeys. No backend, account, analytics, ad, AI, payment or cloud service was added.
