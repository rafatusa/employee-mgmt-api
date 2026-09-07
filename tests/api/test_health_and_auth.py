"""Health, reverse-proxy and authentication checks against the live deployment."""

import requests

TIMEOUT = 20


def test_health_endpoint_reports_up(base_url):
    response = requests.get(f"{base_url}/actuator/health", timeout=TIMEOUT)
    assert response.status_code == 200
    assert response.json()["status"] == "UP"


def test_health_endpoint_does_not_leak_component_detail(base_url):
    """The public health endpoint must not disclose infrastructure detail.

    show-details=when_authorized keeps component internals (database vendor,
    connection state, disk metrics) off a publicly reachable endpoint. This
    asserts the hardening stays in place rather than silently regressing to
    show-details=always.
    """
    response = requests.get(f"{base_url}/actuator/health", timeout=TIMEOUT)
    assert response.status_code == 200
    assert "components" not in response.json()


def test_database_connectivity_via_authenticated_query(base_url, auth_headers):
    """Prove the datasource is live the way a real client experiences it.

    Component detail is not exposed publicly, so instead of reading
    /actuator/health/db this issues an authenticated query that can only return
    200 if JPA successfully reached PostgreSQL and executed the statement.
    A broken datasource surfaces here as a 5xx.
    """
    response = requests.get(
        f"{base_url}/api/v1/employees",
        headers=auth_headers,
        params={"page": 0, "size": 1},
        timeout=TIMEOUT,
    )
    assert response.status_code == 200, response.text
    body = response.json()
    assert "content" in body
    assert isinstance(body["content"], list)


def test_login_of_unknown_user_reaches_the_database(base_url):
    """A rejected login still requires a real lookup against the users table.

    This is the credential-free database probe the smoke suite relies on: a
    reachable database answers "no such user" with 401, whereas an unreachable
    datasource cannot answer and produces a 5xx.
    """
    response = requests.post(
        f"{base_url}/api/v1/auth/login",
        json={"username": "smoke-probe-nonexistent", "password": "invalid"},
        timeout=TIMEOUT,
    )
    assert response.status_code == 401


def test_response_is_served_through_nginx(base_url):
    response = requests.get(f"{base_url}/actuator/health", timeout=TIMEOUT)
    assert response.headers.get("Server", "").lower().startswith("nginx")
    assert response.headers.get("X-Served-By") == "nginx-reverse-proxy"


def test_openapi_document_is_published(base_url):
    response = requests.get(f"{base_url}/v3/api-docs", timeout=TIMEOUT)
    assert response.status_code == 200
    body = response.json()
    assert "openapi" in body
    assert "/api/v1/employees" in body["paths"]


def test_landing_page_is_served(base_url):
    response = requests.get(f"{base_url}/", timeout=TIMEOUT)
    assert response.status_code == 200
    assert "Employee Management API" in response.text


def test_login_returns_bearer_token(base_url, admin_credentials):
    username, password = admin_credentials
    response = requests.post(
        f"{base_url}/api/v1/auth/login",
        json={"username": username, "password": password},
        timeout=TIMEOUT,
    )
    assert response.status_code == 200
    body = response.json()
    assert body["tokenType"] == "Bearer"
    assert body["accessToken"].count(".") == 2
    assert body["expiresIn"] > 0


def test_login_rejects_wrong_password(base_url, admin_credentials):
    username, _ = admin_credentials
    response = requests.post(
        f"{base_url}/api/v1/auth/login",
        json={"username": username, "password": "definitely-not-the-password"},
        timeout=TIMEOUT,
    )
    assert response.status_code == 401


def test_protected_endpoint_requires_authentication(base_url):
    response = requests.get(f"{base_url}/api/v1/employees", timeout=TIMEOUT)
    assert response.status_code == 401


def test_protected_endpoint_rejects_invalid_token(base_url):
    response = requests.get(
        f"{base_url}/api/v1/employees",
        headers={"Authorization": "Bearer not-a-real-token"},
        timeout=TIMEOUT,
    )
    assert response.status_code == 401
