ALTER TABLE property
    ADD COLUMN frontage_m NUMERIC(10, 2),
    ADD COLUMN passage_width_m NUMERIC(10, 2),
    ADD COLUMN building_area_m2 NUMERIC(12, 2),
    ADD COLUMN construction_year INTEGER,
    ADD COLUMN existing_floors INTEGER,
    ADD COLUMN existing_units INTEGER,
    ADD COLUMN orientation VARCHAR(30),
    ADD COLUMN property_type VARCHAR(80),
    ADD COLUMN building_condition VARCHAR(80),
    ADD COLUMN attributes_schema_version VARCHAR(20) NOT NULL DEFAULT '1.0',
    ADD COLUMN attributes JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE property
    ADD CONSTRAINT chk_property_frontage
        CHECK (frontage_m IS NULL OR frontage_m > 0),
    ADD CONSTRAINT chk_property_passage_width
        CHECK (passage_width_m IS NULL OR passage_width_m > 0),
    ADD CONSTRAINT chk_property_building_area
        CHECK (building_area_m2 IS NULL OR building_area_m2 > 0),
    ADD CONSTRAINT chk_property_construction_year
        CHECK (construction_year IS NULL OR construction_year BETWEEN 1000 AND 2500),
    ADD CONSTRAINT chk_property_existing_floors
        CHECK (existing_floors IS NULL OR existing_floors BETWEEN 0 AND 200),
    ADD CONSTRAINT chk_property_existing_units
        CHECK (existing_units IS NULL OR existing_units BETWEEN 0 AND 10000),
    ADD CONSTRAINT chk_property_orientation
        CHECK (
            orientation IS NULL OR orientation IN (
                'NORTH', 'SOUTH', 'EAST', 'WEST',
                'NORTH_EAST', 'NORTH_WEST',
                'SOUTH_EAST', 'SOUTH_WEST'
            )
        ),
    ADD CONSTRAINT chk_property_attributes_object
        CHECK (jsonb_typeof(attributes) = 'object');

CREATE INDEX idx_property_construction_year
    ON property(construction_year);

CREATE INDEX idx_property_property_type
    ON property(property_type);

CREATE INDEX idx_property_orientation
    ON property(orientation);

CREATE INDEX idx_property_attributes_gin
    ON property USING GIN (attributes);
