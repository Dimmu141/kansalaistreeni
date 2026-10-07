# MVP verification — 6 October 2026

Historical record for version 0.1.0. See [version 0.2.0 verification](VERIFICATION-0.2.0.md) for the current learning rules, migration and checks. Immediate-repeat score inflation described below was addressed in version 0.2.0.

## Toolchain and artifacts

- Windows / Android Studio JBR, Gradle 9.7.1, AGP 9.4.0, Kotlin 2.4.10.
- Compile SDK 37.0, target SDK 36, minimum SDK 26.
- Installable debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Minified/shrunk unsigned release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`.
- Release smoke artifact under ignored `.verification/` was temporarily signed using the standard Android debug key, only for local testing. No store signing or publication occurred.

## Automated checks

Final Gradle command passed:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:connectedDebugAndroidTest --offline --console=plain
```

- 18 learning-engine unit tests: answer validation, score bounds, counters, review intervals, incorrect-answer reset, revisions, accuracy, empty statistics, question weighting, due boundaries, queue ordering and local-calendar streaks.
- 8 content unit tests: valid demo dataset, variable option counts, duplicate IDs, topic references, answer references, demo provenance, schema and translation/source validation.
- 7 Room/repository device tests: reopen persistence, duplicate/concurrent saves, revision updates/history, rejected imports, required revisions, invalid answers, and protection against overwriting an imported package with the bundled demo.
- 1 full learning-flow device test: navigation, correct/incorrect feedback, next-question visibility, activity recreation without duplicate recording, completed lesson, due review, progress and persisted settings/easy-explanation visibility.
- Debug lint: **0 errors, 7 warnings**. Release lint: **0 errors, 1 warning**. Warnings identify newer stable patch versions of Gradle/Core/Navigation/Room and target SDK 36 rather than the installed SDK 37. No lint baseline or blanket suppression is used.
- Source hygiene and XML/JSON parsing check passed via `scripts/verify-source.ps1`. This checks tabs, trailing whitespace and final newlines; it is not a full Kotlin formatter.

Reports are under `app/build/reports/` and `app/build/outputs/androidTest-results/connected/debug/`.

## Rendered and runtime checks

Dedicated `Citizenship_MVP` Android 16/API 36 emulator, created within the ignored verification workspace:

- Phone: 1080 × 2400, density 420. Inspected home, question and feedback; exercised all screens through the full-flow test.
- Large text: phone font scale 1.5. Home hierarchy, primary action and bottom navigation remained legible; content scrolls.
- Wide tablet: 1920 × 1200, density 240. Inspected home, topics, question, answer explanations/vocabulary/source, review, progress and settings. Content is bounded to 760 dp rather than stretched across the whole display.
- Dark theme: checked on a phone after force-stop/relaunch. System-bar icons, navigation, text, controls and mastery remain visible.
- Minified release: launched, parsed/imported content, answered a question, saved progress, rendered topics/progress/review/settings and persisted the dark-theme preference. AndroidRuntime showed no fatal exception.
- Background process kill: confirmed the old PID disappeared; after restarting and returning to the existing task, answered-question feedback was restored. Stored progress remained one answer, one practised question, 100% answer accuracy, and 2% material mastery.
- Fixed a discovered question-flow issue: feedback is brought into view after checking an answer, and a new question starts at the top instead of retaining the explanation's scroll position. The full-flow test asserts the next question is displayed.
- Final emulator was returned to phone dimensions/default text size and a clean demo installation.

Local screenshots and UI readbacks are retained under ignored `.verification/`, including `home-phone.png`, `home-large-text.png`, `question-phone.png`, `feedback-phone.png`, `home-tablet.png`, `question-tablet.png`, `topics-tablet.png`, `review-tablet.png`, `progress-tablet.png`, `settings-tablet.png` and `home-dark-phone.png`.

## Limits

- No physical-device, API 26, or API 37 runtime testing yet.
- No completed TalkBack or hardware-keyboard traversal audit; the UI uses semantic headings, radio groups, labelled controls and textual answer feedback.
- No kill-during-write or mid-import fault injection; duplicate/concurrent save and rejected-import tests passed.
- No validation of actual exam readiness. Immediate repeat answers can inflate the experimental mastery score.
- Finnish-only UI, questions and explanations. English/Swedish vocabulary exists; Swedish study/learner translations/audio remain visibly pending.
- Easy Finnish text is provisional and has not been reviewed as selkokieli.
- Content source/provenance review remains an editorial task, even when JSON validation succeeds.
- This workspace began empty and has no Git checkout; new source files were reviewed directly rather than through a Git diff.
