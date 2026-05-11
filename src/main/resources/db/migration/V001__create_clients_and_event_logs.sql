-- Clients table: persistent storage for API key registrations
CREATE TABLE IF NOT EXISTS clients (
                                       id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL UNIQUE,
    api_key_hash VARCHAR(64) NOT NULL UNIQUE,
    plan VARCHAR(50) NOT NULL DEFAULT 'free',
    rate_limit_per_second INT NOT NULL DEFAULT 10,
    rate_limit_per_minute INT NOT NULL DEFAULT 100,
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    surcharge_balance DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

-- Event logs: audit trail for all gateway events
CREATE TABLE IF NOT EXISTS event_logs (
                                          id BIGSERIAL PRIMARY KEY,
                                          client_id VARCHAR(255),
    event_type VARCHAR(50) NOT NULL,
    endpoint VARCHAR(255),
    ip_address VARCHAR(45),
    response_code INT,
    plan_at_time VARCHAR(50),
    request_count INT,
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

-- Indexes for common queries
CREATE INDEX idx_clients_api_key_hash ON clients(api_key_hash);
CREATE INDEX idx_clients_client_id ON clients(client_id);
CREATE INDEX idx_event_logs_client_id ON event_logs(client_id);
CREATE INDEX idx_event_logs_event_type ON event_logs(event_type);
CREATE INDEX idx_event_logs_created_at ON event_logs(created_at);
CREATE INDEX idx_event_logs_client_event ON event_logs(client_id, event_type);