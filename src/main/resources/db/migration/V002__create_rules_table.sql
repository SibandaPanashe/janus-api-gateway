-- Rules table: stores Drools DRL rules that can be hot-reloaded
CREATE TABLE IF NOT EXISTS rules (
                                     id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    drl_content TEXT NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

-- Insert the existing rate-limiting rules as seed data
INSERT INTO rules (id, name, description, drl_content, priority, is_active) VALUES
                                                                                (
                                                                                    'seed-free-tier',
                                                                                    'Free Tier Rate Limit',
                                                                                    'Blocks free tier clients when they exceed 10 requests per second',
                                                                                    'package com.sibanda.co.zw.janusgateway.rules;

                                                                                import com.sibanda.co.zw.janusgateway.model.ClientProfile;

                                                                                dialect "mvel"

                                                                                rule "Free Tier - Rate Limit Per Second"
                                                                                    when
                                                                                        $client: ClientProfile(
                                                                                            plan == "free",
                                                                                            requestCountSecond >= limitPerSecond,
                                                                                            blocked == false
                                                                                        )
                                                                                    then
                                                                                        System.out.println("[Drools] Blocking free client: " + $client.getClientId() +
                                                                                            " - Reached " + $client.getRequestCountSecond() + " req/s (limit: " + $client.getLimitPerSecond() + ")");
                                                                                        $client.setBlocked(true);
                                                                                        update($client);
                                                                                end',
                                                                                    10,
                                                                                    TRUE
                                                                                ),
                                                                                (
                                                                                    'seed-blocked-deny',
                                                                                    'Blocked Client Deny All',
                                                                                    'Rejects all requests from blocked clients',
                                                                                    'package com.sibanda.co.zw.janusgateway.rules;

                                                                                import com.sibanda.co.zw.janusgateway.model.ClientProfile;

                                                                                dialect "mvel"

                                                                                rule "Blocked Client - Deny All"
                                                                                    when
                                                                                        $client: ClientProfile(blocked == true)
                                                                                    then
                                                                                        System.out.println("[Drools] Request denied for blocked client: " + $client.getClientId());
                                                                                end',
                                                                                    20,
                                                                                    TRUE
                                                                                ),
                                                                                (
                                                                                    'seed-payg-surcharge',
                                                                                    'Pay As You Go Surcharge',
                                                                                    'Adds $0.01 surcharge per request for pay-as-you-go clients',
                                                                                    'package com.sibanda.co.zw.janusgateway.rules;

                                                                                import com.sibanda.co.zw.janusgateway.model.ClientProfile;

                                                                                dialect "mvel"

                                                                                rule "Pay As You Go - Add Surcharge"
                                                                                    when
                                                                                        $client: ClientProfile(plan == "pay-as-you-go", blocked == false)
                                                                                    then
                                                                                        System.out.println("[Drools] Adding $0.01 surcharge for client: " + $client.getClientId());
                                                                                        $client.setSurchargeAmount($client.getSurchargeAmount() + 0.01);
                                                                                        update($client);
                                                                                end',
                                                                                    30,
                                                                                    TRUE
                                                                                );

CREATE INDEX idx_rules_active ON rules(is_active);
CREATE INDEX idx_rules_priority ON rules(priority);