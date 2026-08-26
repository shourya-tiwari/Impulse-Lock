-- ImpulseLock V2 schema (see docs/v2/database-design.md for full rationale).
--
-- Phase-0 note: `transactions.decision_type` / `risk_score` / `explanation` / `triggered_rules`
-- are nullable here even though the target design treats them as required. The Phase 0 JPA
-- migration re-points persistence at JPA but does not yet wire the DecisionEngine's output
-- onto the transaction row (see docs/v2/tasks.md, Phase 2, and docs/v2/roadmap.md Phase 2).
-- A later migration tightens these to NOT NULL once that write path lands.

CREATE TABLE roles (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(30) NOT NULL,
    CONSTRAINT uk_roles_name UNIQUE (name)
) ENGINE = InnoDB;

CREATE TABLE users (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    username                VARCHAR(50)   NOT NULL,
    email                   VARCHAR(255)  NOT NULL,
    password_hash           VARCHAR(255)  NOT NULL,
    daily_limit             DECIMAL(12,2) NOT NULL DEFAULT 0,
    night_spending_allowed  BOOLEAN       NOT NULL DEFAULT FALSE,
    sensitivity_level       TINYINT       NOT NULL DEFAULT 5,
    enabled                 BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at              DATETIME(3)   NOT NULL,
    updated_at              DATETIME(3)   NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_sensitivity_level CHECK (sensitivity_level BETWEEN 1 AND 10),
    CONSTRAINT chk_users_daily_limit CHECK (daily_limit >= 0)
) ENGINE = InnoDB;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE restricted_categories (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    category   VARCHAR(50) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_restricted_categories_user_category UNIQUE (user_id, category),
    CONSTRAINT fk_restricted_categories_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE transactions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id       CHAR(36)               NOT NULL,
    user_id         BIGINT                 NOT NULL,
    amount          DECIMAL(12,2)          NOT NULL,
    category        VARCHAR(50)            NULL,
    merchant        VARCHAR(100)           NULL,
    occurred_at     DATETIME(3)            NOT NULL,
    decision_type   ENUM('ALLOW','DELAY','BLOCK') NULL,
    risk_score      DECIMAL(5,2)           NULL,
    explanation     TEXT                   NULL,
    triggered_rules JSON                   NULL,
    created_at      DATETIME(3)            NOT NULL,
    CONSTRAINT uk_transactions_public_id UNIQUE (public_id),
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_transactions_amount CHECK (amount >= 0),
    CONSTRAINT chk_transactions_risk_score CHECK (risk_score IS NULL OR risk_score BETWEEN 0 AND 100)
) ENGINE = InnoDB;

CREATE INDEX idx_transactions_user_occurred_at ON transactions (user_id, occurred_at DESC);
CREATE INDEX idx_transactions_user_decision_type ON transactions (user_id, decision_type);
CREATE INDEX idx_transactions_user_category ON transactions (user_id, category);

CREATE TABLE rule_configs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_code   VARCHAR(50)   NOT NULL,
    weight      DECIMAL(5,2)  NOT NULL,
    enabled     BOOLEAN       NOT NULL DEFAULT TRUE,
    params      JSON          NULL,
    updated_at  DATETIME(3)   NOT NULL,
    CONSTRAINT uk_rule_configs_rule_code UNIQUE (rule_code)
) ENGINE = InnoDB;

CREATE TABLE refresh_tokens (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  DATETIME(3)  NOT NULL,
    revoked_at  DATETIME(3)  NULL,
    created_at  DATETIME(3)  NOT NULL,
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE audit_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_user_id  BIGINT       NULL,
    action         VARCHAR(60)  NOT NULL,
    entity_type    VARCHAR(50)  NULL,
    entity_id      VARCHAR(50)  NULL,
    metadata       JSON         NULL,
    ip_address     VARCHAR(45)  NULL,
    correlation_id CHAR(36)     NULL,
    created_at     DATETIME(3)  NOT NULL,
    CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB;

CREATE INDEX idx_audit_log_actor_created_at ON audit_log (actor_user_id, created_at DESC);
CREATE INDEX idx_audit_log_action_created_at ON audit_log (action, created_at DESC);
