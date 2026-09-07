# Deploys the application container: environment file, image pull and a systemd
# unit that owns the container lifecycle.
#
# The image is published to a PUBLIC GHCR package, so the host pulls it
# anonymously and holds no registry credential at all. See the registry_*
# parameters in init.pp for why an authenticated pull is deliberately absent.
class employee_api::app {

  $app_root       = $employee_api::app_root
  $container_name = $employee_api::container_name
  $image          = $employee_api::image
  $app_port       = $employee_api::app_port

  file { $app_root:
    ensure => directory,
    owner  => 'root',
    group  => 'root',
    mode   => '0750',
  }

  # Runtime configuration consumed by the container via --env-file.
  file { "${app_root}/.env":
    ensure    => file,
    owner     => 'root',
    group     => 'root',
    mode      => '0600',
    show_diff => false,
    content   => epp('employee_api/app.env.epp', {
      'db_host'         => $employee_api::db_host,
      'db_port'         => $employee_api::db_port,
      'db_name'         => $employee_api::db_name,
      'db_username'     => $employee_api::db_username,
      'db_password'     => $employee_api::db_password.unwrap,
      'jwt_secret'      => $employee_api::jwt_secret.unwrap,
      'jwt_ttl_seconds' => $employee_api::jwt_ttl_seconds,
      'admin_username'  => $employee_api::admin_username,
      'admin_password'  => $employee_api::admin_password.unwrap,
      'app_port'        => $app_port,
    }),
    require   => File[$app_root],
  }

  # Earlier revisions wrote a GHCR credential here so the host could
  # `docker login` before pulling. That credential was the CI job's
  # GITHUB_TOKEN: job-scoped, already expired by the time the container
  # restarted, and unnecessary now the package is public. Declaring it absent
  # removes the stale secret from hosts provisioned by those revisions instead
  # of leaving an expired token at rest on disk.
  file { "${app_root}/registry-token":
    ensure  => absent,
    require => File[$app_root],
  }

  # Records the image reference so systemd and subsequent runs agree on it.
  file { "${app_root}/image":
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => "${image}\n",
    require => File[$app_root],
  }

  # The package is public: no `docker login` and no credential on disk. The pull
  # is retried because GHCR occasionally rate-limits anonymous requests.
  exec { 'pull-application-image':
    command   => "/usr/bin/docker pull ${image}",
    unless    => "/usr/bin/docker image inspect ${image}",
    timeout   => 900,
    tries     => 3,
    try_sleep => 20,
    require   => File[$app_root],
  }

  file { '/etc/systemd/system/employee-api.service':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => epp('employee_api/employee-api.service.epp', {
      'container_name' => $container_name,
      'image'          => $image,
      'app_root'       => $app_root,
      'app_port'       => $app_port,
    }),
    require => File["${app_root}/.env"],
  }

  exec { 'systemd-daemon-reload':
    command     => '/usr/bin/systemctl daemon-reload',
    refreshonly => true,
    subscribe   => File['/etc/systemd/system/employee-api.service'],
  }

  service { 'employee-api':
    ensure    => running,
    enable    => true,
    require   => [
      Exec['systemd-daemon-reload'],
      Exec['pull-application-image'],
    ],
    subscribe => [
      File['/etc/systemd/system/employee-api.service'],
      File["${app_root}/.env"],
      File["${app_root}/image"],
    ],
  }

  # Block until the container answers its health endpoint so the Nginx class and
  # the pipeline's verify stage do not race application startup.
  exec { 'wait-for-application-health':
    command   => "/usr/bin/curl --fail --silent --max-time 5 http://127.0.0.1:${app_port}/actuator/health",
    tries     => 30,
    try_sleep => 10,
    timeout   => 60,
    require   => Service['employee-api'],
  }
}
