package com.sakhtyar.quality;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class QualityCatalogTest {
    @Test void catalogHasUniqueDescribedScenarios() throws Exception {
        var catalog=new QualityCatalog(new ObjectMapper());
        assertTrue(catalog.all().size()>=100);
        assertTrue(catalog.all().stream().allMatch(d->!d.description().isBlank() && !d.expected().isBlank()));
        assertNotNull(catalog.require("INF-001"));
        assertNotNull(catalog.require("KNW-002"));
        assertThrows(IllegalArgumentException.class,()->catalog.require("UNTRUSTED_SHELL"));
    }
}
