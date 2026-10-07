# v0.15.0 — authored lessons and expanded provisional material

Verified on 7 October 2026. This increment creates a local Git baseline, adds independently authored reading material, and expands every provisional topic. It does not claim official exam coverage or human publication approval.

## Delivered

- Local `main` repository; baseline commit `fa75cac` records v0.14. Build outputs, device/verification files, SDK paths and keystores remain ignored. No remote is configured.
- Package v5: 30 Finnish and 30 Swedish questions, five per topic/language. The original 24 question payloads, IDs and revisions are unchanged. The 18 new concept pairs have balanced correct-answer positions across their three alternatives.
- Six standalone three-section lessons in each study language, six optional English supports, easier Finnish text, vocabulary from the question bank, and source references. All question and lesson statuses remain DEMO.
- Lesson IDs/revisions are independent of question revisions. Native lesson changes require a higher lesson revision and package version; English-only support edits need a package version. Reading never writes attempts, scores or review dates.
- Room migration 5→6 adds a lesson JSON column with an empty-map default. Imports, restart and backups preserve lesson variants. Imported packages without a matching native lesson retain explanation-based reading.
- Import previews count changed lesson language versions and expose their text, easy explanations, provenance and sources before confirmation. Assessment topic snapshots omit unrelated reading lessons to avoid copying a course into every saved result.
- The source ledger fingerprints 60 questions and 18 lesson variants, with evidence for every section/source. Migri's dated offline status was refreshed to 7 October. Content and specialist language review remain deferred.

## Automated checks

Full `scripts/verify.ps1 -Offline` checks domain tests, content gates, resource/source hygiene, both lint variants, debug/minified release assembly, unsigned release bundle and connected Android tests.

- 153 JVM tests; 133 emulator tests; nine Python content-gate tests. No failures, errors or skips in the passing full run.
- 87 source/test/script files passed hygiene checks; 301 Finnish/English resources have matching keys and format arguments.
- Lint: zero errors. Existing warnings are `OldTargetApi` (debug/release) and `AndroidGradlePluginVersion` (debug).
- The correct v5 asset is byte-identical in the debug APK, release APK and AAB. Old regression content and verification files are absent from application artifacts.
- Source asset SHA-256: `01de75816e60b9e27814ed1598bc59458bd4836373d1fc07615bfca76567aaab`.

New domain/device checks cover lesson validation, native-language selection, conservative provenance, aligned translations, independent revisions, lesson-change preview counts, old-style fallback, full five-question topic completion, optional explanations, recreation, actual v4→v5 expansion, Room 5→6 migration with saved assessment data, lesson import/backup reopening, stale previews and transaction rollback/retry on storage failure.

Five existing UI recovery/recap suites use a fixed v0.14 content fixture under `androidTest/assets` to retain deterministic short journeys. New lesson flows and repository/import checks exercise the full expanded package. This fixture is test-only, not a production data shortcut.

The initial broader run exposed an old import assertion expecting 22 unchanged questions; the correct expanded count is 58. That assertion and its denominator-dependent aggregate were corrected before the passing gate. Native inspection also prompted smaller supporting headings and a source note suitable for both lessons and questions.

## Runtime evidence

The dedicated `Citizenship_MVP` emulator uses Android 16/API 36. The minified release APK is signed only with the local debug key for installation; this is not distribution signing. Automated run output is retained in `.verification/v15-full-device-tests.xml`. Native screenshots and inspection notes are retained under `.verification/v15-*`.

Native inspection covered the Finnish authored lesson with easier Finnish and sources, Swedish reading with optional English support, 200% system text size, and a 1920×1200 landscape tablet layout at density 240. Reading text wraps and scrolls, supporting headings remain subordinate to section titles, and practice/vocabulary controls remain accessible. The tablet keeps a bounded reading width. Switching screen size preserved the current reading position; a partially visible item at the viewport edge was scrollable rather than clipped by a fixed-height container.

Local evidence: `v15-final-finnish-reading.png`, `v15-final-large-reading.png`, `v15-final-tablet-reading.png`, and `v15-final-swedish-reading.png`. Inspection also corrected the settings description to explain that English support appears alongside reading material as well as after answers.

After the final full gate passed, the rebuilt minified release was installed again. A native click-through changed study language to Swedish, opened the democracy lesson, started its five-question practice, selected Riksdagen, and verified correct feedback, Swedish explanation, optional English support, vocabulary, source and the scheduled review time. Evidence: `v15-final-swedish-question.png` and `v15-final-swedish-feedback.png`. The filtered AndroidRuntime error log contained no crash. Disposable QA data was cleared and the app was left open on its Finnish home screen at normal phone size/text scale; `v15-final-home.png` shows 0/30 practised questions.

## Limits

No human civic/language/easy-language review, learner study or official topic mapping was performed. Physical devices, other Android versions and spoken screen-reader review of the new reading content remain unverified. Reading is deliberately unscored; vocabulary still comes from questions. Mastery is a demo heuristic and may fall when the material gains unseen questions even though existing question scores/dates survive. It does not predict passing the citizenship test.

Backups with authored lessons require v0.15 or newer; earlier backups remain readable. Source labels in imported files remain assertions. No remote delivery, accounts, analytics, ads, AI, payments or publishing was added.
