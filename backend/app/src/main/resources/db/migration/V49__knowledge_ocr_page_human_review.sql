-- SakhtYar: human review queue for low-confidence/unreadable OCR pages.
-- Additive; does not change V1-V48 or existing knowledge/review decisions.
create table if not exists knowledge_ocr_page_review (
    execution_id uuid not null references knowledge_intake_execution(id),
    document_id uuid not null references knowledge_source_document(id),
    page_no integer not null check (page_no > 0),
    status varchar(24) not null default 'PENDING'
        check (status in ('PENDING','TEXT_PROVIDED','NOT_IMPORTANT')),
    reason text not null,
    native_chars integer not null default 0,
    ocr_chars integer not null default 0,
    review_text text,
    reviewed_by varchar(160),
    reviewed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (execution_id, document_id, page_no),
    constraint chk_ocr_review_text check (
        (status = 'TEXT_PROVIDED' and nullif(btrim(review_text),'') is not null)
        or (status <> 'TEXT_PROVIDED' and review_text is null)
    )
);
create index if not exists ix_ocr_review_pending
    on knowledge_ocr_page_review(status, created_at desc);
create index if not exists ix_ocr_review_execution
    on knowledge_ocr_page_review(execution_id, status);
