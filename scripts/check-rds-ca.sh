#!/usr/bin/env sh
# Checks the vendored AWS RDS CA bundle and, when RDS_CA_CHECK_IMAGE is set,
# that the built runtime image exposes a non-empty /run/rds-ca.pem.
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
BUNDLE="$ROOT/docker/rds-ca/ap-northeast-2-bundle.pem"
EXPECTED_SHA256=913fb5b814f17af79d4c1622584a8d0ceddf5b0d76fe353d0c7d1186cdd6b229
EXPECTED_CERTS=3

fail() { echo "FAIL: $*" >&2; exit 1; }

test -s "$BUNDLE" || fail "missing or empty bundle: $BUNDLE"

actual_sha=$(sha256sum "$BUNDLE" | cut -d' ' -f1)
[ "$actual_sha" = "$EXPECTED_SHA256" ] || fail "sha256 mismatch: $actual_sha (see docker/rds-ca/README.md before rotating)"

certs=$(grep -c 'BEGIN CERTIFICATE' "$BUNDLE")
[ "$certs" -eq "$EXPECTED_CERTS" ] || fail "expected $EXPECTED_CERTS certificates, found $certs"

# Every certificate must parse and still be valid for at least 30 days.
tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT
awk -v dir="$tmpdir" '/BEGIN CERTIFICATE/{n++} n{print > (dir "/cert" n ".pem")}' "$BUNDLE"
for cert in "$tmpdir"/cert*.pem; do
  openssl x509 -in "$cert" -noout >/dev/null 2>&1 || fail "unparsable certificate in bundle: $cert"
  openssl x509 -in "$cert" -noout -checkend 2592000 >/dev/null || fail "certificate expires within 30 days: $(openssl x509 -in "$cert" -noout -subject)"
  echo "ok: $(openssl x509 -in "$cert" -noout -subject | sed 's/.*CN = //') notAfter=$(openssl x509 -in "$cert" -noout -enddate | cut -d= -f2)"
done

if [ -n "${RDS_CA_CHECK_IMAGE:-}" ]; then
  command -v docker >/dev/null 2>&1 || fail "RDS_CA_CHECK_IMAGE set but docker is not available"
  docker run --rm --entrypoint sh "$RDS_CA_CHECK_IMAGE" -c 'test -s /run/rds-ca.pem && test -d /run/secrets' \
    || fail "image $RDS_CA_CHECK_IMAGE does not contain /run/rds-ca.pem and /run/secrets"
  echo "ok: image $RDS_CA_CHECK_IMAGE has /run/rds-ca.pem and /run/secrets"
fi

echo "PASS: RDS CA bundle checks"
