"""Atomically publish a local commit through GitHub's Git Data API."""

from __future__ import annotations

import argparse
import base64
import json
import subprocess
import sys
from pathlib import Path


def run(*args: str, cwd: Path, binary: bool = False) -> bytes | str:
    result = subprocess.run(
        args,
        cwd=cwd,
        check=True,
        capture_output=True,
    )
    return result.stdout if binary else result.stdout.decode("utf-8").strip()


def gh_api(repo: str, endpoint: str, payload: dict, method: str = "POST") -> dict:
    result = subprocess.run(
        ["gh", "api", "-X", method, f"repos/{repo}/{endpoint}", "--input", "-"],
        check=True,
        input=json.dumps(payload).encode("utf-8"),
        capture_output=True,
    )
    return json.loads(result.stdout)


def gh_get(repo: str, endpoint: str) -> dict:
    result = subprocess.run(
        ["gh", "api", f"repos/{repo}/{endpoint}"],
        check=True,
        capture_output=True,
    )
    return json.loads(result.stdout)


def resolve_base_tree(repo: str, root: Path, parent: str) -> str:
    result = subprocess.run(
        ["git", "rev-parse", f"{parent}^{{tree}}"],
        cwd=root,
        capture_output=True,
    )
    if result.returncode == 0:
        return result.stdout.decode("utf-8").strip()
    return gh_get(repo, f"git/commits/{parent}")["tree"]["sha"]


def changed_entries(root: Path, parent: str, head: str) -> list[dict]:
    statuses = run(
        "git",
        "-c",
        "core.quotepath=false",
        "diff",
        "--name-status",
        parent,
        head,
        cwd=root,
    ).splitlines()
    entries: list[dict] = []
    for line in statuses:
        fields = line.split("\t")
        status = fields[0][0]
        if status == "D":
            entries.append({"path": fields[1], "mode": "100644", "type": "blob", "sha": None})
            continue
        if status == "R":
            entries.append({"path": fields[1], "mode": "100644", "type": "blob", "sha": None})
            path = fields[2]
        else:
            path = fields[1]
        tree_line = run("git", "ls-tree", head, "--", path, cwd=root)
        if not tree_line:
            raise RuntimeError(f"Cannot resolve changed path {path}")
        metadata, resolved_path = tree_line.split("\t", 1)
        mode, object_type, object_sha = metadata.split()
        if object_type != "blob":
            raise RuntimeError(f"Changed path is not a blob: {resolved_path}")
        entries.append(
            {
                "path": resolved_path,
                "mode": mode,
                "type": "blob",
                "sha": object_sha,
                "_content": run("git", "cat-file", "blob", object_sha, cwd=root, binary=True),
            }
        )
    return entries


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True, help="OWNER/REPOSITORY")
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--parent", required=True)
    parser.add_argument("--head", default="HEAD")
    parser.add_argument(
        "--diff-base",
        help="Revision used to select changed paths (defaults to --parent)",
    )
    parser.add_argument("--branch", default="main")
    parser.add_argument("--message", required=True)
    args = parser.parse_args()

    entries = changed_entries(args.root, args.diff_base or args.parent, args.head)
    if not entries:
        raise RuntimeError("There are no changes to publish")

    for entry in entries:
        content = entry.pop("_content", None)
        if content is None:
            continue
        blob = gh_api(
            args.repo,
            "git/blobs",
            {"content": base64.b64encode(content).decode("ascii"), "encoding": "base64"},
        )
        entry["sha"] = blob["sha"]

    base_tree = resolve_base_tree(args.repo, args.root, args.parent)
    tree = gh_api(
        args.repo,
        "git/trees",
        {"base_tree": base_tree, "tree": entries},
    )
    commit = gh_api(
        args.repo,
        "git/commits",
        {"message": args.message, "tree": tree["sha"], "parents": [args.parent]},
    )
    ref = gh_api(
        args.repo,
        f"git/refs/heads/{args.branch}",
        {"sha": commit["sha"], "force": False},
        method="PATCH",
    )
    print(
        json.dumps(
            {
                "repo": args.repo,
                "branch": args.branch,
                "changed": len(entries),
                "tree": tree["sha"],
                "commit": commit["sha"],
                "ref": ref.get("object", {}).get("sha"),
            }
        )
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as error:
        sys.stderr.write(error.stderr.decode("utf-8", errors="replace"))
        raise
