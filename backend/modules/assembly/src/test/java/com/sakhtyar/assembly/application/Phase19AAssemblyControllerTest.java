package com.sakhtyar.assembly.application;

import com.sakhtyar.assembly.api.AssemblyController;
import com.sakhtyar.assembly.api.AssemblyDefinitionController;
import com.sakhtyar.assembly.application.AssemblyDefinitionService.Definition;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** MVC mapping/validation only. Security filter-chain acceptance is a separate runtime gate. */
class Phase19AAssemblyControllerTest {
    @Test void acceptsComponentPutAndRejectsInvalidQuantity() throws Exception {
        AssemblyService service=mock(AssemblyService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new AssemblyController(service)).build();
        UUID a=UUID.randomUUID(), c=UUID.randomUUID(), m=UUID.randomUUID();
        String path="/api/v1/assemblies/"+a+"/components/"+c;
        String json="""
            {"materialItemId":"%s","quantity":2,"wasteFactor":0.1,"unitCode":"KG","priceBasis":"MEDIAN","sortOrder":0}
            """.formatted(m);
        mvc.perform(put(path).contentType("application/json").content(json)).andExpect(status().isOk());
        verify(service).updateComponent(eq(a),eq(c),any());
        mvc.perform(put(path).contentType("application/json").content(json.replace("\"quantity\":2","\"quantity\":0")))
            .andExpect(status().isBadRequest());
        verifyNoMoreInteractions(service);
    }
    @Test void exposesExplicitLegacyDefinitionUnavailable() throws Exception {
        AssemblyDefinitionService service=mock(AssemblyDefinitionService.class);
        UUID id=UUID.randomUUID();when(service.estimateDefinition(id)).thenReturn(new Definition(null,null,true,false));
        var mvc=MockMvcBuilders.standaloneSetup(new AssemblyDefinitionController(service)).build();
        mvc.perform(get("/api/v1/assemblies/estimates/"+id+"/definition"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        verify(service).estimateDefinition(id);
    }
}
