# Installs the Docker engine from the official Docker apt repository.
class employee_api::docker {

  exec { 'apt-update-docker-prereqs':
    command     => '/usr/bin/apt-get update',
    tries       => 5,
    try_sleep   => 15,
    timeout     => 600,
    environment => ['DEBIAN_FRONTEND=noninteractive'],
    unless      => '/usr/bin/test -f /etc/apt/keyrings/docker.asc',
  }

  package { ['ca-certificates', 'curl', 'gnupg']:
    ensure  => installed,
    require => Exec['apt-update-docker-prereqs'],
  }

  file { '/etc/apt/keyrings':
    ensure => directory,
    owner  => 'root',
    group  => 'root',
    mode   => '0755',
  }

  exec { 'docker-gpg-key':
    command => '/usr/bin/curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc',
    creates => '/etc/apt/keyrings/docker.asc',
    require => [File['/etc/apt/keyrings'], Package['curl']],
  }

  file { '/etc/apt/keyrings/docker.asc':
    ensure  => file,
    mode    => '0644',
    require => Exec['docker-gpg-key'],
  }

  file { '/etc/apt/sources.list.d/docker.list':
    ensure  => file,
    owner   => 'root',
    group   => 'root',
    mode    => '0644',
    content => "deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu jammy stable\n",
    require => File['/etc/apt/keyrings/docker.asc'],
  }

  exec { 'apt-update-docker-repo':
    command     => '/usr/bin/apt-get update',
    tries       => 5,
    try_sleep   => 15,
    timeout     => 600,
    environment => ['DEBIAN_FRONTEND=noninteractive'],
    subscribe   => File['/etc/apt/sources.list.d/docker.list'],
    refreshonly => true,
  }

  package { ['docker-ce', 'docker-ce-cli', 'containerd.io', 'docker-compose-plugin']:
    ensure          => installed,
    install_options => ['--no-install-recommends'],
    require         => Exec['apt-update-docker-repo'],
  }

  service { 'docker':
    ensure  => running,
    enable  => true,
    require => Package['docker-ce'],
  }
}
