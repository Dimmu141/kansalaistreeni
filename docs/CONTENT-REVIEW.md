# Content review and delivery

This is a local editorial workflow for demo package v4, checked on **6 October 2026**. It is not a claim that the course or wording has human publication approval.

[Migri's citizenship FAQ](https://migri.fi/ukk-suomen-kansalaisuus) still says that learning material is unavailable and registration is not open. Recheck the FAQ and [the 2027 law-change page](https://migri.fi/kansalaisuuslain-muutos-2027) before making product claims about the test. The app displays a dated, explicitly offline snapshot in Study material. It does not poll or silently update this information.

## What was checked

`source-review.json` maps each of the 24 FI/SV demo variants to:

- its source URL and a directly inspected evidence page;
- the relevant section or heading and a short claim summary;
- the check date and a SHA-256 fingerprint of the entire question, including translations;
- `ASSISTANT_SOURCE_CHECK` and `publicationApproved: false`.

The checks cover the answer keys and short factual explanations. English support paraphrases the same facts. They do not certify Finnish easy language, Swedish fluency, completeness, legal accuracy for individual cases, or official exam coverage.

InfoFinland's FI/SV history, employment, safety and library pages and Suomi.fi's emergency-service pages were read. Finlex's consolidated HTML did not expose the statute text to the fetch used here. Constitutional sections were read in the original Finnish/Swedish statute PDFs, and the ledger records that limitation. Do not describe this as a comprehensive current-law audit. A civic-content editor should verify the consolidated text before public distribution.

## Checks an author runs

From the project root, with Python 3.10+ and the Android toolchain:

```powershell
python scripts/verify-content.py
python scripts/test_content_review.py
.\scripts\verify-source.ps1
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug :app:lintRelease :app:assembleRelease :app:bundleRelease
```

The content gate fails if wording or an answer changes after the recorded source check, review records are missing/duplicated, language variants disagree, English support is misaligned, or the demo is promoted to official/human-approved status. Negative tests exercise these failures. Kotlin tests use the actual JSON decoder and importer as well; the script is an additional editorial gate, not a second importer.

`python scripts/verify-content.py --fingerprints` prints fingerprints for review. It does not modify or approve the ledger. A matching hash proves only that the reviewed bytes have not changed; it does not prove that an editor's claim is true.

## Editorial handoff

1. Read the original source and identify the exact claim being taught. Verify the correct answer, plausible wrong answers, explanation, vocabulary and all language variants together.
2. Have a Finnish/Swedish civic-content editor review naturalness, ambiguity and difficulty. Review easy-Finnish wording separately; do not use a certified selkokieli claim without that review. Have an English-language editor review supporting explanations.
3. Record evidence, section, date, reviewer and reuse/attribution decision. Keep source checking separate from language review and publication approval. The current ledger intentionally cannot act as human approval.
4. Increase the package version. Change the question revision for changes to the practised content or facts. Supporting-translation-only changes can retain the question revision and learning progress. Run the checks before delivery.
5. Test the exact file through Settings → Study material: preview, inspect changed questions and translations, cancel, reselect, confirm, restart, and check history and review counts. The local full-package importer is the current delivery mechanism. There is no server, signing infrastructure, remote updater or store publication.

Keep official material in a separately reviewed package when it arrives. `OFFICIALLY_SOURCED` means app-authored material based on official sources. `OFFICIAL` requires actual official authorship and reviewed reuse rights; the source being a government site is insufficient. Imported statuses remain assertions supplied by the file, not a verification result from the app.

## Still pending

Specialist bilingual and easy-language review, source-reuse approval, official topic coverage, and a production distribution/trust model require human or external input. No such approval was fabricated in this iteration.
