# Entry point applied by the CI configure stage:
#   puppet apply --modulepath=/tmp/puppet/modules \
#                --hiera_config=/tmp/puppet/hiera.yaml \
#                /tmp/puppet/manifests/site.pp
node default {
  include employee_api
}
