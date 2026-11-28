package com.sakhtyar.material.application;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class Phase18MaterialFoundationTest {
    private static BigDecimal d(String s){return new BigDecimal(s);}
    @Test void exactConversions(){
        assertEquals(0,d("1250").compareTo(MaterialFoundation.convert(d("1.25"),"TON","KG")));
        assertEquals(0,d("0.0001").compareTo(MaterialFoundation.convert(d("1"),"CM2","M2")));
        assertEquals(0,d("1000").compareTo(MaterialFoundation.convert(d("1"),"M3","L")));
    }
    @Test void incompatibleAndUnknownUnitsFail(){
        assertThrows(IllegalArgumentException.class,()->MaterialFoundation.convert(d("1"),"BAG","KG"));
        assertThrows(IllegalArgumentException.class,()->MaterialFoundation.convert(d("1"),"M2","M"));
        assertThrows(IllegalArgumentException.class,()->MaterialFoundation.unitCode("UNKNOWN"));
        assertThrows(IllegalArgumentException.class,()->MaterialFoundation.convert(d("0"),"KG","G"));
    }
    @Test void baseUnitCannotBeChangedEvenForLegacyCode(){
        assertEquals("legacy",MaterialFoundation.unchangedUnit("legacy"," LEGACY "));
        assertThrows(IllegalArgumentException.class,()->MaterialFoundation.unchangedUnit("KG","TON"));
    }
    @Test void precisionMatchesNumericColumns(){
        assertEquals(d("1.20"),MaterialFoundation.amount(d("1.2000")));
        assertEquals(d("0.000001"),MaterialFoundation.quantity(d("0.000001")));
        assertThrows(IllegalArgumentException.class,()->MaterialFoundation.amount(d("0.001")));
        assertThrows(IllegalArgumentException.class,()->MaterialFoundation.amount(d("1000000000000000000")));
        assertThrows(IllegalArgumentException.class,()->MaterialFoundation.amount(d("0")));
        assertThrows(IllegalArgumentException.class,()->MaterialFoundation.quantity(d("0.0000001")));
        assertThrows(IllegalArgumentException.class,()->MaterialFoundation.confidence(d("1.0001")));
        assertThrows(IllegalArgumentException.class,()->MaterialFoundation.confidence(d("0.12345")));
    }
    @Test void catalogAndPersianNormalization(){
        assertEquals("STEEL-01",MaterialFoundation.code(" steel-01 "));
        assertEquals("TOMAN",MaterialFoundation.currencyCode(" toman "));
        assertThrows(IllegalArgumentException.class,()->MaterialFoundation.currencyCode("??!"));
        assertEquals("میلگرد",MaterialFoundation.search("ميل‌گرد"));
        assertEquals("ایران",MaterialFoundation.required("ايران",400));
        assertEquals("IR",MaterialFoundation.country("ir"));
        assertThrows(IllegalArgumentException.class,()->MaterialFoundation.country("ZZ"));
    }
}
