INSERT INTO global_language(id,code,bcp47_tag,name_en,name_native,direction,active,system_default)
VALUES
('10000000-0000-0000-0000-000000001001','fa','fa-IR','Persian','فارسی','RTL',TRUE,TRUE),
('10000000-0000-0000-0000-000000001002','en','en-US','English','English','LTR',TRUE,FALSE)
ON CONFLICT(code) DO NOTHING;

INSERT INTO global_currency(id,code,numeric_code,name_en,symbol,minor_unit,iso4217,active)
VALUES
('20000000-0000-0000-0000-000000000001','IRR','364','Iranian Rial','﷼',0,TRUE,TRUE),
('20000000-0000-0000-0000-000000000002','TOMAN',NULL,'Iranian Toman','تومان',0,FALSE,TRUE),
('20000000-0000-0000-0000-000000000003','USD','840','US Dollar','$',2,TRUE,TRUE),
('20000000-0000-0000-0000-000000000004','EUR','978','Euro','€',2,TRUE,TRUE),
('20000000-0000-0000-0000-000000000005','GBP','826','Pound Sterling','£',2,TRUE,TRUE),
('20000000-0000-0000-0000-000000000006','CAD','124','Canadian Dollar','CA$',2,TRUE,TRUE),
('20000000-0000-0000-0000-000000000007','AUD','036','Australian Dollar','A$',2,TRUE,TRUE),
('20000000-0000-0000-0000-000000000008','AED','784','UAE Dirham','د.إ',2,TRUE,TRUE)
ON CONFLICT(code) DO NOTHING;

INSERT INTO global_fx_rate(
 id,base_currency_id,quote_currency_id,rate,source_label,observed_at,review_status,created_at
)
VALUES
(
 '21000000-0000-0000-0000-000000000001',
 (SELECT id FROM global_currency WHERE code='TOMAN'),
 (SELECT id FROM global_currency WHERE code='IRR'),
 10,
 'Deterministic Iranian unit relation',
 TIMESTAMPTZ '2000-01-01 00:00:00+00',
 'APPROVED',
 NOW()
)
ON CONFLICT DO NOTHING;