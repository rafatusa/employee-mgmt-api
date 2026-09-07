# Security Notes

## Open finding: embedded Tomcat CVEs block the release gate

**Status: unresolved. The application is intentionally NOT deployed.**

The Trivy container gate in `build-deploy.yml` blocks the image, and it is correct
to do so. The full report is published on every run as the `trivy-report` artifact
(table and JSON) and echoed into the run summary.

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
are authentication or access-control **bypasses**. For a JWT-secured employee record
system, shipping these would be a material risk, not a paperwork item.

### Why it is not fixed yet

`tomcat-embed-core` is a transitive dependency of `spring-boot-starter-web`. Spring
Boot 3.5.16 — the current supported 3.x release — manages version **10.1.55**.

The normal remedy is Spring Boot's `tomcat.version` property, which re-points every
managed `tomcat-embed-*` artifact at once. That was attempted and reverted, because:

```
Could not find artifact org.apache.tomcat.embed:tomcat-embed-core:jar:10.1.58
  in central (https://repo.maven.apache.org/maven2)
```

Tomcat **10.1.58 is tagged** in the Apache Tomcat repository but has **not yet been
published to Maven Central**. Publication lags tagging, typically by days. Pinning a
version that does not resolve breaks the build for everyone, so the override is left
out and the finding is documented instead of hidden.

10.1.57 is tagged as well, but it is below the fixed version Trivy names and would
not remediate the CVEs.

### How to resolve it

Once `tomcat-embed-core` 10.1.58 (or later) is available in Maven Central:

1. Add to `pom.xml` `<properties>`:
   ```xml
   <tomcat.version>10.1.58</tomcat.version>
   ```
2. Run `mvn -B -ntp verify` locally to confirm the dependency resolves.
3. Dispatch `build-deploy.yml`.
4. Download the `trivy-report` artifact and confirm `app/app.jar` reports
   0 vulnerabilities.
5. Remove the override once a Spring Boot release manages >= 10.1.58 natively,
   re-verifying with the same artifact.

Alternatively, upgrading to a future Spring Boot patch release that manages
>= 10.1.58 resolves it with no override at all. Check the Boot release notes for
the managed Tomcat version before upgrading.

### What was explicitly NOT done

None of the following were used to make the pipeline green, and none should be:

* `.trivyignore` or any per-CVE suppression
* lowering the gate from `--severity CRITICAL`
* `--exit-code 0` on the scan
* `continue-on-error` on the step
* deleting or skipping the scan

The gate did its job: it found three real authentication bypasses in a dependency
and refused to ship them. Silencing it would have produced a green pipeline and a
vulnerable deployment.

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
| Trivy | Built container image: OS packages + bundled JARs | `build-deploy.yml` | Yes |
| OWASP dependency-check | Declared dependency tree | `dependency-scan.yml` | Fails that workflow; does not gate releases |

Trivy is the release-blocking dependency check because it scans the artifact that
actually ships. OWASP dependency-check runs separately so that NVD API availability
cannot block a deployment — see the README for the full rationale.
