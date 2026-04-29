#!/usr/bin/env python3
# tools/sanitize-har.py
# Usage: python3 tools/sanitize-har.py fixtures/raw/login.har fixtures/sanitized/account-A/login.har
"""
Sanitizer for HAR files captured against journal.school28-kirov.ru.

Replaces:
- ФИО (Russian full names) — deterministic fakes (Иванов И.И., Петров П.П., Сидоров С.С.)
- Cookie values — same-length 'X' placeholder
- Photo URLs — placeholder.{ext}
Preserves:
- Endpoint paths, query parameter NAMES, JSON structure (keys + non-PII values)
- Grades (numeric + textual marks like '5', 'н', 'зач')
- Subject names, lesson times, room numbers
"""
import json
import re
import sys
import hashlib
from pathlib import Path

try:
    import yaml
except ImportError:
    print("ERROR: PyYAML required. Install: pip3 install --user pyyaml", file=sys.stderr)
    sys.exit(2)

# Load configurable rules from sanitize-rules.yaml (allows updates without code changes)
RULES_PATH = Path(__file__).parent / "sanitize-rules.yaml"
with open(RULES_PATH) as f:
    rules = yaml.safe_load(f)

# Russian name regex — Surname + first name (and optional patronymic) OR Surname Initial.Initial.
RU_FULLNAME_RE = re.compile(r'\b[А-ЯЁ][а-яё]{2,}\s+[А-ЯЁ][а-яё]{2,}(?:\s+[А-ЯЁ][а-яё]{2,})?\b')
RU_INITIAL_RE = re.compile(r'\b[А-ЯЁ][а-яё]{2,}\s+[А-ЯЁ]\.\s*[А-ЯЁ]\.\b')

NAME_POOL = [
    "Иванов И.И.", "Петров П.П.", "Сидоров С.С.",
    "Кузнецов К.К.", "Новиков Н.Н.",
]


def deterministic_fake(real_name: str) -> str:
    """Same real name → same fake (so cross-references stay consistent)."""
    h = int(hashlib.sha256(real_name.encode()).hexdigest(), 16)
    return NAME_POOL[h % len(NAME_POOL)]


def sanitize_text(text: str) -> str:
    text = RU_FULLNAME_RE.sub(lambda m: deterministic_fake(m.group(0)), text)
    text = RU_INITIAL_RE.sub(lambda m: deterministic_fake(m.group(0)), text)
    # Explicit single-word surname redactions from rules — covers cases where
    # ФИО is split into separate fields (e.g. ["lastname","i","p"]) and the
    # 2-word regex never sees lastname adjacent to firstname.
    for word in rules.get("replace_strings", []):
        text = re.sub(
            rf'\b{re.escape(word)}\b',
            lambda m: deterministic_fake(m.group(0)),
            text,
        )
    # Cookie values
    for cookie_name in rules.get("cookie_names", []):
        text = re.sub(
            rf'({re.escape(cookie_name)}=)[^;\s\\"]+',
            lambda m: m.group(1) + 'X' * 32,
            text,
        )
    # Photo URLs
    text = re.sub(r'/photos/\w+\.(jpg|png|jpeg)', r'/photos/placeholder.\1', text, flags=re.I)
    return text


def sanitize_login_form(text: str) -> str:
    """Redact `l=...&p=...` form params inside /login POST body text."""
    for param in rules.get("login_param_names", []):
        text = re.sub(
            rf'(^|&)({re.escape(param)})=[^&\s]*',
            r'\1\2=REDACTED',
            text,
        )
    return text


def walk_har(har: dict) -> dict:
    """Recursively descend into HAR entries, applying sanitization to text bodies."""
    for entry in har.get("log", {}).get("entries", []):
        url = entry["request"].get("url", "")
        is_login = url.endswith("/login") or url.endswith("/auth") or "/login?" in url
        # Request body
        post_data = entry["request"].get("postData")
        if post_data and "text" in post_data:
            post_data["text"] = sanitize_text(post_data["text"])
            if is_login:
                post_data["text"] = sanitize_login_form(post_data["text"])
            # Also redact named params if present
            if is_login and "params" in post_data:
                names_to_redact = set(rules.get("login_param_names", []))
                for p in post_data["params"]:
                    if p.get("name") in names_to_redact:
                        p["value"] = "REDACTED"
        # Response body
        content = entry.get("response", {}).get("content", {})
        if "text" in content:
            content["text"] = sanitize_text(content["text"])
        # Cookie values (request + response)
        for cookies_field in [entry["request"].get("cookies", []), entry["response"].get("cookies", [])]:
            for cookie in cookies_field:
                if "value" in cookie:
                    cookie["value"] = "X" * len(cookie["value"])
    return har


def main():
    if len(sys.argv) != 3:
        print("Usage: sanitize-har.py <input.har> <output.har>", file=sys.stderr)
        sys.exit(1)
    in_path = Path(sys.argv[1])
    out_path = Path(sys.argv[2])
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(in_path, encoding="utf-8") as f:
        har = json.load(f)
    sanitized = walk_har(har)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(sanitized, f, ensure_ascii=False, indent=2)
    print(f"Sanitized {in_path} -> {out_path}", file=sys.stderr)


if __name__ == "__main__":
    main()
