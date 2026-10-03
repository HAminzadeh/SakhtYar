-- SakhtYar Phase 5.13 - Learning Loop
-- Feedback is evidence. Knowledge candidates remain non-authoritative until human review.

CREATE TABLE learning_event (
    id UUID PRIMARY KEY,
    case_id UUID REFERENCES construction_case(id) ON DELETE SET NULL,
    conversation_id UUID REFERENCES agent_conversation(id) ON DELETE SET NULL,
    input_gateway_request_id UUID REFERENCES persian_input_request(id) ON DELETE SET NULL,
    feedback_type VARCHAR(50) NOT NULL,
    status VARCHAR(40) NOT NULL,
    field_key VARCHAR(160),
    original_value TEXT,
    corrected_value TEXT,
    rating SMALLINT,
    note TEXT,
    knowledge_candidate_id UUID REFERENCES knowledge_candidate(id) ON DELETE SET NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_learning_event_status
        CHECK (status IN ('RECORDED', 'CANDIDATE_CREATED')),

    CONSTRAINT chk_learning_event_rating
        CHECK (rating IS NULL OR (rating >= 1 AND rating <= 5)),

    CONSTRAINT chk_learning_event_payload_object
        CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX idx_learning_event_case
    ON learning_event(case_id, created_at DESC);

CREATE INDEX idx_learning_event_conversation
    ON learning_event(conversation_id, created_at DESC);

CREATE INDEX idx_learning_event_input_gateway
    ON learning_event(input_gateway_request_id, created_at DESC);

CREATE INDEX idx_learning_event_candidate
    ON learning_event(knowledge_candidate_id);

CREATE INDEX idx_learning_event_type
    ON learning_event(feedback_type, created_at DESC);