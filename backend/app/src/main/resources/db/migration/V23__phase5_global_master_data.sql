CREATE TABLE global_language (
    id UUID PRIMARY KEY,
    code VARCHAR(16) NOT NULL UNIQUE,
    bcp47_tag VARCHAR(35) NOT NULL UNIQUE,
    name_en VARCHAR(160) NOT NULL,
    name_native VARCHAR(160) NOT NULL,
    direction VARCHAR(3) NOT NULL CHECK (direction IN ('LTR','RTL')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    system_default BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE global_currency (
    id UUID PRIMARY KEY,
    code VARCHAR(8) NOT NULL UNIQUE,
    numeric_code VARCHAR(3),
    name_en VARCHAR(160) NOT NULL,
    symbol VARCHAR(32),
    minor_unit INTEGER,
    iso4217 BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_global_currency_minor_unit CHECK (minor_unit IS NULL OR minor_unit BETWEEN 0 AND 9)
);

CREATE TABLE global_country (
    id UUID PRIMARY KEY,
    iso_alpha2 VARCHAR(2) NOT NULL UNIQUE,
    iso_alpha3 VARCHAR(3),
    iso_numeric VARCHAR(3),
    geonames_id BIGINT,
    name_en VARCHAR(200) NOT NULL,
    name_native VARCHAR(200),
    continent_code VARCHAR(2),
    default_currency_id UUID REFERENCES global_currency(id),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE global_country_language (
    country_id UUID NOT NULL REFERENCES global_country(id) ON DELETE CASCADE,
    language_id UUID NOT NULL REFERENCES global_language(id) ON DELETE CASCADE,
    primary_language BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY(country_id, language_id)
);

CREATE TABLE global_country_currency (
    country_id UUID NOT NULL REFERENCES global_country(id) ON DELETE CASCADE,
    currency_id UUID NOT NULL REFERENCES global_currency(id) ON DELETE CASCADE,
    primary_currency BOOLEAN NOT NULL DEFAULT FALSE,
    valid_from DATE,
    valid_to DATE,
    PRIMARY KEY(country_id, currency_id)
);

CREATE TABLE global_administrative_division (
    id UUID PRIMARY KEY,
    country_id UUID NOT NULL REFERENCES global_country(id) ON DELETE CASCADE,
    parent_id UUID REFERENCES global_administrative_division(id) ON DELETE SET NULL,
    code VARCHAR(80) NOT NULL,
    division_type VARCHAR(80),
    name_en VARCHAR(250) NOT NULL,
    name_native VARCHAR(250),
    level INTEGER NOT NULL DEFAULT 1,
    geonames_id BIGINT,
    latitude NUMERIC(10,7),
    longitude NUMERIC(10,7),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE(country_id, code)
);

CREATE TABLE global_city (
    id UUID PRIMARY KEY,
    country_id UUID NOT NULL REFERENCES global_country(id) ON DELETE CASCADE,
    administrative_division_id UUID REFERENCES global_administrative_division(id) ON DELETE SET NULL,
    geonames_id BIGINT NOT NULL UNIQUE,
    name VARCHAR(300) NOT NULL,
    ascii_name VARCHAR(300),
    latitude NUMERIC(10,7),
    longitude NUMERIC(10,7),
    timezone VARCHAR(100),
    population BIGINT,
    feature_code VARCHAR(20),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_global_city_country_name ON global_city(country_id, name);
CREATE INDEX idx_global_city_admin_name ON global_city(administrative_division_id, name);
CREATE INDEX idx_global_admin_country_name ON global_administrative_division(country_id, name_en);

CREATE TABLE global_master_data_import (
    id UUID PRIMARY KEY,
    dataset VARCHAR(120) NOT NULL,
    source_url VARCHAR(3000) NOT NULL,
    dataset_version VARCHAR(120),
    checksum VARCHAR(128),
    status VARCHAR(30) NOT NULL,
    record_count BIGINT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    error_message TEXT
);

CREATE TABLE user_preference (
    user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    language_id UUID REFERENCES global_language(id),
    country_id UUID REFERENCES global_country(id),
    currency_id UUID REFERENCES global_currency(id),
    timezone VARCHAR(100),
    theme VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
    date_format VARCHAR(50),
    number_format VARCHAR(50),
    first_day_of_week INTEGER,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_user_preference_theme CHECK(theme IN ('SYSTEM','LIGHT','DARK')),
    CONSTRAINT chk_first_day_of_week CHECK(first_day_of_week IS NULL OR first_day_of_week BETWEEN 1 AND 7)
);

CREATE TABLE global_fx_rate (
    id UUID PRIMARY KEY,
    base_currency_id UUID NOT NULL REFERENCES global_currency(id),
    quote_currency_id UUID NOT NULL REFERENCES global_currency(id),
    rate NUMERIC(28,12) NOT NULL CHECK(rate > 0),
    source_label VARCHAR(300) NOT NULL,
    source_url VARCHAR(3000),
    observed_at TIMESTAMPTZ NOT NULL,
    valid_from TIMESTAMPTZ,
    valid_to TIMESTAMPTZ,
    confidence NUMERIC(5,4),
    review_status VARCHAR(40) NOT NULL DEFAULT 'APPROVED',
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE(base_currency_id, quote_currency_id, observed_at)
);

CREATE INDEX idx_fx_pair_observed
    ON global_fx_rate(base_currency_id, quote_currency_id, observed_at DESC);