# v0.16.0 — coherent learning snapshots and independent topic routing

This increment addresses the two reproduced defects from the [8 October architecture audit](ARCHITECTURE-REVIEW.md). Content, scoring rules and the database schema are unchanged. All bundled learning content and readiness estimates remain provisional.

## Changes

- `LearningRepository.snapshot` observes invalidations of the four relevant tables and captures their rows within one Room transaction. Content decoding runs after the transaction on a background dispatcher. Consumers receive a coherent database version rather than a combination of independently refreshed tables.
- Topic practice uses a separate `topicId` navigation argument and a typed `PracticeRequest`. Imported topics named `review` and `all` select their own questions. Legacy saved arguments, saved language, question order, revision checks and pending-answer recovery remain supported.
- Six permanent device regressions cover repeated answer writes, repeated content replacement, initial empty snapshots, both colliding names, ordinary imported topics and saved-queue recovery. Two actual navigation tests recreate the Activity and complete all three questions of an imported topic while other topics remain available.
- Three JVM request tests cover command collisions, legacy requests and saved assessment identity. The focused runner selects permanent tests and preserves the original audit evidence; it no longer copies temporary Kotlin sources.
- Version code/name advance to 16 / 0.16.0. No dependency upgrades or schema migration are needed.

## Verification

Verified on 8 October 2026 using the dedicated `Citizenship_MVP` Android 16/API 36 emulator. `ANDROID_SERIAL=emulator-5556` restricted connected tests to this project while another project's emulator remained running.

The full `scripts/verify.ps1 -Offline` gate passed: 148 Gradle tasks (42 executed, 106 up-to-date), including both lint variants, debug/minified release APK assembly and the unsigned release bundle.

| Check | Result |
|---|---|
| JVM tests | 156 passed; zero failures, errors or skips |
| Android device tests | 141 passed; zero failures, errors or skips |
| Architecture contracts | 8 passed |
| Python content-gate tests | 9 passed |
| Source/resource checks | 93 files; 301 matching Finnish/English resource keys and format arguments |
| Debug/release lint | Zero errors; existing `OldTargetApi` and debug `AndroidGradlePluginVersion` warnings |
| Debug APK / minified unsigned release APK / unsigned AAB | Built successfully |

The focused runner initially executed only the first of two comma-separated class filters. It now invokes each class separately and checks its XML to reject missing execution. The corrected runner passed: six `ArchitectureRegressionTest` cases and two `TopicRoutingFlowTest` cases, with zero failures, errors or skips. These same eight cases also passed in the full 141-test gate. No application or test assertion was weakened to obtain a passing result.

Bundled content is byte-identical in all three artifacts; source SHA-256 remains `01de75816e60b9e27814ed1598bc59458bd4836373d1fc07615bfca76567aaab`. Test-only historical content and audit files are absent from application artifacts. Release metadata and the installed app both report version 16 / 0.16.0.

Portable results: [corrected-version result record](architecture-probes/results-fixed-2026-10-08.json). Local raw evidence: `.verification/v16-full-verification.log`, `v16-full-device-tests.xml`, `v16-focused-verification.log`, and per-class XML/logs under `.verification/architecture`.

## Native minified-app check

The minified APK was signed with the local debug key solely for installation on the QA emulator. Native inspection covered the Finnish home, topics, authored lesson and five-question topic practice at phone size (1080×2400, density 420) and tablet size (1920×1200, density 240). Question text, alternatives and controls remained visible; the tablet retained a bounded reading width. Resizing preserved the current topic queue.

Selecting Eduskunta produced correct feedback, explanation, easy Finnish, vocabulary, a source reference and a review date. After restoring phone dimensions, force-stopping and relaunching the process, home retained **1/30 practised questions and 1% demo mastery**. Progress showed one answer, 100% accuracy and zero reviews due. The filtered AndroidRuntime error log contained no crash. These are smoke-test results, not exam-readiness claims.

Screenshots are retained as `.verification/v16-release-home.png`, `v16-release-question.png`, `v16-release-tablet-question.png`, `v16-release-tablet-feedback.png`, `v16-release-restarted-home.png` and `v16-release-progress.png`. Disposable QA data was cleared after inspection and the emulator started for this verification was closed.

## Follow-up changes after the v0.16.0 verification run

These were made after the full gate above and have **not** been run on an emulator: the project's own AVD was unavailable and the only connected emulator belonged to another project. Checked so far: JVM tests (156 passed), debug and release lint (zero errors), debug/release build, device-test compilation and the source/resource check.

- The snapshot read now uses `useReaderConnection` + `deferredTransaction` with `.conflate()`, so a large read no longer takes the writer lock that answer saves need. **Rerun `scripts/run-architecture-probes.ps1 -Offline`**: it must confirm that DAO queries inside the reader transaction still see one database version.
- Topic names follow the Swedish study language (`TopicLanguage.kt`). Four device tests that clicked the English topic name under Swedish study now use "Samhälle och demokrati", and `topicNamesFollowTheStudyLanguage` is new.
- ViewModel failure paths log their cause through `Diagnostics`.

Run the full `scripts/verify.ps1 -Offline` on the project emulator before treating these as verified.

## Remaining limits

The audit's A3–A5 remain growth risks or future design choices: full-history/dashboard cost needs measurement, large histories need a lossless backup policy, and iOS/JVM repository seams should follow concrete requirements. This fix does not claim large-history performance or Kotlin Multiplatform compatibility.

No human content/language review, learner evaluation, physical-device testing or new Android-version coverage is included. The original audit's failing results remain in `docs/architecture-probes/results-2026-10-08.json` as historical evidence, separate from the corrected version's verification. No content or application was published.
