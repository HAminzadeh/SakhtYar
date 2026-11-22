-- Development-only seed data.
-- Executed by DevSeedRunner only when app.dev-seed.enabled=true.
-- Safe to run repeatedly: deterministic UUIDs + insert-only idempotency.
-- Existing demo/user-edited rows are preserved on application restart.

INSERT INTO construction_case (
    id, project_code, title, status, description, city, district, address,
    land_area_m2, cover_image_url, created_by, created_at, updated_at
)
VALUES
('10000000-0000-0000-0000-000000000001','PRJ-DEMO-0001','پروژه نیاوران','CONTRACT','[DEMO] پرونده مشارکت در ساخت نیاوران با وضعیت قرارداد.','تهران','منطقه ۱ - نیاوران','نیاوران، خیابان باهنر، محدوده جماران',1850,
 '/assets/sakhtyar/projects/project-01.jpg','admin',NOW()-INTERVAL '48 days',NOW()-INTERVAL '1 day'),
('10000000-0000-0000-0000-000000000002','PRJ-DEMO-0002','پروژه سعادت‌آباد','CONSTRUCTION','[DEMO] پروژه در مرحله اجرای سازه و عملیات کارگاهی.','تهران','منطقه ۲ - سعادت‌آباد','سعادت‌آباد، بلوار دریا، محدوده کوی فراز',2400,
 '/assets/sakhtyar/projects/project-02.jpg','admin',NOW()-INTERVAL '90 days',NOW()-INTERVAL '2 hours'),
('10000000-0000-0000-0000-000000000003','PRJ-DEMO-0003','پروژه زعفرانیه','CONTRACT','[DEMO] قرارداد مشارکت امضا شده و در حال تکمیل مدارک شروع عملیات.','تهران','منطقه ۱ - زعفرانیه','زعفرانیه، خیابان آصف، محدوده مقدس اردبیلی',1350,
 '/assets/sakhtyar/projects/project-03.jpg','admin',NOW()-INTERVAL '36 days',NOW()-INTERVAL '3 days'),
('10000000-0000-0000-0000-000000000004','PRJ-DEMO-0004','پروژه ولنجک','CONSTRUCTION','[DEMO] پروژه فعال در مرحله ساخت و پیشرفت فیزیکی.','تهران','منطقه ۱ - ولنجک','ولنجک، بلوار دانشجو، محدوده یمن',3200,
 '/assets/sakhtyar/projects/project-01.jpg','admin',NOW()-INTERVAL '140 days',NOW()-INTERVAL '8 hours'),
('10000000-0000-0000-0000-000000000005','PRJ-DEMO-0005','پروژه الهیه','NEGOTIATION','[DEMO] در حال مذاکره درباره سهم طرفین و برنامه زمان‌بندی.','تهران','منطقه ۱ - الهیه','الهیه، خیابان فرشته، محدوده آقابزرگی',980,
 '/assets/sakhtyar/projects/project-02.jpg','admin',NOW()-INTERVAL '18 days',NOW()-INTERVAL '5 hours'),
('10000000-0000-0000-0000-000000000006','PRJ-DEMO-0006','پروژه فرمانیه','ACTIVE','[DEMO] پرونده فعال با اطلاعات پایه تکمیل شده.','تهران','منطقه ۱ - فرمانیه','فرمانیه، بلوار اندرزگو، محدوده دیباجی شمالی',1750,
 '/assets/sakhtyar/projects/project-03.jpg','admin',NOW()-INTERVAL '24 days',NOW()-INTERVAL '10 hours'),
('10000000-0000-0000-0000-000000000007','PRJ-DEMO-0007','پروژه کامرانیه','DRAFT','[DEMO] پرونده اولیه برای ارزیابی ملک و تکمیل اطلاعات مالکین.','تهران','منطقه ۱ - کامرانیه','کامرانیه شمالی، محدوده شیبانی',2100,
 '/assets/sakhtyar/projects/project-01.jpg','admin',NOW()-INTERVAL '7 days',NOW()-INTERVAL '18 hours'),
('10000000-0000-0000-0000-000000000008','PRJ-DEMO-0008','پروژه محمودیه','ON_HOLD','[DEMO] پروژه موقتاً متوقف تا تکمیل بررسی‌های حقوقی.','تهران','منطقه ۱ - محمودیه','محمودیه، خیابان مقدس اردبیلی، محدوده ب',1450,
 '/assets/sakhtyar/projects/project-02.jpg','admin',NOW()-INTERVAL '72 days',NOW()-INTERVAL '9 days'),
('10000000-0000-0000-0000-000000000009','PRJ-DEMO-0009','پروژه دروس','NEGOTIATION','[DEMO] در حال مذاکره و بررسی سناریوهای مشارکت و تراکم.','تهران','منطقه ۳ - دروس','دروس، خیابان هدایت، محدوده یارمحمدی',1650,
 '/assets/sakhtyar/projects/project-03.jpg','admin',NOW()-INTERVAL '12 days',NOW()-INTERVAL '2 days'),
