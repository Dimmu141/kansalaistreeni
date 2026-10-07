# Verification — v0.13.0 assessment backups

Date: 2026-10-07. Dedicated Android 16/API 36 emulator: `emulator-5556`, `Citizenship_MVP`.

## Change boundary

New backup format v2 includes completed and unfinished practice assessments, with original question/topic snapshots, choices, language, timestamps and position. The preview discloses counts and complete replacement. Material, learning history and assessments restore in one transaction. Assessment changes invalidate a v2 preview; older format v1 preserves local assessments. Results are recomputed against original snapshots and never contribute to learning scores.

There are no new dependencies or Room migrations. Database schema v5, content package v4, mastery rules v2, assessment scoring and daily-session planning are unchanged. All bundled material remains DEMO. The app is not an official examination or preparation course. Human review and learner testing remain postponed.

## Automated checks

- The full `scripts/verify.ps1 -Offline` gate passed twice, including a final run after correcting the assessment-history storage note: **143 JVM tests, 113 device tests and six Python content-gate tests**, with no failures/errors/skips.
- Nine new JVM tests cover snapshot roundtrips, original versus current answer keys, legacy absence versus an empty replacement, language/date/position validation, answer references, completed coverage, duplicate sessions, bounds and strict nested fields.
- Eight new repository device tests cover saved/reopened FI results and SV unfinished sessions, changed material, unchanged learning scores, empty v2 clearing, v1 preservation, stale choices/position/creation/completion and transactional rollback. An injected assessment INSERT failure occurs after learning replacement writes; all material/history/progress/assessment data rolls back, and retry succeeds.
- Three new backup UI tests cover the explicit replacement preview, activity recreation, restoration of both session types, legacy preservation copy and an assessment edit invalidating the preview. Existing backup and assessment tests still pass. Repeat runs do not increase the distinct test counts.
- Final full device report: `.verification/v13-final-full-device-tests.xml`. The earlier successful full report is `.verification/v13-full-device-tests.xml`.
- Source hygiene and JSON/XML syntax cover 80 files. FI/EN resources and formatting arguments match across 285 resources. Debug/release lint has zero errors; only existing `OldTargetApi` warnings and debug `AndroidGradlePluginVersion` remain.
- Debug APK, minified release APK and unsigned release AAB build successfully. Distribution signing and publication are not configured. Both APKs contain byte-identical demo material and exclude all disposable QA backups. Seed SHA-256: `8d690e6a10a9a202cc0fd8de90147f5f2ffd549babb4ceda41a66ecc90c2b1a9`.

## Native file-picker checks

Release smoke APKs are signed only with the local debug key. Disposable backup files are placed only on the dedicated emulator. The initial APK is `.verification/release-v13-smoke.apk`; `.verification/release-v13-final-smoke.apk` includes the corrected assessment-history copy. Backup behavior is identical between these APKs.

1. Selected a v2 DEMO fixture through Android's real Downloads picker. The preview showed one completed and one unfinished assessment, with local count 0 and replacement count 2. The replacement warning and ordinary material/history warning are visible. Screenshots: `v13-release-backup-preview.png`, `v13-release-preview-tablet.png`.
2. Inspected phone layout at 200% text scale. Text wraps and both Restore and Cancel remain reachable through scrolling. At 1920×1200/density 240, content retains its bounded width and the assessment counts, warnings and actions remain readable. Normal dimensions/density and text scale were restored. Screenshots: `v13-release-preview-large-text.png`, `v13-release-preview-large-controls.png`.
3. Confirmed restoration through the native app. Success shows zero ordinary learning answers; assessment records are independent. Screenshot: `v13-release-restore-success.png`.
4. Saved `v13-native-roundtrip.json` through Android's real Create document dialog. The file contains schema v2, both FI/SV assessment records and zero learning attempts. A structured comparison verifies original material, choices, question/topic snapshots, positions and timestamps; optional null fields omitted by Moshi are normalized. Evidence: `.verification/v13-native-export-check.txt`, `v13-native-export-success.png`.
5. Installed the final minified APK, cleared disposable application data and restored the actual exported file through the native picker and confirmation. Then restored a v1 fixture: the preview explicitly states that local assessments stay unchanged. The Finnish saved result still shows 11/12 (91%); the history screen has the corrected storage note. Screenshots: `v13-final-native-roundtrip-restored.png`, `v13-final-legacy-preview.png`, `v13-final-restored-assessment-history.png`, `v13-final-restored-fi-result.png`.
6. Switched study language to Swedish, observed two of twelve saved assessment answers and resumed question 2 with option 200 selected. Backgrounded the app, used `am kill`, asserted the PID was absent and brought its saved task back. PID changed from 31464 to 32256; the same Swedish question, selection and answer count returned. This is actual process death, not activity recreation. Evidence: `.verification/v13-process-death.txt`, `v13-final-restored-sv-resume.png`, `v13-final-restored-sv-question.png`, `v13-final-sv-after-process-death.png`.
7. No fatal exception appears in `.verification/v13-final-release-runtime.log`. After verification, disposable QA history and the three v13 Downloads files were removed. The final debug APK is installed and open on fresh Home, with Finnish defaults, zero learning/assessment history, normal phone dimensions/density and 100% text scale. Screenshot: `v13-final-fresh-home.png`.

Screenshots and disposable files are retained in the ignored `.verification/` directory. Review used saved originals in `.verification/v13-before/`, direct diffs, final source, automated reports and rendered native screens. There is no Git repository in this folder. Changes are confined to backup serialization/transactions, assessment DAO reads/deletion, preview/storage copy, relevant tests, version and documentation.

## Limits

Backups are unencrypted snapshots, not merges or authenticated proof of learning/content. Earlier app releases cannot read format v2 and need an update. There is no cloud synchronization or assessment-history deletion control. Physical devices, a second physical device, other Android versions and a spoken TalkBack journey were not checked in this increment. Human content approval and learner testing remain pending.
