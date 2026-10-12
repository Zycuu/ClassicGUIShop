#!/usr/bin/env python3
"""Prepare a GitHub wiki checkout from wiki/ without committing or deleting files."""

import argparse
import re
import subprocess
from pathlib import Path


def git(directory: Path, *args: str) -> str:
    return subprocess.check_output(
        ["git", "-C", str(directory), *args], text=True
    ).strip()


def render(text: str, names: set[str]) -> str:
    """Convert page.md links to wiki routes; leave fenced code untouched."""
    fence = None
    result = []

    def link(match: re.Match) -> str:
        page, anchor = match.group(1), match.group(2) or ""
        return f"]({page}{anchor})" if page in names else match.group(0)

    for line in text.splitlines(keepends=True):
        marker = re.match(r"^\s{0,3}(`{3,}|~{3,})", line)
        if marker:
            value = marker.group(1)
            if fence is None:
                fence = value
            elif value[0] == fence[0] and len(value) >= len(fence):
                fence = None
            result.append(line)
        else:
            result.append(
                re.sub(r"\]\(([A-Za-z0-9_-]+)\.md(#[^\s)]*)?\)", link, line)
                if fence is None else line
            )
    return "".join(result)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("checkout", type=Path, help="separate cloned *.wiki.git repository")
    options = parser.parse_args()
    source = Path(__file__).resolve().parents[2] / "wiki"
    destination = options.checkout.resolve(strict=True)
    if not destination.is_dir() or not (destination / ".git").exists():
        parser.error("destination must be a separate Git wiki checkout")
    root = Path(git(destination, "rev-parse", "--show-toplevel")).resolve()
    if root != destination or root == source.parent or root.is_relative_to(source.parent):
        parser.error("destination must be the root of a separate checkout outside the source repository")
    remote = git(destination, "remote", "get-url", "origin")
    if not re.search(r"\.wiki(?:\.git)?/?$", remote):
        parser.error("destination origin must identify a *.wiki.git repository")
    pages = sorted(source.glob("*.md"))
    names = {page.stem for page in pages}
    changed = 0
    for page in pages:
        target = destination / page.name
        if target.is_symlink():
            parser.error(f"refusing symlink destination: {target.name}")
        content = render(page.read_text(encoding="utf-8"), names).encode("utf-8")
        if not target.exists() or target.read_bytes() != content:
            target.write_bytes(content)
            changed += 1
    print(f"Prepared {len(pages)} pages; updated {changed}. No files deleted, commits made, or pushes performed.")


if __name__ == "__main__":
    main()
