#!/usr/bin/env bash
# Installs the Puppet agent on the Ubuntu 22.04 application host.
# Idempotent: exits early when the agent is already present.
set -euo pipefail

if [ -x /opt/puppetlabs/bin/puppet ]; then
  echo "Puppet agent already installed: $(/opt/puppetlabs/bin/puppet --version)"
  exit 0
fi

export DEBIAN_FRONTEND=noninteractive

for attempt in 1 2 3 4 5; do
  if sudo apt-get update; then
    break
  fi
  echo "apt-get update failed (attempt ${attempt}); retrying in 15s"
  sleep 15
done

sudo apt-get install -y --no-install-recommends wget ca-certificates

TMP_DEB="$(mktemp /tmp/puppet-release-XXXXXX.deb)"
wget -q -O "${TMP_DEB}" https://apt.puppet.com/puppet8-release-jammy.deb
sudo dpkg -i "${TMP_DEB}"
rm -f "${TMP_DEB}"

sudo apt-get update
sudo apt-get install -y --no-install-recommends puppet-agent

echo "Installed: $(/opt/puppetlabs/bin/puppet --version)"
