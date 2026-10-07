"""Check the bundled demo against its dated source-review ledger, using only stdlib.

This detects changed/unreviewed content; it cannot establish factual truth or human approval.
"""
import argparse
import hashlib
import json
import sys
from datetime import date
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parent.parent


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON key: {key}")
        result[key] = value
    return result


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"), object_pairs_hook=unique_object)


def fingerprint(question):
    # Includes translation wording. Structural validation is also run by JVM tests.
    text = json.dumps(question, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def verify(content, ledger):
    if content["status"] != "DEMO" or any(q["status"] != "DEMO" for q in content["questions"]):
        raise ValueError("The bundled dataset must remain explicitly DEMO")
    if ledger["packageId"] != content["packageId"] or ledger["version"] != content["version"]:
        raise ValueError("Review ledger does not match the content package")
    questions = {q["id"]: q for q in content["questions"]}
    reviews = {r["questionId"]: r for r in ledger["reviews"]}
    if len(questions) != len(content["questions"]) or len(reviews) != len(ledger["reviews"]) or questions.keys() != reviews.keys():
        raise ValueError("Every question needs exactly one review record")
    for question_id, q in questions.items():
        review = reviews[question_id]
        if review["sha256"] != fingerprint(q):
            raise ValueError(f"Content changed after source review: {question_id}")
        if review["sourceUrl"] != q["source"]["url"] or not review["claim"] or not review["locator"]:
            raise ValueError(f"Incomplete source review: {question_id}")
        if date.fromisoformat(review["checkedAt"]) > date.today():
            raise ValueError(f"Future review date: {question_id}")
        if review["publicationApproved"] or review["reviewType"] != "ASSISTANT_SOURCE_CHECK":
            raise ValueError("Demo source checks must not imply human publication approval")
        evidence = urlparse(review["evidenceUrl"])
        if evidence.scheme != "https" or evidence.username or evidence.hostname not in {"www.finlex.fi", "infofinland.fi", "www.suomi.fi"}:
            raise ValueError(f"Unexpected evidence origin: {question_id}")
        en = q.get("translations", {}).get("en")
        if not en or [o["id"] for o in en["options"]] != [o["id"] for o in q["options"]]:
            raise ValueError(f"Missing or misaligned English support: {question_id}")
    finnish = {q["id"]: q for q in questions.values() if q["language"] == "fi"}
    swedish = {q["id"].removesuffix("-sv"): q for q in questions.values() if q["language"] == "sv"}
    if finnish.keys() != swedish.keys():
        raise ValueError("Bilingual demo concepts do not match")
    for key, fi in finnish.items():
        sv = swedish[key]
        if fi["correctOptionId"] != sv["correctOptionId"] or fi["topicId"] != sv["topicId"] or fi["translations"]["en"] != sv["translations"]["en"]:
            raise ValueError(f"Bilingual answer key or English support mismatch: {key}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fingerprints", action="store_true", help="Print fingerprints for an editor; does not update or approve the ledger")
    args = parser.parse_args()
    path = ROOT / "app/src/main/assets/demo-content.json"
    if path.stat().st_size > 2 * 1024 * 1024:
        raise ValueError("Bundled content exceeds the import limit")
    content = read_json(path)
    if args.fingerprints:
        print(json.dumps({q["id"]: fingerprint(q) for q in content["questions"]}, indent=2))
        return
    verify(content, read_json(ROOT / "docs/source-review.json"))
    print(f"Content review ledger passed: {len(content['questions'])} DEMO variants, package v{content['version']}; human publication approval remains pending.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError) as error:
        print(f"Content check failed: {error}", file=sys.stderr)
        sys.exit(1)
