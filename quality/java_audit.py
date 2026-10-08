from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def code_only(source: str) -> str:
    result = []
    i = 0
    in_string = False
    in_char = False
    in_line_comment = False
    in_block_comment = False
    in_text_block = False
    while i < len(source):
        current = source[i]
        nxt = source[i + 1] if i + 1 < len(source) else ""
        if in_line_comment:
            if current == "\n":
                in_line_comment = False
                result.append("\n")
            else:
                result.append(" ")
            i += 1
            continue
        if in_block_comment:
            if current == "*" and nxt == "/":
                result.extend("  ")
                i += 2
                in_block_comment = False
            else:
                result.append("\n" if current == "\n" else " ")
                i += 1
            continue
        if in_text_block:
            if source.startswith('"""', i):
                result.extend("   ")
                i += 3
                in_text_block = False
            else:
                result.append("\n" if current == "\n" else " ")
                i += 1
            continue
        if in_string:
            if current == "\\":
                result.extend("  ")
                i += 2
            elif current == '"':
                result.append(" ")
                i += 1
                in_string = False
            else:
                result.append("\n" if current == "\n" else " ")
                i += 1
            continue
        if in_char:
            if current == "\\":
                result.extend("  ")
                i += 2
            elif current == "'":
                result.append(" ")
                i += 1
                in_char = False
            else:
                result.append(" ")
                i += 1
            continue
        if source.startswith('"""', i):
            result.extend("   ")
            i += 3
            in_text_block = True
        elif current == "/" and nxt == "/":
            result.extend("  ")
            i += 2
            in_line_comment = True
        elif current == "/" and nxt == "*":
            result.extend("  ")
            i += 2
            in_block_comment = True
        elif current == '"':
            result.append(" ")
            i += 1
            in_string = True
        elif current == "'":
            result.append(" ")
            i += 1
            in_char = True
        else:
            result.append(current)
            i += 1
    return "".join(result)

for path in ROOT.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    if "\x00" in text:
        raise SystemExit(f"NUL byte found: {path}")
    balance = 0
    for number, line in enumerate(code_only(text).splitlines(), start=1):
        balance += line.count("{") - line.count("}")
        if balance < 0:
            raise SystemExit(f"Unexpected closing brace: {path}:{number}")
    if balance != 0:
        raise SystemExit(f"Unbalanced braces ({balance}): {path}")

print("Java lexical audit: PASS")
