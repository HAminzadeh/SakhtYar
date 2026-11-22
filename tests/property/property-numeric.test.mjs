import test from 'node:test';
import assert from 'node:assert/strict';
import {propertyNumber, propertyInteger, invalidPropertyNumbers} from '../../frontend/src/domain/propertyNumeric.ts';

test('Persian and Arabic decimal input', () => {
  assert.equal(propertyNumber('۱٬۲۳۴٫۵۰'), 1234.5);
  assert.equal(propertyNumber('١٢٫٧٥'), 12.75);
  assert.equal(propertyNumber('35.72'), 35.72);
});
test('reject malformed numeric values rather than serializing NaN to null', () => {
  assert.ok(Number.isNaN(propertyNumber('abc')));
  assert.ok(Number.isNaN(propertyNumber('1,234')));
  assert.ok(Number.isNaN(propertyNumber('۱۲٬۳۴')));
  assert.ok(Number.isNaN(propertyInteger('12.5')));
  assert.equal(propertyNumber('  '), null);
});
const base = {landAreaM2:'۱۰۰٫۵', frontageM:'۱۰',passageWidthM:'۱۲',buildingAreaM2:'۱۳۰',constructionYear:'۱۴۰۰',existingFloors:'۳',existingUnits:'۲',latitude:'۳۵٫۷',longitude:'۵۱٫۴'};
test('form rejects incomplete coordinates and invalid integers', () => {
  assert.equal(invalidPropertyNumbers(base), false);
  assert.equal(invalidPropertyNumbers({...base, longitude:''}), true);
  assert.equal(invalidPropertyNumbers({...base, existingFloors:'۲٫۵'}), true);
  assert.equal(invalidPropertyNumbers({...base, constructionYear:'۲۶۰۰'}), true);
  assert.equal(invalidPropertyNumbers({...base, latitude:'-۹۱'}), true);
  assert.equal(invalidPropertyNumbers({...base, landAreaM2:'-۲'}), true);
});
