UPDATE knowledge_source ks
SET country_id=c.id
FROM global_country c
WHERE ks.country_id IS NULL
  AND ks.jurisdiction_country IS NOT NULL
  AND upper(ks.jurisdiction_country)=c.iso_alpha2;

UPDATE urban_rule ur
SET country_id=c.id
FROM global_country c
WHERE ur.country_id IS NULL
  AND ur.jurisdiction_country IS NOT NULL
  AND upper(ur.jurisdiction_country)=c.iso_alpha2;

UPDATE knowledge_source
SET jurisdiction_scope=CASE
  WHEN city_id IS NOT NULL THEN 'CITY'
  WHEN administrative_division_id IS NOT NULL THEN 'ADMIN_DIVISION'
  WHEN country_id IS NOT NULL THEN 'COUNTRY'
  ELSE 'GLOBAL'
END;

UPDATE urban_rule
SET jurisdiction_scope=CASE
  WHEN city_id IS NOT NULL THEN 'CITY'
  WHEN administrative_division_id IS NOT NULL THEN 'ADMIN_DIVISION'
  WHEN country_id IS NOT NULL THEN 'COUNTRY'
  ELSE 'GLOBAL'
END;