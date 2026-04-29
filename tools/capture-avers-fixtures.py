#!/usr/bin/env python3
# tools/capture-avers-fixtures.py
"""
Capture HAR fixtures from journal.school28-kirov.ru programmatically.

Replaces the manual Chrome DevTools workflow described in tools/README.md by
replaying the ExtJS SPA's API calls (Broker.deal_uri) directly. Reads creds
from .env.local, logs in for each account, then walks the bootstrap chain
(get_user_data, get_uch_year, GET_STUDENT_CLASS, GET_STUDENT_PARALLEL),
captures the six target endpoints, and writes raw HAR files to
fixtures/raw/account-{A,B}/<endpoint>.har for downstream sanitization.

Stdlib only (urllib + http.cookiejar + hashlib). No third-party deps.

Usage:
    python3 tools/capture-avers-fixtures.py --probe              # login probe only
    python3 tools/capture-avers-fixtures.py --account A          # capture A only
    python3 tools/capture-avers-fixtures.py --full               # both accounts
"""
from __future__ import annotations

import argparse
import hashlib
import http.client
import http.cookiejar
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent
ENV_FILE = REPO_ROOT / ".env.local"
RAW_DIR = REPO_ROOT / "fixtures" / "raw"

BASE_URL = "https://journal.school28-kirov.ru/"
USER_AGENT = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)
TIMEOUT_SECONDS = 30


# ---------- credential loading -----------------------------------------------


def load_env(path: Path) -> dict[str, str]:
    if not path.exists():
        sys.exit(f"ERROR: {path} not found.")
    out: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue
        key, _, val = line.partition("=")
        out[key.strip()] = val.strip()
    required = ("CHILD1_LOGIN", "CHILD1_PASSWORD", "CHILD2_LOGIN", "CHILD2_PASSWORD")
    missing = [k for k in required if not out.get(k)]
    if missing:
        sys.exit(f"ERROR: missing keys in .env.local: {missing}")
    return out


def sha1_hex(s: str) -> str:
    # ExtJS sha1.js uses chrsz=8 (ASCII byte-per-char); for Cyrillic users
    # this likely treats the JS string as code units & masks to a byte.
    # AVERS logins/passwords are ASCII in this school's system. UTF-8 is safe.
    return hashlib.sha1(s.encode("utf-8")).hexdigest()


_JS_ESCAPE_SAFE = set(
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789*@-_+./"
)


def js_escape(s: str) -> str:
    """Polyfill JS escape() — codepoints <256 → %XX, ≥256 → %uXXXX.

    Critical for AVERS auth: Cyrillic logins (e.g. "Чудиновских") are stored in
    `ys-user` cookie as %u0427%u0443… via Ext.state.Provider.encodeValue → JS
    `escape()`. Python's urllib.parse.quote uses UTF-8 byte encoding (%D0%A7…)
    which the AVERS server does NOT accept.
    """
    out: list[str] = []
    for ch in s:
        if ch in _JS_ESCAPE_SAFE:
            out.append(ch)
        else:
            cp = ord(ch)
            if cp < 256:
                out.append(f"%{cp:02X}")
            else:
                out.append(f"%u{cp:04X}")
    return "".join(out)


def ext_encode_string(value: str) -> str:
    return js_escape("s:" + value)


def ext_encode_number(value: int | float) -> str:
    return js_escape("n:" + str(value))


def make_ys_cookie(name: str, value: str, host: str) -> http.cookiejar.Cookie:
    return http.cookiejar.Cookie(
        version=0,
        name=name,
        value=value,
        port=None,
        port_specified=False,
        domain=host,
        domain_specified=True,
        domain_initial_dot=False,
        path="/",
        path_specified=True,
        secure=False,
        expires=None,
        discard=False,
        comment=None,
        comment_url=None,
        rest={},
        rfc2109=False,
    )


def current_academic_year(today: datetime) -> int:
    # AVERS academic year representation: start year of period (2025 = 2025-2026).
    # Aug 1 is the cutover (school starts in September).
    return today.year if today.month >= 8 else today.year - 1


