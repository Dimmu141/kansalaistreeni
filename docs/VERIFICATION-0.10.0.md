# Version 0.10.0 verification — 7 October 2026

## Implemented increment

Restoring a backup previously changed the installed material's raw JSON hash because export reconstructs its serialization. Reselecting the original same-version material file could then fail with a version error, despite identical learning content. Version 0.10 fixes this compatibility problem without changing stored learning data or the backup format.

Same package/version now compares complete persisted topic and question DTOs. Unchanged material produces an already-installed preview with replacement disabled. Whitespace, object-key order, optional-field defaults and equivalent serialization no longer force a material version increase. Question order, option order, translations, sources, provenance and all other persisted fields still matter. Topics follow explicit display order, with original insertion order preserved for ties. DAO reads now specify these orders explicitly.

Real material edits still require a newer package version and the existing question-revision rules. Reimporting equivalent material does not update metadata or history, so it cannot invalidate pending material/backup previews. Real intervening updates still do. The existing hash remains a transaction-baseline fingerprint rather than an authenticity signature.

Package-level provenance is validated on read but is not stored as learning data. An envelope-label-only difference cannot promote installed question provenance: the individual question statuses remain unchanged. This fix adds no dependency, migration, learning-rule change, factual content, networking permission or service. Room remains schema v4, backup/content schemas remain v1, and learning rules remain v2.

The exact minified v0.9/v0.10 APKs contain byte-identical `assets/demo-content.json` (SHA-256 `8d690e6a10a9a202cc0fd8de90147f5f2ffd549babb4ceda41a66ecc90c2b1a9`). All 24 FI/SV variants remain DEMO, package v4. The review ledger and dated 6 October material-status snapshot are unchanged.

## Automated verification

The final complete gate passed:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\scripts\verify.ps1 -Python 'C:\Users\dimmu\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -Offline
```

- **107 JVM tests**, zero failures/errors. New checks cover equivalent material with different hashes, rejection of all relevant same-version edits even if a caller supplies the old hash, explicit/tied topic ordering, question ordering and unchanged question provenance after an envelope-label difference.
- **83 device tests**, zero failures/errors/skips on Android 16/API 36. Four new repository tests cover reformatted import without metadata/history/schedule changes, restore → original reimport → legitimate versioned update, rejection of unversioned topic/translation/fact/provenance/order changes after restore, and preservation/invalidation of pending previews as appropriate. One new UI test covers original-file selection after restoring both language histories, disabled replacement, activity recreation and cancellation.
- The corrected isolated import UI suite also passed independently: **five tests**. Existing backup, migration, locale, question, recap, vocabulary, learning and platform-accessibility tests remain passing in the complete run.
- **Six Python content-review regression tests**, passed; 24 demo fingerprints still match their ledger. Source hygiene and JSON/XML checks passed for **66 files**, with **244 matching FI/EN resources** and format-argument parity.
- Final lint: zero errors, **two debug warnings and one release warning**, all existing issue types (`OldTargetApi`, plus `AndroidGradlePluginVersion` in debug). No new baseline or suppression.
- Debug APK, minified unsigned release APK and unsigned release AAB built successfully. No production signing, upload or publication occurred.

Two initial full runs each failed only the new recreation test. Its isolated screen was initially not reattached after MainActivity recreation; after fixing that, the test incorrectly waited for a lazy-list heading that had scrolled out of composition. The fixture now reattaches its screen, checks the retained preview and scrolls to the actual confirmation control. Production code required no change for these test failures. Failed reports are preserved as `.verification/v10-initial-device-tests.xml` and `v10-recreation-fixture-tests.xml`; the focused passing report is `v10-corrected-import-ui-tests.xml`, and the final full report is `v10-full-device-tests.xml`.

## Installed release and native documents

The exact final minified release was signed only with the local debug key as `.verification/release-v10-smoke.apk` and installed on the dedicated `emulator-5556`. No physical device or learner history was used.

1. Through OpenDocument, selected the real JSON backup exported during v0.9 verification, containing one correct Finnish and one incorrect Swedish answer. The preview showed package v4, 24 questions, FI 1/SV 1 and complete replacement. Explicit restoration succeeded. Screenshots: `.verification/v10-old-backup-preview.png`, `v10-old-backup-restored.png`.
2. Selected the original material through the real content picker. It showed 24 unchanged questions, no additions/revisions/removals and the new already-installed message. Replacement was disabled. Selected a compact reserialization and got the same result. Screenshots: `v10-original-material-confirmation-disabled.png`, `v10-reformatted-material-disabled.png`.
3. Inspected Android accessibility XML and actual button behavior. The clickable parent has `enabled=false`; its child TextView's `enabled=true` is not the control state. An initial native-harness assertion queried that child incorrectly. Checking the parent and tapping the disabled control confirmed no commit or success state. Screenshot: `v10-no-op-confirmation-cannot-write.png`.
4. Changed system text scale to 200%, causing ordinary recreation in the real app. The no-op preview remained, with wrapped text and reachable disabled-confirmation/cancel controls. Returned to 100%. Screenshots: `v10-no-op-preview-large-text.png`, `v10-no-op-large-text-controls.png`.
5. Selected a disposable topic-description edit at the same package version. It was rejected and package v4 remained installed. Selected the same edit with version 5, reviewed it, explicitly confirmed and got the success state. Screenshots: `v10-unversioned-edit-rejected.png`, `v10-valid-update-preview.png`, `v10-valid-update-success.png`. The altered description was a local QA fixture, not new bundled or published learning content.
6. Exported through CreateDocument again and pulled/read the actual file, retained locally as `.verification/v10-after-update-export.json`. Every original FI/SV answer ID, revision, language, selected option, correctness value and timestamp matched the v0.9 backup. Every question payload was unchanged; only the expected topic-description/package-version update was present. Screenshot: `v10-after-update-backup-saved.png`.
7. Progress showed Finnish mastery 2%, one practised question, one saved answer, 100% accuracy and zero due reviews. Backgrounded the app, killed its process with `am kill`, confirmed no PID remained, and reopened the task. PID changed from 14566 to 16176; the same Progress screen and values returned. Screenshots: `v10-progress-after-update.png`, `v10-progress-after-process-restart.png`.

No fatal app exception appeared in the inspected release smoke log, retained as `.verification/v10-release-runtime.log`. After checks, only the dedicated emulator's disposable fixture was cleared. Six QA-created Downloads files were removed by exact path; unrelated files were preserved. The final debug APK is installed and open on Home, with Finnish defaults, bundled package v4, zero history, normal phone dimensions/density and 100% text size. Screenshot: `.verification/v10-final-fresh-home.png`.

## Boundaries

There is no Git repository here. Review used saved before-change source files in `.verification/v10-before`, direct diffs, tests, archived APK/content comparisons and rendered native journeys. Changes are limited to material comparison/ordering, version/copy, meaningful regression tests and documentation.

Only the API 36 emulator was available. Physical devices, other Android versions and spoken TalkBack review remain unverified. Human civic/language review and learner sessions remain pending; their prepared checklists/scripts do not constitute completed reviews or findings. Backup files remain unencrypted user-controlled snapshots, and source/provenance labels do not authenticate them. Material mastery remains a heuristic, not a pass prediction. No official exam specification or completeness claim was added.
