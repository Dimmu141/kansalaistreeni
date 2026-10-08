# Content authoring and local imports

The app now accepts a local JSON content package through **Settings → Study material → Choose a content file**. Selection and preview do not change installed content. The learner must choose **Replace study material** to commit the reviewed file.

This is an offline import mechanism, not an editorial approval system. Valid JSON, an HTTPS link or an `OFFICIAL` label cannot establish factual accuracy, authenticity or permission to reuse material. Review those independently before distributing a package. No official exam format or threshold is defined here.

## Package format

Use `app/src/main/assets/demo-content.json` as the complete, runnable example. It is schema version 1, package version 5, with 30 Finnish and 30 Swedish questions, all DEMO. It must remain labelled DEMO unless actual content review justifies a different provenance.

```json
{
  "schemaVersion": 1,
  "packageId": "your-stable-package-id",
  "version": 1,
  "status": "EDITORIAL",
  "topics": [],
  "questions": []
}
```

The empty arrays above illustrate the envelope only; a valid package needs at least one topic and question. Every question's `topicId` must refer to a topic in the package. A full package replaces the whole current dataset; it does not append questions. Omitted questions leave practice and the review queue.

Topics contain `id`, `title`, `description`, `order`, and optional `translations`, a language-tag map to `{ "title": "...", "description": "..." }`. Topic IDs use lowercase letters, digits and hyphens.

Questions contain `id`, `revision`, `topicId`, `text`, `options`, `correctOptionId`, `explanation`, optional `easyExplanation`, optional `vocabulary`, `source`, `language`, `difficulty`, `tags`, `status`, and optional `translations`.

- `options` contains at least two `{ "id": "...", "text": "..." }` objects. Exactly one option ID matches `correctOptionId`; option IDs are unique within a question.
- `vocabulary` contains `{ "term": "...", "meaning": "...", "swedish": "..." }`. `term` is Finnish and `meaning` is English. Swedish questions show `swedish` as the main term, with Finnish and English meanings; omit the Swedish field only if unavailable. Easier Finnish explanations appear only for Finnish questions.
- `source` contains a nonblank `title` and a valid HTTPS `url`. Link validity and content accuracy are separate editorial checks.
- `difficulty` is `EASY`, `MEDIUM` or `HARD`.
- Provenance is `DEMO`, `EDITORIAL`, `OFFICIALLY_SOURCED` or `OFFICIAL`. `OFFICIALLY_SOURCED` means app-authored material reviewed against official sources; it does not mean an official exam question. `OFFICIAL` is reserved for authentic official learning content whose origin, rights and attribution have been reviewed. A DEMO package may contain only DEMO questions.
- The importer accepts `fi`, `sv`, or mixed packages. The saved study-language choice filters questions, topics, review and statistics. Missing-language packages show an empty state, with no fallback. Use distinct stable IDs for independently practised language variants, such as `democracy-1-fi` and `democracy-1-sv`. Question translation maps supply supporting explanations after answering; they do not create independently scored questions.

## Question formats (content schema 2)

From v0.17 a question may set `format` to `SINGLE_CHOICE` (the default when omitted) or `TRUE_FALSE`. A `TRUE_FALSE` question's `text` is a statement. It must have exactly two options with the IDs `true` and `false`, in either order; `correctOptionId` is one of them. The app shows the answers as two large buttons labelled in the interface language (Oikein/Väärin, True/False), so the option `text` is used only in the import preview. English support translations keep the same two option IDs.

A package that contains any `TRUE_FALSE` question must declare `"schemaVersion": 2`. Schema 1 packages remain valid and every question in them is single choice. Questions installed before v0.17, saved assessment snapshots and backups have no `format` field and read as single choice; their IDs and revisions do not change, so progress is kept. App versions before v0.17 reject schema 2 packages instead of showing a statement as an ordinary choice.

Practice assessments aim for about 30% true/false items and fill from the other format when one is missing. The length and share are set in one place, `AssessmentMix` in `domain/Assessment.kt`; they are practice choices, not the official exam format.

## Learning objectives (content schema 2)

