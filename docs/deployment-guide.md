# Deployment Guide

How to take this repository from a fresh clone to a running deployment on AWS.

## Prerequisites

* An AWS account with permission to create VPC, EC2, EIP, RDS, IAM and CloudWatch resources.
* A GitHub repository with Actions enabled and the package write scope available (GHCR).
* An S3 bucket for Terraform state (the UDAP platform provisions and injects this).

### Required repository secrets

| Secret | Provided by | Purpose |
| --- | --- | --- |
| `AWS_ACCESS_KEY_ID` | platform | Terraform and AWS CLI authentication |
| `AWS_SECRET_ACCESS_KEY` | platform | Terraform and AWS CLI authentication |
| `PROJECT_NAME` | platform | Resource name prefix and Terraform state key |
| `TF_STATE_BUCKET` | platform | Remote Terraform state bucket |
| `SSH_USER` | platform | Login user, derived from the AMI (`ubuntu`) |
| `SSH_PRIVATE_KEY` | platform | Private half of the deploy key pair |
| `SSH_PUBLIC_KEY` | platform | Registered on the EC2 key pair by Terraform |
| `DB_PASSWORD` | you | RDS master password — alphanumeric, at least 20 characters |
| `JWT_SECRET` | you | JWT signing key — alphanumeric, at least 32 characters |
| `NVD_API_KEY` | you | NVD API key for the OWASP dependency scan (see below) |

`GITHUB_TOKEN` is supplied automatically by Actions and is used for GHCR authentication.

> **`NVD_API_KEY` is required, not optional.** Since the NVD retired unauthenticated
> bulk downloads, `dependency-check` fails with *"Error updating the NVD Data; the NVD
> returned a 403 or 404 error"* when no key is present. Request a free key at
> <https://nvd.nist.gov/developers/request-an-api-key> (issued by email in minutes) and
> add it as a repository secret before running `build-deploy.yml`.

> Generate `DB_PASSWORD` and `JWT_SECRET` as strictly alphanumeric values. Characters
> such as `%`, `$`, `@`, `:` and `/` break JDBC URLs, shell interpolation and property
> files, and produce failures that look like authentication errors.

## Deployment order

The three workflows are independent and dispatched manually from the **Actions** tab.
They must be run in this order the first time, because each depends on the previous
one's result:

```
1. infrastructure.yml   →  creates the VPC, EC2, EIP and RDS instance
2. build-deploy.yml     →  builds and ships the application onto that instance
3. validation.yml       →  proves the deployed system behaves correctly
```

### Step 1 — Provision infrastructure

Run **infrastructure.yml**. It performs `terraform fmt -check`, `validate`, `plan`
and `apply`, then asserts against the AWS APIs that each resource really exists:

* VPC available, at least two subnets, an internet gateway and a default route
* App security group publishes port 80; database security group accepts 5432
  **only** from the app security group and is not open to the internet
* EC2 instance is `running`
* Elastic IP is associated with that instance
* RDS instance is `available`, engine `postgres`, and not publicly accessible

Finally it publishes a `terraform-outputs` artifact containing the public IP and
resource identifiers. Download it — the public IP is the application's address.

Typical duration: 8–12 minutes, dominated by RDS creation.

### Step 2 — Build and deploy the application

Run **build-deploy.yml**. Stages, in order:

| Stage | What must pass |
| --- | --- |
| Maven build, Checkstyle, PMD, SpotBugs | No style, error-prone or bytecode findings |
| Unit tests + JaCoCo | All tests green, line coverage ≥ 90% |
| Semgrep SAST, OWASP dependency-check | No findings; no dependency at CVSS ≥ 9 |
| Docker build + Trivy | No fixable CRITICAL vulnerabilities in the image |
| Push to GHCR | Image tagged with the commit SHA and `latest` |
| Puppet configure | Docker engine, `.env`, systemd unit, Nginx vhost |
| Deploy + health | Container running, `/actuator/health` returns `UP` through Nginx |

The Puppet run is idempotent: re-running the workflow converges the host rather
than rebuilding it. The systemd unit `employee-api.service` owns the container
lifecycle, so the application survives reboots.

Typical duration: 10–15 minutes on a cold Maven cache; the first dependency-check
run is slower because it populates the local NVD database.

### Step 3 — Validate the deployment

Run **validation.yml**. It verifies, against the live system:

* Health endpoint reports `UP`, and the `db` health component confirms PostgreSQL connectivity
* Authentication issues a usable JWT and rejects bad credentials
* Employee create, read, update, delete and duplicate-email handling
* Responses carry `Server: nginx` and `X-Served-By: nginx-reverse-proxy`
* k6 load profile (ramp to 25 virtual users) holds p95 below 500 ms and errors below 1%

It publishes a `validation-reports` artifact containing an HTML summary of the
performance budget plus the full functional test report.

## Verifying by hand

```bash
IP=<public ip from the terraform-outputs artifact>

curl "http://$IP/actuator/health"
curl -I "http://$IP/actuator/health"          # expect Server: nginx

TOKEN=$(curl -s -X POST "http://$IP/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<admin password>"}' | jq -r .accessToken)

curl "http://$IP/api/v1/employees" -H "Authorization: Bearer $TOKEN"
```

Open `http://$IP/` in a browser for the landing page, or `http://$IP/swagger-ui.html`
for interactive API documentation.

The administrator password is derived during deployment as the first 32 characters
of the SHA-256 hash of `JWT_SECRET`:

```bash
printf '%s' "$JWT_SECRET" | sha256sum | cut -c1-32
```

## Redeploying

* **Application change only** — run `build-deploy.yml`. Terraform is not touched;
  Puppet pulls the new image tag and restarts the systemd unit.
* **Infrastructure change** — run `infrastructure.yml`, then `build-deploy.yml`.
  Terraform state lives in the platform bucket under `<PROJECT_NAME>/terraform.tfstate`,
  so a rerun reconciles existing resources rather than duplicating them.

## Rolling back

Re-run `build-deploy.yml` from an earlier commit: the image tag follows the commit
SHA, so the pipeline pulls and starts exactly that build. Because the schema is
managed by Flyway, confirm that the earlier build's migrations are compatible with
the current database before rolling back across a migration boundary.

## Tearing down

`destroy.yml` runs `terraform destroy` with the same backend configuration. RDS is
created with `skip_final_snapshot = true`, so **all data is lost on teardown** —
take a manual snapshot first if the data matters.

## Troubleshooting

| Symptom | Cause and remedy |
| --- | --- |
| `terraform init` fails on the backend | `TF_STATE_BUCKET` or `PROJECT_NAME` is missing; check repository secrets |
| Provision fails with a duplicate resource | Backend init flags drifted; confirm `-reconfigure` and the state key, do not import by hand |
| `Error updating the NVD Data ... 403 or 404` | `NVD_API_KEY` is missing or invalid — the scan cannot run unauthenticated |
| `Permission denied (publickey)` | `SSH_USER` must be `ubuntu` for the Ubuntu 22.04 AMI; if key material mismatches, rotate the project keys from the platform |
| Puppet fails on `apt` 404s | Stale package index on a fresh host; the manifests retry `apt-get update`, re-run the workflow |
| Health check times out | Container still starting or cannot reach RDS; see the operations guide for log commands |
| RDS connection refused | Database security group must allow 5432 from the app security group — `verify_infra.sh security_groups` asserts this |
