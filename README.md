# Employee Management API

Production-grade REST API for managing employee records, built with Spring Boot 3.2
on Java 21 and deployed to AWS EC2 as a Docker container behind an Nginx reverse
proxy, with PostgreSQL on Amazon RDS.

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)](https://www.postgresql.org/)

## What it does

| Capability | Detail |
| --- | --- |
| Employee CRUD | Create, read, update, delete with validation and pagination |
| Filtering | List employees by department |
| Authentication | JWT bearer tokens issued from `/api/v1/auth/login` |
| API documentation | OpenAPI 3 document and Swagger UI |
| Operational endpoints | Spring Boot Actuator health, info, metrics, Prometheus |
| Persistence | PostgreSQL with Flyway-managed schema migrations |

## Architecture

The deployed topology is defined as code in [`.udap/architecture.d2`](.udap/architecture.d2)
and rendered in [`docs/architecture.d2`](docs/architecture.d2); the delivery pipeline
is drawn in [`docs/cicd-pipeline.d2`](docs/cicd-pipeline.d2).

```
Client → Elastic IP → EC2 (Ubuntu 22.04)
                       ├─ Nginx :80  (reverse proxy, default vhost removed)
                       └─ Docker container :8080  (Spring Boot, systemd-managed)
                                    ↓ JDBC/TLS
                            RDS PostgreSQL 16 (private, app SG only)
```

* **VPC** `10.20.0.0/16` with two public subnets (RDS requires two availability zones),
  an internet gateway and a default route.
* **Security groups**: the app SG publishes 80/443 and 22; the database SG accepts
  5432 *only* from the app SG and is never open to the internet.
* **IAM instance role** grants CloudWatch Logs writes and SSM core access.
* **CloudWatch** log group plus an alarm on sustained CPU above 80%.

## Repository layout

```
src/main/java/com/udap/employee/   Application code (domain, repository, service, web, security)
src/main/resources/db/migration/   Flyway migrations
src/test/java/                     Unit and slice tests (90% line coverage gate)
infra/                             Terraform: VPC, EC2, EIP, RDS, IAM, CloudWatch
puppet/                            Puppet modules: Docker, app container, systemd, Nginx
scripts/verify_infra.sh            AWS assertions used by the infrastructure workflow
tests/                             Smoke, functional/integration, k6 load tests, report generator
docs/                              Deployment guide, operations guide, API reference, diagrams
config/                            Checkstyle, PMD, SpotBugs, dependency-check configuration
.udap/pipeline.yaml                Pipeline specification — the CI workflows are rendered from it
```

## Running locally

Requires Docker and Docker Compose.

```bash
cp .env.example .env
# Fill in DATABASE_PASSWORD, JWT_SECRET (>= 32 alphanumeric chars) and ADMIN_PASSWORD
docker compose up --build
```

The API is then available on <http://localhost:8080>:

```bash
# Obtain a token
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<ADMIN_PASSWORD>"}'

# List employees
curl http://localhost:8080/api/v1/employees \
  -H "Authorization: Bearer <token>"
```

Without Docker, run the test suite and the application directly:

```bash
./mvnw verify          # Compiles, runs the tests and enforces the coverage gate
./mvnw spring-boot:run # requires the environment variables from .env.example
```

## API reference

Full endpoint documentation lives in [`docs/api.md`](docs/api.md). Once deployed,
the live contract is served from the application itself:

* Swagger UI — `http://<public-ip>/swagger-ui.html`
* OpenAPI JSON — `http://<public-ip>/v3/api-docs`

## Deployment

Three independent GitHub Actions workflows, each triggered manually from the
Actions tab:

| Workflow | Purpose |
| --- | --- |
| `infrastructure.yml` | Terraform fmt → validate → plan → apply, then verifies the VPC, security groups, EC2 instance, Elastic IP and RDS instance, and publishes a Terraform outputs report |
| `build-deploy.yml` | Maven build, Checkstyle, PMD, SpotBugs, unit tests with a 90% JaCoCo gate, Semgrep SAST, OWASP dependency scan, Docker build, Trivy scan, push to GHCR, Puppet configuration of the EC2 host, container deployment, Nginx setup and a health check |
| `validation.yml` | Smoke tests, functional and integration API tests, k6 load test with a p95 < 500 ms and error-rate < 1% budget, HTML report generation and artifact publication |

`deploy.yml` and `destroy.yml` are additionally rendered from the pipeline
specification for the platform's own deploy and teardown actions.

Step-by-step instructions are in [`docs/deployment-guide.md`](docs/deployment-guide.md).

## Configuration

Runtime configuration is supplied entirely through environment variables. In AWS,
Puppet writes them to `/opt/employee-api/.env` from repository secrets and
Terraform outputs; nothing sensitive is stored in the repository.

| Variable | Secret | Description |
| --- | --- | --- |
| `DATABASE_URL` | no | JDBC URL of the PostgreSQL instance |
| `DATABASE_USERNAME` | no | Database user |
| `DATABASE_PASSWORD` | yes | Database password (repository secret `DB_PASSWORD`) |
| `JWT_SECRET` | yes | HMAC signing key, at least 32 alphanumeric characters |
| `JWT_TTL_SECONDS` | no | Token lifetime, default 3600 |
| `ADMIN_USERNAME` | no | Seeded administrator username, default `admin` |
| `ADMIN_PASSWORD` | yes | Seeded administrator password, derived from `JWT_SECRET` during deployment |
| `SERVER_PORT` | no | Application listen port, default 8080 |

Repository secrets consumed by the workflows: `AWS_ACCESS_KEY_ID`,
`AWS_SECRET_ACCESS_KEY`, `PROJECT_NAME`, `TF_STATE_BUCKET`, `SSH_USER`,
`SSH_PRIVATE_KEY`, `SSH_PUBLIC_KEY`, `DB_PASSWORD`, `JWT_SECRET`, `NVD_API_KEY`.

> `NVD_API_KEY` is required by the OWASP dependency-check stage: the NVD no longer
> serves unauthenticated bulk downloads, so the scan fails without one. Free keys are
> issued at <https://nvd.nist.gov/developers/request-an-api-key>.

## Operations

Day-to-day procedures — log locations, restarting the service, database access,
credential rotation and teardown — are documented in
[`docs/operations-guide.md`](docs/operations-guide.md).

## Quality gates

Every gate below fails the build rather than warning:

* **Checkstyle** — import hygiene, braces, naming, line length
* **PMD** — error-prone constructs and unused code
* **SpotBugs** — bytecode analysis at maximum effort, high threshold
* **JaCoCo** — minimum 90% line coverage across the bundle
* **Semgrep** — Java and secret-detection rule packs
* **OWASP dependency-check** — fails on CVSS ≥ 9
* **Trivy** — fails on CRITICAL, fixable container vulnerabilities

## License

Apache-2.0
