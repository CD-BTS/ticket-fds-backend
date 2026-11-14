"""Read the existing AI CSV; dry-run by default, never create or delete DB data directly."""

import argparse
import csv
import hashlib
import json
import math
import os
import re
import sys
import uuid
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
from itertools import islice
from pathlib import Path
from urllib import error, parse, request

DEFAULT_CSV = Path(__file__).resolve().parents[2] / "ai" / "data" / "behavior_event.csv"
COLUMNS = {"event_id", "external_event_id", "user_id", "event_type", "booking_id",
           "ticket_id", "performance_id", "occurred_at", "metadata"}
UTC = timezone.utc


def parse_time(value):
    # Match the ISO representation used by the CSV, without Python's permissive date syntax.
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})", value):
        raise ValueError("occurred_at: ISO 8601 UTC offset is required")
    fraction = re.search(r"\.(\d+)", value)
    if fraction and any(c != "0" for c in fraction[1]):
        raise ValueError("occurred_at: DATETIME(0) cannot store nonzero fractional seconds")
    instant = datetime.fromisoformat(value.replace("Z", "+00:00"))
    offset = abs(instant.utcoffset().total_seconds())
    if offset > 18 * 3600:
        raise ValueError("occurred_at: UTC offset must be within +/-18:00")
    instant = instant.astimezone(UTC)
    if instant.year < 1000:
        raise ValueError("occurred_at: UTC year must be within 1000..9999")
    return instant


def text_field(value, name, maximum, required=True):
    if value is None or (required and not value.strip()):
        raise ValueError(f"{name}: required nonblank string")
    # Java @Size uses UTF-16 code units.
    if len(value.encode("utf-16-le")) // 2 > maximum:
        raise ValueError(f"{name}: maximum {maximum} characters")
    return value


def strict_object(value):
    def pairs(items):
        obj = {}
        for key, item in items:
            if key in obj:
                raise ValueError(f"metadata: duplicate key {key!r}")
            obj[key] = item
        return obj

    def invalid_constant(value):
        raise ValueError(f"metadata: invalid JSON number {value}")

    obj = json.loads(value, object_pairs_hook=pairs, parse_constant=invalid_constant)
    if not isinstance(obj, dict):
        raise ValueError("metadata: JSON Object required")
    # Reject numeric overflow (e.g. 1e999), including inside additional properties.
    def finite(item):
        if isinstance(item, float) and not math.isfinite(item):
            raise ValueError("metadata: nonfinite JSON number")
        if isinstance(item, dict):
            for child in item.values():
                finite(child)
        elif isinstance(item, list):
            for child in item:
                finite(child)
    finite(obj)
    if "bookingDurationSeconds" in obj:
        value = obj["bookingDurationSeconds"]
        if isinstance(value, bool) or not isinstance(value, (int, float)) or value < 0:
            raise ValueError("metadata.bookingDurationSeconds: nonnegative number required")
    if "deviceChanged" in obj and not isinstance(obj["deviceChanged"], bool):
        raise ValueError("metadata.deviceChanged: boolean required")
    return obj


def convert_row(row, service):
    external_user = text_field(row["user_id"], "user_id", 100)
    event_type = row["event_type"]
    if event_type not in {"BOOKING", "CANCEL", "CHECK_IN"}:
        raise ValueError("event_type: expected BOOKING, CANCEL or CHECK_IN")
    booking = row["booking_id"] or None
    ticket = row["ticket_id"] or None
    text_field(booking, "booking_id", 100) if event_type != "CHECK_IN" else None
    text_field(ticket, "ticket_id", 100) if event_type == "CHECK_IN" else None
    for name, value in (("booking_id", booking), ("ticket_id", ticket)):
        if value is not None:
            text_field(value, name, 100, required=False)
    instant = parse_time(row["occurred_at"])
    metadata = strict_object(row["metadata"])
    performance = row["performance_id"]
    if performance:
        if "performance_id" in metadata and metadata["performance_id"] != performance:
            raise ValueError("performance_id conflicts with existing metadata.performance_id")
        # API has no top-level performance field; keep the original column name explicitly.
        metadata["performance_id"] = performance
    payload = {"serviceId": service,
               "externalEventId": text_field(row["external_event_id"], "external_event_id", 100),
               "eventType": event_type, "bookingId": booking, "ticketId": ticket,
               "occurredAt": row["occurred_at"], "metadata": metadata}
    return external_user, payload, instant


