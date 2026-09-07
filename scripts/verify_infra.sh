#!/usr/bin/env bash
# Asserts that the infrastructure Terraform claims to have created really exists
# in AWS. Run from the infra/ directory after `terraform init`.
#
# Usage: bash ../scripts/verify_infra.sh <vpc|security_groups|ec2|eip|rds|report>
set -euo pipefail

CHECK="${1:?usage: verify_infra.sh <vpc|security_groups|ec2|eip|rds|report>}"
REGION="${AWS_REGION:-us-east-1}"

tf_out() {
  terraform output -raw "$1"
}

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

case "${CHECK}" in
  vpc)
    VPC_ID="$(tf_out vpc_id)"
    STATE="$(aws ec2 describe-vpcs --region "${REGION}" --vpc-ids "${VPC_ID}" \
      --query 'Vpcs[0].State' --output text)"
    [ "${STATE}" = "available" ] || fail "VPC ${VPC_ID} is in state '${STATE}'"

    SUBNETS="$(aws ec2 describe-subnets --region "${REGION}" \
      --filters "Name=vpc-id,Values=${VPC_ID}" \
      --query 'length(Subnets)' --output text)"
    [ "${SUBNETS}" -ge 2 ] || fail "expected at least 2 subnets, found ${SUBNETS}"

    IGW="$(aws ec2 describe-internet-gateways --region "${REGION}" \
      --filters "Name=attachment.vpc-id,Values=${VPC_ID}" \
      --query 'length(InternetGateways)' --output text)"
    [ "${IGW}" -eq 1 ] || fail "expected 1 internet gateway, found ${IGW}"

    ROUTES="$(aws ec2 describe-route-tables --region "${REGION}" \
      --filters "Name=vpc-id,Values=${VPC_ID}" \
      --query "length(RouteTables[?Routes[?DestinationCidrBlock=='0.0.0.0/0']])" --output text)"
    [ "${ROUTES}" -ge 1 ] || fail "no route table carries a default route"

    echo "OK: VPC ${VPC_ID} available with ${SUBNETS} subnets, IGW and a default route"
    ;;

  security_groups)
    APP_SG="$(tf_out app_security_group_id)"
    DB_SG="$(tf_out db_security_group_id)"

    HTTP_OPEN="$(aws ec2 describe-security-groups --region "${REGION}" --group-ids "${APP_SG}" \
      --query "length(SecurityGroups[0].IpPermissions[?FromPort==\`80\`])" --output text)"
    [ "${HTTP_OPEN}" -ge 1 ] || fail "app security group ${APP_SG} does not allow port 80"

    DB_SOURCE="$(aws ec2 describe-security-groups --region "${REGION}" --group-ids "${DB_SG}" \
      --query "SecurityGroups[0].IpPermissions[?FromPort==\`5432\`].UserIdGroupPairs[0].GroupId" \
      --output text)"
    [ "${DB_SOURCE}" = "${APP_SG}" ] \
      || fail "database port 5432 is not restricted to the app SG (source: ${DB_SOURCE})"

    DB_PUBLIC="$(aws ec2 describe-security-groups --region "${REGION}" --group-ids "${DB_SG}" \
      --query "length(SecurityGroups[0].IpPermissions[?IpRanges[?CidrIp=='0.0.0.0/0']])" --output text)"
    [ "${DB_PUBLIC}" -eq 0 ] || fail "database security group is open to the internet"

    echo "OK: app SG ${APP_SG} serves HTTP; db SG ${DB_SG} accepts 5432 only from the app SG"
    ;;

  ec2)
    INSTANCE_ID="$(tf_out instance_id)"
    STATE="$(aws ec2 describe-instances --region "${REGION}" --instance-ids "${INSTANCE_ID}" \
      --query 'Reservations[0].Instances[0].State.Name' --output text)"
    [ "${STATE}" = "running" ] || fail "instance ${INSTANCE_ID} is '${STATE}', expected 'running'"

    TYPE="$(aws ec2 describe-instances --region "${REGION}" --instance-ids "${INSTANCE_ID}" \
      --query 'Reservations[0].Instances[0].InstanceType' --output text)"

    CHECKS="$(aws ec2 describe-instance-status --region "${REGION}" --instance-ids "${INSTANCE_ID}" \
      --query 'InstanceStatuses[0].InstanceStatus.Status' --output text)"

    echo "OK: EC2 ${INSTANCE_ID} (${TYPE}) is running; status checks: ${CHECKS}"
    ;;

  eip)
    PUBLIC_IP="$(tf_out public_ip)"
    INSTANCE_ID="$(tf_out instance_id)"

    ASSOCIATED="$(aws ec2 describe-addresses --region "${REGION}" \
      --filters "Name=public-ip,Values=${PUBLIC_IP}" \
      --query 'Addresses[0].InstanceId' --output text)"
    [ "${ASSOCIATED}" = "${INSTANCE_ID}" ] \
      || fail "Elastic IP ${PUBLIC_IP} is associated with '${ASSOCIATED}', expected ${INSTANCE_ID}"

    echo "OK: Elastic IP ${PUBLIC_IP} is associated with ${INSTANCE_ID}"
    ;;

  rds)
    DB_HOST="$(tf_out db_host)"
    IDENTIFIER="$(aws rds describe-db-instances --region "${REGION}" \
      --query "DBInstances[?Endpoint.Address=='${DB_HOST}'].DBInstanceIdentifier | [0]" --output text)"
    [ -n "${IDENTIFIER}" ] && [ "${IDENTIFIER}" != "None" ] \
      || fail "no RDS instance found with endpoint ${DB_HOST}"

    STATUS="$(aws rds describe-db-instances --region "${REGION}" \
      --db-instance-identifier "${IDENTIFIER}" \
      --query 'DBInstances[0].DBInstanceStatus' --output text)"
    [ "${STATUS}" = "available" ] || fail "RDS ${IDENTIFIER} is '${STATUS}', expected 'available'"

    ENGINE="$(aws rds describe-db-instances --region "${REGION}" \
      --db-instance-identifier "${IDENTIFIER}" \
      --query 'DBInstances[0].Engine' --output text)"
    [ "${ENGINE}" = "postgres" ] || fail "RDS engine is '${ENGINE}', expected 'postgres'"

    PUBLIC="$(aws rds describe-db-instances --region "${REGION}" \
      --db-instance-identifier "${IDENTIFIER}" \
      --query 'DBInstances[0].PubliclyAccessible' --output text)"
    [ "${PUBLIC}" = "False" ] || fail "RDS ${IDENTIFIER} is publicly accessible"

    echo "OK: RDS ${IDENTIFIER} (${ENGINE}) is available and private"
    ;;

  report)
    {
      echo "# Terraform Outputs"
      echo
      echo "Generated by the infrastructure workflow."
      echo
      echo "| Output | Value |"
      echo "| --- | --- |"
      echo "| VPC | \`$(tf_out vpc_id)\` |"
      echo "| EC2 instance | \`$(tf_out instance_id)\` |"
      echo "| Public IP (EIP) | \`$(tf_out public_ip)\` |"
      echo "| Application URL | $(tf_out application_url) |"
      echo "| App security group | \`$(tf_out app_security_group_id)\` |"
      echo "| DB security group | \`$(tf_out db_security_group_id)\` |"
      echo "| Database name | \`$(tf_out db_name)\` |"
      echo "| CloudWatch log group | \`$(tf_out log_group_name)\` |"
      echo
      echo "The RDS endpoint is intentionally omitted: it embeds the project name,"
      echo "which is a repository secret and would be redacted in the published artifact."
    } > terraform-outputs.md

    cat terraform-outputs.md
    ;;

  *)
    fail "unknown check '${CHECK}'"
    ;;
esac
