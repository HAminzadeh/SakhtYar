package com.sakhtyar.globalization;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class GlobalScenarioContractTest {
    @Test void commonGlobalCurrenciesExistInJdkCatalog(){
        for(String code:new String[]{"USD","EUR","GBP","CAD","AUD","AED","IRR"}){
            assertNotNull(Currency.getInstance(code));
        }
    }
    @Test void tomanIsIntentionallyNonIso(){
        assertThrows(IllegalArgumentException.class,()->Currency.getInstance("TOMAN"));
    }
    @Test void iranAndCanadaUseDifferentIsoCountryCodes(){
        assertNotEquals("IR","CA");
    }
}