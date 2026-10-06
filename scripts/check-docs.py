#!/usr/bin/env python3
"""Check active Markdown links and byte-preserved team document snapshots."""
import hashlib
import json
from pathlib import Path
import re
import sys
from urllib.parse import unquote, urlsplit


def main():
    root = Path(__file__).resolve().parents[1]
    archive = root / 'docs/history/team-project'
    errors = []
    manifest = json.loads((archive / 'manifest.json').read_text())
    for entry in manifest['files']:
        path = root / entry['archive']
        if not path.is_file():
            errors.append(f"Missing snapshot: {entry['archive']}")
        elif hashlib.sha256(path.read_bytes()).hexdigest() != entry['sha256']:
            errors.append(f"Changed snapshot: {entry['archive']}")

    documents = list(root.glob('*.md')) + list((root / 'docs').rglob('*.md'))
    documents += list((root / '.github').glob('*.md'))
    checked = 0
    for document in sorted(documents):
        if archive in document.parents and document.name != 'README.md':
            continue  # Archived relative links intentionally retain original paths.
        checked += 1
        for target in re.findall(r'\[[^\]]*\]\(([^)]+)\)', document.read_text()):
            target = target.strip().split(' "', 1)[0].strip('<>')
            parsed = urlsplit(target)
            if parsed.scheme or not parsed.path:
                continue
            path = (document.parent / unquote(parsed.path)).resolve()
            if not path.exists():
                errors.append(f'{document.relative_to(root)}: missing target {target}')
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        return 1
    print(f'OK: {len(manifest["files"])} preserved snapshots, {checked} Markdown files (local paths only)')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
