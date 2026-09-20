CREATE TABLE agent_conversation (
    id UUID PRIMARY KEY,
    case_id UUID REFERENCES construction_case(id) ON DELETE CASCADE,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_agent_conversation_case_id
    ON agent_conversation(case_id);

CREATE INDEX idx_agent_conversation_updated_at
    ON agent_conversation(updated_at DESC);

CREATE TABLE agent_message (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES agent_conversation(id) ON DELETE CASCADE,
    role VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    payload_text TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_agent_message_conversation
    ON agent_message(conversation_id, created_at);

CREATE TABLE agent_run (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL UNIQUE,
    conversation_id UUID NOT NULL REFERENCES agent_conversation(id) ON DELETE CASCADE,
    case_id UUID REFERENCES construction_case(id) ON DELETE SET NULL,
    schema_version VARCHAR(30) NOT NULL,
    intent VARCHAR(80),
    workflow VARCHAR(80),
    status VARCHAR(40) NOT NULL,
    duration_ms BIGINT NOT NULL,
    input_text TEXT NOT NULL,
    output_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_agent_run_conversation
    ON agent_run(conversation_id, created_at DESC);

CREATE INDEX idx_agent_run_case
    ON agent_run(case_id, created_at DESC);

CREATE TABLE persian_glossary_entry (
    id UUID PRIMARY KEY,
    term VARCHAR(200) NOT NULL,
    normalized_term VARCHAR(200) NOT NULL UNIQUE,
    meaning TEXT NOT NULL,
    aliases_text TEXT,
    status VARCHAR(30) NOT NULL,
    created_by VARCHAR(150) NOT NULL,
    approved_by VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_persian_glossary_status
    ON persian_glossary_entry(status, term);
