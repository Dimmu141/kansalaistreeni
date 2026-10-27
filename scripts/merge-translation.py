#!/usr/bin/env python3
"""Merge a drafted support translation into the bundled content package.

The draft directory holds JSON files written by a translator or a model:
- any number of question files: { "<Finnish question id>": { "text", "options": {optionId: text}, "explanation" } }
- topics.json: { "topics": { topicId: { title, description, introduction, sections: { sectionId: { title, body } } } },
                 "objectives": { objectiveId: statement } }

One translation per bilingual concept is written to both the Finnish question and its Swedish twin
(`<id>-sv`). Vocabulary meanings come from docs/termbase/<lang>.json, never from the draft, so every
question uses the same word for the same Finnish term. The supporting lesson reuses the English
lesson's IDs and sources. Merged text is marked unreviewed; scripts/verify-translations.py checks it
and records it in docs/translation-review.json. The package version is raised by one, together with
the source-review ledger's version, because a translation-only change keeps question revisions.
"""
import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / "app/src/main/assets/demo-content.json"
LEDGER = ROOT / "docs/source-review.json"


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("language", help="BCP 47 tag of the support language, e.g. ru")
    parser.add_argument("drafts", type=Path, help="Directory of draft JSON files")
    args = parser.parse_args()
    lang = args.language
    if lang in ("fi", "sv"):
        raise ValueError("fi and sv are study languages, not support translations")

    content = read(CONTENT)
    termbase = read(ROOT / f"docs/termbase/{lang}.json")["terms"]
    drafts = {}
    topics_draft = None
    for path in sorted(args.drafts.glob("*.json")):
        data = read(path)
        if path.name == "topics.json":
            topics_draft = data
            continue
        overlap = drafts.keys() & data.keys()
        if overlap:
            raise ValueError(f"Question drafted twice: {sorted(overlap)[:3]}")
        drafts.update(data)

    questions = {q["id"]: q for q in content["questions"]}
    finnish = [q for q in content["questions"] if q["language"] == "fi"]
    missing = [q["id"] for q in finnish if q["id"] not in drafts]
    unknown = [key for key in drafts if key not in questions or questions[key]["language"] != "fi"]
    if missing or unknown:
        raise ValueError(f"Draft does not match the Finnish questions: missing {missing[:5]}, unknown {unknown[:5]}")

    for q in finnish:
        draft = drafts[q["id"]]
        if set(draft["options"]) != {o["id"] for o in q["options"]}:
            raise ValueError(f"Option IDs differ: {q['id']}")
        for target in (q, questions[q["id"] + "-sv"]):
            vocabulary = {}
            for word in target.get("vocabulary", []):
                if word["term"] not in termbase:
                    raise ValueError(f"Term missing from the {lang} termbase: {word['term']}")
                vocabulary[word["term"]] = termbase[word["term"]][lang]
            translation = {
                "text": draft["text"],
                "options": [{"id": o["id"], "text": draft["options"][o["id"]]} for o in target["options"]],
                "explanation": draft["explanation"],
            }
            if vocabulary:
                translation["vocabulary"] = vocabulary
            target.setdefault("translations", {})[lang] = translation

    if topics_draft is None:
        raise ValueError("topics.json is missing")
    for topic in content["topics"]:
        draft = topics_draft["topics"][topic["id"]]
        topic.setdefault("translations", {})[lang] = {"title": draft["title"], "description": draft["description"]}
        english = topic["lessons"]["en"]
        if set(draft["sections"]) != {s["id"] for s in english["sections"]}:
            raise ValueError(f"Lesson sections differ: {topic['id']}")
        topic["lessons"][lang] = {
            "id": english["id"], "revision": english["revision"], "introduction": draft["introduction"],
            "status": english["status"],
            "sections": [{"id": s["id"], "title": draft["sections"][s["id"]]["title"], "body": draft["sections"][s["id"]]["body"],
                          "sources": s["sources"]} for s in english["sections"]],
        }
    for objective in content["objectives"]:
        objective["statement"][lang] = topics_draft["objectives"][objective["id"]]

    content["version"] += 1
    ledger = read(LEDGER)
    ledger["version"] = content["version"]
    write(CONTENT, content)
    write(LEDGER, ledger)
    print(f"Merged {lang}: {len(finnish)} concepts into {2 * len(finnish)} questions, {len(content['topics'])} lessons, "
          f"{len(content['objectives'])} objectives; package v{content['version']}. Next: scripts/verify-translations.py --record {lang}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError) as error:
        print(f"Merge failed: {error}", file=sys.stderr)
        sys.exit(1)
