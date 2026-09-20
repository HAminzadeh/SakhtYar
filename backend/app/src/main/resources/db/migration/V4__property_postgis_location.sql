ALTER TABLE property
    ADD COLUMN location geography(Point, 4326);

UPDATE property
SET location = ST_SetSRID(
        ST_MakePoint(longitude::double precision, latitude::double precision),
        4326
    )::geography
WHERE latitude IS NOT NULL
  AND longitude IS NOT NULL;

CREATE OR REPLACE FUNCTION sync_property_geography()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.latitude IS NULL OR NEW.longitude IS NULL THEN
        NEW.location := NULL;
    ELSE
        NEW.location := ST_SetSRID(
            ST_MakePoint(
                NEW.longitude::double precision,
                NEW.latitude::double precision
            ),
            4326
        )::geography;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_property_sync_geography
BEFORE INSERT OR UPDATE OF latitude, longitude
ON property
FOR EACH ROW
EXECUTE FUNCTION sync_property_geography();

CREATE INDEX idx_property_location_gist
    ON property
    USING GIST(location);
