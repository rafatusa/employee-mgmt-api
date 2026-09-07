# syntax=docker/dockerfile:1

# ---- build stage -------------------------------------------------------------
# Toolchain only. Nothing from this stage ships, so it tracks the current Maven
# release rather than a hard pin.
FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests -Dcheckstyle.skip=true -Dpmd.skip=true -Dspotbugs.skip=true package \
    && cp target/employee-mgmt-api-1.0.0.jar /workspace/app.jar

# ---- runtime stage -----------------------------------------------------------
# This is the layer Trivy gates on, so it must carry current OS and JRE security
# patches. `21-jre-noble` resolves to the latest Temurin 21 JRE on Ubuntu 24.04.
#
# A hard patch pin (e.g. 21.0.5_11-jre-jammy) goes stale between releases and
# accumulates fixable CRITICAL CVEs, which is exactly what failed this build.
# Rebuilding picks up the current patch level; the Trivy gate in the image stage
# is what proves the result is clean, so a floating minor tag is verified on
# every run rather than trusted blindly.
FROM eclipse-temurin:21-jre-noble

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 appuser \
    && useradd --system --uid 10001 --gid appuser --home-dir /app --shell /usr/sbin/nologin appuser

WORKDIR /app
COPY --from=build --chown=appuser:appuser /workspace/app.jar /app/app.jar

USER appuser
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseContainerSupport"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD curl --fail --silent http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