('10000000-0000-0000-0000-000000000010','PRJ-DEMO-0010','پروژه پاسداران','COMPLETED','[DEMO] نمونه پروژه تکمیل‌شده برای نمایش سوابق و گزارش‌ها.','تهران','منطقه ۳ - پاسداران','پاسداران، خیابان گلستان، محدوده ضرابخانه',1900,
 '/assets/sakhtyar/projects/project-01.jpg','admin',NOW()-INTERVAL '300 days',NOW()-INTERVAL '20 days')
ON CONFLICT (id) DO NOTHING;

INSERT INTO property (
    id, case_id, province, city, district, neighborhood, address,
    land_area_m2, frontage_m, passage_width_m, building_area_m2,
    construction_year, existing_floors, existing_units, orientation,
    property_type, building_condition,
    registry_main_no, registry_sub_no, registry_section, postal_code,
    latitude, longitude, attributes_schema_version, attributes,
    created_at, updated_at
)
VALUES
('20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','تهران','تهران','منطقه ۱','نیاوران','نیاوران، خیابان باهنر، محدوده جماران',1850,28,12,920,1385,3,2,'NORTH','RESIDENTIAL','OLD',NULL,NULL,NULL,NULL,35.8140000,51.4690000,'1.0','{"demo":true,"deedType":"SIX_DANG","landUse":"RESIDENTIAL"}'::jsonb,NOW()-INTERVAL '48 days',NOW()-INTERVAL '1 day'),
('20000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000002','تهران','تهران','منطقه ۲','سعادت‌آباد','سعادت‌آباد، بلوار دریا، محدوده کوی فراز',2400,34,16,1300,1390,4,4,'NORTH_EAST','RESIDENTIAL','GOOD',NULL,NULL,NULL,NULL,35.7800000,51.3760000,'1.0','{"demo":true,"deedType":"SIX_DANG","landUse":"RESIDENTIAL"}'::jsonb,NOW()-INTERVAL '90 days',NOW()-INTERVAL '2 hours'),
('20000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000003','تهران','تهران','منطقه ۱','زعفرانیه','زعفرانیه، خیابان آصف، محدوده مقدس اردبیلی',1350,24,12,780,1380,3,2,'EAST','RESIDENTIAL','OLD',NULL,NULL,NULL,NULL,35.8060000,51.4090000,'1.0','{"demo":true,"deedType":"SIX_DANG","landUse":"RESIDENTIAL"}'::jsonb,NOW()-INTERVAL '36 days',NOW()-INTERVAL '3 days'),
('20000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000004','تهران','تهران','منطقه ۱','ولنجک','ولنجک، بلوار دانشجو، محدوده یمن',3200,40,18,1650,1388,4,5,'NORTH','RESIDENTIAL','GOOD',NULL,NULL,NULL,NULL,35.8040000,51.3950000,'1.0','{"demo":true,"deedType":"SIX_DANG","landUse":"RESIDENTIAL"}'::jsonb,NOW()-INTERVAL '140 days',NOW()-INTERVAL '8 hours'),
('20000000-0000-0000-0000-000000000005','10000000-0000-0000-0000-000000000005','تهران','تهران','منطقه ۱','الهیه','الهیه، خیابان فرشته، محدوده آقابزرگی',980,18,10,640,1378,3,2,'WEST','RESIDENTIAL','OLD',NULL,NULL,NULL,NULL,35.7970000,51.4250000,'1.0','{"demo":true,"deedType":"SIX_DANG","landUse":"RESIDENTIAL"}'::jsonb,NOW()-INTERVAL '18 days',NOW()-INTERVAL '5 hours'),
('20000000-0000-0000-0000-000000000006','10000000-0000-0000-0000-000000000006','تهران','تهران','منطقه ۱','فرمانیه','فرمانیه، بلوار اندرزگو، محدوده دیباجی شمالی',1750,27,14,1080,1386,4,3,'SOUTH_EAST','RESIDENTIAL','GOOD',NULL,NULL,NULL,NULL,35.8060000,51.4660000,'1.0','{"demo":true,"deedType":"SIX_DANG","landUse":"RESIDENTIAL"}'::jsonb,NOW()-INTERVAL '24 days',NOW()-INTERVAL '10 hours'),
('20000000-0000-0000-0000-000000000007','10000000-0000-0000-0000-000000000007','تهران','تهران','منطقه ۱','کامرانیه','کامرانیه شمالی، محدوده شیبانی',2100,31,14,1200,1384,4,4,'NORTH_WEST','RESIDENTIAL','OLD',NULL,NULL,NULL,NULL,35.8120000,51.4530000,'1.0','{"demo":true,"deedType":"SIX_DANG","landUse":"RESIDENTIAL"}'::jsonb,NOW()-INTERVAL '7 days',NOW()-INTERVAL '18 hours'),
('20000000-0000-0000-0000-000000000008','10000000-0000-0000-0000-000000000008','تهران','تهران','منطقه ۱','محمودیه','محمودیه، خیابان مقدس اردبیلی، محدوده ب',1450,23,12,830,1382,3,3,'SOUTH','RESIDENTIAL','OLD',NULL,NULL,NULL,NULL,35.7910000,51.4060000,'1.0','{"demo":true,"deedType":"SIX_DANG","landUse":"RESIDENTIAL"}'::jsonb,NOW()-INTERVAL '72 days',NOW()-INTERVAL '9 days'),
('20000000-0000-0000-0000-000000000009','10000000-0000-0000-0000-000000000009','تهران','تهران','منطقه ۳','دروس','دروس، خیابان هدایت، محدوده یارمحمدی',1650,25,12,900,1387,3,3,'EAST','RESIDENTIAL','GOOD',NULL,NULL,NULL,NULL,35.7750000,51.4530000,'1.0','{"demo":true,"deedType":"SIX_DANG","landUse":"RESIDENTIAL"}'::jsonb,NOW()-INTERVAL '12 days',NOW()-INTERVAL '2 days'),
('20000000-0000-0000-0000-000000000010','10000000-0000-0000-0000-000000000010','تهران','تهران','منطقه ۳','پاسداران','پاسداران، خیابان گلستان، محدوده ضرابخانه',1900,29,14,1150,1391,4,4,'NORTH','RESIDENTIAL','GOOD',NULL,NULL,NULL,NULL,35.7800000,51.4610000,'1.0','{"demo":true,"deedType":"SIX_DANG","landUse":"RESIDENTIAL"}'::jsonb,NOW()-INTERVAL '300 days',NOW()-INTERVAL '20 days')
ON CONFLICT (case_id) DO NOTHING;

