package com.sakhtyar.analysis.application;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SnapshotHasherTest {
    private final SnapshotHasher hasher=new SnapshotHasher(new ObjectMapper());

    @Test
    void sameContentWithDifferentMapInsertionOrderProducesSameHash(){
        Map<String,Object> a=new LinkedHashMap<>();
        a.put("z",1); a.put("a",2);

        Map<String,Object> b=new LinkedHashMap<>();
        b.put("a",2); b.put("z",1);

        assertEquals(hasher.sha256(a,Map.of(),Map.of()),hasher.sha256(b,Map.of(),Map.of()));
    }

    @Test
    void changedResultChangesHash(){
        String first=hasher.sha256(Map.of("x",1),Map.of("profit",10),Map.of());
        String second=hasher.sha256(Map.of("x",1),Map.of("profit",11),Map.of());
        assertNotEquals(first,second);
    }

    @Test
    void sha256IsLowercaseHex(){
        String hash=hasher.sha256(Map.of(),Map.of(),Map.of());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }
}