import json
import re
import unittest
from pathlib import Path

from app.snapshot import build_snapshot

ASSET = Path(__file__).resolve().parents[2] / "android" / "app" / "src" / "main" / "assets" / "leaderboards.json"


class SnapshotContractTests(unittest.TestCase):
    def test_snapshot_schema_from_db(self) -> None:
        snapshot = build_snapshot()

        self.assertEqual(snapshot["schemaVersion"], 2)
        self.assertTrue(re.match(r"^\d{4}-\d{2}-\d{2}T", snapshot["generatedAt"]))
        self.assertTrue(snapshot["boards"])
        self.assertTrue(snapshot["entriesByBoard"])
        self.assertTrue(snapshot["models"])
        for board in snapshot["boards"]:
            self.assertIn(board["slug"], snapshot["entriesByBoard"])
            self.assertEqual(len(snapshot["entriesByBoard"][board["slug"]]), board["model_count"])

    def test_bundled_snapshot_matches_schema(self) -> None:
        snapshot = json.loads(ASSET.read_text(encoding="utf-8"))

        self.assertEqual(snapshot["schemaVersion"], 2)
        self.assertTrue(snapshot["source"]["id"])
        self.assertTrue(snapshot["sources"])
        self.assertIn("modelsage", {item["id"] for item in snapshot["sources"]})
        self.assertTrue(snapshot["generatedAt"])
        self.assertTrue(snapshot["boards"])
        self.assertTrue(snapshot["models"])
        self.assertTrue(snapshot["entriesByBoard"])


if __name__ == "__main__":
    unittest.main()
