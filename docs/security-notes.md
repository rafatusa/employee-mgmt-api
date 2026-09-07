# Security Notes

## ACCEPTED RISK: three embedded Tomcat CVEs are suppressed to allow deployment

**Status: knowingly shipped with a scoped, time-limited exception.**

> This section documents a deliberate decision to deploy with three known CRITICAL
> vulnerabilities. It is recorded here so nobody discovers it by accident, and so
> the exception can be removed the moment upstream makes that possible.

### The decision

| | |
| --- | --- |
| **Accepted by** | Project owner, explicitly, during the build session |
| **Date accepted** | 2026-09-07 |
| **Recommendation given** | Against acceptance — waiting for the upstream fix was advised |
| **Mechanism** | `.trivyignore` at the repository root, three CVE IDs, with `exp:` dates |
| **Expires** | 2026-12-07 — after which the gate re-arms automatically |
| **Exit condition** | Tomcat >= 10.1.58 available in Maven Central; delete `.trivyignore` |

The owner asked to proceed to deployment and fix the vulnerability once the fixed
artifact is published. The exception was implemented in the narrowest form that
achieves that.

### What the scan found

```
ubuntu 24.04    0 vulnerabilities     ← base image is clean
app/app.jar     3 vulnerabilities

Java (jar)  Total: 3 (CRITICAL: 3)

org.apache.tomcat.embed:tomcat-embed-core
  installed 10.1.55   fixed 10.1.58

  CVE-2026-65182  Security constraint bypass due to improper access control
  CVE-2026-65905  Authentication bypass via limited replay in the DIGEST authenticator
  CVE-2026-68525  Unauthorized resource access via FORM authentication bypass
```

All three are in the embedded servlet container that fronts this API, and all three
are authentication or access-control **bypasses**. This is a material risk on a
JWT-secured employee record system, not a paperwork item.

### Why the fix cannot simply be applied

`tomcat-embed-core` is a transitive dependency of `spring-boot-starter-web`. Spring
Boot 3.5.16 — the current supported 3.x release — manages version **10.1.55**.

The normal remedy is Spring Boot's `tomcat.version` property, which re-points every
managed `tomcat-embed-*` artifact at once. It was attempted and reverted:

```
Could not find artifact org.apache.tomcat.embed:tomcat-embed-core:jar:10.1.58
  in central (https://repo.maven.apache.org/maven2)
```

Tomcat **10.1.58 is tagged** in the Apache Tomcat source repository but has **not
been published to Maven Central**. Publication lags tagging, typically by days.
10.1.57 is available but sits below the fixed version and does not remediate.

Verified again on 2026-09-07: still 404 on Maven Central. Latest Spring Boot release
is 4.1.1 (a major version, out of scope for this project's 3.x line).

### Compensating controls

These reduce, but do **not** eliminate, the exposure:

* The application uses a **stateless JWT filter**; DIGEST and FORM authentication
  are not enabled, which is the attack surface two of the three CVEs target.
* Nginx terminates and proxies all traffic; the container is not directly exposed.
* The RDS instance is **not publicly accessible**; port 5432 is reachable only from
  the application security group (`IpRanges` empty — verified against the AWS API).
* The application container runs as a non-root user.

Treat this deployment as time-boxed. Do not extend the expiry without a fresh
decision.

### How to remove the exception

Once `tomcat-embed-core` 10.1.58 (or later) is available in Maven Central:

1. Add to `pom.xml` `<properties>` (the comment block is already in place there):
   ```xml
   <tomcat.version>10.1.58</tomcat.version>
   ```
2. Run `mvn -B -ntp verify` locally to confirm the dependency resolves.
3. **Delete `.trivyignore` entirely.**
4. Dispatch `build-deploy.yml`.
5. Download the `trivy-report` artifact and confirm `app/app.jar` reports
   0 vulnerabilities.

Check here for publication:
<https://repo.maven.apache.org/maven2/org/apache/tomcat/embed/tomcat-embed-core/>

A future Spring Boot 3.5.x patch that manages >= 10.1.58 resolves it with no
override at all, which is preferable.

### What the exception does NOT do

The suppression is deliberately narrow. Still fully in force:

* The Trivy gate still runs `--exit-code 1 --severity CRITICAL --ignore-unfixed`.
  **Any other fixable CRITICAL — OS or Java — still fails the build.**
* The scan report is still generated and published as the `trivy-report` artifact
  and echoed into the run summary, so findings remain visible.
* Semgrep (`p/java`, `p/secrets`) still blocks the release.
* OWASP dependency-check still fails `dependency-scan.yml` at CVSS >= 9.

Explicitly **not** used, and not to be used:

* `continue-on-error` on any step
* lowering the gate from `--severity CRITICAL`
* `--exit-code 0` on the scan
* deleting or skipping the scan
* a blanket or wildcard ignore entry

The difference matters: a scoped ignore with an expiry is an auditable, reversible
risk acceptance. The items above are silent holes.

## Resolved findings (kept for context)

| Finding | Resolution |
| --- | --- |
| Committed BCrypt hash in `V2__seed_admin_user.sql` (Semgrep `detected-bcrypt-hash`) | Migration seeds an unusable `'!'` placeholder; `AdminAccountInitializer` sets the real hash from `ADMIN_PASSWORD` at startup. No credential material in git. |
| Stale runtime base image (`eclipse-temurin:21.0.5_11-jre-jammy`, Q4-2024) | Moved to `eclipse-temurin:21-jre-noble`. The OS layer now scans clean at 0 vulnerabilities. |
| Spring Boot 3.2.11 out of OSS support | Upgraded to 3.5.16, the current supported 3.x release. |

## Scanner coverage

| Scanner | Scope | Where it runs | Blocking? |
| --- | --- | --- | --- |
| Semgrep | Source code, secret detection | `build-deploy.yml` | Yes |
| Trivy | Built container image: OS packages + bundled JARs | `build-deploy.yml` | Yes, except the three CVEs above |
| OWASP dependency-check | Declared dependency tree | `dependency-scan.yml` | Fails that workflow; does not gate releases |

Trivy is the release-blocking dependency check because it scans the artifact that
actually ships. OWASP dependency-check runs separately so that NVD API availability
cannot block a deployment — see the README for the full rationale.
