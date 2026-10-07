# Version 0.5.0 verification — 6 October 2026

## Implemented increment

- Independent persisted Finnish/Swedish study selection alongside the existing Finnish/English interface selection.
- Twelve demo questions in each study language. Swedish questions have separate IDs and Swedish explanations/source references. Finnish payloads/revisions were compared with the archived v0.4 APK and all twelve remain identical.
- Home, topics, review, mastery, accuracy, answer counts and streaks are scoped to the selected study language. No automatic language fallback. Historical attempts retain their language after content removal.
- Active sessions retain their initial language through preference changes and activity recreation. Swedish practice shows Swedish vocabulary with Finnish/English meanings; easier Finnish explanations are restricted to Finnish questions.
- FI/SV/mixed file imports, preview/current language counts, and safeguards against changing an ID's language. Import history protects removed IDs as well.
- Room schema 4 and migration from schema 3. Previous supported Finnish attempts receive `fi`. Still-installed internal Swedish questions are identified from their JSON payload and their attempts receive `sv`; this does not depend on optional SQLite JSON extensions.
- Bundled startup preserves imported material when package versions collide or question revisions cannot safely be replaced.

## Automated checks

The final complete command succeeded using Android Studio's bundled JBR:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug :app:lintDebug :app:lintRelease :app:assembleRelease :app:bundleRelease --offline --console=plain
.\scripts\verify-source.ps1
```

- **60 unit tests**, zero failures: 28 learning-rule tests, 12 content-validation tests, 14 content-update tests, six language-scoping tests.
- **39 device tests**, zero failures on Android 16/API 36 `emulator-5556`: 18 repository tests, six actual old-schema migration tests, three Finnish learning flows, three English locale flows, four import flows, one native document-picker navigation test, four Swedish language/session flows.
- Legacy schema 1/2/3 fixtures migrated using the registered production migrations. History, scores, schedules and imported packages were retained.
- Swedish tests cover questions/feedback, review isolation, accuracy/mastery isolation, removed/revised history, preference/session restoration, Finnish-only imports and missing-language screens. Repository reopen verifies saved Swedish attempt language. A same-version bundled/imported collision test verifies safe startup.
- Source checks: 39 Kotlin/XML/JSON source files; 171 matching Finnish/English resource entries, matching format arguments and plurals.
- Debug lint: zero errors, seven existing toolchain/dependency warnings. Release lint: zero errors, one existing target-SDK warning. No lint suppression or baseline was added.
- Debug APK, minified unsigned release APK and unsigned AAB built. Actual bundle configuration confirms that language splitting is disabled, keeping both interface languages available offline. The distribution AAB has no signing metadata.

The final full device report is retained in ignored scratch output `.verification/v05-full-device-tests.xml`. Early runs exposed stale aggregate count expectations and a test attempting to toggle a partially visible setting; the tests were corrected to scroll to the setting and wait for its persisted value before recreation. All final tests pass.

## Rendered release checks

The minified release APK was signed **only with the local debug key** as `.verification/release-v05-smoke.apk` for emulator verification. This is not a production-signed release.

- Fresh Finnish interface, light theme, Finnish study: zero actual attempts/mastery and twelve available questions. The main study action remains visible on the phone.
- Selected Swedish in the real settings UI, completed both democracy questions, inspected Swedish explanations, vocabulary and source references. Easier Finnish explanations were absent.
- Switched to the English interface and dark theme; turned off easier Finnish explanations. Force-stopped/restarted the app. Swedish study, English menus, dark theme, the preference, two attempts, 100% accuracy and 4% mastery remained. Switching to Finnish showed zero answers/mastery and no accuracy value.
- Home's study-language shortcut opened settings. Checked long Swedish working-life questions and answer controls at 150% system text scaling on a 1080×2400 phone and a 1920×1200, density-240 landscape tablet. Layouts remained scrollable and the tablet content width stayed bounded.
- Selected a Swedish-only JSON package from the real Android Downloads picker. Preview showed twelve unchanged Swedish questions, twelve removed Finnish questions, and per-language counts before confirmation. Committed the update and force-stopped/restarted: the imported `sv-demo-local` package remained, with two Swedish answers and 4% mastery. Selecting Finnish then showed the missing-language explanation and a settings action, without a study CTA or a mastery estimate. Screenshots are retained under `.verification/v05-release-*.png`.

After verification, the test fixture was cleared only from the dedicated emulator app. The final debug APK is installed and running with fresh Finnish interface/study defaults, light appearance at normal phone size/text scaling, and zero fabricated history. No fatal app exception appeared in the final inspected log window.

## Practical limits and decisions

Only the API 36 emulator was used. Physical devices, API 26/API 37 runtime behavior, a full TalkBack traversal, mixed-language speech pronunciation, in-flight process death and storage fault injection remain unverified.

All 24 questions are **DEMO**, covering twelve concepts in two languages. Factual/terminology source checks do not constitute editorial approval or official exam coverage. Swedish wording and bilingual vocabulary need human content review. There is no Swedish interface, translated-explanation selector, audio, remote content catalogue, backup/export, backend, analytics or production release signing.

Progress is deliberately independent per study language. It measures practice with that language's questions, not a transferable civic-knowledge score or a validated chance of passing an exam. A future concept-level learning model may connect variants, but should not silently transfer language mastery. The existing reset-on-any-question-revision rule remains conservative, including small explanation edits.
