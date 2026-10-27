#!/usr/bin/env python3
"""Check support translations (languages other than fi, sv and en) against their review ledger.

docs/translation-review.json records, per language and item, a fingerprint of the Finnish source the
translation was made from and of the translation itself, with a status:
- MACHINE_DRAFT: drafted by a model, shown in the app with a machine-translation label;
- HUMAN_REVIEWED: checked by a named person; the content then carries "reviewed": true.

The gate fails when a translation is missing, misaligned, inconsistent with the termbase or changed
since it was recorded, or when its Finnish source changed afterwards (the translation is stale).
It also reports, without failing, places that may need a person's eye: an institution from the
termbase whose Finnish name is not given, and answer-length cues in the translated options.

  verify-translations.py                         check every recorded language
  verify-translations.py --record ru             record current Russian items (new or changed -> MACHINE_DRAFT)
  verify-translations.py --mark-reviewed ru --reviewer "Name" all|ID...
  verify-translations.py --export ru             write docs/translations/ru-review.csv for a reviewer

Machine checks cannot establish that a translation is correct; only a person's review can.
"""
import argparse
import csv
import hashlib
import importlib.util
import json
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CONTENT = ROOT / "app/src/main/assets/demo-content.json"
LEDGER = ROOT / "docs/translation-review.json"
STATUSES = ("MACHINE_DRAFT", "HUMAN_REVIEWED")

_spec = importlib.util.spec_from_file_location("verify_content", ROOT / "scripts/verify-content.py")
verify_content = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(verify_content)


def read(path):
    return verify_content.read_json(path)


