-- SAKHTYAR_PERSIAN_ENCODING_REPAIR_V38
-- Repairs deterministic seed values that may have been persisted as mojibake by earlier tooling.
-- Never touches user-entered free text.
UPDATE global_language SET name_native='فارسی', direction='RTL' WHERE code='fa';
UPDATE global_currency SET name_en='Iranian Toman', symbol='تومان', minor_unit=0, iso4217=FALSE, active=TRUE WHERE code='TOMAN';
UPDATE global_currency SET name_en='Iranian Rial', symbol='﷼', minor_unit=0, iso4217=TRUE, active=TRUE WHERE code='IRR';
UPDATE global_currency SET symbol='د.إ' WHERE code='AED';