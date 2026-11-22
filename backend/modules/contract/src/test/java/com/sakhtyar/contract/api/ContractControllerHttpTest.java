package com.sakhtyar.contract.api;

import com.sakhtyar.contract.application.ContractService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Standalone HTTP routing checks. Spring Security filter integration needs an isolated context. */
class ContractControllerHttpTest {
    @Test void versionRouteCallsService() throws Exception {
        var service=mock(ContractService.class);
        UUID id=UUID.randomUUID();
        when(service.versions(id)).thenReturn(List.of());
        var mvc=MockMvcBuilders.standaloneSetup(new ContractController(service)).build();
        mvc.perform(get("/api/v1/contracts/{id}/versions",id))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(service).versions(id);
    }
    @Test void updatedContractPropagatesIfMatchHeader() throws Exception {
        var service=mock(ContractService.class);
        UUID id=UUID.randomUUID();
        var mvc=MockMvcBuilders.standaloneSetup(new ContractController(service)).build();
        String body="""
            {"contractCode":"CT-1","contractType":"PARTICIPATION_IN_CONSTRUCTION",
             "status":"DRAFT","title":"Synthetic draft","schemaVersion":"1.0","terms":{},"metadata":{}}
            """;
        mvc.perform(put("/api/v1/contracts/{id}",id).header("If-Match","\"2\"")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        verify(service).update(eq(id),any(ContractDtos.UpsertContractRequest.class),eq("\"2\""));
    }
}
