# Version 0.3.0 verification — 6 October 2026

## Changes verified

- Finnish and English interface resources, including navigation, feedback, errors, accessibility labels, plurals and review dates. Finnish remains the default.
- The interface choice persists in DataStore. A scoped Compose resource context applies it without recreating the activity or replacing the active lesson.
- Questions and explanations remain Finnish. English guidance explicitly identifies the study language; Swedish study and translated explanations remain pending.
- Six topic titles/descriptions have content-backed English translations with original-text fallback. Demo package version 2 leaves question IDs, revisions and factual text unchanged.
- Room schema 3 adds topic translations. Non-destructive migrations from schemas 1 and 2 retain content/history; schema 1 also receives the earlier learning-rule replay.
- Both interface languages are bundled together for offline availability. No network permission or new dependency was added.

## Automated checks

Passed with Android Studio JBR and the installed Gradle/Android caches:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:bundleRelease :app:assembleDebugAndroidTest --offline --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest --offline --console=plain
.\scripts\verify-source.ps1
```

- **40 unit tests:** 28 learning-engine and 12 content-validation tests, zero failures. Added translation mapping/fallback and invalid translation validation coverage.
- **20 device tests:** ten repository, four migration, three Finnish learning-flow and three localisation tests, zero failures/errors/skips, on Android 16/API 36.
- Migration fixtures use real old SQLite schemas. Schema 1 and schema 2 upgrades retain bundled or independently imported content and attempts. Topic-only updates preserve question progress; translations persist after reopening the database.
- Localisation tests cover the English learning journey, feedback/vocabulary, review/progress, activity recreation, saved settings, returning to Finnish, language changes during feedback without duplicate recording, resource plurals and locale-specific dates.
- Debug APK, minified/shrunk release APK and release app bundle builds passed. Distribution release artifacts remain **unsigned**.
- Lint: debug **zero errors / seven warnings**; release **zero errors / one warning**. Existing dependency-update/target-SDK warnings remain. No suppression or baseline was added.
- Source hygiene and JSON/XML parsing passed for 31 source files. Finnish/English resource keys match across 125 entries, with matching formatting arguments and plural quantities.
- The generated app bundle's `BundleConfig.pb` was inspected: language splitting is disabled. Its signature entries were also checked; the distribution bundle is unsigned.

After plural and bundle-packaging corrections, all builds, unit tests and lint checks passed again. The three localisation tests were rerun on the final phone build. The three Finnish learning-flow and three localisation tests were then rerun at tablet dimensions: **six passed**. Domain and repository logic did not change in those final resource/packaging corrections.

Reports are in `app/build/reports/` and `app/build/outputs/androidTest-results/connected/debug/`. Exported Room schemas are in `app/schemas/fi.kansalaisuuskoe.data.LearningDatabase/`.

## Runtime and rendered checks

Dedicated emulator `emulator-5556`, Android 16/API 36:

- Installed v0.3.0 over v0.2.0 after recording an answer. The new app retained that answer, one practised question and 2% overall mastery. No app-data reset was used for this upgrade check.
- Selected English in the debug app, force-stopped and relaunched it. English and saved progress survived.
- Phone (1080×2400, density 420): inspected English settings, Home and Topics in light theme.
- Tablet (1920×1200, density 240): inspected English Topics and ran all six learning-flow/localisation tests.
- Minified release: temporarily signed a separate smoke APK with the standard debug key. Inspected English settings, Finnish questions with English guidance, and feedback with vocabulary/source/review date in dark theme at tablet width.
- Phone with 150% text size: inspected English release feedback, Home and Progress. Text wraps and scrolls; the next action remains accessible by scrolling through feedback.
- The minified release saved another correct answer. Force-stop/relaunch retained English, dark theme, two answers, 100% accuracy, two practised questions and 4% overall mastery. No fatal AndroidRuntime exception appeared during the smoke check.
- Restored default phone dimensions/text size and the final debug build. Reset only this dedicated emulator's verification app data for a fresh Finnish demo launch.

Screenshots and UI readbacks remain in ignored `.verification/`, including `v03-after-upgrade.xml`, `v03-settings-english-phone.png`, `v03-home-english-phone.png`, `v03-topics-english-tablet.png`, `v03-feedback-english-tablet.png`, `v03-feedback-english-large-phone.png`, and `v03-release-restarted-progress-large-phone.png`.

## Remaining limits

- All questions/categories remain DEMO/provisional. No official exam specification, pass threshold or validated readiness prediction was added.
- Study questions/explanations remain Finnish. Swedish content, learner explanation translations and sound remain pending; easier Finnish text is not reviewed as selkokieli.
- Language selection is in-app; Android's system per-app language picker is not integrated.
- No physical-device, API 26/API 37, completed TalkBack or kill-during-write/import fault-injection verification in this increment.
- Mastery remains a transparent heuristic. Initial guesses, overdue-score decay and device-clock changes still need product evaluation.
- No production signing, store upload, backend, account, analytics, subscription or payment work.
- No Git checkout exists here. Changed source, schemas, tests and reports were reviewed directly.
