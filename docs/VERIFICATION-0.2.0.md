# Version 0.2.0 verification — 6 October 2026

## Changes verified

- A correct first answer or due review earns mastery. Early correct practice records history/accuracy without increasing mastery, advancing the qualified-success count, or moving the review date.
- Incorrect answers still reduce mastery and schedule a review in ten minutes. An immediate correction preserves that review.
- Home prioritises due review, then new questions. When all current questions have been practised, it displays the next review time and offers topic practice.
- Recommended short lessons contain at most five due/new questions, without early-repeat filler.
- Feedback explains extra practice and displays the next review date. Feedback and date survive activity recreation.
- Room schema 1→2 adds a learning-rule version marker. The repository transactionally replays existing attempts once to rebuild derived progress; attempts, content and settings remain intact.

## Automated checks

Passed with Android Studio JBR and the installed Gradle/Android caches:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest :app:lintDebug :app:lintRelease :app:assembleRelease --offline --console=plain
```

- **36 unit tests:** 28 learning-engine and eight content-validation tests, zero failures. New coverage includes immediate repeats, exact due boundaries, mistakes/corrections, stable history replay, obsolete revisions, recommendation priorities, short-lesson limits and up-to-date material.
- **14 device tests:** nine repository, two migration and three learning-flow tests, zero failures/errors/skips, on the Android 16/API 36 phone emulator.
- Migration fixtures are actual schema-v1 SQLite databases built from the exported v1 schema. Both bundled and independently imported packages retain history/content and recalculate four immediate correct answers from 100 to 25 points. A later due review reaches 50; reinitialisation preserves the updated progress.
- Retry tests return original attempt feedback even after later answers; repeated/concurrent tokens record once.
- Debug and minified/shrunk release APK builds passed. Distribution release remains unsigned.
- Lint: debug **zero errors / seven warnings**; release **zero errors / one warning**. Existing dependency-update and target-SDK warnings remain; no suppression/baseline was added.
- Source hygiene and JSON/XML parsing passed for 28 source files.

After shortening the feedback copy during visual review, both APKs were rebuilt and both lint tasks passed again. The three learning-flow tests were rerun against the final debug APK at tablet dimensions: **three passed**. Domain/data logic was unchanged by this copy edit.

Reports: `app/build/reports/` and `app/build/outputs/androidTest-results/connected/debug/`. Exported schemas: `app/schemas/fi.kansalaisuuskoe.data.LearningDatabase/1.json` and `2.json`.

## Runtime and rendered checks

Dedicated emulator `emulator-5556`, Android 16/API 36:

- Installed v0.2.0 over the existing v0.1.0 APK after recording one answer. Home retained that answer, one practised question and 2% overall mastery; no app-data reset was used for this upgrade check.
- Phone (1080×2400, density 420): inspected early-practice feedback and the all-material-practised home state, including the next review time and topic action.
- Phone with 150% text size: inspected the feedback in light theme and the final shorter copy in dark theme. Text wraps and scrolls without clipping.
- Tablet (1920×1200, density 240): inspected the all-material-practised home state; all three learning-flow tests passed, including navigation, review, progress, settings, recreation and early-practice feedback.
- Minified release: temporarily signed a separate smoke APK using the standard debug key. It opened existing content/progress/settings, displayed early-practice feedback, saved an additional answer, retained the review date, and persisted six answers / 67% accuracy / 6% overall mastery after force-stop/relaunch. No fatal AndroidRuntime exception appeared during this smoke check.
- The release feedback/date/vocabulary/source/continue action were inspected at tablet width in dark theme. The final feedback was also inspected at phone size with 150% text.
- Returned the emulator to default phone dimensions and text size, restored the final debug build and reset only its dedicated verification data for a fresh demo launch.

Screenshots/readbacks remain in ignored `.verification/`, including `upgrade-ui.xml`, `v02-feedback-phone.png`, `v02-feedback-large.png`, `v02-home-complete-phone.png`, `v02-home-complete-tablet.png`, `v02-release-feedback-tablet.png`, `v02-release-feedback-large.png`, and `v02-release-progress.xml`.

## Remaining limits

- Content/categories are still DEMO/provisional. No claim about official exam format, pass requirements or validated readiness was added.
- The score remains a heuristic: guesses can earn initial points, overdue reviews do not reduce scores, and device-clock changes affect scheduling/history replay.
- No physical-device, API 26/API 37, completed TalkBack, or kill-during-write/import fault-injection verification in this increment.
- Finnish-only UI/study/explanations; Swedish study, learner translations and audio remain pending. Easier Finnish text has not been reviewed as selkokieli.
- No production signing, store upload, backend, account, analytics, subscription or payment work.
- No Git checkout exists in this workspace. Changed source, migration/schema, tests and generated reports were reviewed directly.
