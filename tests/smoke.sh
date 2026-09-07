#!/usr/bin/env bash
# Post-deployment smoke tests. Requires BASE_URL (e.g. http://203.0.113.10).
set -euo pipefail

BASE_URL="${BASE_URL:?BASE_URL must be set}"
PASS=0
FAIL=0

check() {
  local name="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    echo "PASS  ${name}"
    PASS=$((PASS + 1))
  else
    echo "FAIL  ${name}"
    FAIL=$((FAIL + 1))
  fi
}

echo "Smoke testing ${BASE_URL}"
echo "---------------------------------------------"

# The application may still be warming up immediately after a deploy.
curl --fail --silent --show-error --retry 20 --retry-delay 15 --retry-all-errors \
  "${BASE_URL}/actuator/health" >/dev/null

check "health endpoint returns UP" \
  bash -c "curl --fail --silent '${BASE_URL}/actuator/health' | grep -q '\"status\":\"UP\"'"

check "database component reports UP" \
  bash -c "curl --fail --silent '${BASE_URL}/actuator/health/db' | grep -q '\"status\":\"UP\"'"

check "landing page is served" \
  bash -c "curl --fail --silent '${BASE_URL}/' | grep -qi 'Employee Management API'"

check "OpenAPI document is published" \
  bash -c "curl --fail --silent '${BASE_URL}/v3/api-docs' | grep -q '\"openapi\"'"

check "Swagger UI is reachable" \
  curl --fail --silent --location "${BASE_URL}/swagger-ui/index.html"

check "requests travel through the Nginx reverse proxy" \
  bash -c "curl --fail --silent --head '${BASE_URL}/actuator/health' | grep -qi '^server: *nginx'"

check "reverse-proxy marker header is present" \
  bash -c "curl --fail --silent --head '${BASE_URL}/actuator/health' | grep -qi 'X-Served-By: *nginx-reverse-proxy'"

check "protected endpoint rejects anonymous access" \
  bash -c "test \"\$(curl --silent -o /dev/null -w '%{http_code}' '${BASE_URL}/api/v1/employees')\" = '401'"

echo "---------------------------------------------"
echo "passed: ${PASS}  failed: ${FAIL}"
[ "${FAIL}" -eq 0 ]