WITH demo_owner (
    id,
    property_id,
    first_name,
    last_name,
    national_id,
    mobile,
    ownership_numerator,
    ownership_denominator,
    created_at
) AS (
    VALUES
    ('30000000-0000-0000-0000-000000000001'::uuid,'20000000-0000-0000-0000-000000000001'::uuid,'حسین','رضایی',NULL,NULL,100,100,NOW()-INTERVAL '47 days'),
    ('30000000-0000-0000-0000-000000000002'::uuid,'20000000-0000-0000-0000-000000000002'::uuid,'مریم','شریفی',NULL,NULL,100,100,NOW()-INTERVAL '89 days'),
    ('30000000-0000-0000-0000-000000000003'::uuid,'20000000-0000-0000-0000-000000000003'::uuid,'رضا','اکبری',NULL,NULL,100,100,NOW()-INTERVAL '35 days'),
    ('30000000-0000-0000-0000-000000000004'::uuid,'20000000-0000-0000-0000-000000000004'::uuid,'سمیه','کاظمی',NULL,NULL,100,100,NOW()-INTERVAL '139 days'),
    ('30000000-0000-0000-0000-000000000005'::uuid,'20000000-0000-0000-0000-000000000005'::uuid,'فرهاد','نوری',NULL,NULL,100,100,NOW()-INTERVAL '17 days'),
    ('30000000-0000-0000-0000-000000000006'::uuid,'20000000-0000-0000-0000-000000000006'::uuid,'نگار','موسوی',NULL,NULL,100,100,NOW()-INTERVAL '23 days'),
    ('30000000-0000-0000-0000-000000000007'::uuid,'20000000-0000-0000-0000-000000000007'::uuid,'محسن','حیدری',NULL,NULL,100,100,NOW()-INTERVAL '6 days'),
    ('30000000-0000-0000-0000-000000000008'::uuid,'20000000-0000-0000-0000-000000000008'::uuid,'لیلا','مرادی',NULL,NULL,100,100,NOW()-INTERVAL '71 days'),
    ('30000000-0000-0000-0000-000000000009'::uuid,'20000000-0000-0000-0000-000000000009'::uuid,'امیر','قاسمی',NULL,NULL,100,100,NOW()-INTERVAL '11 days'),
    ('30000000-0000-0000-0000-000000000010'::uuid,'20000000-0000-0000-0000-000000000010'::uuid,'ندا','تهرانی',NULL,NULL,100,100,NOW()-INTERVAL '299 days')
)
INSERT INTO property_owner (
    id,
    property_id,
    first_name,
    last_name,
    national_id,
    mobile,
    ownership_numerator,
    ownership_denominator,
    is_primary_contact,
    created_at,
    updated_at
)
SELECT
    d.id,
    d.property_id,
    d.first_name,
    d.last_name,
    d.national_id,
    d.mobile,
    d.ownership_numerator,
    d.ownership_denominator,
    TRUE,
    d.created_at,
    NOW()
FROM demo_owner d
WHERE NOT EXISTS (
    SELECT 1
    FROM property_owner existing
    WHERE existing.property_id = d.property_id
)
ON CONFLICT (id) DO NOTHING;
