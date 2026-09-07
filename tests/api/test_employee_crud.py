"""Functional and integration coverage of the employee CRUD surface."""

import requests

TIMEOUT = 20


def test_list_employees_is_paged(base_url, auth_headers):
    response = requests.get(
        f"{base_url}/api/v1/employees", headers=auth_headers, timeout=TIMEOUT
    )
    assert response.status_code == 200
    body = response.json()
    assert "content" in body
    assert "totalElements" in body


def test_create_employee(base_url, auth_headers, employee_payload):
    response = requests.post(
        f"{base_url}/api/v1/employees",
        json=employee_payload,
        headers=auth_headers,
        timeout=TIMEOUT,
    )
    assert response.status_code == 201, response.text
    created = response.json()
    assert created["id"] > 0
    assert created["email"] == employee_payload["email"]
    assert created["createdAt"] is not None

    requests.delete(
        f"{base_url}/api/v1/employees/{created['id']}",
        headers=auth_headers,
        timeout=TIMEOUT,
    )


def test_read_employee_persists_across_requests(base_url, auth_headers, created_employee):
    """A second request re-reads the row from PostgreSQL, proving persistence."""
    response = requests.get(
        f"{base_url}/api/v1/employees/{created_employee['id']}",
        headers=auth_headers,
        timeout=TIMEOUT,
    )
    assert response.status_code == 200
    assert response.json()["email"] == created_employee["email"]


def test_update_employee(base_url, auth_headers, created_employee, employee_payload):
    updated = dict(employee_payload)
    updated["position"] = "Senior Automation Engineer"
    updated["salary"] = 105000.00

    response = requests.put(
        f"{base_url}/api/v1/employees/{created_employee['id']}",
        json=updated,
        headers=auth_headers,
        timeout=TIMEOUT,
    )
    assert response.status_code == 200
    assert response.json()["position"] == "Senior Automation Engineer"


def test_delete_employee(base_url, auth_headers, employee_payload):
    created = requests.post(
        f"{base_url}/api/v1/employees",
        json=employee_payload,
        headers=auth_headers,
        timeout=TIMEOUT,
    ).json()

    deleted = requests.delete(
        f"{base_url}/api/v1/employees/{created['id']}",
        headers=auth_headers,
        timeout=TIMEOUT,
    )
    assert deleted.status_code == 204

    missing = requests.get(
        f"{base_url}/api/v1/employees/{created['id']}",
        headers=auth_headers,
        timeout=TIMEOUT,
    )
    assert missing.status_code == 404


def test_duplicate_email_is_rejected(base_url, auth_headers, created_employee, employee_payload):
    response = requests.post(
        f"{base_url}/api/v1/employees",
        json=employee_payload,
        headers=auth_headers,
        timeout=TIMEOUT,
    )
    assert response.status_code == 409
    assert response.json()["error"] == "Conflict"


def test_validation_rejects_bad_payload(base_url, auth_headers):
    response = requests.post(
        f"{base_url}/api/v1/employees",
        json={
            "firstName": "",
            "lastName": "Nobody",
            "email": "not-an-email",
            "department": "QA",
            "position": "Tester",
            "salary": -5,
            "hireDate": "2024-01-15",
        },
        headers=auth_headers,
        timeout=TIMEOUT,
    )
    assert response.status_code == 400
    assert "firstName" in response.json()["details"]


def test_unknown_employee_returns_404(base_url, auth_headers):
    response = requests.get(
        f"{base_url}/api/v1/employees/99999999",
        headers=auth_headers,
        timeout=TIMEOUT,
    )
    assert response.status_code == 404


def test_department_filter(base_url, auth_headers, created_employee):
    response = requests.get(
        f"{base_url}/api/v1/employees",
        params={"department": created_employee["department"]},
        headers=auth_headers,
        timeout=TIMEOUT,
    )
    assert response.status_code == 200
    emails = [item["email"] for item in response.json()["content"]]
    assert created_employee["email"] in emails
