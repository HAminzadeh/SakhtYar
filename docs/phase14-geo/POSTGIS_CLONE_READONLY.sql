-- PHASE14 / PostgreSQL clone ONLY, under a RESTRICTED READ-ONLY role.
-- This script does not alter tables, rows, functions, roles, policies or indexes.
-- Never treat a successful standalone geometry calculation as an RLS proof.
BEGIN TRANSACTION READ ONLY;
SELECT PostGIS_Full_Version() AS postgis_environment;
SELECT ST_SRID(ST_SetSRID(ST_MakePoint(51.3890,35.6892),4326)) AS srid_4326;
SELECT ST_AsText(ST_SetSRID(ST_MakePoint(51.3890,35.6892),4326)) AS wkt_point_lon_lat;
SELECT ST_Distance(
    ST_SetSRID(ST_MakePoint(51.3890,35.6892),4326)::geography,
    ST_SetSRID(ST_MakePoint(51.3890,35.6892),4326)::geography
) AS self_distance_meters;
SELECT ST_DWithin(
    ST_SetSRID(ST_MakePoint(51.3890,35.6892),4326)::geography,
    ST_SetSRID(ST_MakePoint(51.3900,35.6892),4326)::geography,
    200.0
) AS nearby_within_200m;
SELECT schemaname, tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname='public' AND tablename='property'
  AND indexname='idx_property_location_gist';
-- EXPLAIN without ANALYZE: does NOT execute the query or reveal property coordinates.
EXPLAIN
SELECT p.case_id
FROM public.property p
WHERE p.data_scope='REAL' AND p.test_workspace_id IS NULL
  AND ST_DWithin(p.location,
      ST_SetSRID(ST_MakePoint(51.3890,35.6892),4326)::geography, 1000)
LIMIT 50;
ROLLBACK;
