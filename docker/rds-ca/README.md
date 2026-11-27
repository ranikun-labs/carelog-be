# AWS RDS CA bundle (ap-northeast-2)

Vendored trust anchors for PgJDBC `sslmode=verify-full` (`sslrootcert=/run/rds-ca.pem`).
The runtime image copies this file to `/run/rds-ca.pem`; compose bind-mounts `CARELOG_RDS_CA_FILE` over it.

| Field | Value |
| --- | --- |
| Source | https://truststore.pki.rds.amazonaws.com/ap-northeast-2/ap-northeast-2-bundle.pem |
| Fetched | 2026-11-25 (UTC) |
| SHA-256 | `913fb5b814f17af79d4c1622584a8d0ceddf5b0d76fe353d0c7d1186cdd6b229` |
| Certificates | 3 (Root CA ECC384 G1, RSA2048 G1, RSA4096 G1) |
| Earliest notAfter | 2061-05-20 (RSA2048 G1); ECC384 G1 and RSA4096 G1 expire 2121-05-20 |

`scripts/check-rds-ca.sh` verifies the parse, certificate count, expiry and SHA-256 above.

## Rotation

1. Download the bundle again from the source URL and confirm it is the new AWS bundle (not an error page).
2. Replace `ap-northeast-2-bundle.pem`; run `scripts/check-rds-ca.sh` and update the SHA-256 in the script and in this table.
3. Update the fetch date and certificate expiry in this table.
4. Build a new image and check it: `RDS_CA_CHECK_IMAGE=<tag> scripts/check-rds-ca.sh`.
5. Compare with `CARELOG_RDS_CA_FILE` on the host. If an operator mounted a bundle, that mount still overrides the image copy until it is replaced too.

Do not disable `verify-full` or drop `sslrootcert` to work around a certificate error.
