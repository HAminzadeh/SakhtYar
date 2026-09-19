CREATE TABLE property (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL UNIQUE REFERENCES construction_case(id) ON DELETE CASCADE,

    province VARCHAR(100),
    city VARCHAR(100),
    district VARCHAR(100),
    neighborhood VARCHAR(150),
    address TEXT,

    land_area_m2 NUMERIC(12, 2),

    registry_main_no VARCHAR(100),
    registry_sub_no VARCHAR(100),
    registry_section VARCHAR(100),
    postal_code VARCHAR(20),

    latitude NUMERIC(10, 7),
    longitude NUMERIC(10, 7),

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_property_land_area
        CHECK (land_area_m2 IS NULL OR land_area_m2 > 0),

    CONSTRAINT chk_property_latitude
        CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)),

    CONSTRAINT chk_property_longitude
        CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180))
);

CREATE INDEX idx_property_city
    ON property(city);

CREATE INDEX idx_property_district
    ON property(district);

CREATE INDEX idx_property_postal_code
    ON property(postal_code);

-- Preserve Phase 0 data by creating a Property row for existing cases.
INSERT INTO property (
    id,
    case_id,
    province,
    city,
    district,
    neighborhood,
    address,
    land_area_m2,
    registry_main_no,
    registry_sub_no,
    registry_section,
    postal_code,
    latitude,
    longitude,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    c.id,
    NULL,
    c.city,
    c.district,
    NULL,
    c.address,
    c.land_area_m2,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    c.created_at,
    c.updated_at
FROM construction_case c
WHERE NOT EXISTS (
    SELECT 1
    FROM property p
    WHERE p.case_id = c.id
);

CREATE TABLE property_owner (
    id UUID PRIMARY KEY,
    property_id UUID NOT NULL REFERENCES property(id) ON DELETE CASCADE,

    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(150) NOT NULL,
    national_id VARCHAR(20),
    mobile VARCHAR(30),

    ownership_numerator INTEGER NOT NULL,
    ownership_denominator INTEGER NOT NULL,

    is_primary_contact BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_property_owner_numerator
        CHECK (ownership_numerator > 0),

    CONSTRAINT chk_property_owner_denominator
        CHECK (ownership_denominator > 0),

    CONSTRAINT chk_property_owner_share
        CHECK (ownership_numerator <= ownership_denominator)
);

CREATE INDEX idx_property_owner_property_id
    ON property_owner(property_id);

CREATE INDEX idx_property_owner_national_id
    ON property_owner(national_id);

CREATE UNIQUE INDEX ux_property_owner_national_id
    ON property_owner(property_id, national_id)
    WHERE national_id IS NOT NULL;

CREATE UNIQUE INDEX ux_property_owner_primary_contact
    ON property_owner(property_id)
    WHERE is_primary_contact = TRUE;
