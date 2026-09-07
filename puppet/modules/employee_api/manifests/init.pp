# Configures the Employee Management API host: Docker engine, application
# environment, systemd-managed container and the Nginx reverse proxy.
#
# All data comes from Hiera (puppet/data/*.yaml); deployment.yaml is rendered by
# CI from repository secrets and is never committed.
#
# There are deliberately NO registry credentials here. The application image is
# published to a PUBLIC GHCR package, so the host pulls it anonymously. The
# earlier design passed the CI job's GITHUB_TOKEN through to the instance, which
# cannot work: that credential is job-scoped and expires when the job ends, so a
# container restart or reboot would later fail to pull with no CI run in flight.
# A public image removes the credential, the file on disk and the expiry.
class employee_api (
  String  $image,
  String  $db_host,
  Integer $db_port,
  String  $db_name,
  String  $db_username,
  Sensitive[String] $db_password,
  Sensitive[String] $jwt_secret,
  Sensitive[String] $admin_password,
  Integer $app_port         = 8080,
  String  $container_name   = 'employee-api',
  String  $app_root         = '/opt/employee-api',
  String  $server_name      = '_',
  Integer $jwt_ttl_seconds  = 3600,
  String  $admin_username   = 'admin',
) {

  contain employee_api::docker
  contain employee_api::app
  contain employee_api::nginx

  Class['employee_api::docker']
  -> Class['employee_api::app']
  -> Class['employee_api::nginx']
}
