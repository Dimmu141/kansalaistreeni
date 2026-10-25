# Kansalaisuuskoe — Android learning MVP

A native, offline Android app for practising Finnish civic knowledge. Version **0.19.0** is a working MVP, **not an official citizenship-test preparation course**, and nothing in it is presented as the final format. Eight provisional topics contain 200 Finnish and 200 Swedish EDITORIAL practice questions (25 per topic per language, about 30 % true/false), each mapped to learning objectives, with a three- to five-section lesson per topic in Finnish, Swedish and English. The material is written for the app and checked by the assistant against cited official pages; human publication approval is pending and nothing is labelled OFFICIAL. No exam format, pass threshold, time limit, or predicted pass probability is asserted.

## Run

Open this folder in Android Studio and run the `app` configuration on an Android 8.0/API 26 or newer device or emulator.

The project uses the installed stable toolchain: AGP 9.4.0, Kotlin/Compose compiler 2.4.10, Gradle 9.7.1, compile SDK 37.0, target SDK 36, Java 17 bytecode. Android Studio's bundled JDK is suitable. Dependencies use stable versions available on this workstation, including Compose BOM 2026.08.00, Navigation 2.10.0, Room 2.8.4 and DataStore 1.2.1. AGP supplies Kotlin support; do not add the legacy `org.jetbrains.kotlin.android` plugin.