A schema 2 package may list `objectives`. Each has a lowercase `id`, a `topicId`, a `statement` map with Finnish (`fi`) and Swedish (`sv`) text and optional English (`en`), and an optional `sourceRef` such as "Perustuslaki 11 §". When a package lists objectives, every question must list at least one existing objective in `objectiveIds`. A question may also serve an objective in another topic. Packages without objectives stay valid, but then no question may reference one.

```json
"objectives": [
  { "id": "rights-religion", "topicId": "rights", "sourceRef": "Perustuslaki 11 §",
    "statement": { "fi": "Uskonnon ja omantunnon vapaus", "sv": "Religions- och samvetsfrihet", "en": "Freedom of religion and conscience" } }
]
```

Objectives are the mapping layer for the official learning material once it is published. They are not the official syllabus. **Settings → Study material → Objective coverage** lists every objective by topic with its question count per study language, and highlights objectives with fewer than three questions in a language the package teaches. The import preview reports the same totals. `python scripts/verify-content.py --write-coverage` regenerates `docs/CONTENT-COVERAGE.md` from the bundled package; the content gate fails when that report is out of date. Objectives are stored in their own table (Room schema 7); the 6→7 migration only adds that table.

## Update rules and progress

In v0.15, topics may include an optional `lessons` map keyed by `fi`, `sv`, or supporting `en`. A lesson contains `id`, positive `revision`, `introduction`, `status`, and ordered `sections`. Each section contains a stable `id`, `title`, `body`, one or more HTTPS `sources` (`title`/`url`), and an optional nonblank `easyBody` only for Finnish. English support must use the same lesson ID and ordered section IDs as every native variant. No native lesson is silently replaced by English or the other study language. The bundled demo has three sections per topic in all three variants; imports may omit lessons entirely.

Lesson text is authored independently from question explanations. The UI shows the selected study-language lesson and the existing easy-Finnish/English preferences. Imported material without a matching native lesson keeps the original question-explanation reading view. Vocabulary still derives from question items, deduplicating native terms/meanings while retaining question contexts; there is no separate vocabulary bank or scored word mastery. Reading makes no writes to attempts or question mastery.

Schema version 1 remains readable with an optional lessons field, but v0.15 or newer is required to preserve/display authored lessons. Room schema 6 adds a lesson JSON column with an empty-map default for existing databases. Lesson variants survive import, restart and both backup formats. Old backups without lessons still parse; the normal startup rules may upgrade an old bundled package. Study-material previews let the learner expand all incoming lesson variants, including sources, before confirming replacement.

Increase the package version for any lesson addition/removal/update. Retaining a native lesson ID requires a higher lesson revision for wording, source or other native changes; revision downgrades are rejected. English-only supporting edits require a package version but may keep native lesson/question revisions. Lesson revisions are independent of question revisions: a lesson update does not reset question mastery. Removing a lesson makes reading fall back to explanations. No reading-completion score or lesson spaced repetition is stored. The source-review ledger fingerprints complete lesson variants and records evidence for every section/source.

An exercise selects up to ten matching words in shuffled order and saves their IDs, source question revisions, position and reveal state. Restoring after a source revision changes shows a restart message rather than attaching a saved reveal to revised material. This is an unscored recall exercise, not vocabulary spaced repetition. Separate word mastery remains a future decision requiring learner evidence.

