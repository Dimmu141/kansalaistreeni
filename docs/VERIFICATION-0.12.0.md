# Verification — v0.12.0 adaptive short sessions

Date: 2026-10-07. Dedicated Android 16/API 36 emulator: `emulator-5556`, `Citizenship_MVP`.

## Change boundary

Home now starts a session of up to five eligible questions, balancing due reviews with new material from weaker and unexplored topics. It explains the mix and keeps a separate full-backlog action. Question context explains the selection reason. Session IDs, revisions, reasons, position, language and answer feedback survive saved-task restoration.

The policy is documented in [DAILY-LEARNING.md](DAILY-LEARNING.md). There are no new dependencies or database migrations. Content package v4, Room schema v5, backup format v1, mastery rules v2 and assessment behavior are unchanged. All bundled material remains DEMO. Human review and learner testing remain postponed.

## Checks

- The full `scripts/verify.ps1 -Offline` gate passed: 134 JVM tests, 102 device tests, six Python content-gate regressions, lint, debug/release APKs and unsigned release AAB.
- All 14 new planner domain tests and seven new device tests passed. Existing FI/EN and Swedish session-restoration tests now assert the planned next question instead of the previous file order. Language/answer preservation checks remain in place.
- Source hygiene and JSON/XML syntax cover 78 source files. After the obsolete `review_waiting` string was removed, FI/EN resources and formatting arguments match across 282 resources. This cleanup changes no visible copy or behavior.
- Final full device evidence after the rendered Home provenance fix: `.verification/v12-final-full-device-tests.xml`. The earlier successful full run is retained as `.verification/v12-full-device-tests.xml`. Earlier failed reports are retained separately. Failures were test assertions assuming old queue order and a corrected Windows encoding conversion in the new Finnish UI fixture; the final full suite has no failures/errors/skips.
- The full gate passed again after the final Home provenance fix, including all 102 device tests. The host gate had also passed after resource cleanup. Debug/release lint has zero errors and only the existing `OldTargetApi` warnings plus debug `AndroidGradlePluginVersion`. Both adaptive UI journeys passed again on final resources; `.verification/v12-final-daily-flow-tests.xml` contains those two tests. Repeated runs do not increase the distinct count of 102 device tests.
- Bundled material is unchanged, SHA-256 `8d690e6a10a9a202cc0fd8de90147f5f2ffd549babb4ceda41a66ecc90c2b1a9`.

## Native minified-release checks

The exact final minified release was signed only with the local debug key as `.verification/release-v12-smoke.apk`. All following checks used this final APK through native Android controls. Only the dedicated emulator received disposable QA data.

1. Restored seven deliberately incorrect DEMO attempts through Android's real file picker and the validated backup/confirmation flow. Home showed three review questions, two new questions, a Work focus, four remaining reviews and a separate seven-question full-backlog action. The material status is visible near the heading. Screenshots: `v12-release-home-mix.png`, `v12-release-due-reason.png`.
2. Inspected Home at phone dimensions and 1920×1200/density 240. The main action, score and demo label stayed visible; tablet content retained the existing bounded width. Lower actions remain scrollable. Restored normal dimensions/density. Screenshot: `v12-release-home-tablet.png`.
3. Answered the first due question, backgrounded the app, killed it with `am kill`, asserted that no PID remained, then brought its saved task back. A different PID and the original correct feedback were observed. Evidence: `.verification/v12-process-death.txt`, `v12-release-restored-feedback.png`. This was actual process death, not only activity recreation.
4. Advanced to question 2 and set Android text scale to 200%. The question, demo label and saved due-review reason wrapped without truncation. Selected 200, scrolled to the enabled Check button, answered and reached Next through scrolling. Restored 100%. Screenshots: `v12-release-question-large-text.png`, `v12-release-controls-large-text.png`.
5. Completed the pinned five-question session, including the new Work question and first Everyday-life question. Their original focus/unexplored reasons were visible. The recap showed 5/5. Home showed calculated mastery 10%, nine of twelve questions practised and four remaining due reviews. Progress showed exactly twelve total attempts, accuracy 42% and four due items: seven restored mistakes plus five new answers, with no duplicated answer after process death. Screenshots: `v12-release-focus-reason.png`, `v12-release-new-topic-reason.png`, `v12-release-session-complete.png`, `v12-release-home-after-session.png`, `v12-release-progress-after-session.png`.
6. Opened Review and confirmed its full four-question queue. No fatal exception appeared in the release smoke's AndroidRuntime log, `.verification/v12-release-runtime.log`. Screenshot: `v12-release-remaining-review-queue.png`.

Screenshots above are retained in `.verification/`. Both final APKs contain byte-identical bundled demo content and exclude the disposable smoke backup. After verification, the emulator's QA history and the fixture copied into Downloads were cleared. The final debug APK is installed and open on fresh Home with Finnish defaults, bundled package v4, zero learning/assessment history, normal phone dimensions/density and 100% text scale. Screenshot: `.verification/v12-final-fresh-home.png`.

There is no Git repository in this folder. Review used saved original files in `.verification/v12-before/`, direct diffs, the final source, automated reports and rendered native journeys. Changes are confined to the planner, Home/question context, saved-session revisions/reasons, resource/version updates, relevant tests and documentation.

## Limits

The five-question cap, review/new allocation and topic comparison are product heuristics, not an empirically validated learning system or official test specification. The small demo pool makes topic estimates especially coarse. Physical devices, other Android versions and a spoken TalkBack journey have not been checked in this increment. Distribution artifacts remain unsigned and unpublished.