On this Windows workstation:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r .\app\build\outputs\apk\debug\app-debug.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n fi.kansalaistreeni/fi.kansalaisuuskoe.MainActivity
```

`local.properties` contains this machine's SDK path and is ignored. On another machine let Android Studio generate it. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Release assembly produces a minified APK and `:app:bundleRelease` an app bundle. They are signed with your Play upload key when a git-ignored `keystore.properties` (see `keystore.properties.example`) or `KANSALAISTREENI_KEYSTORE_PROPERTIES` is present, and unsigned otherwise. All three interface languages (Finnish, Swedish, English) are bundled together for offline availability. Play publication is prepared for a closed test; see `play/release-checklist.md`. The app is published as **Kansalaistreeni** with application ID `fi.kansalaistreeni`; the Kotlin namespace stays `fi.kansalaisuuskoe`. `python scripts/generate-icon.py` regenerates the launcher icon and `play/icon-512.png`.

## What works

- **First run:** a one-time screen states that the material is app-authored practice, not official test material, and that the app is not a government service. The same notice stays in Settings → About. Elsewhere top-level screens show only a small "Ei virallista materiaalia" chip in the app bar. The chip is shown whatever label the installed material carries, because an imported or restored file can label itself OFFICIAL without any verification.
- **Home:** a heading, the mastery card (percentage, bar and "x / y harjoiteltu"), one primary action and one secondary action (practice assessment). The primary action starts a balanced session of up to five due reviews and new questions from weaker and unexplored topics, with one line explaining the mix. A compact FI/SV switch in the app bar changes the study language.
- **Topics:** eight provisional topics — democracy, rights, equality, laws, history, culture, safety and digital services — listed first, with question counts, studied counts and topic mastery. Vocabulary is one row at the end of the list. Opening a topic shows its authored lesson with dated sources (or question explanations when an imported package has no lesson) before optional practice. Topic names have English and Swedish translations in the content package.
- **Question formats:** single-choice questions with four options and true/false statements answered with two large buttons labelled in the interface language (Oikein/Väärin, Rätt/Fel, True/False). Options show a radio button without numbering.
- **Learning objectives and coverage:** 55 objectives across the eight topics; every question references at least one. Settings → Study material → Objective coverage lists question counts per objective and language and highlights objectives with fewer than three questions (none in the bundled material). `docs/CONTENT-COVERAGE.md` is generated from the same layer.
- **Sources and review dates:** every question and lesson section cites an HTTPS official source in its own language (Swedish pages for Swedish items) with the date it was checked. Facts that can change carry `validFrom`/`reviewBy` dates; after `reviewBy` the source line warns that the fact is due for review.
- **Vocabulary:** search native terms, English meanings, the alternate FI/SV term or explanation text; filter by topic; expand an entry for its explanations and sources. Missing Swedish vocabulary produces an honest empty state.
- **Word practice:** think of a word's meaning, then reveal its English meaning and alternate FI/SV term. Unscored; no writes to attempts, mastery or review dates. State survives activity recreation.
- **Practice:** a short lesson, a full topic lesson or the due review queue. Check an answer explicitly, then read the explanation, optional easier Finnish, optional English support, vocabulary and source. Review times use relative wording ("myöhemmin tänään", "huomenna", "3 päivän päästä").
- **Lesson recap:** results, earliest scheduled review and missed questions with explanations. Reading a recap changes nothing.
- **Practice assessment:** mixed-topic self-checks in the selected study language. Length, format mix, pass mark and time limit come from a single `ExamProfile`; the app uses the "unknown" profile (20 questions, about 30 % true/false, no pass mark, no time limit), so results show only correct/total and topic results — no pass/fail verdict and no timer. Answers save automatically and can be changed until finishing. Scores never affect mastery, accuracy, streaks or review dates. See [Practice assessments](docs/ASSESSMENTS.md).
- **Review:** scheduled questions, an empty state and the next review time.
- **Progress:** overall and topic mastery, lifetime accuracy, due count and calendar-day streak. The strongest/weakest topic comparison appears once at least two topics have practised questions. Progress bars read as empty at 0 %.
- **Settings:** interface language Finnish, Swedish or English; study language Finnish or Swedish; theme; easier Finnish; optional English support; study material import and coverage; backup and restore; About. Without an explicit interface choice, Swedish study selects the Swedish interface. Sound remains pending.
- **Learning backup:** export and restore all material, FI/SV answer history and practice assessments as a reviewed, all-or-nothing replacement. See [BACKUP-RESTORE.md](docs/BACKUP-RESTORE.md).
- **Upgrades:** v0.17 rewrote the original 30 concepts under new revisions. Answer history, attempts, saved assessments and backups survive the upgrade; current mastery of a rewritten question starts again, as for any revised question. Moving questions between topics or objectives keeps their progress.

Every statistic is scoped to the selected study language; Finnish and Swedish scores do not transfer. A package missing the selected language shows an honest empty state.

Learning data, assessments and settings are stored on the device. The manifest requests no internet permission; there are no accounts, network clients, ads, analytics, payments or AI. Android automatic backup remains excluded; learners can export a local backup file, which is unencrypted and contains answer history. Source URLs are selectable text, not external-browser actions.

## Architecture

The [architecture review](docs/ARCHITECTURE-REVIEW.md) records the 8 October 2026 audit and corrections to snapshot consistency and topic routing. `scripts/verify.ps1` includes the source-level architecture contracts and runtime regressions. `scripts/run-architecture-probes.ps1` runs the focused regression classes; they are permanent tests and must pass.

One `:app` module, with practical package boundaries and manual dependency injection through `AppContainer`:

```text
Compose screens → ViewModels + StateFlow → repositories
                                      ↘ Room: content, attempts, progress
                                      ↘ DataStore: preferences
                    pure Kotlin learning rules and models
