# v0.17.0 — formats, objectives, expanded content, declutter, Swedish interface

This release keeps the existing architecture, provenance statuses, migrations and verification gate. No backend, accounts, analytics or AI were added. Everything in it is practice material and a provisional structure, **not the final format**: the official learning material and exam format have not been published, so topics, objectives and the exam profile remain provisional.

## Changes, by step (one commit each)

1. **True/false format** (`ffc296a`). Content schema 2 adds `format`; `TRUE_FALSE` questions are statements with fixed option IDs `true`/`false`, answered with two large buttons labelled in the interface language. Older packages and saved data read as single choice.
2. **Learning objectives** (`7927140`). Packages list objectives with Finnish and Swedish statements and an optional legal reference; every question references at least one. Room schema 7 stores them; the 6→7 migration only adds the table. Coverage screen and `docs/CONTENT-COVERAGE.md` are generated from this layer.
3. **Topic structure** (`4684a2e`). Eight provisional topics: democracy, rights, equality, laws, history, culture, safety and digital services. Moving questions between topics or objectives is a reclassification that keeps progress. See [TOPIC-MIGRATION.md](TOPIC-MIGRATION.md).
4. **Content expansion** (`6bf1a2d`). 200 Finnish and 200 Swedish EDITORIAL questions (25 per topic per language), 80/80/40 easy/medium/hard, 63 true/false per language (31.5 %), single-choice questions with four options, plain B1 language, explanation, easy Finnish explanation, vocabulary and an HTTPS official source per question — Swedish items cite Swedish pages. No questions on values. Changeable facts carry `validFrom`/`reviewBy`; sources carry `checkedAt`, shown in the source line, with an overdue warning after `reviewBy`. Each topic has a three- to five-section lesson in Finnish, Swedish and English. No item may claim stronger provenance than its package. `verify-content.py` checks option counts, format rules, objective references, duplicates, evidence hosts, per-topic minimums, lesson shape and the difficulty/format distribution.
5. **UI declutter** (`461c2c8`). Full disclaimer once in first-run onboarding and permanently in Settings → About; elsewhere a "Ei virallista materiaalia" chip in the app bar. Per-question status labels and the repeated note under sources removed. Home: heading, mastery card, one primary and one secondary action; FI/SV study-language switch in the app bar. Topics list first, vocabulary as one row, duplicate assessment button removed. Progress bars empty at 0 %. Strongest/weakest only after two practised topics. Relative review times. Options keep the radio button, numbering removed.
6. **Swedish interface** (`da92cac`). `values-sv` covers the whole interface; Settings offers Finnish, Swedish and English. Without an explicit choice the interface follows Swedish study. The resource parity check covers all three languages.
7. **Exam profile** (`11f0e2f`). `ExamProfile` holds question count, format mix, pass threshold and time limit. The app uses `ExamProfile.UNKNOWN` (20 questions, ~30 % true/false, no pass mark, no time limit), so there is no pass/fail verdict and no timer; results show correct/total and topic results only.

Version code/name advance to 17 / 0.17.0.

## Content provenance

All 400 questions and 24 lesson variants are `EDITORIAL`: written for the app and checked by the assistant against the cited official pages on 8 October 2026 (`ASSISTANT_SOURCE_CHECK` in [source-review.json](source-review.json)). `publicationApproved` is false for every item; specialist Finnish, Swedish, English and easy-language review is still required before publication. All 146 source and evidence URLs in the authoring files resolved on 8 October 2026 (some hosts answer only to browser-like requests). Sources are InfoFinland, Finlex (consolidated Constitution and other acts), Suomi.fi, the Ombudsman for Equality, the Ministry for Foreign Affairs, the Government, Parliament, the President, the police, the Emergency Response Centre Agency, rescue services, Kanta, DVV, the Tax Administration, Kela and the Data Protection Ombudsman.

Known source gaps were avoided rather than filled: no question asserts facts whose official source could not be found (for example the national anthem's lyrics, member-only church tax, or Swedish-language Kela pages).

## Learner progress across the upgrade

- The original 30 concepts were rewritten and given new revisions, as approved. Their answer history, attempts, saved assessments and backups survive; current mastery of a rewritten question starts again, the existing rule for revised questions. Device migration tests cover schema 1–6 upgrades and assert this behaviour explicitly.
- Questions moved to other topics or objectives keep their progress (reclassification is not a revision).
- The new onboarding flag defaults to "seen" until DataStore loads, so existing users do not see a flash of onboarding; new installs see it once.

## Verification

Verified on 9 October 2026 on the dedicated `Citizenship_MVP` Android 16/API 36 emulator. `ANDROID_SERIAL=emulator-5556` restricted connected tests to this project's emulator.

The full `scripts/verify.ps1 -Offline` gate passed: 148 Gradle tasks (44 executed, 104 up-to-date), including both lint variants, the debug APK, the minified unsigned release APK and the unsigned release bundle.

| Check | Result |
|---|---|
| JVM tests | 185 passed; zero failures, errors or skips |
| Android device tests | 148 passed; zero failures, errors or skips |
| Architecture contracts | 8 passed |
| Python content-gate tests | 13 passed |
| Content gate | 400 EDITORIAL questions, 24 lesson variants, package v7; coverage report current; 0 thin objectives |
| Source/resource checks | 112 files; 308 matching Finnish/English/Swedish resource keys, format arguments and plurals |
| Debug/release lint | Zero errors; existing `OldTargetApi` and `AndroidGradlePluginVersion` warnings |
| Debug APK / minified unsigned release APK / unsigned AAB | Built successfully |

Bundled content SHA-256: `14fd2312fd3bde63d0e522ad4d175dafb6d81cf6eec3f33adb21978597c0e1a6`. Local raw evidence: `.verification/v17-full-verification.log` and the per-step authoring and source files under `.verification/v17-wip/`.

Test changes made for the new seed were limited to expectations that depended on the old content (counts, texts, revision numbers, DEMO labels) and to the decluttered UI. The v0.14 regression fixture is installed with lifted revisions so it remains a valid update over the newer bundled seed. No assertion about provenance, progress retention or migration safety was weakened; the migration test now asserts the revised-question behaviour explicitly instead of the old unchanged-question case.

## Screenshots

Captured from the debug build on the QA emulator (1080×2400) after clearing app data, in [screenshots/0.17.0](screenshots/0.17.0):

| File | Shows |
|---|---|
| `01-home.png` | Home after onboarding: chip, FI/SV switch, mastery card, one primary and one secondary action |
| `02-topics.png` | Topic list first, empty 0 % bars |
| `03-true-false-question.png` | A Finnish true/false statement with Oikein/Väärin |
| `04-feedback-swedish.png` | Feedback for a Swedish statement with the Swedish interface, relative review time and a dated Swedish source |
| `05-coverage.png` | Objective coverage: 55 objectives, 0 thin |
| `06-progress.png` | Progress with strongest/weakest topics after two practised topics |

## Not verified

- Physical devices, other Android versions and spoken screen-reader output.
- Human editorial and publication review of the new material.
- The pass/fail and timer UI: deliberately not built until a real exam profile exists.