# ---------- HAR builders -----------------------------------------------------


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def parse_set_cookie(headers: list[tuple[str, str]]) -> list[dict[str, Any]]:
    cookies: list[dict[str, Any]] = []
    for name, val in headers:
        if name.lower() != "set-cookie":
            continue
        # Naive single-value parse; HAR cookies field is informational only.
        first = val.split(";", 1)[0]
        if "=" not in first:
            continue
        k, _, v = first.partition("=")
        cookies.append({"name": k.strip(), "value": v.strip()})
    return cookies


def jar_to_har_cookies(jar: http.cookiejar.CookieJar, request_url: str) -> list[dict[str, str]]:
    parsed = urllib.parse.urlparse(request_url)
    out: list[dict[str, str]] = []
    for c in jar:
        if c.domain and c.domain.lstrip(".") not in parsed.hostname:
            continue
        out.append({"name": c.name, "value": c.value or ""})
    return out


def headers_to_har(items: list[tuple[str, str]]) -> list[dict[str, str]]:
    return [{"name": k, "value": v} for k, v in items]


def make_har_envelope() -> dict[str, Any]:
    return {
        "log": {
            "version": "1.2",
            "creator": {
                "name": "lintech-fixture-capture",
                "version": "1.0",
                "comment": "tools/capture-avers-fixtures.py",
            },
            "browser": {"name": "python-urllib", "version": sys.version.split()[0]},
            "pages": [],
            "entries": [],
        }
    }


# ---------- HTTP client wrapper ---------------------------------------------


