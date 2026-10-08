import argparse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EXTENSIONS = {
    "python": {".py"},
    "java": {".java"},
    "typescript": {".ts", ".tsx"},
}

parser = argparse.ArgumentParser()
parser.add_argument("--language", choices=EXTENSIONS, required=True)
parser.add_argument("--max-length", type=int, required=True)
args = parser.parse_args()

violations = []
for path in ROOT.rglob("*"):
    if not path.is_file() or path.suffix not in EXTENSIONS[args.language]:
        continue
    if any(part in {"node_modules", "target", "dist", ".git"} for part in path.parts):
        continue
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if len(line) > args.max_length:
            violations.append((path.relative_to(ROOT), number, len(line)))

print(f"{args.language} lines over {args.max_length}: {len(violations)}")
for path, number, length in violations[:40]:
    print(f"  {path}:{number} ({length})")
