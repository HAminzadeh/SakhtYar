package com.sakhtyar.assembly.application;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase19ADefinitionPolicyTest {
    @Test void validatesWasteStorageAndRange(){
        assertEquals(new BigDecimal("0.100000"),AssemblyDefinitionPolicy.waste(new BigDecimal("0.1")));
        assertEquals(new BigDecimal("10.000000"),AssemblyDefinitionPolicy.waste(BigDecimal.TEN));
        for (String s:List.of("-0.1","10.000001","0.0000001","1E100"))
            assertThrows(IllegalArgumentException.class,()->AssemblyDefinitionPolicy.waste(new BigDecimal(s)));
        assertThrows(IllegalArgumentException.class,()->AssemblyDefinitionPolicy.waste(null));
    }
    @Test void protectsOutputUnitEvenForLegacyCode(){
        assertEquals("M2",AssemblyDefinitionPolicy.unchangedOutputUnit("M2"," m2 "));
        assertEquals("LEGACY",AssemblyDefinitionPolicy.unchangedOutputUnit("LEGACY","legacy"));
        assertThrows(IllegalArgumentException.class,()->AssemblyDefinitionPolicy.unchangedOutputUnit("M2","M3"));
    }
    @Test void multilineTextAndControlValidation(){
        assertEquals("شرح\nعملیات",AssemblyDefinitionPolicy.optionalText(" شرح\nعملیات ",100));
        assertNull(AssemblyDefinitionPolicy.optionalText(" ",100));
        assertThrows(IllegalArgumentException.class,()->AssemblyDefinitionPolicy.optionalText("a\0b",100));
        assertThrows(IllegalArgumentException.class,()->AssemblyDefinitionPolicy.optionalText("123",2));
    }
    @Test void metadataLimitsAndJsonShape(){
        assertEquals(Map.of(),AssemblyDefinitionPolicy.metadata(null));
        assertEquals(Map.of("x",List.of("y",1)),AssemblyDefinitionPolicy.metadata(Map.of("x",List.of("y",1))));
        var invalid=new HashMap<String,Object>();invalid.put("x",null);
        assertThrows(IllegalArgumentException.class,()->AssemblyDefinitionPolicy.metadata(invalid));
        assertThrows(IllegalArgumentException.class,()->AssemblyDefinitionPolicy.metadata(Map.of("x","a".repeat(65537))));
        assertThrows(IllegalArgumentException.class,()->AssemblyDefinitionPolicy.metadata(Map.of("x",Double.NaN)));
        var cycle=new HashMap<String,Object>();cycle.put("x",cycle);
        assertThrows(IllegalArgumentException.class,()->AssemblyDefinitionPolicy.metadata(cycle));
    }
}
