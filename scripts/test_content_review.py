"""Meaningful negative checks for the editorial gate. Run with Python 3.10+."""
import copy
import unittest
import runpy
from pathlib import Path

module = runpy.run_path(str(Path(__file__).with_name("verify-content.py")))
ROOT, read_json, verify = (module[name] for name in ("ROOT", "read_json", "verify"))
verify_objectives, coverage_markdown = module["verify_objectives"], module["coverage_markdown"]


def objective_package():
    def question(qid, language, refs):
        return {"id": qid, "language": language, "objectiveIds": refs}
    return {
        "packageId": "objectives", "version": 1,
        "topics": [{"id": "rights", "title": "Oikeudet", "order": 0}],
        "objectives": [
            {"id": "wide", "topicId": "rights", "statement": {"fi": "Laaja", "sv": "Bred"}, "sourceRef": "PL 11 §"},
            {"id": "thin", "topicId": "rights", "statement": {"fi": "Kapea", "sv": "Smal"}},
        ],
        "questions": [question(f"q{i}", lang, ["wide"]) for i in range(3) for lang in ("fi", "sv")] + [question("t", "fi", ["thin"])],
    }


class ObjectiveGateTest(unittest.TestCase):
    def test_valid_objectives_pass_and_report_marks_thin(self):
        package = objective_package()
        verify_objectives(package)
        report = coverage_markdown(package)
        self.assertIn("| `wide` | Laaja (PL 11 §) | 3 | 3 |  |", report)
        self.assertIn("| `thin` | Kapea | 1 | 0 | thin |", report)
        self.assertIn("- Thin objectives: 1", report)
        self.assertEqual(report, coverage_markdown(objective_package()))

    def test_missing_or_unknown_references_fail(self):
        for refs in ([], ["missing"], ["wide", "wide"]):
            package = objective_package()
            package["questions"][0]["objectiveIds"] = refs
            with self.assertRaises(ValueError):
                verify_objectives(package)

    def test_objective_needs_topic_and_both_languages(self):
        for change in ({"topicId": "missing"}, {"statement": {"fi": "Vain suomeksi"}}):
            package = objective_package()
            package["objectives"][0].update(change)
            with self.assertRaises(ValueError):
                verify_objectives(package)


class ReviewGateTest(unittest.TestCase):
    def setUp(self):
        self.content = read_json(ROOT / "app/src/main/assets/demo-content.json")
        self.ledger = read_json(ROOT / "docs/source-review.json")

    def test_current_content_passes(self):
        verify(self.content, self.ledger)

    def test_answer_change_requires_new_review(self):
        first = self.content["questions"][0]
        first["correctOptionId"] = next(o["id"] for o in first["options"] if o["id"] != first["correctOptionId"])
        with self.assertRaises(ValueError):
            verify(self.content, self.ledger)

    def test_reclassification_keeps_the_source_review(self):
        # Two questions swap topic and objectives, so every topic and objective keeps its size.
        # Both language variants move together; the bilingual check requires matching topics.
        first = self.content["questions"][0]
        other = next(q for q in self.content["questions"] if q["topicId"] != first["topicId"])
        moves = {first["id"]: (other["topicId"], other["objectiveIds"]), other["id"].removesuffix("-sv"): (first["topicId"], first["objectiveIds"])}
        for question in self.content["questions"]:
            move = moves.get(question["id"].removesuffix("-sv"))
            if move:
                question["topicId"], question["objectiveIds"] = move[0], list(move[1])
        verify(self.content, self.ledger)

    def test_translation_change_requires_new_review(self):
        self.content["questions"][0]["translations"]["en"]["explanation"] = "Wrong fact."
        with self.assertRaises(ValueError):
            verify(self.content, self.ledger)

    def test_missing_or_duplicate_reviews_fail(self):
        for reviews in (self.ledger["reviews"][1:], self.ledger["reviews"] + [copy.deepcopy(self.ledger["reviews"][0])]):
            with self.assertRaises(ValueError):
                verify(self.content, {**self.ledger, "reviews": reviews})

    def test_check_cannot_silently_promote_demo(self):
        self.content["status"] = "OFFICIAL"
        with self.assertRaises(ValueError):
            verify(self.content, self.ledger)

    def test_source_check_is_not_human_publication_approval(self):
        self.ledger["reviews"][0]["publicationApproved"] = True
        with self.assertRaises(ValueError):
            verify(self.content, self.ledger)

    def test_lesson_and_easy_text_changes_require_review(self):
        for field in ("body", "easyBody"):
            changed = copy.deepcopy(self.content)
            changed["topics"][0]["lessons"]["fi"]["sections"][0][field] = "Unreviewed wording"
            with self.assertRaises(ValueError):
                verify(changed, self.ledger)

    def test_missing_lesson_and_section_evidence_fail(self):
        for ledger in (dict(self.ledger, lessonReviews=self.ledger["lessonReviews"][1:]), copy.deepcopy(self.ledger)):
            if len(ledger["lessonReviews"]) == len(self.ledger["lessonReviews"]):
                ledger["lessonReviews"][0]["sections"][0]["sources"] = []
            with self.assertRaises(ValueError):
                verify(self.content, ledger)

    def test_lesson_review_cannot_claim_human_approval(self):
        self.ledger["lessonReviews"][0]["publicationApproved"] = True
        with self.assertRaises(ValueError):
            verify(self.content, self.ledger)


if __name__ == "__main__":
    unittest.main()
