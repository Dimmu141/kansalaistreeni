# Native-speaker review of support translations

Every support language except Russian is a machine draft: study content (questions, explanations, lessons, topic names, learning goals), the interface strings and the store-listing draft. The app labels unreviewed explanations as machine translations. This guide is for the maintainer: how to hand a reviewer one self-contained packet and how to apply what comes back. Reviewer instructions themselves are in `reviewer-intro/` (one file per language, followed by the English text).

## Build a packet

```
python scripts/review-packet.py build ar
```

This writes `build/review-packets/kansalaistreeni-ar-review.zip` (not committed; rebuild whenever content changes). It contains:

| File | What the reviewer does | Size per language |
| --- | --- | --- |
| `0-READ-ME-FIRST.txt` | Instructions in their language, then English | — |
| `1-study-content.csv` | Mark each row OK or Fix with a correction | 271 rows (Russian: 0, already reviewed) |
| `2-app-interface.csv` | Same, for menus, buttons and messages | about 420–500 strings |
| `3-store-listing.txt` | Comment on the Play description draft | 1 page |
| `4-glossary.csv` | Reference: fixed renderings of official names | about 310 terms |

The CSV files are UTF-8 with a byte-order mark and open directly in Excel or Google Sheets. Study content is grouped by topic (topic name, lesson, questions, learning goals), so a reviewer can stop after one topic or split the eight topics with someone else. Expect several hours for the whole study content; one topic is about 35 rows. `--contact` changes the return address (default: the Play feedback address).

## Finding reviewers

Good places to ask, in rough order: teachers and volunteers in integration training (kotoutumiskoulutus) and Finnish courses, the learners' own community associations, people who have recently passed the citizenship process themselves, and university language students. Ask for a native speaker who also reads Finnish well enough to compare. Two reviewers per language are better than one; split topics between them.

For Persian, the text is written to suit both Iranian Persian and Dari; a reviewer from Afghanistan is especially useful. For Arabic, the aim is Modern Standard Arabic at about B1, readable by Iraqi and Syrian learners.

Only ask; never pay or promise anything on the project's behalf without deciding it yourself. Reviewer names end up in `docs/translation-review.json`, which is public on GitHub, so record a name only with the reviewer's consent; otherwise use a role such as "Native Arabic reviewer".

## Applying a returned sheet

```
python scripts/review-packet.py read ar returned.csv
```

This changes nothing. It counts accepted, fixed and untouched rows, prints every fix with the reviewer's comment, and prints the `verify-translations.py --mark-reviewed` command for the accepted rows. Then:

1. Apply each fix by hand in `app/src/main/assets/demo-content.json` (the matching `translations.<lang>` field, lesson or objective statement), keeping the termbase renderings and option IDs.
2. Run `python scripts/verify-translations.py --record ar`. Edited items get a new fingerprint and stay MACHINE_DRAFT.
3. Mark the reviewed items, both the accepted rows and the fixed ones once corrected: `python scripts/verify-translations.py --mark-reviewed ar --reviewer "NAME" ID ...`. This sets `"reviewed": true`, which removes the machine-translation label in the app.
4. Apply interface fixes in `app/src/main/res/values-<lang>/strings.xml` (escape apostrophes as `\'`) and store-listing fixes in `play/store-listing.md`.
5. Raise the content package version, run `scripts/verify.ps1` and the translation, content and Android checks, and note the review in the release notes.

A sheet built from older content may list IDs whose Finnish source has changed since; `read` reports IDs missing from the ledger, and `verify-translations.py` fails on stale translations, so nothing outdated can be marked reviewed silently.

## What a review does not cover

The reviewer checks the language, not Finnish law or facts; factual review remains the separate process in [HUMAN-CONTENT-REVIEW.md](../HUMAN-CONTENT-REVIEW.md). A reviewed translation still teaches only what the Finnish material says.
