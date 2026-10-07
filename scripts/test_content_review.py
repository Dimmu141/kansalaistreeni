"""Meaningful negative checks for the editorial gate. Run with Python 3.10+."""
import copy
import unittest
import runpy
from pathlib import Path

module = runpy.run_path(str(Path(__file__).with_name("verify-content.py")))
ROOT, read_json, verify = (module[name] for name in ("ROOT", "read_json", "verify"))


class ReviewGateTest(unittest.TestCase):
    def setUp(self):
        self.content = read_json(ROOT / "app/src/main/assets/demo-content.json")
        self.ledger = read_json(ROOT / "docs/source-review.json")

    def test_current_demo_passes(self):
        verify(self.content, self.ledger)

    def test_answer_change_requires_new_review(self):
        self.content["questions"][0]["correctOptionId"] = "a"
        with self.assertRaises(ValueError):
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


if __name__ == "__main__":
    unittest.main()
