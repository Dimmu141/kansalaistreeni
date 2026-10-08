"""Small source-level architecture contracts; no Android SDK or third-party packages needed.

These checks guard package boundaries and a few dangerous escape hatches. They are
not a Kotlin compiler, a performance benchmark, or proof of transaction consistency.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/fi/kansalaisuuskoe"


def code_only(source):
    # Keep newlines for anchored declarations; ignore literals and comments.
    token = r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/'
    return re.sub(token, lambda match: "\n" * match[0].count("\n"), source)


class ArchitectureContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sources = {p: code_only(p.read_text(encoding="utf-8")) for p in MAIN.rglob("*.kt")}
        if not cls.sources or not (MAIN / "domain/Models.kt").is_file():
            raise AssertionError("Production Kotlin source tree is missing")

    def layer(self, name):
        found = {p: text for p, text in self.sources.items() if p.relative_to(MAIN).parts[0] == name}
        self.assertTrue(found, f"Missing {name} sources")
        return found

    def imports(self, source):
        return re.findall(r"^import\s+([\w.*]+)", source, re.MULTILINE)

    def test_domain_depends_only_on_standard_libraries_and_domain(self):
        for path, source in self.layer("domain").items():
            for dependency in self.imports(source):
                self.assertTrue(dependency.startswith(("kotlin.", "java.", "fi.kansalaisuuskoe.domain.")),
                                f"{path.name}: domain dependency {dependency}")

    def test_data_does_not_depend_on_presentation(self):
        for path, source in self.layer("data").items():
            self.assertIsNone(re.search(r"\bfi\.kansalaisuuskoe\.(?:ui\.|R\b|MainActivity\b|AppContainer\b)", source), path.name)

    def test_ui_cannot_access_database_or_preference_implementations(self):
        storage = r"\b(?:LearningDatabase|LearningDao|AssessmentDao|TopicEntity|QuestionEntity|AttemptEntity|ProgressEntity|AssessmentEntity|LearningRulesEntity)\b"
        for path, source in self.layer("ui").items():
            self.assertIsNone(re.search(storage, source), path.name)
            self.assertIsNone(re.search(r"\b(?:androidx\.room\.|androidx\.datastore\.|android\.database\.|container\.database\b)", source), path.name)

    def test_viewmodels_do_not_hold_platform_or_compose_dependencies(self):
        models = {p: source for p, source in self.layer("ui").items() if p.name.endswith("ViewModel.kt")}
        self.assertTrue(models)
        for path, source in models.items():
            for dependency in self.imports(source):
                self.assertFalse(dependency.startswith(("android.", "androidx.compose.", "androidx.navigation.")),
                                 f"{path.name}: platform dependency {dependency}")

    def test_mutable_state_flows_are_private(self):
        for path, source in self.layer("ui").items():
            for declaration in re.findall(r"^\s*(.*\b(?:val|var)\s+\w+\s*=\s*MutableStateFlow\b.*)$", source, re.MULTILINE):
                self.assertIn("private", declaration.split(), f"{path.name}: {declaration}")

    def test_no_blocking_or_destructive_storage_escape_hatches(self):
        forbidden = r"\b(?:GlobalScope|runBlocking|allowMainThreadQueries|fallbackToDestructiveMigration\w*)\b"
        for path, source in self.sources.items():
            self.assertIsNone(re.search(forbidden, source), path.name)

    def test_all_room_migrations_are_exported_and_registered(self):
        source = (MAIN / "data/LearningDatabase.kt").read_text(encoding="utf-8")
        version = int(re.search(r"@Database\([\s\S]*?version\s*=\s*(\d+)", source)[1])
        bootstrap = (MAIN / "CitizenshipApplication.kt").read_text(encoding="utf-8")
        for number in range(1, version + 1):
            self.assertTrue((ROOT / f"app/schemas/fi.kansalaisuuskoe.data.LearningDatabase/{number}.json").is_file())
            if number < version:
                migration = f"MIGRATION_{number}_{number + 1}"
                self.assertIn(f"val {migration}", source)
                self.assertIn(f"LearningDatabase.{migration}", bootstrap)

    def test_literal_and_comment_filter_does_not_hide_real_dependencies(self):
        sample = '// import android.fake.Hidden\nval label = "androidx.room.Fake"\nimport android.content.Context\n'
        self.assertEqual(["android.content.Context"], self.imports(code_only(sample)))


if __name__ == "__main__":
    unittest.main(verbosity=2)
