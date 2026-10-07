# Version 0.7.0 verification — 7 October 2026

## Implemented increment

The completion screen now helps the learner understand what to do next. It shows the saved lesson score, earliest scheduled review and missed questions with the selected/correct answers. Explanations can be expanded in the study language, with the same optional easier-Finnish/English support as question feedback and a selectable source reference. The return action appears before optional long explanations.

The recap resolves saved attempt IDs for this navigation session. Lifetime history and other study languages cannot leak into it. Duplicate retry tokens appear once; incompatible revisions, language/option changes and missing attempts do not produce invented explanations. Completed recaps and expansion state survive recreation, while saved IDs recover details after supported process restoration. A session restored from an older version without IDs retains its counts and reports unavailable details. Recaps are not a permanent lesson archive; underlying attempts/progress remain stored in Room after leaving the screen.

Reading explanations makes no persistence writes and does not alter mastery or scheduling. No Room schema, learning-rule version, content package, answer key, dependency or network capability changed. All 24 FI/SV variants remain DEMO, package v4. The dated material-status snapshot remains explicitly checked on 6 October; it was not silently re-dated.

## Automated verification

The full gate uses Android Studio's bundled JBR and the installed Python runtime:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\scripts\verify.ps1 -Python 'C:\Users\dimmu\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -Offline
```

- **79 JVM tests**, zero failures. Six new recap tests cover session-only ordering, duplicate tokens, unavailable/changed content, language isolation, review scope and empty sessions.
- **52 device tests**, zero failures/errors/skips on the dedicated Android 16/API 36 emulator. New UI journeys cover missed-question explanations, saved expansion state, no extra attempts and an all-correct Swedish recap excluding earlier Finnish mistakes. Repository tests now verify recap recovery after pending saves before/after commit, completed-session restoration and legacy session fallback. Existing learning, explanation, locale, import, migration and native accessibility checks also passed.
- **Six Python content-gate tests**, passed; all 24 question fingerprints and review records remain aligned.
- Source hygiene: 52 files, 186 matching FI/EN resource entries with format/plural parity and syntax checks.
- Debug/release lint: zero errors, seven existing debug warnings and one existing release target-SDK warning. No baseline or suppression was added.
- The complete gate passed, including debug APK, minified unsigned release APK and unsigned AAB assembly. The exact release APK's bundled JSON was compared with the archived v0.6 APK: the entire content package is identical.

New test failures initially came from incorrectly copied English labels (`Correct:` and `Return to your learning path`) and waiting for a lazy-list heading that was intentionally offscreen after scroll restoration. Assertions were corrected to use the actual copy and restored expanded control. The rendered screen was inspected rather than weakening the expected behaviour. The final full device report is retained in ignored `.verification/v07-full-device-tests.xml`.

Accessibility uses native Material button semantics, question-specific expansion labels/state descriptions, semantic headings and language spans. Guidance consulted: [Android Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics). Automated semantics/native-node evidence is not a human spoken TalkBack review.

## Rendered release and recovery checks

The minified release APK was signed only with the local debug key as `.verification/release-v07-smoke.apk` for emulator installation. It is not a production-signed release.

- Installed the actual archived v0.6 APK, saved a Finnish answer and upgraded to the minified v0.7 APK. Home retained 2% material mastery, one of twelve questions practised and a one-day streak. Before/after screenshots are `v07-before-upgrade-home.png` and `v07-after-upgrade-home.png` in ignored `.verification`.
- Enabled English support in Finnish settings. Completed a new two-question topic lesson with one mistake and one early correct repeat. The recap showed 1/2 correct rather than lifetime totals. Expanded the missed question's native, easier-Finnish and English explanations and source reference.
- Sent the expanded recap to the background, used `am kill`, confirmed the process was absent, then brought its task back. The PID changed from 14009 to 14478. Expanded explanation and scroll position were restored. Progress afterward showed exactly three lifetime attempts, 67% accuracy and unchanged 2% mastery; reading/restoration did not record another answer.
- Visually inspected the recap/explanations at 200% system text scaling on a 1080×2400 phone and on a 1920×1200, density-240 landscape tablet. Text and source URLs wrap and remain scrollable, content width is bounded, and the return action works. Screenshots are `.verification/v07-release-*.png`.
- No fatal app exception appeared in the inspected release/final log windows. The earlier version's unresolved native rendering crash remains recorded in VERIFICATION-0.6.0.md; this increment does not establish its root cause.

After verification, only the dedicated emulator app's fixture was cleared. The final debug APK is installed and open on fresh Home with Finnish defaults and zero study history, at normal phone dimensions/density and 100% text size. Accessibility remains disabled as originally configured. Screenshot: `.verification/v07-final-fresh-home.png`.

## Scope and limits

Only the dedicated API 36 emulator is available. Physical devices, other Android versions, audible mixed-language pronunciation, human content approval and learner validation remain outstanding. Existing mastery limitations are unchanged: overdue scores do not decay and first correct answers can be guesses. No official exam readiness claim is justified.

Source/content review limits remain in CONTENT-REVIEW.md. Release signing/publication are not configured. No account, backend, analytics, advertisement, payment or AI feature was added. There is no Git repository here; final review used source inspection and archived APK/content comparisons rather than a Git diff.
