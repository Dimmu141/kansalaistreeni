# Version 0.8.0 verification — 7 October 2026

## Implemented increment

Topics now open a reading screen with the installed questions' native explanations, optional easier Finnish/English support and source references. The question-practice action remains directly available. These are explanations assembled from the current demo package, not a separately authored or official course.

Vocabulary is accessible globally from Topics and with a topic filter from a lesson. It searches native terms, English meanings, alternate FI/SV terms and native explanation contexts. Equivalent Unicode and letter case are normalized; every search token must match. Duplicate entries retain their question contexts and references, while different meanings remain distinct. Missing Swedish terms are omitted without substituting Finnish. Imported packages update both views automatically.

Word practice shuffles up to ten words matching the search/filter. Learners recall a meaning, reveal it and continue. It has no correctness score, separate word mastery or spaced repetition. Reading and recall make no writes to attempts, question mastery or review dates. Saved state holds search/filter inputs and small word-ID/revision queues, position, language and reveal state. Changed revisions or missing words invalidate restored practice instead of attaching a reveal to replacement material.

The increment adds no dependency, Room schema, learning-rule version, network permission, new factual content or source claim. All 24 FI/SV question variants remain DEMO, package v4. The existing dated material-status snapshot remains checked on 6 October; it was not silently re-dated. The archived v0.7 APK and exact minified v0.8 release contain byte-identical demo JSON.

## Automated verification

The complete gate passed using the installed Android Studio JBR and Python runtime:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\scripts\verify.ps1 -Python 'C:\Users\dimmu\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -Offline
```

- **89 JVM tests**, zero failures. Ten new tests cover topic/language scoping, unavailable material, imported explanations/sources, vocabulary deduplication, missing Swedish terms, search token/Unicode handling, topic-specific context search and preservation of history/scores.
- **65 device tests**, zero failures/errors/skips on Android 16/API 36. Eight new UI journeys cover lesson reading and onward question practice, filtered search, source/translation expansion, recreation, unscored recall, restored position/reveal, Swedish terms, imported vocabulary and missing-language vocabulary. Five direct ViewModel tests cover restored queues, language pinning, revised/removed words, invalid positions and empty filters. Existing answer, recap, migration, import, locale and native accessibility checks still pass.
- **Six Python content-gate tests**, passed. All question fingerprints remain aligned with the existing review ledger; human publication/content approval remains pending.
- Source hygiene: 61 checked files and 220 matching FI/EN resource entries, with format/plural parity and JSON/XML syntax checks.
- Debug/release lint: zero errors, seven existing debug warnings and one existing release warning. No new baseline or suppression.
- Debug APK, minified unsigned release APK and unsigned AAB assembly passed. No app or content was published.

The first device run had one assertion failure: the new test had copied Swedish `lagar` instead of the packaged `lagarna`. The actual displayed explanation was correct. The assertion was corrected to the content fixture, and the complete gate was rerun successfully. Its device report is archived in ignored `.verification/v08-full-device-tests.xml`.

## Installed release and restoration

The exact minified release APK was signed only with the local debug key for installation as `.verification/release-v08-smoke.apk`. This is not production signing.

- Installed the archived v0.7 APK on the dedicated emulator, saved one Finnish answer, then upgraded to v0.8. Home retained 2% material mastery, one of twelve questions practised and the one-day streak. Screenshots: `.verification/v08-before-upgrade-home.png` and `v08-after-upgrade-home.png`.
- Inspected and clicked through Finnish topic reading, filtered vocabulary, expanded native/easier-Finnish explanations and source references, and word recall. The word order is shuffled once per exercise.
- Backgrounded the revealed word exercise, killed its process with `am kill`, confirmed no remaining PID, and returned to the task. PID changed from 5202 to 5544; the word, queue position and revealed meanings were restored. Screenshots: `v08-release-word-revealed.png` and `v08-release-word-restored.png`.
- Repeated real background process death with search `power`, the democracy topic filter, an expanded explanation and scroll position. PID changed from 5544 to 6004. Search, filter, expansion and scroll were restored. Screenshot: `v08-release-search-context-restored.png`.
- Inspected 200% system text scaling on the 1080×2400 phone and a 1920×1200, density-240 landscape tablet. Long explanations wrap, controls and URLs remain reachable by scrolling, and content width is bounded. Screenshots: `.verification/v08-release-*-200.png`, `v08-release-context-tablet.png`, `v08-release-search-tablet.png` and `v08-release-lesson-tablet.png`.
- Progress after reading/recall still showed exactly one saved answer, 100% answer accuracy, 2% material mastery and no due review. Screenshot: `v08-release-progress-after-reading.png`.
- Inspected Swedish topic reading, the corresponding Swedish source reference, vocabulary and revealed FI/EN meanings in the installed release. Screenshots: `.verification/v08-release-swedish-*.png`.
- No fatal app exception appeared in the inspected release log window, retained as `.verification/v08-release-runtime.log`.

After these checks, the dedicated emulator's verification fixture was cleared. The final debug APK is installed and running at Topics with Finnish defaults, zero study history, normal phone dimensions/density and 100% text size. Accessibility configuration was not changed. Screenshots: `.verification/v08-final-fresh-home.png` and `v08-final-topics.png`.

## Boundaries and next decisions

Only the dedicated API 36 emulator was available. Physical devices, other Android versions, human spoken TalkBack review, human editorial/linguistic review and learner validation remain outstanding. Native language spans, standard Material controls, semantic headings, live-region feedback and labelled expansion state are used, but automated semantics are not a pronunciation audit.

The current lessons reuse short question explanations and will need purpose-written learning structure when reviewed material exists. Word recall deliberately contributes no score; deciding whether it needs its own persistent review model should follow learner feedback. Existing material mastery is still a heuristic, not an exam-readiness prediction. Source links remain selectable text.

Local progress export/restore, independent content review and a small learner pilot are the next priorities. There is no Git repository here; final review used source inspection, meaningful tests and archived APK/content comparisons. No backend, account, analytics, ad, payment, AI or cloud capability was added.
