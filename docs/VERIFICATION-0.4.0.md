# Version 0.4.0 verification — 6 October 2026

## Changes verified

- Settings now opens a Study material screen showing the installed package/version and topic/question counts.
- Android document selection reads a bounded UTF-8 JSON file without broad storage permission. Files over 2 MiB and invalid UTF-8 are rejected; a UTF-8 byte order mark is accepted.
- A read-only preview lists added, revised, unchanged and removed questions, explains effects on progress, and offers expandable answers/explanations/source references. Cancellation does not write data.
- Confirmation replaces the reviewed package atomically. Unchanged questions retain progress; revised/new questions start unpractised; removed questions leave learning/review. Historical attempts remain.
- Confirmation rejects a preview after an intervening content update. Answers recorded after preview remain saved. Concurrent confirmation commits once.
- A removed, previously answered question ID cannot reclaim old mastery at a historical revision. Shared IDs across packages must preserve payload/revision or advance the revision. An answer also checks its full question against current stored content.
- Imported provenance is explicitly a file assertion, not factual/authenticity/rights verification. Public labels for sourced/official material reflect that distinction.
- Imports currently accept Finnish questions only. Swedish selection remains pending. Both interface languages are supported.
- No database schema change, new dependency, network client or manifest permission was required.

## Automated checks

Passed using Android Studio JBR, the installed caches, and the dedicated Android 16/API 36 emulator:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug :app:lintDebug :app:lintRelease :app:assembleRelease :app:bundleRelease --offline --console=plain
.\scripts\verify-source.ps1
```

- **52 unit tests:** 28 learning-engine, 12 content-validation and 12 update/file-reading tests; zero failures/errors/skips. New coverage includes preview counts, topic-only updates, version/revision conflicts, historical ID reuse, cross-package shared IDs, exact installed files, byte limits, UTF-8 and BOM handling.
- **31 device tests:** 16 repository, four migration, three Finnish learning-flow, three localisation, four import-flow and one real-navigation test; zero failures/errors/skips. The navigation test covers both languages, screen recreation, opening the real Android document chooser and cancellation.
- Import-flow tests use the actual Room repository, ViewModel and Compose body. Document-provider selection is separately exercised through actual navigation and runtime file selection.
- Debug APK, minified/shrunk unsigned release APK and unsigned release app bundle builds passed.
- Lint: debug **zero errors / seven warnings**, release **zero errors / one warning**. Only existing dependency/target warnings remain; no suppression or baseline was added.
- Source hygiene/JSON/XML checks passed for 37 files. Finnish/English key and format-argument parity passed for 165 resources, including singular/plural topic counts.

The first rendered navigation check exposed a crash: the in-app locale context prevented the file picker from finding an ActivityResultRegistryOwner. The locale wrapper now preserves that owner before replacing LocalContext. A real-navigation regression test was added. The complete test suite and both build variants were rerun after the correction. Isolated screen tests alone would not have caught this integration failure.

Reports are in `app/build/reports/` and `app/build/outputs/androidTest-results/connected/debug/`. A copy of the final 31-test device report is retained in ignored `.verification/v04-full-device-tests.xml`.

## Runtime and rendered checks

- Phone (1080×2400, density 420), Finnish/light: opened material management through Settings and selected an actual JSON file from Downloads with the Android chooser.
- Before the update, answered both democracy questions correctly: two saved answers, two practised questions and 4% overall mastery.
- The test update advanced demo package 2→3, revised the first explanation/question revision, and changed a topic description. Preview showed zero additions, one revision, 11 unchanged questions and zero removals. The content remained clearly labelled DEMO.
- Cancelled that preview using its Cancel update button: current material still showed package version 2. Selected the file again and confirmed it: package version 3 appeared, both attempts remained, one current question was practised and overall mastery became 2%. The revised question did not inherit its former mastery.
- Inspected the Finnish picker/preview/change counts/progress explanation/cancel/confirmation layouts. Expandable question review is covered by the Compose import-flow tests.

- Minified release, English/dark: selected an actual topic-only update from Downloads (package 3→4). Preview showed 12 unchanged questions and zero revisions/additions/removals. Confirmation retained question progress and both historical attempts.
- Inspected the English release preview at phone size with 150% text and at tablet dimensions (1920×1200, density 240). Labels/paragraphs wrap, controls remain reachable by scrolling, and content width is bounded. The pending preview survived these configuration changes and could still be confirmed.
- Selected an invalid file through the real chooser in the release app. The translated error appeared and current material remained version 4.
- Force-stop/relaunch retained English, dark theme, package version 4, two answers, 100% lifetime accuracy, one practised current question and 2% overall mastery. No fatal AndroidRuntime exception appeared in this smoke run.
- Final code review found that a rejected stale preview could leave the old current-package label on screen. The ViewModel now reloads installed metadata after rejecting the preview. A fourth import-flow test verifies the updated version label; the full 31-test device suite, unit tests, lint and both release artifacts passed after this correction.
- Inspected the final app bundle configuration: language splitting is disabled, and no distribution signature is present.
- Rebuilt and debug-key signed the final minified smoke APK after the stale-label correction. A fresh launch selected English, imported the actual package-version-3 file, and retained English and version 3 after force-stop/relaunch. No fatal AndroidRuntime exception appeared.
- Restored the final debug build, default phone dimensions/text size and a fresh Finnish demo launch. Only dedicated emulator verification data was reset.

Screenshots remain in ignored `.verification/`, including `v04-material-finnish-phone.png`, `v04-preview-changes-phone.png`, `v04-after-file-import.png`, `v04-release-preview-controls-large-phone.png`, `v04-release-preview-tablet.png`, `v04-release-invalid-file.png`, `v04-release-restarted-progress.png`, and `v04-final-release-restarted-material.png`.

## Remaining limits

- All bundled content remains DEMO/provisional. Imported labels cannot establish official origin, accuracy or reuse rights. No trusted publisher catalogue, signed content verification or remote updater is included.
- Previews survive activity recreation through a ViewModel but are not persisted through process death; choose the file again. No kill-during-import/write or storage fault-injection test was added.
- Only Finnish study imports are offered. Swedish study selection, learner explanation translations and sound remain pending.
- No physical-device, API 26/API 37 or completed TalkBack verification in this increment.
- Revising an explanation/source/translation resets the question's mastery under the existing conservative revision policy. A later distinction between factual corrections and supporting-text edits may be useful.
- No production signing, store upload, backend, account, analytics, subscription or payment work. Temporary debug-key signing is used only for a separate minified smoke APK.
- No Git checkout exists here; source, tests, reports and rendered screens were reviewed directly.
