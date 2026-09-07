"""Health, reverse-proxy and authentication checks against the live deployment."""

import requests

TIMEOUT = 20


def test_health_endpoint_reports_up(base_url):
    response = requests.get(f"{base_url}/actuator/health", timeout=TIMEOUT)
    assert response.status_code == 200
    assert response.json()["status"] == "UP"


def test_database_connectivity_via_health_component(base_url):
    response = requests.get(f"{base_url}/actuator/health/db", timeout=TIMEOUT)
    assert response.status_code == 200
    assert response.json()["status"] == "UP"


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