def load_csv(path, service, limit=None):
    records, errors = [], []
    users, types, metadata_types = set(), Counter(), defaultdict(Counter)
    offsets, empty_metadata = Counter(), 0
    with path.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream, strict=True)
        headers = reader.fieldnames or []
        if len(headers) != len(set(headers)) or set(headers) != COLUMNS:
            raise ValueError(f"CSV columns do not match inspected AI source: {headers}; expected {sorted(COLUMNS)}")
        rows = 0
        for row in islice(reader, limit):
            rows += 1
            # Physical ending line, including quoted multiline CSV records.
            line = reader.line_num
            users.add(row.get("user_id"))
            types[row.get("event_type")] += 1
            try:
                if None in row or any(value is None for value in row.values()):
                    raise ValueError("CSV row has missing or extra cells")
                external_user, payload, instant = convert_row(row, service)
                original = strict_object(row["metadata"])
                empty_metadata += not original
                offsets[re.search(r"(?:Z|[+-]\d{2}:\d{2})$", row["occurred_at"])[0]] += 1
                for key, value in original.items():
                    metadata_types[key][type(value).__name__] += 1
                records.append((line, external_user, payload, instant))
            except (ValueError, TypeError, OverflowError) as exc:
                errors.append((line, str(exc)))
    return records, errors, {"limit": limit, "rows": rows, "users": len(users), "event_types": dict(types),
                             "metadata_types": {key: dict(value) for key, value in metadata_types.items()},
                             "original_empty_metadata": empty_metadata, "utc_offsets": dict(offsets)}


def report(path, records, errors, stats, as_of):
    times = [record[3] for record in records]
    start = as_of - timedelta(days=30)
    recent = Counter(p["eventType"] for _, _, p, time in records if start <= time <= as_of)
    signatures, replays, conflicts = {}, 0, 0
    for _, user, payload, time in records:
        signature = dict(payload, userId=user, occurredAt=time.isoformat())
        key = payload["externalEventId"]
        if key in signatures:
            if signature == signatures[key]:
                replays += 1
            else:
                conflicts += 1
        else:
            signatures[key] = signature
    print(json.dumps({"csv": str(path.resolve()), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                      **stats, "valid_rows": len(records), "validation_errors": len(errors),
                      "utc_min": min(times).isoformat() if times else None,
                      "utc_max": max(times).isoformat() if times else None,
                      "as_of": as_of.isoformat(), "last_30_days": sum(recent.values()),
                      "last_30_days_by_type": dict(recent),
                      "before_window": sum(t < start for t in times),
                      "future": sum(t > as_of for t in times),
                      "duplicate_identical_rows": replays, "duplicate_conflicting_rows": conflicts,
                      "mapping": "event_id ignored; performance_id preserved in metadata.performance_id; original offsets and metadata keys retained"},
                     ensure_ascii=False, indent=2))
    for line, message in errors:
        print(f"CSV line {line}: {message}", file=sys.stderr)


