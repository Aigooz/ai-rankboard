import hashlib
import importlib.util
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "prepare_release", Path(__file__).resolve().parents[1] / "prepare_release.py"
)
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.gradle = 'versionCode = 66\nversionName = "0.20.16"'
        self.manifest = {
            "versionCode": 66,
            "versionName": "0.20.16",
            "apkUrl": release.CHANNEL + "app-release.apk",
            "sha256": hashlib.sha256(b"apk").hexdigest().upper(),
            "sizeBytes": 3,
        }

    def test_matching_version(self):
        code, sha, size = release.validate_manifest(self.manifest, "0.20.16", self.gradle)
        self.assertEqual((code, sha, size), (66, self.manifest["sha256"].lower(), 3))

    def test_reject_invalid_fields(self):
        cases = {
            "versionCode": [65, "66", True, 0],
            "versionName": ["0.20.15", None],
            "sha256": ["invalid", None, "g" * 64],
            "sizeBytes": [0, -1, "3", True, 200_000_001],
            "apkUrl": ["http://example.com/app.apk", "https://example.com/app.apk", None],
        }
        for field, values in cases.items():
            for value in values:
                with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                    release.validate_manifest({**self.manifest, field: value}, "0.20.16", self.gradle)

    def test_reject_requested_version_and_source_mismatch(self):
        for version, gradle in [("v0.20.16", self.gradle), ("0.20.15", self.gradle),
                                ("0.20.16", ""), ("0.20.16", self.gradle.replace("66", "67"))]:
            with self.subTest(version=version, gradle=gradle), self.assertRaises(ValueError):
                release.validate_manifest(self.manifest, version, gradle)
        with self.assertRaises(ValueError):
            release.validate_manifest([], "0.20.16", self.gradle)

    def test_hash_and_size_verification(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "app.apk"
            path.write_bytes(b"apk")
            digest = self.manifest["sha256"].lower()
            release.verify_apk(path, digest, 3)
            for sha, size in [(digest, 4), ("0" * 64, 3)]:
                with self.assertRaises(ValueError):
                    release.verify_apk(path, sha, size)

    def test_changelog_does_not_include_other_versions(self):
        text = "# Changes\n\n## v0.20.16 (date)\n\n- New\n\n## v0.20.15\n- Old\n"
        self.assertEqual(release.changelog_section(text, "0.20.16"), "- New")
        for version in ["0.20.1", "0.20.17"]:
            with self.assertRaises(ValueError):
                release.changelog_section(text, version)

    def test_failed_preparation_removes_partial_files(self):
        def broken_download(url, path, limit):
            path.write_bytes(b"partial")
            raise OSError("network interrupted")

        with tempfile.TemporaryDirectory() as temp, patch.object(release, "download", broken_download):
            with self.assertRaises(OSError):
                release.prepare("0.20.16", Path(temp))
            self.assertEqual(list(Path(temp).iterdir()), [])


if __name__ == "__main__":
    unittest.main()
