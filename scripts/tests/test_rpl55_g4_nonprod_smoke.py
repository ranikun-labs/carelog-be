"""Self-tests for the local, non-production G4 smoke runner; NOT application E2E."""
from __future__ import annotations

import contextlib
import io
import json
import os
import sys
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import rpl55_g4_nonprod_smoke as smoke


class StubHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/unsafe-redirect":
            self.send_response(302)
            self.send_header("Location", "http://127.0.0.1:9/should-never-follow")
            self.end_headers()
            return
        if self.path.startswith("/api/v1/internal/"):
            status, body = 403, b""
        elif self.path.startswith("/api/v1/users/customers"):
            if self.headers.get("Authorization") == "Bearer synthetic-token":
                status, body = 200, b'{"data":[{"publicId":"fixture-customer"}]}'
            else:
                status, body = 401, b""
        else:
            status, body = 404, b""
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        pass


class NonprodSmokeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), StubHandler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.url = "http://127.0.0.1:" + str(cls.server.server_port)

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=5)

    def test_rejects_remote_and_https_targets(self):
        for url in (
            "https://127.0.0.1:8080",
            "http://example.com:8080",
            "http://127.0.0.1:8080/other",
            "http://127.0.0.1:8080?token=leak",
        ):
            with self.subTest(url=url), self.assertRaises(ValueError):
                smoke.loopback_base(url)

    def test_no_ambient_proxy_can_capture_loopback_token(self):
        # A proxy configured in the environment must never see synthetic
        # test credentials, even if NO_PROXY is empty.
        from urllib.request import ProxyHandler
        proxy_handlers = [
            handler for handler in smoke._NO_REDIRECT_OPENER.handlers
            if isinstance(handler, ProxyHandler)
        ]
        # build_opener omits an empty ProxyHandler after using it to
        # suppress environment-derived default proxy setup.
        self.assertEqual(proxy_handlers, [])

        with patch.dict(os.environ, {
            "HTTP_PROXY": "http://127.0.0.1:9",
            "http_proxy": "http://127.0.0.1:9",
            "NO_PROXY": "",
            "no_proxy": "",
        }):
            result = smoke.get(self.url, smoke.CUSTOMERS, "synthetic-token")
        self.assertEqual(result.status, 200)

    def test_local_redirect_is_not_followed_with_bearer(self):
        # A local 302 to another endpoint must not forward any synthetic
        # token or attempt to connect to an attacker-controlled endpoint.
        response = smoke.get(
            self.url, "/unsafe-redirect", bearer="synthetic-token",
            extra_headers=smoke.SPOOFED,
        )
        self.assertEqual(response.status, 302)

    def test_requires_explicit_nonprod_ack(self):
        with patch.dict(os.environ, {"G4_GATEWAY_URL": self.url,
                                    "G4_MANAGER_ACCESS_TOKEN": "synthetic-token"}, clear=True):
            with self.assertRaises(ValueError):
                smoke.run()

    def test_requires_synthetic_token(self):
        with patch.dict(os.environ, {"G4_GATEWAY_URL": self.url,
                                    "G4_NONPROD_ACK": "YES_NONPROD_ONLY"}, clear=True):
            with self.assertRaises(ValueError):
                smoke.run()

    def test_stub_contract_passes_only_edge_smoke(self):
        with patch.dict(os.environ, {
            "G4_NONPROD_ACK": "YES_NONPROD_ONLY",
            "G4_GATEWAY_URL": self.url,
            "G4_MANAGER_ACCESS_TOKEN": "synthetic-token",
        }, clear=True):
            records = smoke.run()
        found = {item["case"]: item["status"] for item in records}
        self.assertEqual(found["G4-01-read"], "PASS")
        self.assertEqual(found["G4-02-spoofed-headers"], "PASS")
        self.assertEqual(found["G4-02-same-owner-view"], "PASS")
        self.assertEqual(found["G4-03-missing-bearer"], "PASS")
        self.assertEqual(found["G4-03-malformed-bearer"], "PASS")
        self.assertEqual(found["G4-08-public-internal"], "PASS")
        self.assertEqual(found["G4-06"], "NOT_RUN")

    def test_cli_output_never_discloses_token_or_claims_full_e2e(self):
        with patch.dict(os.environ, {
            "G4_NONPROD_ACK": "YES_NONPROD_ONLY",
            "G4_GATEWAY_URL": self.url,
            "G4_MANAGER_ACCESS_TOKEN": "synthetic-token",
        }, clear=True):
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                code = smoke.main()
        self.assertEqual(code, 0)
        payload = json.loads(output.getvalue())
        self.assertEqual(payload["verdict"], "EDGE_SMOKE_PASS_ONLY")
        self.assertEqual(payload["full_g4_e2e"], "NOT_VERIFIED")
        self.assertNotIn("synthetic-token", output.getvalue())


    def test_unexpected_customer_contract_is_failure_not_blocker(self):
        original_get = smoke.get

        def corrupt_customer_body(base, path, bearer=None, extra_headers=None):
            if path == smoke.CUSTOMERS and bearer == "synthetic-token":
                return smoke.HttpResult(200, b'{"data":"not-an-array"}')
            return original_get(base, path, bearer, extra_headers)

        with patch.dict(os.environ, {
            "G4_NONPROD_ACK": "YES_NONPROD_ONLY",
            "G4_GATEWAY_URL": self.url,
            "G4_MANAGER_ACCESS_TOKEN": "synthetic-token",
        }, clear=True), patch.object(smoke, "get", side_effect=corrupt_customer_body):
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                code = smoke.main()
        self.assertEqual(code, 1)
        payload = json.loads(output.getvalue())
        self.assertEqual(payload["verdict"], "FAIL")
        self.assertNotIn("synthetic-token", output.getvalue())
        self.assertNotIn("not-an-array", output.getvalue())

    def test_failed_unauthenticated_probe_returns_nonzero(self):
        original_get = smoke.get

        def broken_missing_auth(base, path, bearer=None, extra_headers=None):
            if path == smoke.CUSTOMERS and bearer is None:
                return smoke.HttpResult(200, b'{"data":[]}')
            return original_get(base, path, bearer, extra_headers)

        with patch.dict(os.environ, {
            "G4_NONPROD_ACK": "YES_NONPROD_ONLY",
            "G4_GATEWAY_URL": self.url,
            "G4_MANAGER_ACCESS_TOKEN": "synthetic-token",
        }, clear=True), patch.object(smoke, "get", side_effect=broken_missing_auth):
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                code = smoke.main()
        self.assertEqual(code, 1)
        payload = json.loads(output.getvalue())
        self.assertEqual(payload["verdict"], "FAIL")
        self.assertNotIn("synthetic-token", output.getvalue())

    def test_missing_nonprod_ack_returns_blocked(self):
        with patch.dict(os.environ, {
            "G4_GATEWAY_URL": self.url,
            "G4_MANAGER_ACCESS_TOKEN": "synthetic-token",
        }, clear=True):
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                code = smoke.main()
        self.assertEqual(code, 2)
        self.assertEqual(json.loads(output.getvalue())["verdict"], "BLOCKED")


if __name__ == "__main__":
    unittest.main()
