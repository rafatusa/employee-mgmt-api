# Operations Guide

Day-2 procedures for the Employee Management API running on AWS EC2.

Connect to the instance with the deploy key:

```bash
ssh -i ~/.ssh/deploy_key ubuntu@<public-ip>
```

## Service topology on the host

| Component | Managed by | Location |
| --- | --- | --- |
| Application container | systemd unit `employee-api.service` | image recorded in `/opt/employee-api/image` |
| Runtime configuration | Puppet | `/opt/employee-api/.env` (mode 0600, root only) |
| Reverse proxy | systemd unit `nginx` | `/etc/nginx/sites-available/employee-api.conf` |
| Container runtime | systemd unit `docker` | Docker CE from the official apt repository |

## Health and status

```bash
# End-to-end, through the proxy
curl http://<public-ip>/actuator/health

# On the host: application directly, bypassing Nginx
curl http://127.0.0.1:8080/actuator/health

systemctl status employee-api
systemctl status nginx
docker ps
```

A healthy system shows `active (running)` for both units, one running container
named `employee-api`, and `{"status":"UP"}` from both curl commands. If the direct
call succeeds but the proxied one fails, the fault is in Nginx; if both fail, it is
the application or the database.

## Logs

```bash
# Application container logs (JSON-file driver, 10 MB x 3 rotation)
docker logs --tail 200 -f employee-api

# systemd unit lifecycle (restarts, start failures)
journalctl -u employee-api -n 200 --no-pager
journalctl -u employee-api -f

# Nginx
tail -f /var/log/nginx/employee-api.access.log
tail -f /var/log/nginx/employee-api.error.log
```

Instance-level logs are also shipped to the CloudWatch log group
`/aws/ec2/<project-name>` with 14-day retention.

## Restarting

```bash
sudo systemctl restart employee-api   # application only
sudo systemctl reload nginx           # after a config change
sudo nginx -t                         # always test before reloading
```

Restarting is safe at any time: the container is stateless and all data lives in RDS.
The unit is configured `Restart=always` with a 10-second backoff, so a crashed
container is restarted automatically.

## Deploying a new version

Do **not** run `docker pull` or `docker run` by hand — the systemd unit and the
Puppet catalog would then disagree about which image is current. Run the
`build-deploy.yml` workflow instead; it pushes the new image, updates
`/opt/employee-api/image` and restarts the unit through Puppet.

## Database

The RDS instance is private: it accepts connections only from the application's
security group, so `psql` must be run from the EC2 instance.

```bash
# Read the connection details Puppet wrote (root only)
sudo grep DATABASE_URL /opt/employee-api/.env

sudo apt-get install -y postgresql-client
psql "postgresql://<username>@<rds-host>:5432/employeedb?sslmode=require"
```

Useful queries:

```sql
SELECT count(*) FROM employees;
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
```

### Backups

Automated RDS backups run daily in the 03:00–04:00 UTC window with 7-day retention.
Restore by creating a new instance from a snapshot in the RDS console, then update
`DATABASE_URL` in the Terraform variables and re-run the deploy.

Take a manual snapshot before any risky change:

```bash
aws rds create-db-snapshot \
  --db-instance-identifier <project>-postgres \
  --db-snapshot-identifier <project>-manual-$(date +%Y%m%d)
```

### Schema migrations

Flyway applies pending migrations at application startup. Never edit a migration
that has already run — add a new `V<n>__description.sql` file instead. If a
migration fails, the application refuses to start; check `docker logs employee-api`
for the failing statement, then repair the schema or add a corrective migration.

## Monitoring

* **CloudWatch alarm** `<project>-cpu-high` fires when average CPU exceeds 80%
  for two consecutive 5-minute periods. Attach an SNS topic to the alarm to receive
  notifications — no action is wired by default.
* **Metrics** are exposed at `/actuator/metrics` and `/actuator/prometheus`
  (authentication required) for scraping by an external Prometheus.

Investigating high CPU:

```bash
top -b -n 1 | head -20
docker stats --no-stream
```

## Credential rotation

Both credentials are repository secrets consumed at deploy time. Rotation is a
deploy, not a manual edit on the host.

**Database password** — update the `DB_PASSWORD` secret, then run
`infrastructure.yml` (Terraform applies the new master password to RDS) followed by
`build-deploy.yml` (Puppet rewrites `/opt/employee-api/.env`). Running them in the
other order leaves the application holding the old password.

**JWT secret** — update the `JWT_SECRET` secret and run `build-deploy.yml`. All
issued tokens are invalidated immediately, and the administrator password changes
with it because it is derived from this value.

Constraint for both: strictly alphanumeric, at least 20 characters (32 for the JWT
secret). Special characters break JDBC URLs and property parsing.

## Certificates and HTTPS

The deployment currently serves plain HTTP on the Elastic IP. Port 443 is already
open in the security group. To add TLS you need a domain name pointing at the
Elastic IP, then either terminate with certbot on the instance or place an ALB with
an ACM certificate in front — see the optional enhancements in the project plan.

## Incident checklist

1. `curl http://<public-ip>/actuator/health` — is the symptom reproducible externally?
2. `systemctl status employee-api nginx docker` — which unit is down?
3. `docker logs --tail 200 employee-api` — application-level errors, migration failures?
4. `curl http://127.0.0.1:8080/actuator/health` on the host — isolates Nginx from the app.
5. `/actuator/health/db` — distinguishes an application fault from a database fault.
6. Check the RDS instance status and the `<project>-cpu-high` alarm in the AWS console.
7. If the host is unrecoverable, re-run `infrastructure.yml` then `build-deploy.yml`:
   the instance is disposable and carries no state.

## Teardown

Run the `destroy.yml` workflow. RDS is configured with `skip_final_snapshot = true`,
so **all employee data is permanently deleted**. Take a manual snapshot first if it
must be retained.
