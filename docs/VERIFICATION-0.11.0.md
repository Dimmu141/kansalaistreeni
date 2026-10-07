# Version 0.11.0 verification — 7 October 2026

## Implemented increment

Added mixed-topic practice assessments, persistent/resumable choices and position, editable answers, deferred feedback, results by topic, readable saved question snapshots and compatible mistake practice. Home and Topics provide entry points. Finnish and Swedish sessions remain separate; an active session and its follow-up retain their original language when the preference changes.

Assessment records use their own Room table. Schema 4→5 adds that table without changing existing material, attempts, scores or preferences. Assessments never write learning attempts/progress, and their percentages are not predictions of official test success. Follow-up uses the ordinary idempotent learning-answer path. Material updates and learning-backup restoration preserve assessment snapshots.

Assessments are device-local and excluded from learning backups. Both affected screens explain this. Backup/content formats remain schema v1, learning rules remain v2, and no factual content, dependency, networking permission or service was added. Categories and all 24 bundled FI/SV variants remain DEMO, package v4.

## Automated checks

- Source hygiene, JSON/XML syntax and Finnish/English resource/format parity: 74 files and 272 resources.
- Content-review ledger: all 24 bundled variants passed their existing fingerprints; independent human publication approval remains pending. All six Python gate regression tests passed.
- JVM: 120 tests, including 13 new assessment-domain tests. Coverage includes topic-balanced sampling, language filtering, arbitrary answer counts, edits, invalid selections/positions, incomplete completion, withheld feedback, accuracy/topic totals, immutable/idempotent completion and compatible mistake selection.
- Device: the full gate passed all 94 tests. After a wording-only counter refinement and one additional edge-case test, all four assessment UI tests passed again. This is 95 distinct passing device tests across the full run and final targeted run, not a claim that the final targeted invocation ran the entire suite. Evidence: `.verification/v11-full-device-tests.xml` and `v11-final-assessment-flow-tests.xml`.
- Persistence coverage includes database reopening, concurrent starts, separate FI/SV sessions, failed writes, invalid/incomplete completion, unchanged mastery/history, immutable results, content replacement, backup restoration and normal learning follow-up. Exported schema v4 was migrated through the actual production path, retaining existing history and enabling assessment storage. Existing older-schema checks also passed.
- UI coverage includes a full 12-question Finnish journey, answer changes, activity recreation, leaving/resuming, deferred answers/explanations, topic results, native/English explanations, saved result reopening and practising only the one mistake. A Swedish journey changes the study preference before follow-up and confirms that its recorded learning attempt remains Swedish. A one-question/three-option imported course finishes with an all-correct result and no mistake-practice action.
- Final debug/release lint: zero errors. Existing warnings remain: `OldTargetApi` in both builds and `AndroidGradlePluginVersion` in debug. A new counter wording initially drew two plural warnings; the final wording removed both.
- Debug APK, minified unsigned release APK and unsigned release AAB built successfully. The final host gate was repeated after the wording refinement; device verification was supplied separately by the passing full and final targeted runs.

Two new UI-test fixtures were corrected during verification: the Swedish test initially expected a label different from the existing interface, and the injected storage-failure trigger initially blocked SQL UPDATE rather than Room's actual INSERT OR REPLACE. Corrected tests passed. The fault test now verifies that a failed save retains the prior choice and that retry stores it exactly once. The original reports are retained in `.verification/v11-first-full-device-tests.xml` and `v11-corrected-assessment-flow-tests.xml`.

## Native minified-release checks

The release was signed only with the local debug key for `.verification/release-v11-smoke.apk`, then installed on dedicated `emulator-5556` (Android 16/API 36). Distribution artifacts remain unsigned and unpublished. QA resets affected only that dedicated emulator.

1. Opened the real assessment entry screen, confirmed the demo/non-official explanation and actual 12-question count, and selected answers through native Android controls. Saved-answer count, position, demo label and the chosen radio option were visible. Screenshots: `v11-release-assessment-intro.png`, `v11-release-question.png`, `v11-release-saved-second-question.png`.
2. Backgrounded the app, killed its process with `am kill`, asserted that no PID remained, and reopened its task. Question 2, two saved choices and the selected answer were restored. PID evidence is in `.verification/v11-process-death.txt`; screenshot: `v11-release-restored-question.png`. This was actual process death, not only activity recreation.
3. Changed system text scale to 200%. Question text and answers remained readable, the saved selection remained visible, and Next/Previous were reachable by scrolling. The first run exercised a long freedom-of-expression answer; the final repetition exercised a multiline emergency question with short numeric answers. Restored 100%. Final screenshots: `v11-release-question-large-text.png`, `v11-release-controls-large-text.png`.
4. Completed all 12 questions through native controls with one deliberate mistake. The result showed 11/12 (91%), actual topic counts and the one-question follow-up action. Screenshot: `v11-release-result.png`.
5. Inspected the result at 1920×1200/density 240. Content stayed centered within the existing bounded width and actions remained reachable. Restored normal phone size/density. Screenshot: `v11-release-result-tablet.png`.

The complete native result journey preceded the final saved-counter wording refinement. The final release has byte-identical `classes.dex`; equality/hashes are retained in `.verification/v11-copy-code-comparison.txt`. The exact final APK was then installed and its saved-session/process-death/200%-text checks repeated, replacing the corresponding screenshots with final-copy evidence. Assessment results and other UI wording were unchanged by the refinement.

Bundled material is byte-identical to the archived v0.10 release, SHA-256 `8d690e6a10a9a202cc0fd8de90147f5f2ffd549babb4ceda41a66ecc90c2b1a9`.

No fatal app exception appeared in the final release smoke log, retained as `.verification/v11-release-runtime.log`. Returning Home after assessment-only choices showed zero saved learning attempts (`v11-release-home-after-assessment.png`). After checks, only the dedicated emulator's disposable fixture was cleared. The final debug APK is installed and open on the fresh assessment entry page, with Finnish defaults, bundled package v4, zero learning/assessment history, normal phone dimensions/density and 100% text scale. Screenshot: `.verification/v11-final-fresh-assessment-entry.png`.

## Boundaries

There is no Git repository in this folder; review used direct source inspection, generated-schema inspection, the existing baseline reports/APK, meaningful tests, release code/content comparisons and native rendered journeys. Changes are limited to assessment models/storage/UI/navigation, the shared answer control, migration registration, tests, version/copy and documentation.

Physical devices, other Android versions, spoken TalkBack assessment journeys, human content review and learner sessions remain unverified. The result announcement and option language/selection semantics are implemented; an emulator accessibility tree is not a substitute for a spoken screen-reader check. Topic weighting is provisional, small result samples do not measure complete civic knowledge, and assessment history still needs portable backup/deletion support before broader distribution. No official examination format or passing prediction is asserted.
