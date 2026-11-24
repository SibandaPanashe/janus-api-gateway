CREATE TABLE services (
    id UUID PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    backend_url VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_client FOREIGN KEY (client_id) REFERENCES clients(client_id)
);

-- Add service_id to rules. If NULL, the rule is global.
ALTER TABLE rules ADD COLUMN service_id UUID REFERENCES services(id) ON DELETE CASCADE;
