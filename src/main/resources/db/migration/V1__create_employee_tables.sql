CREATE TABLE IF NOT EXISTS employees (
    id          BIGSERIAL PRIMARY KEY,
    first_name  VARCHAR(80)    NOT NULL,
    last_name   VARCHAR(80)    NOT NULL,
    email       VARCHAR(160)   NOT NULL UNIQUE,
    department  VARCHAR(80)    NOT NULL,
    position    VARCHAR(80)    NOT NULL,
    salary      NUMERIC(12, 2) NOT NULL,
    hire_date   DATE           NOT NULL,
    created_at  TIMESTAMP      NOT NULL,
    updated_at  TIMESTAMP      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_employees_department ON employees (department);

CREATE TABLE IF NOT EXISTS app_users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(80)  NOT NULL UNIQUE,
    password_hash VARCHAR(120) NOT NULL,
    role          VARCHAR(32)  NOT NULL
);
