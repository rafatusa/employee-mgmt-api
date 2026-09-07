# employee-mgmt-api — working notes

## Status
validate_project PASS + test_project PASSED. Ready to push and deploy.

## Key decisions
- **Spring Boot 3.2.11 / Java 21**, pinned explicitly. The `use_scaffold` (Spring
  Initializr) returned Boot **4.1.1** with non-existent starters
  (`spring-boot-starter-webmvc`, `-actuator-test`, `-data-jpa-test`). The POM was
  rewritten by hand with real starters. Do NOT regenerate the POM from the scaffold.
- **Package renamed** `com.example.employeemgmtapi` → `com.udap.employee`; the old
  scaffold classes were deleted.
- **Three independent workflows** requested by the user live under the spec's
  `pipelines:` key (infrastructure / build-deploy / validation), rendered as
  workflow_dispatch workflows. The mandatory platform backbone (provision→configure→
  verify) remains as `deploy.yml`; `destroy.yml` is rendered automatically. Five
  workflow files total — expected, not a duplicate.
- **Admin password derivation**: `sha256(JWT_SECRET)[:32]`, computed identically in
  `puppet/render_hieradata.sh`, `tests/api/conftest.py` and `tests/load/load-test.js`.
  Avoids a third secret. Rotating JWT_SECRET rotates the admin password (documented).
- **VPC is dedicated** (10.20.0.0/16). Two public subnets because an RDS subnet group
  requires two AZs. Probe showed 3/5 VPCs used in us-east-1 — this fits.
- **Puppet, not Ansible** (user requirement). `bootstrap.sh` installs puppet-agent from
  apt.puppet.com; `puppet apply` runs masterless over SSH. `--detailed-exitcodes`
  returns 2 for "changes applied", so CI maps {0,2}→success.
- **Coverage gate 90%** line, BUNDLE scope, excluding `config/**`, `dto/**` and the
  main class (framework wiring and records).

## Bugs found and fixed during rehearsal (do not regress these)
1. **@WebMvcTest context failure.** `SecurityConfig` is a `@Configuration` in the
   scanned package and constructor-injected `JwtAuthenticationFilter`, which is not
   instantiated in a web slice → unsatisfied dependency → context failed to load.
   FIX: `JwtAuthenticationFilter` is no longer a `@Component`; `SecurityConfig`
   creates it as a `@Bean`. Both controller tests additionally use
   `excludeFilters = @ComponentScan.Filter(ASSIGNABLE_TYPE, SecurityConfig.class)`
   plus `@AutoConfigureMockMvc(addFilters = false)`.
   Note: excluding SecurityAutoConfiguration alone did NOT work — the failure was the
   bean graph, not the filter chain.
2. **OWASP dependency-check NVD 403/404.** The NVD API rejects unauthenticated bulk
   downloads. Running without a key fails in CI too — this was a real defect, not a
   sandbox artifact. FIX: pom exposes an `nvd.api.key` property; both security stages
   pass `-Dnvd.api.key="$NVD_API_KEY"` from the `NVD_API_KEY` secret.
   **The NVD_API_KEY secret MUST be set or the security stage fails.**
   Free key: https://nvd.nist.gov/developers/request-an-api-key
3. **Semgrep via pip is not installable in the UDAP sandbox** (read-only venv, and
   `--user` is disabled inside a virtualenv). Switched to the official
   `semgrep/semgrep-action@v1` — correct practice for Actions anyway, and the sandbox
   skips `uses:` steps cleanly.
4. **Invented Docker digest.** The Dockerfile initially pinned a fabricated sha256 for
   `maven:3.9.9-eclipse-temurin-21`. Replaced with real tags. Never invent digests.
5. **Secret scanner** flagged literal test signing keys. Replaced with
   `TestKeys.signingKey()` (random per run) — no key material in source at all.

## Other gotchas already handled
- `IMAGE_REF` lowercased with `tr` (GHCR rejects uppercase repo names). Avoided bash
  `${VAR,,}` for portability in rendered steps.
- Secrets never flow through job outputs: every stage needing the public IP or DB
  endpoint re-runs `terraform init` with identical backend flags and reads
  `terraform output` itself (PROJECT_NAME is a secret; derived hostnames get dropped).
- `terraform-outputs.md` deliberately omits the RDS endpoint (embeds project name).
- Nginx default site removed, else it shadows the app vhost on bare-IP access.
- `nginx -t` runs before any reload.
- GHCR token written to a 0600 file and piped via stdin, never on the command line.

## Secrets to set after the first push
- `DB_PASSWORD` — alphanumeric, >= 20 chars
- `JWT_SECRET` — alphanumeric, >= 32 chars
- `NVD_API_KEY` — user must supply; required by the security stage
(Platform supplies AWS_*, PROJECT_NAME, TF_STATE_BUCKET, SSH_* automatically.)

## Deploy order for the user
infrastructure.yml → build-deploy.yml → validation.yml (see docs/deployment-guide.md).
The platform's `deploy.yml` runs the whole backbone end to end in one pass.
