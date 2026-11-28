package com.sakhtyar.assembly.application;

import com.sakhtyar.assembly.api.AssemblyController;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Mapping and validation only; real security filter chain needs runtime acceptance. */
class Phase19BControllerTest {
    @Test void acceptsUuidIdempotencyHeaderAndRejectsMalformedKey() throws Exception {
        var svc=mock(AssemblyService.class); var mvc=MockMvcBuilders.standaloneSetup(new AssemblyController(svc)).build();
        UUID id=UUID.randomUUID(),key=UUID.randomUUID();
        var path="/api/v1/assemblies/"+id+"/calculate";
        var json="""
            {"quantity":5,"priceType":"RETAIL","currencyCode":"TOMAN","province":"تهران","city":"تهران"}
            """;
        mvc.perform(post(path).header("Idempotency-Key",key.toString()).contentType("application/json").content(json)).andExpect(status().isOk());
        verify(svc).calculate(eq(id),any(),isNull(),eq(key));
        mvc.perform(post(path).header("Idempotency-Key","bad-key").contentType("application/json").content(json)).andExpect(status().isBadRequest());
        verifyNoMoreInteractions(svc);
    }
    @Test void bindsRecalculateCompareAndLineageRoutes() throws Exception {
        var svc=mock(AssemblyService.class); var mvc=MockMvcBuilders.standaloneSetup(new AssemblyController(svc)).build();
        UUID id=UUID.randomUUID(),other=UUID.randomUUID(),key=UUID.randomUUID();String path="/api/v1/assemblies/estimates/"+id;
        mvc.perform(post(path+"/recalculate").header("Idempotency-Key",key.toString())).andExpect(status().isOk());
        verify(svc).recalculate(eq(id),isNull(),eq(key));
        mvc.perform(get(path+"/compare").param("otherId",other.toString())).andExpect(status().isOk());verify(svc).compareEstimates(id,other);
        mvc.perform(get(path+"/lineage")).andExpect(status().isOk());verify(svc).calculationLineage(id);
    }
}
