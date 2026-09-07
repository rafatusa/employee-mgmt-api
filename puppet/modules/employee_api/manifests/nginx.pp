# Installs Nginx and publishes the application on port 80 as a reverse proxy in
# front of the container listening on 127.0.0.1:<app_port>.
class employee_api::nginx {

  $app_port    = $employee_api::app_port
  $server_name = $employee_api::server_name

  package { 'nginx':
    ensure          => installed,
    install_options => ['--no-install-recommends'],
  }

  # The distribution default site answers on port 80 and would shadow the
  # application vhost for requests made against the bare public IP.
  file { '/etc/nginx/sites-enabled/default':
    ensure  => absent,
    require => Package['nginx'],
    notify  => Service['nginx'],
  }

  file { '/etc/nginx/sites-available/employee-api.conf':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => epp('employee_api/nginx-site.conf.epp', {
      'app_port'    => $app_port,
      'server_name' => $server_name,
    }),
    require => Package['nginx'],
  }

  file { '/etc/nginx/sites-enabled/employee-api.conf':
    ensure  => link,
    target  => '/etc/nginx/sites-available/employee-api.conf',
    require => File['/etc/nginx/sites-available/employee-api.conf'],
    notify  => Exec['nginx-config-test'],
  }

  # Validate the configuration before any reload so a bad template cannot take
  # the running proxy down.
  exec { 'nginx-config-test':
    command     => '/usr/sbin/nginx -t',
    refreshonly => true,
    subscribe   => File['/etc/nginx/sites-available/employee-api.conf'],
    notify      => Service['nginx'],
  }

  service { 'nginx':
    ensure  => running,
    enable  => true,
    require => [
      Package['nginx'],
      File['/etc/nginx/sites-enabled/employee-api.conf'],
    ],
  }
}