```

- `domain/Models.kt`: question/options, difficulty, provenance status, translations, vocabulary, topics, attempts and progress. Topics have optional translated names/descriptions and versioned, source-backed localized lessons. Domain models have no Android or database dependencies.
- `domain/LearningEngine.kt`: answer validation, mastery, review scheduling, learning priorities, weighted progress, accuracy and local-calendar streaks. Time is passed into rules for deterministic testing. Java time and Unicode normalization APIs would need equivalents for an eventual Kotlin Multiplatform migration.
- `domain/LearningContent.kt`: selects authored topic lessons by study language, with an explanation-based fallback, and derives deduplicated vocabulary with original question contexts from the installed content. Search normalizes case and equivalent Unicode and applies all query terms within the selected topic. Authored reading material is separate from question explanations, with no seed-specific lookup.
- `domain/SessionRecap.kt`: resolves only a lesson's saved attempt IDs, preserving order, excluding other-language/history items and withholding explanations for incompatible revisions. It has no Android dependencies or persistence writes.
- `data/Content.kt`: Moshi-generated JSON DTOs, domain mapping and validation. `data/ContentUpdate.kt` handles bounded UTF-8 file reading and pure update validation/preview counts. Same-version compatibility compares complete persisted topic/question payloads, so formatting differences and backup reserialization cannot turn unchanged material into a rejected update.
- `data/LearningDatabase.kt`: Room entities and DAO. Flexible question payloads are stored as JSON; attempts and progress use separate tables. Schemas v1–v6 and non-destructive migrations are included. Schema v4 records the language of each attempt; old supported Finnish history receives `fi`, and still-installed internal Swedish questions are recovered from their payload. Schema v6 adds a JSON lesson column without deleting history or assessments. A separate table records the learning-rule version.
- `data/LearningRepository.kt`: transactionally imported content, coherent reactive snapshots captured in one read-only (deferred) transaction on the reader connection, and atomic/idempotent answer recording. Attempt tokens prevent double recording after retry or recreation and return original feedback. A rule update replays retained attempts transactionally to rebuild derived progress once.
- `data/LearningBackup.kt`: versioned Moshi backup DTOs, bounded strict UTF-8 reading and structural/content/history validation. It imports no derived scores. Repository export takes a consistent snapshot; restore previews capture a content/history/assessment fingerprint and commit only against that baseline.
- `domain/Assessment.kt`: pure topic-balanced queue selection, answer editing, completion validation, assessment accuracy/topic results and compatible mistake selection. Results require completion; the model has no mastery writes.
- `data/AssessmentRepository.kt`: Room-backed assessment snapshots, atomic answer/position updates, separate FI/SV resumable sessions, idempotent completion and persistent results. Schema v5 adds a separate assessments table without rewriting existing learning history.
- `ui/AssessmentViewModel.kt` and `AssessmentScreen.kt`: saved-session list, deferred-feedback question flow, results, explanations and onward practice. Snapshot language remains pinned when settings change.
- `data/SettingsRepository.kt`: typed DataStore preferences, including independent saved interface, study and supporting-explanation preferences.
- `ui/AppViewModel.kt`: loading/error handling and reactive dashboard state; `LearningSnapshot.forLanguage` scopes questions, topics, progress and historical attempts before applying learning rules. Time refreshes every 30 seconds.
- `ui/ContentImportViewModel.kt` and `ContentImportScreen.kt`: Android document selection, an in-memory review, explicit confirmation, error/retry/cancel states and current-package details. A stale preview is rejected inside the commit transaction.
- `ui/QuestionViewModel.kt`: stable session queue, study language, selected answer, position and feedback restored with `SavedStateHandle`. `PracticeRequest.kt` separates daily/review/assessment commands from topic IDs. Changing a preference cannot switch questions halfway through an existing session.
- `ui/TopicLessonScreen.kt`, `VocabularyScreen.kt`, `VocabularyViewModel.kt`, `WordPracticeScreen.kt` and `WordPracticeViewModel.kt`: reading, filtered search and unscored vocabulary recall. Saved state holds small inputs/IDs/revisions rather than entire question payloads.
- `ui/BackupViewModel.kt` and `BackupScreen.kt`: Storage Access Framework creation/selection, export success/error handling, read-only restore previews, explicit replacement, cancellation and storage retry. Preview survives activity recreation in its ViewModel; after process death the learner selects the file again.
- `ui/CitizenshipApp.kt`: Navigation Compose and persistent bottom destinations; lessons, vocabulary, word practice, assessments, settings and question practice have back navigation.
- `ui/Components.kt`, `Theme.kt`, and the screen files: reusable, scrollable Material 3 layouts, bounded content width, semantic headings/radio controls, text feedback and large touch targets.
- `ui/TopicLanguage.kt`: topic names follow the study language when it is Swedish (the content carries Swedish titles), otherwise the interface language. `ui/Diagnostics.kt` logs the cause of failures that the UI reports as short messages; it never logs answers or content.
- `ui/StudyText.kt`: language spans on questions, answers and explanations reach native accessibility text, including English support alongside Finnish/Swedish study text.
- `ui/SessionRecapScreen.kt`: the completion screen and the explanation component shared with question feedback. The onward action appears before optional long explanations.
- `ui/InterfaceLocale.kt`: applies the saved in-app language through a scoped configuration/resource context on API 26+, retaining navigation, active sessions and the activity-result registry needed by the document picker. It does not change the device language or use Android's system per-app language picker. `values/strings.xml` and `values-en/strings.xml` hold interface copy and plurals; `ReviewTime.kt` formats dates using those resources and the selected locale.

We deliberately avoid Hilt, multiple Gradle modules and generic use-case wrappers at this size. Repositories isolate persistence and JSON from the learning engine. Splitting a domain module later is straightforward if it becomes useful.

## Content imports and provenance

`app/src/main/assets/demo-content.json` is the source of the bundled dataset. A local file can be selected in **Settings → Study material**. The preview shows added/revised/translation-only/unchanged/removed questions, changed lesson language versions, and expandable lesson text/explanations/sources; confirmation replaces the full package in one transaction. Cancellation and invalid files leave current content intact. `LearningRepository.importContent(json)` remains the internal import API. No remote updater is included. See [Content authoring](docs/CONTENT-AUTHORING.md) for the format, update rules and a test workflow.

A package has `schemaVersion`, `packageId`, `version`, `status`, `topics`, and `questions`. Each question includes a stable ID, revision, topic ID, arbitrary list of options with stable IDs, correct option ID, explanation, optional easy explanation, vocabulary, HTTPS source title/URL, language, difficulty, tags, status, and optional translation map.

Topics may include `translations`, a map from language tag to `{ "title": "...", "description": "..." }`. Missing translations fall back to the original topic text. The bundled package is version 9 (content schema 2): 200 EDITORIAL questions per study language across eight topics and 55 objectives. Version 8 rewrites 17 true statements per language as false ones so that the true/false answer key is balanced (31 true, 32 false); those questions are new revisions, so their current mastery restarts. Version 9 rewrites the distractors of 59 single-choice concepts per language so that the correct option is no longer recognisable by length (longest option correct 26–29 %, length ratio 1.03–1.05×); correct answers are unchanged, but the options are practised content, so these questions are also new revisions. The original 30 concepts were rewritten under new revisions, so their current mastery restarts while answer history, attempts and saved assessments survive; questions moved to another topic or objective keep their progress. Overall mastery can fall because new questions count as unseen; that is a changed material denominator, not lost progress. Swedish variants retain separate `-sv` IDs and progress. Interface and supporting-explanation changes never change question IDs. Question translation maps support explanations after answering; the translated question/options are not offered as an independently practised language.

[Content review and delivery](docs/CONTENT-REVIEW.md) describes the dated source checks, question and lesson fingerprints, negative gate tests and human editorial handoff. Run `python scripts/verify-content.py` and `python scripts/test_content_review.py` alongside the Android checks. The content gate also checks the answer key per study language: 40–60 % of true/false statements must be true, and the correct single-choice option may not be recognisable by length. The longest option may be the correct one in at most 35 % of single-choice questions (chance is 25 %), and correct options may be at most 1.25× as long as their distractors on average. [Learning evaluation](docs/LEARNING-EVALUATION.md) explains the score invariants, counterexamples and missing empirical validation.

Provenance statuses have distinct meanings:

| Status | Meaning |
| --- | --- |
| `DEMO` | Sample content for development and exploration. |
| `EDITORIAL` | App-authored learning material. |
| `OFFICIALLY_SOURCED` | App-authored material checked against official sources; not an official exam question. |
| `OFFICIAL` | Actual official learning material, to be used only after origin, reuse rights and attribution have been reviewed. |

The importer checks schema, unique IDs, topic references, valid answer IDs, difficulty/status, languages and HTTPS sources. Translations must retain the original answer IDs. Parsing/validation happens before a single database transaction replaces the current package. A malformed update leaves existing content intact.

Increment the package version for an update. Increment a question revision whenever the practised content or facts change. A supporting-translation-only edit can keep the question revision, and the preview identifies it separately so progress is preserved. Package downgrades and changes without proper version/revision increases are rejected. Reselecting unchanged material at the installed package/version shows an already-installed preview, even if JSON formatting differs or a backup was restored. It writes no material, metadata or learning-history changes. Question order, option order and all persisted fields are compared; topic array order is normalized by its explicit display order. Question IDs are global across packages. Changed shared IDs require a newer revision; a removed, previously answered ID must return at a revision above its saved history. Revised questions start with zero current mastery, while historical attempts remain. Removed questions are excluded from mastery and due reviews. The bundled demo is imported on first launch and on compatible bundled updates; it cannot overwrite a newer or different imported package.

Source existence and structural validation are **not factual approval**. Status is an editorial assertion in the content file. Imported provenance labels are presented as file assertions. The user-facing import accepts Finnish, Swedish or mixed packages, reads at most 2 MiB of UTF-8 JSON, and stores no persistent file URI permission. Previews show counts by language. A question ID cannot change its language, even with a higher revision or after removal when its attempts remain. Previews survive activity recreation but must be reselected after process death. Do not label an app-authored question `OFFICIAL` merely because its source is a government document. Categories remain explicitly provisional.

Bundled startup updates preserve a user's imported demo variant when its version collides with the new bundled version or its question revisions cannot be safely replaced. Study material details show the package actually installed; startup does not force the new demo over a conflicting import.

## Learning rules and limitations

For each question, a correct **first answer or due review** adds 25 mastery points; an incorrect answer always subtracts 35, clamped to 0–100. Eligible consecutive correct answers schedule review after 1, 3, 7, then 14 days. Incorrect answers schedule review in 10 minutes and reset the consecutive-success count. A correct answer before the review is due records the attempt, accuracy and study day, but preserves mastery, consecutive-success count and the review date. An immediate correction after a mistake therefore does not erase the scheduled review.

The recommended short lesson contains only due reviews and unseen questions, capped at five. It reserves up to three review slots when new material exists, selects new material across weaker and unexplored topics, and returns unused slots to reviews. Due reviews are ordered by deadline, then lower mastery. Topic weakness compares practised current revisions only; unseen questions are not evidence of weakness. Home offers the full backlog separately. See [Daily learning](docs/DAILY-LEARNING.md) for the policy and its limits. Topic lessons still permit extra practice and prioritise oldest due reviews, unseen questions, then weaker questions. The version 0.2.0 upgrade recalculates older scores using these rules without deleting attempts, resetting settings or replacing imported content. Scores and review dates can consequently change on upgrade.

Overall mastery averages **questions in the selected study language**, not topic percentages. Unseen/revised questions contribute zero. Accuracy covers all retained attempts in that language, including older revisions and removed material. Strongest/weakest compare practised topics; ties use the first topic. Streaks use the device's local calendar and include an ongoing streak ending yesterday. Due checks use device time.

This is a transparent demo heuristic. Immediate correct repeats no longer inflate mastery, but a first correct answer can still be a guess; scores do not decay when review is overdue, and device time can affect scheduling. There is no official coverage model or validated readiness prediction. The UI therefore calls the score **material mastery**, not readiness to pass. Easier Finnish explanations are not certified or reviewed as selkokieli.

## Verification

The full local gate is `scripts/verify.ps1` (Python 3.10+ and the Android toolchain required). It checks the source-review ledger before tests/builds. Use `-Offline` with cached Gradle dependencies and `-Python <executable>` if Python is not on PATH. `-SkipDeviceTests` is an explicit incomplete-runtime check; leave it off for the full gate.

```powershell
.\scripts\verify.ps1
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
.\gradlew.bat :app:connectedDebugAndroidTest
.\scripts\verify-source.ps1
```

Use a dedicated emulator for instrumentation. Learning-flow tests reset the demo's content, progress and settings. Repository/migration tests use separate databases. Tests cover daily-session selection and pinned queue/reason restoration, learning rules, content validation, safe imports, legacy schema upgrades, FI/EN interfaces, Swedish practice and review, per-language accuracy/mastery, imported language gaps, session restoration and saved preferences. The source check verifies Finnish/English resource and formatting-argument parity.

Runtime verification uses an Android 16/API 36 emulator. Current records are in `docs/VERIFICATION-0.17.0.md`; earlier checks remain in the versioned documents. Physical devices and other OS versions remain unverified. Recovery tests cover database-write failure, backup restore rollback/stale previews, pending saves before/after commit, lesson recaps, vocabulary filters and word practice; the verification reports distinguish deterministic checks from real background-process death and TalkBack checks. Release builds are signed only when the git-ignored upload-key configuration is present.

The [human content-review checklist](docs/HUMAN-CONTENT-REVIEW.md) and [learner-pilot script](docs/LEARNER-PILOT.md) are prepared. Independent human review, recruitment and learner sessions have not been conducted or approved by these documents.

## Content references

Sample facts were checked against these public sources. Explanations are our short paraphrases, not official test questions:

- [Finlex: Suomen perustuslaki, §§ 3, 6, 11, 12, 17, 24](https://www.finlex.fi/fi/lainsaadanto/1999/731)
- [InfoFinland: Suomen historiaa](https://infofinland.fi/fi/information-about-finland/finnish-history)
- [InfoFinland: Työsopimus ja työsuhteen ehdot](https://infofinland.fi/fi/work-and-enterprise/during-employment/employment-contract-and-terms-of-employment)
- [InfoFinland: Työturvallisuus ja työsuojelu](https://infofinland.fi/fi/work-and-enterprise/during-employment/health-and-safety-at-work)
- [InfoFinland: Kirjastot](https://infofinland.fi/fi/leisure/libraries)
- [Suomi.fi / Hätäkeskuslaitos: Hätäilmoituksen tekeminen](https://www.suomi.fi/palvelut/hatailmoituksen-tekeminen-hatakeskuslaitos/01a571b5-3eb9-4b49-a27e-ec8427737c5b)
- [Migri: Kansalaisuuskoetta koskevat kysymykset](https://migri.fi/ukk-suomen-kansalaisuus)

The Swedish demo uses the corresponding Swedish source pages for [history](https://infofinland.fi/sv/information-about-finland/finnish-history), [employment](https://infofinland.fi/sv/work-and-enterprise/during-employment/employment-contract-and-terms-of-employment), [workplace safety](https://infofinland.fi/sv/work-and-enterprise/during-employment/health-and-safety-at-work), [libraries](https://infofinland.fi/sv/leisure/libraries), and [emergencies](https://www.suomi.fi/service/gor-ett-nodmeddelande-nodcentralsverket/01a571b5-3eb9-4b49-a27e-ec8427737c5b). Constitutional facts and terminology were also checked against Finlex's [Swedish original statute PDF](https://www.finlex.fi/api/assets/extra/statute-book-of-finland-pdf/swe/1999/19990074.pdf). This source check is not a full legal/editorial or language-level review; the short app explanations remain our demo paraphrases.

## Next priorities

1. Have a Finnish/Swedish civic-content editor and English-language editor review the wording, vocabulary and easy explanations. Dated assistant source checks and a content gate are implemented; human publication approval is not.
2. Import and map the actual official learning material when available, including verified coverage and reuse rights. Until then the course remains EDITORIAL and the topics and objectives provisional.
3. Run the documented learner evaluation before adjusting mastery or introducing pass-readiness estimates. The score still does not decay when overdue or account for guesses.
4. Complete physical-device and spoken screen-reader testing across supported OS versions. The emulator report records exactly which recovery/accessibility checks ran.
5. Define the production package name, app identity and a trusted content-distribution method before store release. Local import remains the delivery method; no backend was added.

## Local version control

This folder is now a Git repository on `main`. Commit `fa75cac` records the working v0.14 baseline before this content expansion. Build outputs, local SDK configuration, verification/device data and keystores are ignored. The repository is hosted on GitHub; signing keys and `keystore.properties` are never committed. Use `git log --oneline`, `git status`, and `git diff` to review local changes.
