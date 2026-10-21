#!/usr/bin/env python3
"""Read-only, non-production HTTP smoke probes for RPL-55 G4.

This is NOT the full three-process G4 E2E, a deployment probe, or cutover proof.
The caller supplies a running non-production Gateway and a synthetic MANAGER token.
Only loopback HTTP endpoints are accepted. No request body or credential is logged.
"""
from __future__ import annotations

import json
import os
import sys
from dataclasses import dataclass
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener

CUSTOMERS = "/api/v1/users/customers"
INTERNAL = "/api/v1/internal/identity/claims/00000000-0000-0000-0000-000000000000"
SPOOFED = {
    "X-User-Id": "spoofed-user",
    "X-Organization-Id": "spoofed-organization",
    "X-Role": "spoofed-role",
    "X-Public-Id": "spoofed-public",
    "X-Gateway-Secret": "spoofed-gateway-secret",
    "X-Service-Id": "spoofed-service-id",
    "X-Service-Secret": "spoofed-service-secret",
}


@dataclass(frozen=True)
class HttpResult:
    status: int
    body: bytes


class ResponseContractViolation(RuntimeError):
    """A successful upstream HTTP status with an invalid Product response."""


def loopback_base(raw: str) -> str:
    parsed = urlsplit(raw)
    if (
        parsed.scheme != "http"
        or parsed.hostname not in {"localhost", "127.0.0.1", "::1"}
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
        or parsed.path not in {"", "/"}
    ):
        raise ValueError("Only non-production loopback HTTP origins are permitted")
    return raw.rstrip("/")


class _NoRedirect(HTTPRedirectHandler):
    """Never forward the synthetic bearer or trusted-header probes on 3xx."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


# Ignore HTTP_PROXY/HTTPS_PROXY for synthetic loopback-only test credentials.
_NO_REDIRECT_OPENER = build_opener(ProxyHandler({}), _NoRedirect())


def get(base: str, path: str, bearer: str | None = None,
        extra_headers: dict[str, str] | None = None) -> HttpResult:
    headers = {"Accept": "application/json"}
    if bearer is not None:
        headers["Authorization"] = "Bearer " + bearer
    headers.update(extra_headers or {})
    request = Request(base + path, headers=headers, method="GET")
    try:
        with _NO_REDIRECT_OPENER.open(request, timeout=8) as response:
            return HttpResult(response.status, response.read(1024 * 1024))
    except HTTPError as error:
        with error:
            return HttpResult(error.code, error.read(1024 * 1024))
    except (URLError, TimeoutError, OSError) as error:
        raise RuntimeError("Loopback endpoint unreachable; inspect non-production runtime") from error


def customer_items(result: HttpResult) -> list:
    try:
        parsed = json.loads(result.body)
        items = parsed["data"]
        if not isinstance(items, list):
            raise ValueError("data must be an array")
        return items
    except (ValueError, KeyError, TypeError) as error:
        raise ResponseContractViolation("Customer response is not the expected JSON data array") from error


def run() -> list[dict[str, str]]:
    if os.getenv("G4_NONPROD_ACK") != "YES_NONPROD_ONLY":
        raise ValueError("Set G4_NONPROD_ACK=YES_NONPROD_ONLY for disposable local tests")
    base = loopback_base(os.getenv("G4_GATEWAY_URL", "http://127.0.0.1:8080"))
    token = os.getenv("G4_MANAGER_ACCESS_TOKEN", "")
    if not token or "\r" in token or "\n" in token:
        raise ValueError("Provide a synthetic, non-production G4_MANAGER_ACCESS_TOKEN")

    records: list[dict[str, str]] = []

    def expect(case: str, result: HttpResult, status: int, detail: str) -> None:
        records.append({
            "case": case,
            "status": "PASS" if result.status == status else "FAIL",
            "http_status": str(result.status),
            "expected_http_status": str(status),
            "scope": detail,
        })

    baseline = get(base, CUSTOMERS, token)
    expect("G4-01-read", baseline, 200, "real Gateway HTTP → configured Product upstream")
    if baseline.status == 200:
        baseline_items = customer_items(baseline)
        spoofed = get(base, CUSTOMERS, token, SPOOFED)
        expect("G4-02-spoofed-headers", spoofed, 200, "same bearer, hostile external headers")
        if spoofed.status == 200:
            records.append({
                "case": "G4-02-same-owner-view",
                "status": "PASS" if customer_items(spoofed) == baseline_items else "FAIL",
                "scope": "same Customer list with and without externally supplied trusted headers",
            })
    else:
        records.append({"case": "G4-02", "status": "BLOCKED",
                        "scope": "requires a successful G4-01 owner view"})

    expect("G4-03-missing-bearer", get(base, CUSTOMERS), 401,
           "Gateway HTTP rejection; upstream invocation not directly observed")
    expect("G4-03-malformed-bearer", get(base, CUSTOMERS, "not.a.jwt"), 401,
           "Gateway HTTP rejection; upstream invocation not directly observed")

    for label, env in (
        ("expired", "G4_EXPIRED_ACCESS_TOKEN"),
        ("revoked", "G4_REVOKED_ACCESS_TOKEN"),
    ):
        optional = os.getenv(env)
        if optional:
            expect("G4-03-" + label, get(base, CUSTOMERS, optional), 401,
                   "caller-provided synthetic token")
        else:
            records.append({"case": "G4-03-" + label, "status": "NOT_RUN",
                            "scope": "optional token not supplied"})

    expect("G4-08-public-internal", get(base, INTERNAL, extra_headers=SPOOFED), 403,
           "public Gateway must deny /api/v1/internal/**")
    expect("G4-08-authenticated-internal", get(base, INTERNAL, token, SPOOFED), 403,
           "bearer does not authorize an internal-only path")

    foreign_id = os.getenv("G4_FOREIGN_CUSTOMER_PUBLIC_ID", "")
    if foreign_id:
        # This optional synthetic fixture must exist in another organization.
        import uuid
        foreign = str(uuid.UUID(foreign_id))
        expect("G4-05-foreign-owner", get(base, CUSTOMERS + "/" + foreign, token),
               404, "requires seeded other-organization Customer")
    else:
        records.append({"case": "G4-05-foreign-owner", "status": "NOT_RUN",
                        "scope": "requires seeded G4_FOREIGN_CUSTOMER_PUBLIC_ID"})

    # This probe cannot establish live token issuance, Core→Carelog projection,
    # actual upstream-call counts, DB state, or full G4 cutover/rollback evidence.
    for case in ("G4-04", "G4-06", "G4-07"):
        records.append({"case": case, "status": "NOT_RUN",
                        "scope": "needs separate real-process/integration evidence"})
    return records


def main() -> int:
    try:
        records = run()
    except ResponseContractViolation:
        # A reachable server returning an invalid Product contract is a failure,
        # not an environment blocker. Do not print the response body.
        print(json.dumps({"verdict": "FAIL", "reason": "Unexpected Customer response contract"},
                         ensure_ascii=False))
        return 1
    except (ValueError, RuntimeError) as error:
        # Avoid echoing endpoint response bodies, tokens, URL credentials, or secrets.
        print(json.dumps({"verdict": "BLOCKED", "reason": str(error)},
                         ensure_ascii=False))
        return 2

    failures = any(record["status"] == "FAIL" for record in records)
    summary = {
        "verdict": "FAIL" if failures else "EDGE_SMOKE_PASS_ONLY",
        "full_g4_e2e": "NOT_VERIFIED",
        "source_merge_or_cutover_authorized": False,
        "checks": records,
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
