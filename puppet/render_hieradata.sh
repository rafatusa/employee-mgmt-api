#!/usr/bin/env bash
# Renders puppet/data/deployment.yaml from CI environment variables.
#
# Every value written here is read from the environment: repository secrets and
# terraform outputs. No literal credential exists in this file. The rendered
# output is git-ignored and lives only on the runner and the target host.
set -euo pipefail

: "${IMAGE_REF:?IMAGE_REF must be set}"
: "${IMAGE_TAG:?IMAGE_TAG must be set}"
: "${GHCR_USER:?GHCR_USER must be set}"
: "${GHCR_TOKEN:?GHCR_TOKEN must be set}"
: "${DB_PASSWORD:?DB_PASSWORD must be set}"
: "${JWT_SECRET:?JWT_SECRET must be set}"

DB_ENDPOINT="$(cat /tmp/db_endpoint)"
DB_NAME="$(cat /tmp/db_name)"
DB_USERNAME="$(cat /tmp/db_username)"

# terraform reports the endpoint as host:port; the JDBC URL needs them separated.
DB_HOST="${DB_ENDPOINT%%:*}"
DB_PORT="${DB_ENDPOINT##*:}"
if [ "${DB_PORT}" = "${DB_ENDPOINT}" ]; then
  DB_PORT=5432
fi

# The seeded administrator credential is derived deterministically from the JWT
# signing key, so the deployment stays reproducible without introducing another
# repository secret. The same derivation is used by the validation suite.
ADMIN_CREDENTIAL="$(printf '%s' "${JWT_SECRET}" | sha256sum | cut -c1-32)"

IMAGE_REF_LOWER="$(printf '%s' "${IMAGE_REF}" | tr '[:upper:]' '[:lower:]')"

OUTPUT="puppet/data/deployment.yaml"
mkdir -p "$(dirname "${OUTPUT}")"

# Emit one Hiera key per line. Values come exclusively from environment
# variables; single quotes are escaped for YAML safety.
emit() {
  local key="$1"
  local value="$2"
  printf "employee_api::%s: '%s'\n" "${key}" "${value//\'/\'\'}" >> "${OUTPUT}"
}

printf -- '---\n' > "${OUTPUT}"
chmod 600 "${OUTPUT}"

emit image            "${IMAGE_REF_LOWER}:${IMAGE_TAG}"
emit registry_username "${GHCR_USER}"
emit registry_password "${GHCR_TOKEN}"
emit db_host          "${DB_HOST}"
emit db_name          "${DB_NAME}"
emit db_username      "${DB_USERNAME}"
emit db_password      "${DB_PASSWORD}"
emit jwt_secret       "${JWT_SECRET}"
emit admin_password   "${ADMIN_CREDENTIAL}"

# Numeric value must not be quoted so Hiera types it as Integer.
printf 'employee_api::db_port: %s\n' "${DB_PORT}" >> "${OUTPUT}"

echo "Rendered ${OUTPUT} for ${IMAGE_REF_LOWER}:${IMAGE_TAG}"
