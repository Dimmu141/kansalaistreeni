# Version 0.6.0 verification — 6 October 2026

## Implemented increment

- English supporting explanations on all 24 Finnish/Swedish demo variants, selected independently from the interface and study language. Native study text stays visible. Missing support has an explicit fallback.
- Bundled content package v4. Supporting-translation-only updates preserve question revisions, mastery and review dates; factual/practised-content changes still require revision increases. Import previews distinguish these changes and show the supporting explanation.
- A dated offline official-material snapshot, per-question source-review ledger and SHA-256 fingerprints, six negative content-gate tests and a repeatable verification script. All questions remain DEMO; assistant source checks are explicitly not human publication approval.
- Mixed-language Compose text carries language spans through to Android accessibility nodes. Additional tests cover native accessibility actions, preferences, database-write failure, retry idempotency and pending saves before/after commit.
- Deterministic learning simulations and a written learner-evaluation protocol. The score remains material mastery, with explicit counterexamples showing why it cannot predict exam success.

## Final automated gate

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\scripts\verify.ps1 -Python 'C:\Users\dimmu\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -Offline
```

The complete gate passed on the dedicated Android 16/API 36 emulator (`emulator-5556`):

- **73 JVM tests**, no failures: 28 learning-engine, four simulation, 18 content-update, 17 content-validation and six language-scoping tests.
- **48 device tests**, no failures/errors/skips: 22 repository, six migration, four content import, one native document-picker navigation, four explanation, three learning flow, three interface-locale, four study-language and one platform accessibility test.
- **Six Python content-gate tests**, passed. The ledger covers every bundled variant, verifies full-question fingerprints and bilingual/English alignment, and rejects silent promotion of demo content.
- Source hygiene: 48 source files, 175 matching Finnish/English resource entries with format/plural parity, JSON/XML parsing and Python/PowerShell syntax checks.
- Debug and release lint: zero errors; seven existing debug warnings and one existing release target-SDK warning. No suppression or baseline was added.
- Debug APK, minified unsigned release APK and unsigned AAB built. Release signing/publication remain unconfigured.

The final device report is retained in ignored scratch output `.verification/v06-full-device-tests.xml`. The repository has no Git metadata, so review used source inspection and comparisons against the archived v0.5 APK rather than a Git diff.

Early runs exposed stale test assumptions about package versions, a partially visible settings control and merged radio semantics. Those assertions/navigation helpers were corrected. One run aborted in Android's native `libhwui` RenderThread (`AnimatorManager::pushStaging`, SIGSEGV); it had no Java app exception stack. Standard instrumentation animation disabling was added; subsequent complete suites passed, including the final 48-test run. The root cause of that native crash is not established. Normal-animation manual release checks below showed no recurrence; broader device testing remains necessary.

## Rendered minified-release checks

The release APK was signed **only with the local debug key** as `.verification/release-v06-smoke.apk` to install on the emulator. This is not production signing.

- Selected English support in the real settings UI and inspected Finnish and Swedish questions, native explanations, English support, vocabulary and source references. English menus did not change the Swedish question language or transfer Finnish progress.
- Upgraded the actual archived v0.5 APK to the minified v0.6 APK after saving one Finnish answer. Before/after Home retained one studied question, 2% material mastery and a one-day streak. All 24 practised payloads and revisions were compared with v0.5: only supporting translations changed.
- On a 1080×2400 phone, visually inspected Swedish practice and feedback at **200% system font scaling**, including wrapped source URLs and explanations. On a 1920×1200, density-240 landscape tablet, inspected question/feedback layouts and clicked through to the next question. Content stays bounded rather than stretching across the display.
- English support and explanation preferences survived restart/process restoration. Existing device tests cover light/dark appearance, FI/EN menus, native file selection, import cancellation/confirmation and language gaps.
- Opened Study material in the minified app and inspected the installed v4 FI/SV counts, dated offline material-status notice, selectable source URL and file-selection action.

Screenshots are retained under `.verification/v06-release-*.png`, with actual upgrade comparison images `.verification/v06-before-upgrade.png` and `v06-after-upgrade.png`.

## Process interruption and storage failure

With the minified release, selected an answer, sent the app to the background, used `am kill` and verified the process had disappeared. Restart restored the question and selected answer. Submitted it, then repeated background process death after feedback. The feedback and original review time were restored and Progress still showed exactly one answer. Process IDs changed on both restorations. This tests real background process death, not merely activity recreation or force-stop.

Repository tests additionally inject a failing SQLite trigger during a progress write: the attempt is rolled back too. Retrying the same submission token commits exactly once. SavedStateHandle tests restore a pending submission both before the database commit and after it; attempts, correct counts and original review times remain consistent. These deterministic fault cases do not simulate every filesystem-full, database-corruption or concurrent OS-kill condition.

## Accessibility evidence and limits

TalkBack 16.0.0.738667889 was temporarily enabled on the dedicated emulator; its bound service and touch-exploration state were confirmed with Android diagnostics. A native accessibility-node flow passed while TalkBack was active, using accessibility focus and click actions to answer once. It checked heading/checkable semantics, touch-target bounds, native Finnish/English LocaleSpan propagation and a single stored attempt. The targeted report is `.verification/v06-platform-accessibility-with-talkback.xml`. The final full suite includes the same platform assertions through UiAutomation, with its temporary flags restored afterward.

Shell-injected TalkBack traversal gestures did not produce a reliably advancing focus in this environment. **A full gesture journey, audible mixed-language pronunciation and human screen-reader usability are not verified.** Locale spans are evidence that language metadata reaches Android, not proof of correct speech. Original accessibility settings were restored; no TalkBack requirement is imposed on users.

## Remaining limits

Only the API 36 emulator was available; no physical device was attached. API 26/API 37 runtime checks, independent human accessibility review and learner studies remain outstanding. Source review is limited as documented in CONTENT-REVIEW.md; consolidated current-law review, bilingual wording, easy-language quality and reuse/publication approval still require human editorial work.

All 24 questions represent twelve DEMO concepts. Topics remain provisional. The score can remain 100% after a long overdue interval and can reward a guessed first answer; it must not be presented as calibrated readiness. Sound, Swedish interface, production identity/signing, trusted distribution, progress export and official coverage are not implemented. No backend, accounts, analytics, ads, payments or AI were introduced.

After checks, only the dedicated emulator app's verification fixture was cleared. The final debug APK is installed and open with fresh Finnish interface/study defaults, normal phone size/density, 100% system text size and zero study history. Original accessibility settings are restored. No fatal app exception appeared in the final inspected log window. The fresh Home screenshot is `.verification/v06-final-fresh-home.png`.