class AversClient:
    def __init__(self, base_url: str = BASE_URL):
        self.base_url = base_url.rstrip("/") + "/"
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.jar)
        )
        self.opener.addheaders = [
            ("User-Agent", USER_AGENT),
            ("Accept", "*/*"),
            ("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8"),
            ("Accept-Encoding", "identity"),  # avoid gzip — keep response bodies plain
        ]
        self.entries: list[dict[str, Any]] = []
        self.last_login_redaction: tuple[str, str] | None = None  # (login, sha1hex) for raw redaction

    # ----- core ------------------------------------------------------------
    def request(
        self,
        method: str,
        url: str,
        params: dict[str, Any] | None = None,
        body: str | None = None,
        record_into: list[dict[str, Any]] | None = None,
        comment: str | None = None,
    ) -> tuple[int, str, list[tuple[str, str]]]:
        """Make request, record HAR entry, return (status, body_text, response_headers)."""
        if method == "GET" and params:
            qs = urllib.parse.urlencode(params, doseq=True)
            full_url = f"{url}?{qs}" if "?" not in url else f"{url}&{qs}"
            data: bytes | None = None
            post_data_har: dict[str, Any] | None = None
        elif method == "POST":
            qs_text = body if body is not None else (
                urllib.parse.urlencode(params, doseq=True) if params else ""
            )
            data = qs_text.encode("utf-8")
            full_url = url
            post_data_har = {
                "mimeType": "application/x-www-form-urlencoded",
                "text": qs_text,
                "params": [
                    {"name": k, "value": str(v)}
                    for k, v in (urllib.parse.parse_qsl(qs_text, keep_blank_values=True))
                ],
            }
        else:
            full_url = url
            data = None
            post_data_har = None

        req = urllib.request.Request(full_url, data=data, method=method)
        if method == "POST":
            req.add_header("Content-Type", "application/x-www-form-urlencoded")
        # Capture pre-request cookies (from jar)
        request_har_cookies = jar_to_har_cookies(self.jar, full_url)
        # Add header set
        request_headers = list(self.opener.addheaders)
        if method == "POST":
            request_headers.append(("Content-Type", "application/x-www-form-urlencoded"))

        started = now_iso()
        t0 = time.perf_counter()
        try:
            resp = self.opener.open(req, timeout=TIMEOUT_SECONDS)
        except urllib.error.HTTPError as e:
            resp = e
        elapsed_ms = int((time.perf_counter() - t0) * 1000)
        try:
            raw = resp.read()
        except http.client.IncompleteRead as e:
            # AVERS nginx announces wrong Content-Length on / and a few static
            # responses. Use partial body — it's almost always valid HTML/JSON.
            raw = e.partial
        # ExtJS responses are usually Windows-1251 or UTF-8. Try UTF-8 first.
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError:
            text = raw.decode("cp1251", errors="replace")

        resp_headers = [(k, v) for k, v in resp.headers.items()]
        resp_cookies = parse_set_cookie(resp_headers)
        mime = resp.headers.get("Content-Type", "text/plain")

        # Build HAR query string from URL
        parsed_full = urllib.parse.urlparse(full_url)
        qs_pairs = urllib.parse.parse_qsl(parsed_full.query, keep_blank_values=True)
        query_string_har = [{"name": k, "value": v} for k, v in qs_pairs]

        request_har: dict[str, Any] = {
            "method": method,
            "url": full_url,
            "httpVersion": "HTTP/1.1",
            "cookies": request_har_cookies,
            "headers": headers_to_har(request_headers),
            "queryString": query_string_har,
            "headersSize": -1,
            "bodySize": len(data) if data else 0,
        }
        if post_data_har is not None:
            request_har["postData"] = post_data_har

        response_har = {
            "status": getattr(resp, "status", 200),
            "statusText": getattr(resp, "reason", "OK") or "",
            "httpVersion": "HTTP/1.1",
            "cookies": resp_cookies,
            "headers": headers_to_har(resp_headers),
            "content": {"size": len(raw), "mimeType": mime, "text": text},
            "redirectURL": resp.headers.get("Location", "") or "",
            "headersSize": -1,
            "bodySize": len(raw),
        }

        entry = {
            "startedDateTime": started,
            "time": elapsed_ms,
            "request": request_har,
            "response": response_har,
            "cache": {},
            "timings": {"send": 0, "wait": elapsed_ms, "receive": 0},
        }
        if comment:
            entry["comment"] = comment

        if record_into is not None:
            record_into.append(entry)
        return response_har["status"], text, resp_headers

    # ----- bootstrap ------------------------------------------------------

    def probe_root(self, record_into: list[dict[str, Any]]) -> None:
        # Fetch / to get any initial session cookie if server sets one.
        self.request("GET", self.base_url, record_into=record_into, comment="root probe")

    def login(
        self,
        login: str,
        password: str,
        record_into: list[dict[str, Any]],
    ) -> dict[str, Any]:
        password_sha1 = sha1_hex(password)
        self.last_login_redaction = (login, password_sha1)
        status, text, _ = self.request(
            "POST",
            f"{self.base_url}login",
            params={"l": login, "p": password_sha1},
            record_into=record_into,
            comment="POST /login",
        )
        if status != 200:
            raise RuntimeError(f"login HTTP {status}")
        if text.strip() == "[error_symbol]":
            raise RuntimeError("login: server returned [error_symbol] (bad creds)")
        try:
            users = json.loads(text)
        except json.JSONDecodeError:
            raise RuntimeError(f"login: cannot parse response: {text[:200]!r}")
        if not isinstance(users, list) or len(users) != 1:
            raise RuntimeError(f"login: unexpected shape {text[:200]!r}")
        user_row = users[0]
        # user[0]=UserId, user[1]=UserType, user[8]=UserHumanId (parent/student)
        user_id = user_row[0]
        user_type = user_row[1]
        user_human_id = user_row[8] if len(user_row) > 8 else user_row[-1]

        # AUTH MECHANISM (reverse-engineered from site/client/Journal.js +
        # site/extjs/ext-all.js Ext.state.CookieProvider): server reads three
        # client-set cookies on every /act/ request:
        #   ys-user      = s:<login>      (Ext.state.Provider.encodeValue, then escape())
        #   ys-password  = s:<sha1_hex>
        #   ys-userId    = n:<user_id>
        # Server NEVER issues Set-Cookie; the SPA writes them directly via
        # document.cookie in StateManager.set(). We replicate by injecting
        # into the cookiejar manually.
        host = urllib.parse.urlparse(self.base_url).hostname or ""
        for name, value in (
            ("ys-user", ext_encode_string(login)),
            ("ys-password", ext_encode_string(password_sha1)),
            ("ys-userId", ext_encode_number(user_id)),
        ):
            self.jar.set_cookie(make_ys_cookie(name, value, host))

        # confirm auth state (mirrors Login.js side-effect)
        self.request(
            "POST",
            f"{self.base_url}auth",
            params={"uId": user_id, "act": 1},
            record_into=record_into,
            comment="POST /auth (login confirm)",
        )
        return {
            "user_id": user_id,
            "user_type": user_type,
            "user_human_id": user_human_id,
        }

    def act(
        self,
        action: str,
        params: dict[str, Any] | None,
        record_into: list[dict[str, Any]],
        comment: str | None = None,
    ) -> str:
        """GET <base>/act/<action>?params (or POST if params present, per Broker.deal)."""
        url = f"{self.base_url}act/{action}"
        # Broker.deal: GET when no args, POST when args present (Ext.urlEncode → form body).
        if params:
            status, text, _ = self.request(
                "POST", url, params=params, record_into=record_into, comment=comment or action
            )
        else:
            status, text, _ = self.request(
                "GET", url, record_into=record_into, comment=comment or action
            )
        if status >= 400:
            print(f"  WARN: {action} → HTTP {status}", file=sys.stderr)
        return text