1. Keep the package ID stable for successive versions. Increase its positive integer `version` for every material update, including topic-only changes. Downgrades and changed material at the installed version are rejected. Reselecting the same material produces a disabled, already-installed preview, even when JSON formatting differs or the material was restored from a backup. Every persisted topic/question field is compared. Question and option order remain meaningful; topic array order is normalized by explicit `order`, preserving array order for ties. Whitespace and JSON object-key order are not material updates.
2. Keep question IDs globally stable across packages. Do not reuse an ID for an unrelated question or change its language. Language changes are rejected even with a higher revision; retained attempt history also protects removed IDs. Shared IDs retain progress when the practised content and revision are unchanged, including translation-only updates.
3. Increase a question's positive integer `revision` whenever practised content or factual fields change, including the native explanation, sources or tags. Supporting-translation-only edits may retain the revision. A revision increase starts current mastery at zero. From v0.17, moving a question to another topic or changing its `objectiveIds` is **reclassification**: it keeps the revision, mastery and review date, and the preview counts it separately. Reclassification cannot hide another change; any other edit still needs a new revision. See [Topic migration](TOPIC-MIGRATION.md).
4. If a removed, previously answered question returns, give it a revision higher than every saved attempt for that ID. This prevents old progress from silently attaching to replacement content. The app cannot compare a removed question's old text because historical attempts do not retain full payloads.
5. New/revised questions start unpractised. Unchanged questions keep scores and dates. Removed questions no longer contribute to current mastery. All attempts remain in lifetime accuracy and calendar-day streaks for their saved language. Learning history does not transfer between languages.

Preview reports new, revised, translation-only, unchanged and removed question counts, plus added/changed/removed lesson language-version counts. It includes every incoming question, expandable answers/explanations and source references. An update of topic labels alone leaves question progress intact. Imported content may change the overall score by changing the number of questions, without changing any retained question's mastery.

Confirmation checks the installed package identity/version/hash inside the same transaction as the update. A content update between preview and confirmation invalidates the preview; the learner must select the file again. Answers saved after preview are retained. Import failure leaves the current package intact. A newer or different imported package is not overwritten by the bundled demo at restart.

An already-installed reimport makes no material, metadata or progress changes, so harmless reformatting cannot invalidate a pending update/backup preview. The file hash still identifies the installed transaction baseline; it is not an authenticity signature. Package-level provenance is a validated declaration rather than persisted learning data. Changing that envelope label alone cannot promote the installed questions: their individual statuses must actually change through a versioned material update and the usual editorial process.

## Preparing a test update

Copy the bundled JSON to a separate file, increase its package version to 6, and change a topic description. Keep all question payloads unchanged. The preview should show 60 unchanged questions, zero revised/new/removed questions, and 30 questions in each language. Existing progress remains after confirmation.

For a revision test, change one explanation and increase that question's revision to 2. The preview should show one revised question and 59 unchanged questions. That revised question starts unpractised; previous attempts remain in its language history.

These are test fixtures, not reviewed study material. Do not redistribute altered demo files as official content.

## File handling

Files must be UTF-8 JSON and at most 2 MiB. A UTF-8 byte order mark is accepted; invalid UTF-8 and oversize streams are rejected. The limit is checked on bytes actually read, rather than trusting provider size metadata. Files are opened with Android's [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files), without broad storage permission. The app reads the selected document once and retains the parsed preview in memory. It does not modify the source file or persist a file URI permission.

Activity recreation retains the preview through its ViewModel. After process death, choose the file again; unfinished previews are intentionally not persisted. Device tests inject a database-write failure and verify atomic rollback and a successful retry. Process death during an active import write and physical storage exhaustion remain unverified. A user-supplied provenance label remains an assertion; no signature verification, trusted-content catalogue or remote updater is included.

## Supporting translations in v0.6

All bundled FI/SV demo variants include `translations.en` with translated text/options and an English explanation. The app shows only the supporting explanation, after answering and when enabled in Settings. Questions and their original explanation remain in the study language. An imported question without English support shows an explicit message instead of substituting other content.

A **translation-only** change requires a new package version but may keep the question revision. The importer compares every other question field against the installed version. If text, options, answer key, source, explanation, vocabulary, tags, difficulty, status or base language also changes, the usual revision rules still apply. The preview counts translation-only updates separately; they keep mastery and review schedules. Increasing the revision deliberately still resets current mastery.

Translation language tags must be valid and differ from the base language, and option IDs must remain aligned. Blank vocabulary, blank easy explanations and source URLs with embedded credentials are rejected.

See [Content review](CONTENT-REVIEW.md) for source checks and fingerprints. Changing a supporting translation preserves learning progress but still requires a fresh editorial review entry. Machine validation cannot approve factual or linguistic quality.
