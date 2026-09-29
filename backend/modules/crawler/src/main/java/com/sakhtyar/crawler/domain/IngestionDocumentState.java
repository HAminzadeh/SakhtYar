package com.sakhtyar.crawler.domain;

public enum IngestionDocumentState {
    DISCOVERED,
    EXTRACTED,
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    SUPERSEDED,
    ARCHIVED
}