def write(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def sha(value):
    text = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def without_review_flag(value):
    return {k: v for k, v in value.items() if k != "reviewed"}


def items(content, lang):
    """(key, source, translation) for every translatable item. The source is what the translation was
    made from: the Finnish item and its English support text."""
    questions = {q["id"]: q for q in content["questions"]}
    for q in content["questions"]:
        if q["language"] != "fi":
            continue
        source = {k: v for k, v in q.items() if k not in ("translations",) + verify_content.CLASSIFICATION}
        source["en"] = q.get("translations", {}).get("en")
        yield q["id"], source, q.get("translations", {}).get(lang), questions.get(q["id"] + "-sv")
    for topic in content["topics"]:
        yield f"topic:{topic['id']}", {"title": topic["title"], "description": topic["description"],
                                        "en": topic.get("translations", {}).get("en")}, topic.get("translations", {}).get(lang), None
        lessons = topic.get("lessons", {})
        yield f"lesson:{topic['id']}", {"fi": lessons.get("fi"), "en": lessons.get("en")}, lessons.get(lang), None
    for objective in content.get("objectives", []):
        statement = objective["statement"]
        yield f"objective:{objective['id']}", {"fi": statement.get("fi"), "en": statement.get("en")}, statement.get(lang), None


def check_structure(content, lang, termbase):
    errors = []
    for key, source, translation, twin in items(content, lang):
        if translation is None:
            errors.append(f"{key}: no {lang} translation")
            continue
        if key.startswith("lesson:"):
            english = source["en"]
            if [s["id"] for s in translation["sections"]] != [s["id"] for s in english["sections"]] or translation["id"] != english["id"]:
                errors.append(f"{key}: sections differ from the English lesson")
            elif [s["sources"] for s in translation["sections"]] != [s["sources"] for s in english["sections"]]:
                errors.append(f"{key}: sources differ from the English lesson")
            continue
        if ":" in key:
            continue
        if twin is None or twin.get("translations", {}).get(lang) is None:
            errors.append(f"{key}: Swedish twin has no {lang} translation")
        elif without_review_flag(twin["translations"][lang]) | {"vocabulary": None} != without_review_flag(translation) | {"vocabulary": None}:
            errors.append(f"{key}: Finnish and Swedish variants have different {lang} text")
        if [o["id"] for o in translation["options"]] != [o["id"] for o in source["options"]]:
            errors.append(f"{key}: options misaligned")
        for target in filter(None, (content_question(content, key), twin)):
            vocabulary = target["translations"][lang].get("vocabulary", {})
            expected = {w["term"]: termbase.get(w["term"], {}).get(lang) for w in target.get("vocabulary", [])}
            if vocabulary != expected:
                errors.append(f"{target['id']}: vocabulary meanings differ from docs/termbase/{lang}.json")
    return errors


def content_question(content, question_id):
    return next(q for q in content["questions"] if q["id"] == question_id)


def advisories(content, lang, termbase):
    """Things a reviewer should look at; never a failure on their own."""
    notes = []
    for q in content["questions"]:
        if q["language"] != "fi" or lang not in q.get("translations", {}):
            continue
        t = q["translations"][lang]
        text = (t["text"] + " " + t["explanation"] + " " + " ".join(o["text"] for o in t["options"])).lower()
        for word in q.get("vocabulary", []):
            entry = termbase.get(word["term"], {})
            # Finnish words inflect, so look for the first five letters of each part of the term.
            if entry.get("keepFinnish") and not all(part[:5].lower() in text for part in word["term"].split()):
                notes.append(f"{q['id']}: Finnish name of '{word['term']}' not given in the {lang} text")
    bias = verify_content.answer_key_bias({"questions": [
        {**q, "language": lang, "options": q["translations"][lang]["options"]} for q in content["questions"]
        if q["language"] == "fi" and lang in q.get("translations", {})]}).get(lang)
    return notes, bias


def check_ledger(content, lang, records):
    errors = []
    current = {key: (source, translation) for key, source, translation, _ in items(content, lang)}
    if set(records) != set(current):
        missing, extra = sorted(set(current) - set(records)), sorted(set(records) - set(current))
        errors.append(f"{lang}: ledger out of date (unrecorded {missing[:3]}, removed {extra[:3]}); run --record {lang}")
    for key, record in records.items():
        if key not in current or current[key][1] is None:
            continue
        source, translation = current[key]
        if record["status"] not in STATUSES:
            errors.append(f"{key}: unknown status {record['status']}")
        if record["sourceSha"] != sha(source):
            errors.append(f"{key}: the Finnish source changed after translation; retranslate, then --record {lang}")
        if record["translationSha"] != sha(without_review_flag(translation) if isinstance(translation, dict) else translation):
            errors.append(f"{key}: {lang} text changed after it was recorded; run --record {lang}")
        reviewed = record["status"] == "HUMAN_REVIEWED"
        if reviewed and not (record.get("reviewer") and record.get("reviewedAt")):
            errors.append(f"{key}: a human review needs a reviewer and a date")
        if isinstance(translation, dict) and translation.get("reviewed", False) != reviewed:
            errors.append(f"{key}: content 'reviewed' flag does not match the ledger status")
    return errors


def record(content, ledger, lang):
    records = ledger.setdefault("languages", {}).setdefault(lang, {})
    fresh = {}
    for key, source, translation, _ in items(content, lang):
        if translation is None:
            raise ValueError(f"{key}: no {lang} translation to record")
        entry = {"sourceSha": sha(source), "translationSha": sha(without_review_flag(translation) if isinstance(translation, dict) else translation)}
        old = records.get(key)
        if old and old["status"] == "HUMAN_REVIEWED" and {k: old[k] for k in entry} == entry:
            fresh[key] = old
        else:
            fresh[key] = {**entry, "status": "MACHINE_DRAFT"}
            set_reviewed(content, key, lang, False)
    ledger["languages"][lang] = fresh
    return sum(r["status"] == "MACHINE_DRAFT" for r in fresh.values())


def set_reviewed(content, key, lang, value):
    """Mirror the ledger status into the content so the app can label unreviewed text."""
    targets = []
    if ":" not in key:
        targets = [q["translations"][lang] for q in content["questions"] if q["id"] in (key, key + "-sv")]
    elif key.startswith("lesson:"):
        targets = [t["lessons"][lang] for t in content["topics"] if t["id"] == key.split(":", 1)[1]]
    for target in targets:
        if value:
            target["reviewed"] = True
        else:
            target.pop("reviewed", None)


def export(content, ledger, lang):
    out = ROOT / f"docs/translations/{lang}-review.csv"
    out.parent.mkdir(parents=True, exist_ok=True)
    records = ledger["languages"][lang]
    with out.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["id", "status", "finnish", "english", lang, "reviewer notes"])
        for key, source, translation, _ in items(content, lang):
            if ":" not in key:
                options = lambda opts: " | ".join(f"{o['id']}) {o['text']}" for o in opts)
                fi = f"{source['text']}\n{options(source['options'])}\nCorrect: {source['correctOptionId']}\n{source['explanation']}"
                en = f"{source['en']['text']}\n{options(source['en']['options'])}\n{source['en']['explanation']}"
                tr = f"{translation['text']}\n{options(translation['options'])}\n{translation['explanation']}"
                if translation.get("vocabulary"):
                    tr += "\n" + "; ".join(f"{k} = {v}" for k, v in translation["vocabulary"].items())
            elif key.startswith("lesson:"):
                fi = "\n\n".join(f"{s['title']}: {s['body']}" for s in [{"title": "", "body": source["fi"]["introduction"]}] + source["fi"]["sections"])
                en = "\n\n".join(f"{s['title']}: {s['body']}" for s in [{"title": "", "body": source["en"]["introduction"]}] + source["en"]["sections"])
                tr = "\n\n".join(f"{s['title']}: {s['body']}" for s in [{"title": "", "body": translation["introduction"]}] + translation["sections"])
            elif key.startswith("topic:"):
                fi, en = f"{source['title']}\n{source['description']}", f"{source['en']['title']}\n{source['en']['description']}"
                tr = f"{translation['title']}\n{translation['description']}"
            else:
                fi, en, tr = source["fi"], source["en"], translation
            writer.writerow([key, records[key]["status"], fi, en, tr, ""])
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    action = parser.add_mutually_exclusive_group()
    action.add_argument("--record", metavar="LANG")
    action.add_argument("--mark-reviewed", metavar="LANG")
    action.add_argument("--export", metavar="LANG")
    parser.add_argument("--reviewer")
    parser.add_argument("ids", nargs="*")
    args = parser.parse_args()
    content = read(CONTENT)
    ledger = read(LEDGER) if LEDGER.is_file() else {"schemaVersion": 1, "languages": {}}

    if args.record:
        termbase = read(ROOT / f"docs/termbase/{args.record}.json")["terms"]
        errors = check_structure(content, args.record, termbase)
        if errors:
            raise ValueError("; ".join(errors[:10]))
        drafts = record(content, ledger, args.record)
        write(CONTENT, content)
        write(LEDGER, ledger)
        print(f"Recorded {args.record}: {len(ledger['languages'][args.record])} items, {drafts} awaiting human review.")
        return
    if args.mark_reviewed:
        lang = args.mark_reviewed
        if not args.reviewer or not args.ids:
            raise ValueError("--mark-reviewed needs --reviewer and item IDs (or 'all')")
        records = ledger["languages"][lang]
        keys = list(records) if args.ids == ["all"] else args.ids
        for key in keys:
            records[key].update(status="HUMAN_REVIEWED", reviewer=args.reviewer, reviewedAt=date.today().isoformat())
            set_reviewed(content, key, lang, True)
        write(CONTENT, content)
        write(LEDGER, ledger)
        print(f"Marked {len(keys)} {lang} items as reviewed by {args.reviewer}. Raise the package version before release.")
        return
    if args.export:
        print(f"Wrote {export(content, ledger, args.export).relative_to(ROOT)}")
        return

    failures = []
    for lang, records in ledger.get("languages", {}).items():
        termbase = read(ROOT / f"docs/termbase/{lang}.json")["terms"]
        failures += check_structure(content, lang, termbase) + check_ledger(content, lang, records)
        notes, bias = advisories(content, lang, termbase)
        reviewed = sum(r["status"] == "HUMAN_REVIEWED" for r in records.values())
        print(f"{lang.upper()}: {len(records)} items, {reviewed} human-reviewed, {len(records) - reviewed} machine drafts.")
        if bias:
            print(f"{lang.upper()} options: longest option correct {bias['longest']:.0%}; correct/distractor length {bias['ratio']:.2f}x")
            if bias["longest"] > verify_content.LONGEST_CORRECT_MAX or bias["ratio"] > verify_content.LENGTH_RATIO_MAX:
                failures.append(f"{lang}: translated options give the answer away by length")
        for note in notes:
            print(f"  review: {note}")
    undeclared = {lang for q in content["questions"] for lang in q.get("translations", {})} - verify_content.REVIEWED_LANGUAGES - set(ledger.get("languages", {}))
    if undeclared:
        failures.append(f"Support languages without a translation ledger: {', '.join(sorted(undeclared))}")
    if failures:
        raise ValueError("\n  ".join(failures[:20]))
    print("Translation ledger passed; machine drafts are labelled in the app until a person reviews them.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError) as error:
        print(f"Translation check failed: {error}", file=sys.stderr)
        sys.exit(1)
