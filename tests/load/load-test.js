// k6 load test for the Employee Management API.
//
// Thresholds encode the acceptance criteria:
//   * 95th percentile response time below 500 ms
//   * error rate below 1 %
//
// Usage: BASE_URL=http://<public-ip> k6 run tests/load/load-test.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import crypto from 'k6/crypto';

const errorRate = new Rate('errors');

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const ADMIN_USERNAME = __ENV.ADMIN_USERNAME || 'admin';

// Mirrors the derivation Puppet performs so the test can authenticate without
// requiring a second secret to be published to the runner.
function adminPassword() {
  if (__ENV.ADMIN_PASSWORD) {
    return __ENV.ADMIN_PASSWORD;
  }
  if (__ENV.JWT_SECRET) {
    return crypto.sha256(__ENV.JWT_SECRET, 'hex').substring(0, 32);
  }
  return '';
}

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '1m', target: 25 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    errors: ['rate<0.01'],
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  const password = adminPassword();
  if (!password) {
    return { token: null };
  }
  const response = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username: ADMIN_USERNAME, password: password }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (response.status !== 200) {
    throw new Error(`login failed during setup: ${response.status} ${response.body}`);
  }
  return { token: response.json('accessToken') };
}

export default function (data) {
  const health = http.get(`${BASE_URL}/actuator/health`, { tags: { endpoint: 'health' } });
  check(health, {
    'health returns 200': (r) => r.status === 200,
    'health reports UP': (r) => r.json('status') === 'UP',
  }) || errorRate.add(1);

  if (data.token) {
    const params = {
      headers: { Authorization: `Bearer ${data.token}` },
      tags: { endpoint: 'employees' },
    };
    const list = http.get(`${BASE_URL}/api/v1/employees?page=0&size=20`, params);
    check(list, {
      'employee list returns 200': (r) => r.status === 200,
      'employee list is paged': (r) => r.json('content') !== undefined,
    }) || errorRate.add(1);
  }

  sleep(1);
}
