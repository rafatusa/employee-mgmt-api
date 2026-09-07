"""Shared fixtures for the post-deployment API test suite."""

import hashlib
import os
import uuid

import pytest
import requests

DEFAULT_TIMEOUT = 20


def _base_url() -> str:
    url = os.environ.get("BASE_URL")
    if not url:
        pytest.fail("BASE_URL environment variable is required")
    return url.rstrip("/")


@pytest.fixture(scope="session")
def base_url() -> str:
    return _base_url()


@pytest.fixture(scope="session")
def admin_credentials() -> tuple:
    """Credentials of the seeded administrator.

    The deployment derives the administrator password from the JWT secret with
    the same transformation Puppet applies (sha256, first 32 characters), so the
    suite can authenticate without a second secret.
    """
    username = os.environ.get("ADMIN_USERNAME", "admin")
    password = os.environ.get("ADMIN_PASSWORD")
    if not password:
        jwt_secret = os.environ.get("JWT_SECRET")
        if not jwt_secret:
            pytest.skip("neither ADMIN_PASSWORD nor JWT_SECRET is available")
        password = hashlib.sha256(jwt_secret.encode()).hexdigest()[:32]
    return username, password


@pytest.fixture(scope="session")
def token(base_url: str, admin_credentials: tuple) -> str:
    username, password = admin_credentials
    response = requests.post(
        f"{base_url}/api/v1/auth/login",
        json={"username": username, "password": password},
        timeout=DEFAULT_TIMEOUT,
    )
    assert response.status_code == 200, (
        f"login failed: {response.status_code} {response.text}"
    )
    return response.json()["accessToken"]


@pytest.fixture(scope="session")
def auth_headers(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture
def employee_payload() -> dict:
    unique = uuid.uuid4().hex[:10]
    return {
        "firstName": "Test",
        "lastName": "Employee",
        "email": f"test.{unique}@example.com",
        "department": "Quality Assurance",
        "position": "Automation Engineer",
        "salary": 92000.00,
        "hireDate": "2024-01-15",
    }


@pytest.fixture
def created_employee(base_url: str, auth_headers: dict, employee_payload: dict):
    """Creates an employee and removes it after the test."""
    response = requests.post(
        f"{base_url}/api/v1/employees",
        json=employee_payload,
        headers=auth_headers,
        timeout=DEFAULT_TIMEOUT,
    )
    assert response.status_code == 201, response.text
    employee = response.json()
    yield employee
    requests.delete(
        f"{base_url}/api/v1/employees/{employee['id']}",
        headers=auth_headers,
        timeout=DEFAULT_TIMEOUT,
    )
