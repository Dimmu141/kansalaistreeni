#!/usr/bin/env python3
"""Build a review packet for a native-speaker translation reviewer, and read a returned sheet back.

  review-packet.py build LANG [--contact EMAIL]   write build/review-packets/kansalaistreeni-LANG-review.zip
  review-packet.py read LANG RETURNED.csv          summarise a returned study-content sheet

The packet holds, for one support language: the reviewer's instructions (their language, then English),
the study content still marked MACHINE_DRAFT, the app's interface strings, the store-listing draft and
the termbase. Spreadsheets are UTF-8 CSV with a byte-order mark, so Excel and Google Sheets open them
directly. Reading a returned sheet never changes content: it lists the fixes to apply by hand and prints
the verify-translations.py command that marks the rows the reviewer accepted as OK.
"""
import argparse
import csv
import importlib.util
import json
import re
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "build/review-packets"
INTRO = ROOT / "docs/translations/reviewer-intro"
LISTING = ROOT / "play/store-listing.md"
DEFAULT_CONTACT = "dmitry.gurbanov@protonmail.com"
LANGUAGES = {"ru": "Русский", "ar": "العربية", "fa": "فارسی", "so": "Soomaali", "uk": "Українська", "tr": "Türkçe", "th": "ไทย"}
VERDICT, COMMENT = "OK / Fix", "Corrected text or comment"

_spec = importlib.util.spec_from_file_location("verify_translations", ROOT / "scripts/verify-translations.py")
vt = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(vt)


def write_csv(path, header, rows):
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(header)
        writer.writerows(rows)
    return len(rows)


def content_rows(lang):
    """Unreviewed study content, grouped by topic so several reviewers can split the work."""
    content = vt.read(vt.CONTENT)
    records = vt.read(vt.LEDGER)["languages"][lang]
    # Reuse the ledger's sheet export, but in a scratch folder so the committed review sheets stay untouched.
    with tempfile.TemporaryDirectory() as scratch:
        saved, vt.ROOT = vt.ROOT, Path(scratch)
        try:
            with vt.export(content, {"languages": {lang: records}}, lang).open(encoding="utf-8-sig", newline="") as handle:
                sheet = {row["id"]: row for row in csv.DictReader(handle)}
        finally:
            vt.ROOT = saved
    topics = {t["id"]: (t["order"], t.get("translations", {}).get("en", {}).get("title", t["title"])) for t in content["topics"]}
    topic_of = {q["id"]: q["topicId"] for q in content["questions"]}
    topic_of.update({f"objective:{o['id']}": o["topicId"] for o in content.get("objectives", [])})
    kinds = {"topic": "Topic", "lesson": "Lesson", "objective": "Learning goal"}
    rows = []
    for key, row in sheet.items():
        if records[key]["status"] != "MACHINE_DRAFT":
            continue
        prefix = key.split(":")[0] if ":" in key else None
        topic = key.split(":", 1)[1] if prefix in ("topic", "lesson") else topic_of[key]
        rows.append((topics[topic][0], ["topic", "lesson", None, "objective"].index(prefix), key,
                     [topics[topic][1], kinds.get(prefix, "Question"), key, row["finnish"], row["english"], row[lang], "", ""]))
    rows.sort(key=lambda r: r[:3])
    return [r[3] for r in rows]


def strings(path):
    """name -> text for strings and plural items of one strings.xml; untranslatable entries are skipped."""
    root = ET.parse(path).getroot()
    out = {}
    for node in root:
        if node.get("translatable") == "false":
            continue
        if node.tag == "string":
            out[node.get("name")] = "".join(node.itertext()).replace("\\'", "'").replace('\\"', '"')
        elif node.tag == "plurals":
            for item in node:
                out[f"{node.get('name')} [{item.get('quantity')}]"] = "".join(item.itertext()).replace("\\'", "'")
    return out


def interface_rows(lang):
    english = strings(ROOT / "app/src/main/res/values-en/strings.xml")
    translated = strings(ROOT / f"app/src/main/res/values-{lang}/strings.xml")
    # Placeholders are filled in by the app; a translated or dropped one breaks the screen.
    note = ["(note)", "Keep codes such as %1$s, %2$s and %d exactly as they are, in a natural place in the sentence.", "", "", ""]
    return [note] + [[key, english.get(key, ""), text, "", ""] for key, text in translated.items()]