class NoRedirect(request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None  # Never forward an API key to a redirected host.


def post_json(opener, base, key, endpoint, payload, timeout):
    req = request.Request(base + endpoint, data=json.dumps(payload, allow_nan=False).encode("utf-8"),
                          headers={"Content-Type": "application/json", "X-API-Key": key}, method="POST")
    try:
        with opener.open(req, timeout=timeout) as response:
            status, raw = response.status, response.read()
    except error.HTTPError as exc:
        status, raw = exc.code, exc.read()
    except (error.URLError, TimeoutError, OSError) as exc:
        return 0, {"message": str(exc).replace(key, "<redacted>")}
    try:
        result = json.loads(raw)
        if not isinstance(result, dict):
            raise ValueError("response is not an object")
    except (ValueError, UnicodeError):
        result = {"message": "response is not a JSON object"}
    return status, result


def category(status, body):
    if status in (200, 201) and body.get("success") is True and body.get("code") == f"COMMON_{status}":
        return "created_201" if status == 201 else "replayed_200"
    return "conflict_409" if status == 409 else "failed"


def send(records, base, key, service, timeout):
    opener = request.build_opener(request.ProxyHandler({}), NoRedirect())
    categories = {"created_201": 0, "replayed_200": 0, "conflict_409": 0, "failed": 0}
    mapping, totals = {}, {"users": Counter(categories), "events": Counter(categories)}
    user_lines = defaultdict(list)
    for line, user, _, _ in records:
        user_lines[user].append(line)
    for user, lines in user_lines.items():
        status, body = post_json(opener, base, key, "/api/v1/users",
                                 {"serviceId": service, "externalUserId": user}, timeout)
        outcome = category(status, body)
        if outcome in ("created_201", "replayed_200"):
            data = body.get("data")
            try:
                internal = data["userId"]
                uuid.UUID(internal)
                if data.get("externalUserId") != user or data.get("serviceId") != service:
                    raise ValueError("user identity differs from request")
                mapping[user] = internal
            except (TypeError, KeyError, ValueError, AttributeError):
                outcome = "failed"
                body = {"message": "invalid internal UUID or mismatched registration response"}
        totals["users"][outcome] += 1
        if outcome not in ("created_201", "replayed_200"):
            print(f"CSV lines {lines}: user {user!r}, HTTP {status}, {json.dumps(body, ensure_ascii=False)}".replace(key, "<redacted>"), file=sys.stderr)
    for line, user, payload, _ in records:
        if user not in mapping:
            totals["events"]["skipped_user_failure"] += 1
            print(f"CSV line {line}: event skipped because user registration failed", file=sys.stderr)
            continue
        status, body = post_json(opener, base, key, "/api/v1/events",
                                 dict(payload, userId=mapping[user]), timeout)
        outcome = category(status, body)
        totals["events"][outcome] += 1
        if outcome not in ("created_201", "replayed_200"):
            print(f"CSV line {line}: HTTP {status}, {json.dumps(body, ensure_ascii=False)}".replace(key, "<redacted>"), file=sys.stderr)
    print(json.dumps({"result": {group: dict(counts) for group, counts in totals.items()},
                      "mapped_users": len(mapping)}, ensure_ascii=False, indent=2))
    return 1 if any(counts.get(name, 0) for counts in totals.values()
                    for name in ("failed", "conflict_409", "skipped_user_failure")) else 0


def positive_integer(value):
    try:
        number = int(value)
    except ValueError:
        raise argparse.ArgumentTypeError("limit must be a positive integer") from None
    if number <= 0:
        raise argparse.ArgumentTypeError("limit must be a positive integer")
    return number


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--csv", type=Path, default=Path(os.getenv("BTS_DUMMY_CSV", str(DEFAULT_CSV))))
    parser.add_argument("--base-url", default=os.getenv("BTS_API_BASE_URL", "http://localhost:8080"))
    parser.add_argument("--api-key", default=os.getenv("BTS_API_KEY"), help="Prefer BTS_API_KEY environment variable")
    parser.add_argument("--service-id", default=os.getenv("BTS_SERVICE_ID"))
    parser.add_argument("--as-of", help="UTC-offset ISO timestamp for the 30-day report only; no date shifting")
    parser.add_argument("--timeout", type=float, default=30)
    parser.add_argument("--limit", type=positive_integer, default=None,
                        help="Process only the first N CSV events (default: unlimited)")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", help="Validate only (default); no network requests")
    mode.add_argument("--execute", action="store_true", help="Actually register users and submit selected events")
    parser.add_argument("--confirm-test-environment", action="store_true",
                        help="Confirm that the backend AND its DB are a disposable/nonproduction test environment")
    parser.add_argument("--allow-remote", action="store_true", help="Explicitly permit a non-loopback test backend")
    args = parser.parse_args(argv)
    try:
        service = text_field(args.service_id, "serviceId", 50)
        if not math.isfinite(args.timeout) or args.timeout <= 0:
            raise ValueError("timeout must be positive and finite")
        as_of = parse_time(args.as_of) if args.as_of else datetime.now(UTC).replace(microsecond=0)
        records, errors, stats = load_csv(args.csv, service, args.limit)
        report(args.csv, records, errors, stats, as_of)
        if errors:
            print("Preflight failed; no API requests sent.", file=sys.stderr)
            return 2
        if not records:
            raise ValueError("CSV has no events")
        if not args.execute:
            print("DRY RUN: no API requests sent; dates were not shifted.")
            return 0
        base = args.base_url.rstrip("/")
        url = parse.urlsplit(base)
        if url.scheme not in ("http", "https") or not url.hostname or url.username or url.password or url.query or url.fragment:
            raise ValueError("base URL must be an HTTP(S) URL without credentials, query or fragment")
        if url.hostname not in ("localhost", "127.0.0.1", "::1") and not args.allow_remote:
            raise ValueError("non-loopback target requires --allow-remote")
        if not args.confirm_test_environment:
            raise ValueError("actual import requires --confirm-test-environment after checking the backend DB settings")
        if not args.api_key or not args.api_key.strip():
            raise ValueError("set BTS_API_KEY or --api-key for actual import")
        if "\r" in args.api_key or "\n" in args.api_key:
            raise ValueError("API key must not contain newline characters")
        print(f"Target: {base}, serviceId={service}, users={stats['users']}, events={len(records)}")
        return send(records, base, args.api_key, service, args.timeout)
    except (OSError, ValueError, OverflowError, csv.Error) as exc:
        message = str(exc)
        if args.api_key:
            message = message.replace(args.api_key, "<redacted>")
        print(f"ERROR: {message}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
