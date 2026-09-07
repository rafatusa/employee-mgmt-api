# API Reference

Base URL: `http://<public-ip>` (the Elastic IP reported by the infrastructure workflow).

The live, authoritative contract is served by the application:

* Swagger UI — `/swagger-ui.html`
* OpenAPI 3 JSON — `/v3/api-docs`

All responses are `application/json`. All timestamps are ISO-8601 UTC.

---

## Authentication

Every endpoint under `/api/v1/employees` requires a bearer token. Tokens are HMAC-signed
JWTs valid for one hour by default (`JWT_TTL_SECONDS`).

### POST /api/v1/auth/login

Exchange credentials for an access token.

Request:

```json
{
  "username": "admin",
  "password": "your-admin-password"
}
```

Response `200 OK`:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

Errors: `401 Unauthorized` for bad credentials, `400 Bad Request` when a field is blank.

Use the token on subsequent calls:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

## Employees

### GET /api/v1/employees

List employees, newest page first.

| Query parameter | Type | Default | Description |
| --- | --- | --- | --- |
| `department` | string | – | Case-insensitive department filter |
| `page` | integer | `0` | Zero-based page index |
| `size` | integer | `20` | Page size |
| `sort` | string | – | e.g. `lastName,asc` |

Response `200 OK`:

```json
{
  "content": [
    {
      "id": 1,
      "firstName": "Ada",
      "lastName": "Lovelace",
      "email": "ada@example.com",
      "department": "Engineering",
      "position": "Principal Engineer",
      "salary": 185000.00,
      "hireDate": "2021-03-01",
      "createdAt": "2021-03-01T00:00:00Z",
      "updatedAt": "2021-03-01T00:00:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

### GET /api/v1/employees/{id}

Fetch one employee. Returns `404 Not Found` when the id does not exist.

### POST /api/v1/employees

Create an employee. Returns `201 Created` with a `Location` header.

Request body:

| Field | Type | Constraints |
| --- | --- | --- |
| `firstName` | string | required, max 80 |
| `lastName` | string | required, max 80 |
| `email` | string | required, valid email, max 160, unique |
| `department` | string | required, max 80 |
| `position` | string | required, max 80 |
| `salary` | number | required, ≥ 0 |
| `hireDate` | string | required, `YYYY-MM-DD` |

```json
{
  "firstName": "Grace",
  "lastName": "Hopper",
  "email": "grace@example.com",
  "department": "Research",
  "position": "Rear Admiral",
  "salary": 210000.00,
  "hireDate": "2019-05-20"
}
```

Errors: `400 Bad Request` on validation failure, `409 Conflict` when the email is taken.

### PUT /api/v1/employees/{id}

Replace an existing employee. Same body and constraints as `POST`.
Errors: `404 Not Found`, `400 Bad Request`, `409 Conflict` (email owned by another record).

### DELETE /api/v1/employees/{id}

Remove an employee. Returns `204 No Content`, or `404 Not Found`.

---

## Error format

Every handled error uses the same envelope:

```json
{
  "timestamp": "2026-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "details": {
    "email": "must be a well-formed email address"
  }
}
```

`details` is empty for errors that are not field-level.

| Status | Meaning |
| --- | --- |
| `400` | Request body failed validation |
| `401` | Missing, invalid or expired token; bad credentials |
| `404` | Employee id does not exist |
| `409` | Email address already registered |

---

## Operational endpoints

| Endpoint | Auth | Description |
| --- | --- | --- |
| `GET /actuator/health` | public | Overall health, `{"status":"UP"}` |
| `GET /actuator/health/db` | public | Database connectivity component |
| `GET /actuator/info` | public | Application name and version |
| `GET /actuator/metrics` | authenticated | Micrometer metrics |
| `GET /actuator/prometheus` | authenticated | Prometheus scrape endpoint |

Responses served through the reverse proxy carry `X-Served-By: nginx-reverse-proxy`;
the validation suite asserts on this header to prove traffic traversed Nginx.
