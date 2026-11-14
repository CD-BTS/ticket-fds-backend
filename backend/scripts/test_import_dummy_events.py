"""Standard-library tests; no real backend or DB requests."""

import contextlib
import csv
import io
import json
import tempfile
import unittest
from urllib.error import HTTPError
from pathlib import Path
from unittest.mock import patch

import import_dummy_events as importer


def row(**changes):
    original = {"event_id": "1", "external_event_id": "EXT-0000001", "user_id": "U0001",
                "event_type": "BOOKING", "booking_id": "B0001-001", "ticket_id": "T0001-001",
                "performance_id": "PERF-001", "occurred_at": "2026-09-13T16:53:52+00:00",
                "metadata": json.dumps({"booking_duration_seconds": 25, "custom": {"a": [1, False]}})}
    return dict(original, **changes)


class ImportTests(unittest.TestCase):
    def csv_file(self, directory, rows):
        path = Path(directory) / "events.csv"
        with path.open("w", encoding="utf-8-sig", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=list(row()))
            writer.writeheader()
            writer.writerows(rows)
        return path

    def test_preserves_metadata_offset_and_performance_without_sending_event_id(self):
        source = row()
        user, payload, instant = importer.convert_row(source, "service")
        self.assertEqual("U0001", user)
        self.assertEqual(source["occurred_at"], payload["occurredAt"])
        self.assertEqual(25, payload["metadata"]["booking_duration_seconds"])
        self.assertEqual({"a": [1, False]}, payload["metadata"]["custom"])
        self.assertEqual("PERF-001", payload["metadata"]["performance_id"])
        self.assertNotIn("eventId", payload)
        self.assertNotIn("event_id", payload)
        self.assertEqual(importer.parse_time("2026-09-14T01:53:52+09:00"), instant)

    def test_limit_selects_csv_prefix_and_default_remains_unlimited(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.csv_file(directory, [row(external_event_id=f"EXT-{i}") for i in range(12)])
            limited, errors, stats = importer.load_csv(path, "service", limit=10)
            self.assertEqual([], errors)
            self.assertEqual(10, stats["rows"])
            self.assertEqual([f"EXT-{i}" for i in range(10)], [item[2]["externalEventId"] for item in limited])
            self.assertEqual(12, len(importer.load_csv(path, "service")[0]))
            self.assertEqual(12, len(importer.load_csv(path, "service", limit=20)[0]))

    def test_limit_dry_run_ignores_errors_outside_selected_prefix(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.csv_file(directory, [row(), row(occurred_at="invalid", user_id="U0002")])
            stdout = io.StringIO()
            with patch.object(importer, "send") as send, contextlib.redirect_stdout(stdout):
                self.assertEqual(0, importer.main(["--csv", str(path), "--service-id", "service", "--dry-run", "--limit", "1"]))
                send.assert_not_called()
            report = json.loads(stdout.getvalue().split("\nDRY RUN:")[0])
            self.assertEqual(1, report["limit"])
            self.assertEqual(1, report["rows"])
            self.assertEqual(1, report["users"])
            self.csv_file(directory, [row(occurred_at="invalid"), row()])
            with patch.object(importer, "send") as send, contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(2, importer.main(["--csv", str(path), "--service-id", "service", "--limit", "1"]))
                send.assert_not_called()

    def test_limit_execute_registers_only_users_of_selected_events(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.csv_file(directory, [row(), row(external_event_id="EXT-2"),
                                             row(user_id="U0002", external_event_id="EXT-3")])
            user_response = {"success": True, "code": "COMMON_200", "data": {
                "userId": "c4825ba5-5692-44eb-9882-a8abf733008e", "externalUserId": "U0001", "serviceId": "service"}}
            replies = [(200, user_response), (201, {"success": True, "code": "COMMON_201"}),
                       (200, {"success": True, "code": "COMMON_200"})]
            with patch.object(importer, "post_json", side_effect=replies) as post, contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(0, importer.main(["--csv", str(path), "--service-id", "service",
                                                  "--limit", "2", "--execute", "--confirm-test-environment",
                                                  "--api-key", "secret", "--base-url", "http://localhost:8080"]))
            self.assertEqual(3, post.call_count)
            self.assertEqual("/api/v1/users", post.call_args_list[0].args[3])
            self.assertEqual("U0001", post.call_args_list[0].args[4]["externalUserId"])
            self.assertEqual(["EXT-0000001", "EXT-2"], [call.args[4]["externalEventId"] for call in post.call_args_list[1:]])

    def test_limit_rejects_zero_negative_and_noninteger_values(self):
        for value in ("0", "-1", "1.5", "invalid"):
            with self.subTest(value=value), contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as error:
                importer.main(["--limit", value])
            self.assertEqual(2, error.exception.code)

    def test_rejects_missing_offsets_invalid_calendar_and_subsecond_loss(self):
        for value in ("2026-09-13T16:53:52", "2026-02-30T00:00:00Z",
                      "2026-09-13T16:53:52.000000001Z", "2026-09-13T16:53:52+19:00"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                importer.parse_time(value)
        self.assertEqual(importer.parse_time("2026-09-13T16:53:52Z"),
                         importer.parse_time("2026-09-13T16:53:52.000Z"))

    def test_rejects_bad_metadata_and_preserves_unknown_properties(self):
        for value in ('[]', 'null', '{"x": NaN}', '{"x": 1e999}', '{"x": 1, "x": 2}',
                      '{"bookingDurationSeconds": true}', '{"bookingDurationSeconds": -1}',
                      '{"deviceChanged": 1}'):
            with self.subTest(value=value), self.assertRaises(ValueError):
                importer.strict_object(value)
        self.assertEqual({"bookingDurationSeconds": 0.25, "deviceChanged": False},
                         importer.strict_object('{"bookingDurationSeconds": 0.25, "deviceChanged": false}'))
        with self.assertRaises(ValueError):
            importer.convert_row(row(metadata='{"performance_id": "different"}'), "service")

    def test_conditionally_required_identifiers(self):
        for changes in ({"booking_id": ""}, {"event_type": "CANCEL", "booking_id": " "},
                        {"event_type": "CHECK_IN", "ticket_id": ""}, {"event_type": "OTHER"}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                importer.convert_row(row(**changes), "service")
        importer.convert_row(row(event_type="CHECK_IN", booking_id=""), "service")

    def test_dry_run_never_sends_and_reports_bad_csv_line(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.csv_file(directory, [row(), row(occurred_at="missing-offset")])
            records, errors, stats = importer.load_csv(path, "service")
            self.assertEqual(2, stats["rows"])
            self.assertEqual(1, len(records))
            self.assertEqual(3, errors[0][0])
            with patch.object(importer, "send") as send, contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(2, importer.main(["--csv", str(path), "--service-id", "service", "--dry-run"]))
                send.assert_not_called()
            self.csv_file(directory, [row()])
            with patch.object(importer, "send") as send, contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(0, importer.main(["--csv", str(path), "--service-id", "service"]))
                send.assert_not_called()

    def test_user_uuid_mapping_summary_and_row_failure_continue(self):
        records = []
        for line in range(2, 6):
            user, payload, instant = importer.convert_row(row(external_event_id=f"EXT-{line}"), "service")
            records.append((line, user, payload, instant))
        internal_id = "c4825ba5-5692-44eb-9882-a8abf733008e"
        def response(status):
            return {"success": True, "code": f"COMMON_{status}",
                    "data": {"userId": internal_id, "externalUserId": "U0001", "serviceId": "service"}}
        replies = [(200, response(200)), (201, response(201)), (200, response(200)),
                   (409, {"code": "DATA_CONFLICT"}), (400, {"code": "INVALID_REQUEST"})]
        stdout, stderr = io.StringIO(), io.StringIO()
        with patch.object(importer, "post_json", side_effect=replies) as post, contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            self.assertEqual(1, importer.send(records, "http://localhost:8080", "secret", "service", 10))
        self.assertEqual(5, post.call_count)
        for call in post.call_args_list[1:]:
            payload = call.args[4]
            self.assertEqual(internal_id, payload["userId"])
            self.assertEqual("2026-09-13T16:53:52+00:00", payload["occurredAt"])
        result = json.loads(stdout.getvalue())["result"]
        self.assertEqual({"created_201": 0, "replayed_200": 1, "conflict_409": 0, "failed": 0}, result["users"])
        self.assertEqual({"created_201": 1, "replayed_200": 1, "conflict_409": 1, "failed": 1}, result["events"])
        self.assertIn("CSV line 4", stderr.getvalue())
        self.assertIn("CSV line 5", stderr.getvalue())

    def test_failed_user_skips_dependent_events_and_redacts_key(self):
        user, payload, instant = importer.convert_row(row(), "service")
        stderr = io.StringIO()
        with patch.object(importer, "post_json", return_value=(401, {"message": "secret"})) as post, contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(stderr):
            self.assertEqual(1, importer.send([(2, user, payload, instant)], "http://localhost", "secret", "service", 10))
        self.assertEqual(1, post.call_count)
        self.assertNotIn("secret", stderr.getvalue())
        self.assertIn("CSV line 2", stderr.getvalue())

    def test_execute_requires_environment_confirmation(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.csv_file(directory, [row()])
            with patch.object(importer, "send") as send, contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(2, importer.main(["--csv", str(path), "--service-id", "service", "--execute", "--api-key", "secret"]))
                send.assert_not_called()

    def test_http_transport_serializes_payload_and_reads_error_body(self):
        class Opener:
            def open(self, req, timeout):
                self.request = req
                self.timeout = timeout
                raise HTTPError(req.full_url, 409, "Conflict", {}, io.BytesIO(
                    b'{"success":false,"code":"DATA_CONFLICT","data":null,"message":"conflict"}'))
        opener = Opener()
        status, result = importer.post_json(opener, "http://localhost:8080", "secret",
                                            "/api/v1/events", {"metadata": {"original_key": 25}}, 10)
        self.assertEqual(409, status)
        self.assertEqual("conflict_409", importer.category(status, result))
        self.assertEqual("POST", opener.request.get_method())
        self.assertEqual("secret", opener.request.get_header("X-api-key"))
        self.assertEqual({"metadata": {"original_key": 25}}, json.loads(opener.request.data))

    def test_window_and_duplicate_report_use_instants(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.csv_file(directory, [row(), row(occurred_at="2026-09-14T01:53:52+09:00")])
            records, errors, stats = importer.load_csv(path, "service")
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                importer.report(path, records, errors, stats, importer.parse_time("2026-10-11T00:00:00Z"))
            report = json.loads(output.getvalue())
            self.assertEqual(2, report["last_30_days"])
            self.assertEqual(1, report["duplicate_identical_rows"])
            self.assertEqual(0, report["duplicate_conflicting_rows"])


if __name__ == "__main__":
    unittest.main()
