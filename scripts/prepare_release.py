"""Prepare GitHub Release assets from the existing signed update channel."""

import argparse
import hashlib
import json
import re
import sys
import time
from pathlib import Path
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


CHANNEL = "https://raw.githubusercontent.com/Aigooz/ai-rankboard-updates/main/"
ROOT = Path(__file__).resolve().parents[1]


def validate_manifest(manifest, version, gradle):
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError("Version must have the form 0.20.16 (without v)")
    if not isinstance(manifest, dict):
        raise ValueError("Update manifest must be an object")
    code = manifest.get("versionCode")
    if type(code) is not int or code <= 0:
        raise ValueError("versionCode must be a positive integer")
    source_name = re.search(r'\bversionName\s*=\s*"([^"]+)"', gradle)
    source_code = re.search(r"\bversionCode\s*=\s*(\d+)", gradle)
    if not source_name or not source_code:
        raise ValueError("Cannot read Android source version")
    if manifest.get("versionName") != version or source_name[1] != version:
        raise ValueError("Requested, source and update channel versions must match")
    if code != int(source_code[1]):
        raise ValueError("Source and update channel versionCode must match")
    sha = manifest.get("sha256")
    if not isinstance(sha, str) or not re.fullmatch(r"[a-fA-F0-9]{64}", sha):
        raise ValueError("Manifest must contain a SHA-256 digest")
    size = manifest.get("sizeBytes")
    if type(size) is not int or not 0 < size <= 200_000_000:
        raise ValueError("APK size must be between 1 and 200000000 bytes")
    if manifest.get("apkUrl") != CHANNEL + "app-release.apk":
        raise ValueError("APK must come from the official HTTPS update channel")
    return code, sha.lower(), size


def verify_apk(path, sha, size):
    if path.stat().st_size != size:
        raise ValueError("APK byte count does not match update manifest")
    with path.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    if digest != sha:
        raise ValueError("APK SHA-256 does not match update manifest")


def changelog_section(text, version):
    match = re.search(
        rf"^## v{re.escape(version)}(?![\d.])(?:[^\r\n]*)\r?\n(.*?)(?=^## |\Z)",
        text,
        re.MULTILINE | re.DOTALL,
    )
    if not match or not match[1].strip():
        raise ValueError("Requested version has no changelog entry")
    return match[1].strip()


def download(url, destination, limit):
    request = Request(
        url + "?release=" + str(time.time_ns()),
        headers={"User-Agent": "AI-Rankboard-Release", "Cache-Control": "no-cache"},
    )
    with urlopen(request, timeout=90) as response, destination.open("wb") as output:
        if urlsplit(response.url).scheme != "https":
            raise ValueError("Insecure download redirect")
        total = 0
        while block := response.read(64 * 1024):
            total += len(block)
            if total > limit:
                raise ValueError("Download exceeds expected maximum size")
            output.write(block)


def prepare(version, output):
    output.mkdir(parents=True, exist_ok=True)
    # Use partial files so a failed verification never leaves a publishable APK.
    manifest_path = output / "app-update.json.part"
    apk_path = output / "app-release.apk.part"
    try:
        download(CHANNEL + "app-update.json", manifest_path, 1_000_000)
        manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
        code, sha, size = validate_manifest(
            manifest, version, (ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")
        )
        changes = changelog_section((ROOT / "CHANGELOG.md").read_text(encoding="utf-8"), version)
        download(manifest["apkUrl"], apk_path, size)
        verify_apk(apk_path, sha, size)
        (output / "SHA256SUMS").write_text(f"{sha}  app-release.apk\n", encoding="ascii")
        notes = (
            f"## Android {version}\n\n{changes}\n\n"
            f"- Android 8.0+ / `com.ai.rankboard` / versionCode `{code}`\n"
            "- 下载 Assets 中的 `app-release.apk`，不要下载 Source code。\n"
            "- 使用原发布签名，可覆盖安装；`SHA256SUMS` 提供文件完整性校验。\n"
            "- 自动检查结果见本版本发布工作流；真机覆盖安装验收需另外完成，不能由自动检查替代。\n"
            "- 榜单数据单独更新，无需为每次数据变化重装应用。\n"
        )
        (output / "release-notes.md").write_text(notes, encoding="utf-8")
        manifest_path.replace(output / "app-update.json")
        apk_path.replace(output / "app-release.apk")
        print(f"Prepared Android {version} (code {code}), {size} bytes, SHA-256 {sha}")
    finally:
        manifest_path.unlink(missing_ok=True)
        apk_path.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--output", type=Path, default=ROOT / "dist/release")
    args = parser.parse_args()
    try:
        prepare(args.version, args.output)
    except (ValueError, OSError) as error:
        print(f"Release preparation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