# ---------- per-account capture ---------------------------------------------


def parse_array(text: str) -> Any:
    """ExtJS responses are JSON-array-like. Try strict JSON first; fall back to None."""
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return None


def write_har(path: Path, entries: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    har = make_har_envelope()
    har["log"]["entries"] = entries
    with path.open("w", encoding="utf-8") as f:
        json.dump(har, f, ensure_ascii=False, indent=2)


def fmt_dmy(d: datetime) -> str:
    return d.strftime("%d.%m.%Y")


def capture_account(account_label: str, login: str, password: str, probe_only: bool) -> None:
    """Capture full endpoint set for one account; write 6 HAR files."""
    print(f"\n=== Account {account_label} ===", file=sys.stderr)
    out_dir = RAW_DIR / f"account-{account_label}"

    client = AversClient()

    # login.har: probe + login + auth
    login_entries: list[dict[str, Any]] = []
    client.probe_root(login_entries)
    user = client.login(login, password, login_entries)
    print(f"  login OK: user_id={user['user_id']} type={user['user_type']}", file=sys.stderr)

    # bootstrap (recorded into login.har — they're part of session establishment)
    user_data_text = client.act("get_user_data", None, login_entries, "get_user_data")
    user_data = parse_array(user_data_text) or []
    # Main.js: UchId = userData[0][4]. Observed shape after auth working:
    # [["lastname","f","p","school_full_name", uch_id, max_mark]] — uch_id=1
    # for школа №28 Кирова. Fallback to None if response empty.
    if user_data and len(user_data[0]) >= 5:
        uch_id = user_data[0][4]
    else:
        uch_id = None

    today = datetime.now()
    uch_year_text = client.act(
        "get_uch_year",
        {"currentDate": fmt_dmy(today)},
        login_entries,
        "get_uch_year",
    )
    uch_year_arr = parse_array(uch_year_text) or []
    if uch_year_arr and uch_year_arr[0]:
        uch_year = uch_year_arr[0][0]
    else:
        # Fallback: derive from current date (Aug-cutover convention).
        uch_year = current_academic_year(today)
    print(f"  uchId={uch_id} uchYear={uch_year}", file=sys.stderr)

    write_har(out_dir / "login.har", login_entries)
    print(f"  wrote {out_dir / 'login.har'} ({len(login_entries)} entries)", file=sys.stderr)

    if probe_only:
        return

    student_id = user["user_human_id"]
    student_class_params = {
        "currentDate": fmt_dmy(today),
        "student": student_id,
        "uchYear": uch_year,
        "uchId": uch_id,
    }

    # ---- discover student class (homework + grades chain) ----
    aux_entries: list[dict[str, Any]] = []
    sc_text = client.act("GET_STUDENT_CLASS", student_class_params, aux_entries, "GET_STUDENT_CLASS")
    student_class = parse_array(sc_text) or []
    cls_id = student_class[0][0] if student_class and student_class[0] else None
    print(f"  classId={cls_id}", file=sys.stderr)

    parallel_text = client.act(
        "GET_STUDENT_PARALLEL",
        {"student": student_id, "uchYear": uch_year, "uchId": uch_id},
        aux_entries,
        "GET_STUDENT_PARALLEL",
    )
    parallel_classes = parse_array(parallel_text) or []
    parallel_class_ids = [row[0] for row in parallel_classes if row]
    print(f"  parallelClassIds={parallel_class_ids}", file=sys.stderr)

    # ---- homework.har: GET_DAIRY_CLASS_SUBJECTS + GET_STUDENT_DAIRY ----
    homework_entries = list(aux_entries)  # include bootstrap context
    client.act(
        "GET_DAIRY_CLASS_SUBJECTS",
        {"pClassesIds": parallel_class_ids, "cls": cls_id},
        homework_entries,
        "GET_DAIRY_CLASS_SUBJECTS",
    )
    # current week (Mon..Sun, dmy)
    weekday = today.weekday()  # Mon=0
    monday = today.replace(hour=0, minute=0, second=0, microsecond=0)
    monday = monday.fromordinal(monday.toordinal() - weekday)
    sunday = monday.fromordinal(monday.toordinal() + 6)
    client.act(
        "GET_STUDENT_DAIRY",
        {
            "pClassesIds": parallel_class_ids,
            "student": student_id,
            "cls": cls_id,
            "begin_dt": fmt_dmy(monday),
            "end_dt": fmt_dmy(sunday),
        },
        homework_entries,
        "GET_STUDENT_DAIRY",
    )
    write_har(out_dir / "homework.har", homework_entries)
    print(f"  wrote homework.har ({len(homework_entries)} entries)", file=sys.stderr)

    # ---- grades.har: GET_PERIODS + GET_STUDENT_JOURNAL_DATA + GET_STUDENT_DIRECTOR_DATA ----
    grades_entries = list(aux_entries)
    client.act("GET_PERIODS", None, grades_entries, "GET_PERIODS")
    client.act(
        "GET_STUDENT_JOURNAL_DATA",
        {"cls": cls_id, "parallelClasses": parallel_class_ids, "student": student_id},
        grades_entries,
        "GET_STUDENT_JOURNAL_DATA",
    )
    client.act(
        "GET_STUDENT_DIRECTOR_DATA",
        {"cls": cls_id, "parallelClasses": parallel_class_ids, "student": student_id},
        grades_entries,
        "GET_STUDENT_DIRECTOR_DATA",
    )
    write_har(out_dir / "grades.har", grades_entries)
    print(f"  wrote grades.har ({len(grades_entries)} entries)", file=sys.stderr)

    # ---- schedule.har: GET_TIMETABLE ----
    schedule_entries: list[dict[str, Any]] = []
    client.act("GET_TIMETABLE", None, schedule_entries, "GET_TIMETABLE")
    write_har(out_dir / "schedule.har", schedule_entries)
    print(f"  wrote schedule.har ({len(schedule_entries)} entries)", file=sys.stderr)

    # ---- attendance.har: GET_ATT_JOURNAL_DATA (parent view may be empty/error) ----
    attendance_entries: list[dict[str, Any]] = []
    client.act(
        "GET_ATT_JOURNAL_DATA",
        {
            "cls": cls_id,
            "period_begin": fmt_dmy(monday),
            "period_end": fmt_dmy(sunday),
        },
        attendance_entries,
        "GET_ATT_JOURNAL_DATA",
    )
    write_har(out_dir / "attendance.har", attendance_entries)
    print(f"  wrote attendance.har ({len(attendance_entries)} entries)", file=sys.stderr)

    # ---- messages.har: get_sms ----
    messages_entries: list[dict[str, Any]] = []
    client.act("get_sms", {"uchYear": uch_year}, messages_entries, "get_sms")
    write_har(out_dir / "messages.har", messages_entries)
    print(f"  wrote messages.har ({len(messages_entries)} entries)", file=sys.stderr)


# ---------- entrypoint -------------------------------------------------------


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--probe", action="store_true", help="login probe only")
    parser.add_argument("--account", choices=("A", "B"), help="capture single account")
    parser.add_argument("--full", action="store_true", help="capture both accounts (default if no flags)")
    args = parser.parse_args()

    creds = load_env(ENV_FILE)
    accounts = [
        ("A", creds["CHILD1_LOGIN"], creds["CHILD1_PASSWORD"]),
        ("B", creds["CHILD2_LOGIN"], creds["CHILD2_PASSWORD"]),
    ]
    if args.account:
        accounts = [a for a in accounts if a[0] == args.account]

    for label, login, password in accounts:
        try:
            capture_account(label, login, password, probe_only=args.probe)
        except Exception as e:
            print(f"ERROR account {label}: {e}", file=sys.stderr)
            return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
