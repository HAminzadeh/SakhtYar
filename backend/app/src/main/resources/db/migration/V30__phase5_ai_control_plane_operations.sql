CREATE TABLE ai_provider (
 id UUID PRIMARY KEY, code VARCHAR(80) NOT NULL UNIQUE, display_name VARCHAR(200) NOT NULL,
 provider_type VARCHAR(40) NOT NULL, base_url VARCHAR(1000), enabled BOOLEAN NOT NULL DEFAULT TRUE,
 lifecycle_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT', priority INTEGER NOT NULL DEFAULT 100,
 request_timeout_seconds INTEGER NOT NULL DEFAULT 90, credential_configured BOOLEAN NOT NULL DEFAULT FALSE,
 credential_last4 VARCHAR(8), environment VARCHAR(30) NOT NULL DEFAULT 'LOCAL',
 health_status VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN', last_health_check_at TIMESTAMPTZ,
 last_health_message VARCHAR(1000), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT chk_ai_provider_status CHECK(lifecycle_status IN('DRAFT','TESTED','ACTIVE','DISABLED','FAILED')),
 CONSTRAINT chk_ai_provider_health CHECK(health_status IN('HEALTHY','DEGRADED','UNAVAILABLE','DISABLED','UNKNOWN'))
);
CREATE TABLE ai_model (
 id UUID PRIMARY KEY, provider_id UUID NOT NULL REFERENCES ai_provider(id) ON DELETE CASCADE,
 code VARCHAR(160) NOT NULL, display_name VARCHAR(240) NOT NULL, provider_model_id VARCHAR(300) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE, context_window INTEGER, max_output_tokens INTEGER,
 default_temperature NUMERIC(5,3), supports_json BOOLEAN NOT NULL DEFAULT FALSE,
 supports_tools BOOLEAN NOT NULL DEFAULT FALSE, supports_vision BOOLEAN NOT NULL DEFAULT FALSE,
 supports_embeddings BOOLEAN NOT NULL DEFAULT FALSE, input_cost_per_million NUMERIC(18,6),
 output_cost_per_million NUMERIC(18,6), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(provider_id,code)
);
CREATE TABLE ai_agent_route (
 id UUID PRIMARY KEY, agent_code VARCHAR(80) NOT NULL UNIQUE,
 primary_provider_id UUID REFERENCES ai_provider(id), primary_model_id UUID REFERENCES ai_model(id),
 fallback_provider_id UUID REFERENCES ai_provider(id), fallback_model_id UUID REFERENCES ai_model(id),
 canary_provider_id UUID REFERENCES ai_provider(id), canary_model_id UUID REFERENCES ai_model(id),
 canary_weight_percent INTEGER NOT NULL DEFAULT 0, routing_strategy VARCHAR(40) NOT NULL DEFAULT 'PRIMARY_FALLBACK',
 timeout_seconds INTEGER NOT NULL DEFAULT 90, max_retries INTEGER NOT NULL DEFAULT 1,
 require_json BOOLEAN NOT NULL DEFAULT FALSE, require_tools BOOLEAN NOT NULL DEFAULT FALSE,
 require_vision BOOLEAN NOT NULL DEFAULT FALSE, local_only BOOLEAN NOT NULL DEFAULT FALSE,
 max_cost_per_request NUMERIC(18,6), max_latency_ms BIGINT, enabled BOOLEAN NOT NULL DEFAULT TRUE,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT chk_ai_route_canary CHECK(canary_weight_percent BETWEEN 0 AND 100)
);
CREATE TABLE ai_prompt (
 id UUID PRIMARY KEY, code VARCHAR(120) NOT NULL UNIQUE, agent_code VARCHAR(80),
 display_name VARCHAR(240) NOT NULL, description TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE ai_prompt_version (
 id UUID PRIMARY KEY, prompt_id UUID NOT NULL REFERENCES ai_prompt(id) ON DELETE CASCADE,
 version_no INTEGER NOT NULL, lifecycle_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
 system_prompt TEXT NOT NULL, change_note VARCHAR(1000), created_by VARCHAR(200),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(prompt_id,version_no),
 CONSTRAINT chk_ai_prompt_status CHECK(lifecycle_status IN('DRAFT','STAGING','PRODUCTION','ARCHIVED'))
);
CREATE TABLE ai_budget_policy (
 id UUID PRIMARY KEY, scope_type VARCHAR(30) NOT NULL, scope_code VARCHAR(160) NOT NULL,
 monthly_budget NUMERIC(18,6), requests_per_minute INTEGER, tokens_per_minute BIGINT,
 enabled BOOLEAN NOT NULL DEFAULT TRUE, updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(scope_type,scope_code)
);
CREATE TABLE ai_guardrail_policy (
 id UUID PRIMARY KEY, code VARCHAR(120) NOT NULL UNIQUE, display_name VARCHAR(240) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE, pii_detection BOOLEAN NOT NULL DEFAULT TRUE,
 secret_detection BOOLEAN NOT NULL DEFAULT TRUE, prompt_injection_detection BOOLEAN NOT NULL DEFAULT TRUE,
 sensitive_document_local_only BOOLEAN NOT NULL DEFAULT TRUE, max_context_tokens INTEGER,
 require_output_schema BOOLEAN NOT NULL DEFAULT FALSE, updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE ai_usage_event (
 id UUID PRIMARY KEY, observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), agent_code VARCHAR(80),
 provider_code VARCHAR(80), model_code VARCHAR(300), success BOOLEAN NOT NULL,
 fallback_used BOOLEAN NOT NULL DEFAULT FALSE, latency_ms BIGINT NOT NULL DEFAULT 0,
 input_tokens BIGINT NOT NULL DEFAULT 0, output_tokens BIGINT NOT NULL DEFAULT 0,
 estimated_cost NUMERIC(18,8), error_code VARCHAR(120), correlation_id VARCHAR(128)
);
CREATE INDEX idx_ai_usage_observed ON ai_usage_event(observed_at DESC);
CREATE INDEX idx_ai_usage_agent ON ai_usage_event(agent_code,observed_at DESC);
CREATE TABLE operations_incident (
 id UUID PRIMARY KEY, fingerprint VARCHAR(200) NOT NULL UNIQUE, source VARCHAR(40) NOT NULL,
 severity VARCHAR(20) NOT NULL, lifecycle_status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
 title VARCHAR(500) NOT NULL, summary TEXT, occurrence_count BIGINT NOT NULL DEFAULT 1,
 first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(), last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 acknowledged_by VARCHAR(200), resolved_by VARCHAR(200),
 CONSTRAINT chk_ops_incident_severity CHECK(severity IN('INFO','WARNING','CRITICAL')),
 CONSTRAINT chk_ops_incident_status CHECK(lifecycle_status IN('OPEN','ACKNOWLEDGED','RESOLVED','IGNORED'))
);
INSERT INTO ai_provider(id,code,display_name,provider_type,base_url,enabled,lifecycle_status,priority,request_timeout_seconds,environment,health_status)
VALUES('10000000-0000-0000-0000-000000000001','ollama','Ollama Local','OLLAMA','http://127.0.0.1:11434',TRUE,'ACTIVE',10,90,'LOCAL','UNKNOWN')
ON CONFLICT(code) DO NOTHING;
INSERT INTO ai_model(id,provider_id,code,display_name,provider_model_id,enabled,context_window,max_output_tokens,default_temperature,supports_json)
VALUES('11000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','ollama-default','Ollama Default Model','qwen3.5:4b',TRUE,2048,128,0.1,TRUE)
ON CONFLICT(provider_id,code) DO NOTHING;
INSERT INTO ai_agent_route(id,agent_code,primary_provider_id,primary_model_id,routing_strategy,timeout_seconds,max_retries,require_json,enabled)
VALUES('12000000-0000-0000-0000-000000000001','PERSIAN','10000000-0000-0000-0000-000000000001','11000000-0000-0000-0000-000000000001','PRIMARY_FALLBACK',90,1,TRUE,TRUE)
ON CONFLICT(agent_code) DO NOTHING;
INSERT INTO ai_guardrail_policy(id,code,display_name,enabled,pii_detection,secret_detection,prompt_injection_detection,sensitive_document_local_only,require_output_schema)
VALUES('13000000-0000-0000-0000-000000000001','default','Default SakhtYar Guardrails',TRUE,TRUE,TRUE,TRUE,TRUE,TRUE)
ON CONFLICT(code) DO NOTHING;