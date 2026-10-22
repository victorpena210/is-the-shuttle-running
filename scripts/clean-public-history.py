#!/usr/bin/env python3
"""Remove known legacy employee fixtures; default is local-only verification.

Use a disposable full clone. This rewrites history. --push requires the exact
expected main SHA and refuses concurrent changes to any of the four branches.
No employee values or backup artifacts are printed or uploaded.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import tempfile

EXPECTED_OTHER_HEADS = {
    "fix/dayforce-csv-import": "41b2bb16d70e9ae58e970d5b9567877a7d3fed93",
    "rebrand/is-the-shuttle-running": "8c7dd7601c791e26ecdf7947045021b6d144984a",
    "test/add-automated-tests": "cae2a1e444f7c14b3e2883331e39a8ae80f13f8a",
}


def git(*args):
    return subprocess.check_output(["git", *args])


def objects():
    for row in git("rev-list", "--objects", "--all").decode().splitlines():
        parts = row.split(" ", 1)
        if len(parts) == 2:
            yield parts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expected-main", required=True)
    parser.add_argument("--push", action="store_true")
    args = parser.parse_args()
    expected = {**EXPECTED_OTHER_HEADS, "main": args.expected_main}
    if not re.fullmatch(r"[0-9a-f]{40}", args.expected_main):
        raise SystemExit("Expected main must be a full commit SHA.")
    if git("status", "--porcelain").strip():
        raise SystemExit("Refusing a checkout with uncommitted work.")
    if args.push:
        if os.environ.get("GITHUB_REPOSITORY") != "victorpena210/is-the-shuttle-running":
            raise SystemExit("Push is restricted to the original repository's Actions job.")
        actual = {ref.removeprefix("refs/heads/"): sha for sha, ref in
                  (line.split() for line in git("ls-remote", "--heads", "origin").decode().splitlines())}
        if actual != expected:
            raise SystemExit("Remote branches changed; refusing to overwrite newer work.")
    if git("rev-parse", "HEAD").decode().strip() != args.expected_main:
        raise SystemExit("Checkout does not match the expected main revision.")
    subprocess.run(["git", "checkout", "-B", "main", args.expected_main], check=True)
    for name, sha in EXPECTED_OTHER_HEADS.items():
        subprocess.run(["git", "branch", "-f", name, sha], check=True)
    prepared_tree = git("rev-parse", "main^{tree}").strip()
    pairs = {}
    for sha, path in objects():
        if not re.search(r"/V[23]__seed[^/]*\.sql$", path):
            continue
        if git("cat-file", "-t", sha).strip() != b"blob":
            continue
        source = git("cat-file", "blob", sha).decode()
        for employee, name in re.findall(r"SELECT\s+'([^']+)'\s*,\s*'([^']+)'", source, re.I):
            if not re.fullmatch(r"\d{7}", employee) or " " not in name:
                raise SystemExit("Unexpected seed format; manual review required.")
            if employee in pairs and pairs[employee] != name:
                raise SystemExit("Conflicting seed values; manual review required.")
            pairs[employee] = name
    if len(pairs) != 4:
        raise SystemExit("Expected four legacy employee fixtures; refusing an unreviewed rewrite.")
    replacements = {}
    for index, (employee, name) in enumerate(sorted(pairs.items()), 1):
        replacements[employee] = str(9000000 + index)
        replacements[name] = "Demo Driver " + str(index)
    # The source fixes must already be on main; this rewrite is history-only.
    for entry in git("ls-tree", "-r", "main").decode().splitlines():
        meta, path = entry.split("\t", 1)
        if path.startswith("src/main/resources/data/"):
            raise SystemExit("Main still includes private data files.")
        data = git("cat-file", "blob", meta.split()[2])
        if any(old.encode() in data for old in replacements):
            raise SystemExit("Main still contains legacy employee values.")
    with tempfile.TemporaryDirectory(prefix="shuttle-history-") as temp:
        replacement_file = Path(temp) / "replacements.txt"
        replacement_file.write_text("".join(
            "regex:(?<![A-Za-z0-9_])" + re.escape(old) + r"(?![A-Za-z0-9_])"
            + "==>" + new + "\n" for old, new in replacements.items()))
        replacement_file.chmod(0o600)
        subprocess.run([
            "git", "filter-repo", "--force", "--sensitive-data-removal", "--no-fetch",
            "--path", "src/main/resources/data/", "--invert-paths",
            "--replace-text", str(replacement_file), "--replace-message", str(replacement_file)
        ], check=True)
    if git("rev-parse", "main^{tree}").strip() != prepared_tree:
        raise SystemExit("Prepared main tree changed unexpectedly; refusing to push.")
    checked = 0
    for sha, path in objects():
        if path.startswith("src/main/resources/data/"):
            raise SystemExit("A private data path remains reachable.")
        if git("cat-file", "-t", sha).strip() != b"blob":
            continue
        data = git("cat-file", "blob", sha)
        if any(old.encode() in data for old in replacements):
            raise SystemExit("A legacy employee value remains reachable.")
        checked += 1
    print(f"Verified {checked} reachable blobs; no known employee values or data-directory paths remain.")
    if args.push:
        subprocess.run(["git", "push", "--atomic",
            *[f"--force-with-lease=refs/heads/{name}:{sha}" for name, sha in expected.items()],
            "origin", *[f"refs/heads/{name}:refs/heads/{name}" for name in expected]], check=True)
        print("Rewritten branches pushed. GitHub Support must still purge retained pull-request/cached references.")
    else:
        print("Local verification only; no remote changes were made.")


if __name__ == "__main__":
    main()