def listing(lang):
    text = LISTING.read_text(encoding="utf-8")
    english = text.split("## English (en-US)")[1].split("\n## ")[0].strip()
    match = re.search(rf"\n### {re.escape(LANGUAGES[lang])}[^\n]*\n(.*?)(?=\n### |\n## )", text, re.S)
    if not match:
        raise ValueError(f"No {lang} listing draft in {LISTING.name}")
    return f"=== {LANGUAGES[lang]} (please check) ===\n\n{match.group(1).strip()}\n\n\n=== English (for comparison) ===\n\n{english}\n"


def glossary_rows(lang):
    termbase = vt.read(ROOT / f"docs/termbase/{lang}.json")
    rows = [["(rule)", rule, "", ""] for rule in termbase["rules"]]
    rows += [[fi, entry.get(lang, ""), "yes" if entry.get("keepFinnish") else "", ""] for fi, entry in termbase["terms"].items()]
    return rows


def build(lang, contact):
    folder = f"kansalaistreeni-{lang}-review"
    stage = OUT / folder
    stage.mkdir(parents=True, exist_ok=True)
    counts = {
        "content": write_csv(stage / "1-study-content.csv", ["Topic", "Type", "ID", "Finnish (exam language)", "English",
                             LANGUAGES[lang], VERDICT, COMMENT], content_rows(lang)),
        "interface": write_csv(stage / "2-app-interface.csv", ["Key", "English", LANGUAGES[lang], VERDICT, COMMENT], interface_rows(lang)),
        "glossary": write_csv(stage / "4-glossary.csv", ["Finnish", LANGUAGES[lang], "Keep the Finnish word in brackets", "Comment"], glossary_rows(lang)),
    }
    (stage / "3-store-listing.txt").write_text(listing(lang), encoding="utf-8-sig")
    # The reviewer's own language first, then the same text in English.
    intro = "\n\n{}\n\n".format("=" * 60).join((INTRO / name).read_text(encoding="utf-8") for name in (f"{lang}.txt", "english.txt"))
    intro = intro.replace("{CONTACT}", contact)
    for key, value in counts.items():
        intro = intro.replace("{" + key.upper() + "}", str(value))
    (stage / "0-READ-ME-FIRST.txt").write_text(intro, encoding="utf-8-sig")
    archive = OUT / f"{folder}.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as zipped:
        for file in sorted(stage.iterdir()):
            zipped.write(file, f"{folder}/{file.name}")
    print(f"Wrote {archive.relative_to(ROOT)}: {counts['content']} content rows, {counts['interface']} interface strings, "
          f"{counts['glossary']} glossary rows.")


def read_returned(lang, path):
    with Path(path).open(encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))
    current = vt.read(vt.LEDGER)["languages"][lang]
    ok, fixes, blank = [], [], 0
    for row in rows:
        verdict = (row.get(VERDICT) or "").strip().lower()
        if verdict in ("ok", "yes", "kyllä", "ок", "да", "так", "evet", "haa", "بله", "نعم", "ใช่"):
            ok.append(row["ID"])
        elif verdict or (row.get(COMMENT) or "").strip():
            fixes.append(row)
        else:
            blank += 1
    unknown = [key for key in ok if key not in current]
    print(f"{len(ok)} accepted, {len(fixes)} with fixes or comments, {blank} not reviewed.")
    for row in fixes:
        print(f"\n--- {row['ID']} ({row['Type']}, {row['Topic']})\n{row.get(COMMENT, '').strip()}")
    if unknown:
        print(f"\nIDs not in the ledger (sheet from another content version?): {', '.join(unknown)}")
    accepted = [key for key in ok if key in current]
    if accepted:
        print("\nAfter checking the fixes, mark the accepted rows as reviewed (the reviewer's name is published in the "
              "public repository; use a role such as 'Native Arabic reviewer' if they prefer):")
        print(f'  python scripts/verify-translations.py --mark-reviewed {lang} --reviewer "NAME" {" ".join(accepted)}')


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    b = sub.add_parser("build")
    b.add_argument("lang", choices=sorted(LANGUAGES))
    b.add_argument("--contact", default=DEFAULT_CONTACT)
    r = sub.add_parser("read")
    r.add_argument("lang", choices=sorted(LANGUAGES))
    r.add_argument("sheet")
    args = parser.parse_args()
    if args.command == "build":
        build(args.lang, args.contact)
    else:
        read_returned(args.lang, args.sheet)


if __name__ == "__main__":
    sys.exit(main())
