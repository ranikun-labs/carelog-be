# DEV release

Run the `Carelog DEV release` workflow manually on `dev`. The backend tests run against the dispatch SHA before the shared workflow builds `carelog-be/Dockerfile` from the repository root. Leave `deploy_enabled` false to publish only; true requests deployment of the published digest to DEV. This workflow does not deploy PROD or run Terraform or migrations.

Before enabling releases, the shared `platform-infra` workflow must be published and protected. The caller is pinned to `fdeb22805d9f26779073dc68c6f0a9a166ccb884`; publish and retain that exact commit before enabling releases. Configure the required DEV GitHub environments, access controls, OIDC roles and AWS resources separately; this file alone does not provision or authorize them. A successful release is not evidence that the product journey has been validated.

## Automatic DEV releases

Pushes to `dev` run this same test, publish, and deploy pipeline only when the repository variable `CARELOG_DEV_AUTO_DEPLOY` is exactly `true`. With the variable absent or any other value, push runs are skipped. Manual dispatch remains available independently: `deploy_enabled=false` publishes only and `true` requests deployment. Each release tests and publishes the triggering commit SHA.

Before setting the variable, protect both `dev-carelog-publish` and `dev-carelog-deploy` GitHub environments with appropriate deployment branch restrictions and required reviewers, and restrict who can change repository variables and the `dev` branch. Confirm the DEV resources, OIDC roles and environment variables, and complete the separately controlled database migration readiness gate. The shared deploy workflow requires the active container to explicitly set `SPRING_FLYWAY_ENABLED=false`; it does not run migrations. Environment protection rules still apply to automatic releases. Setting the variable activates future pushes only; clear it to stop future automatic candidates. Already running releases require separate cancellation.
