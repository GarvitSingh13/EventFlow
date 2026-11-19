-- V1: Initialize EventFlow Core Schema

-- 1. Organizations / Workspaces
CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 2. Console Users
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ADMIN',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 3. API Keys (Salted SHA-256 Hashed)
CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    key_hash VARCHAR(64) UNIQUE NOT NULL,
    prefix VARCHAR(16) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 4. Webhook Subscriber Endpoints
CREATE TABLE webhook_endpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    url VARCHAR(2048) NOT NULL,
    description VARCHAR(500),
    secret_token VARCHAR(255) NOT NULL,
    event_types TEXT[] NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    rate_limit INT DEFAULT 100 NOT NULL,
    timeout_ms INT DEFAULT 5000 NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 5. Ingested Events
CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    event_type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    idempotency_key VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 6. Event Deliveries (Durable PostgreSQL Queue)
CREATE TABLE event_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    endpoint_id UUID NOT NULL REFERENCES webhook_endpoints(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    attempt_count INT DEFAULT 0 NOT NULL,
    max_attempts INT DEFAULT 5 NOT NULL,
    next_retry_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    locked_at TIMESTAMP WITH TIME ZONE,
    locked_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 7. Delivery Attempt Logs & Audit History
CREATE TABLE delivery_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id UUID NOT NULL REFERENCES event_deliveries(id) ON DELETE CASCADE,
    attempt_number INT NOT NULL,
    response_status_code INT,
    response_headers TEXT,
    response_body TEXT,
    execution_time_ms INT,
    error_message TEXT,
    executed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- High Performance Indexes
-- Fast queue claiming with SKIP LOCKED
CREATE INDEX idx_event_deliveries_polling ON event_deliveries (status, next_retry_at) 
    WHERE status IN ('PENDING', 'RETRY_SCHEDULED');

CREATE INDEX idx_event_deliveries_event ON event_deliveries (event_id);
CREATE INDEX idx_event_deliveries_endpoint ON event_deliveries (endpoint_id, status);

CREATE INDEX idx_events_org ON events (organization_id, created_at DESC);
CREATE INDEX idx_events_idempotency ON events (organization_id, idempotency_key) 
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_api_keys_hash ON api_keys (key_hash) 
    WHERE status = 'ACTIVE';

CREATE INDEX idx_endpoints_org ON webhook_endpoints (organization_id, status);
CREATE INDEX idx_delivery_attempts_delivery ON delivery_attempts (delivery_id, attempt_number DESC);
