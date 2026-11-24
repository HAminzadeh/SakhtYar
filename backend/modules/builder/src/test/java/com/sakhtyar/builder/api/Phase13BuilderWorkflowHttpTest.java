package com.sakhtyar.builder.api;
import com.sakhtyar.builder.application.BuilderEngagementService;
import com.sakhtyar.builder.application.BuilderService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class Phase13BuilderWorkflowHttpTest {
 @Test void inboxDelegatesToBoundBuilderWorkflow() throws Exception {
   var service=mock(BuilderService.class);var flow=mock(BuilderEngagementService.class);
   when(flow.inbox()).thenReturn(List.of());
   var mvc=MockMvcBuilders.standaloneSetup(new BuilderWorkflowController(service,flow)).build();
   mvc.perform(get("/api/v1/builder-workflow/inbox").accept(MediaType.APPLICATION_JSON))
      .andExpect(status().isOk()).andExpect(content().json("[]"));
   verify(flow).inbox();
 }
 @Test void invalidInvitationPayloadRejectedByValidation() throws Exception {
   var service=mock(BuilderService.class);var flow=mock(BuilderEngagementService.class);
   UUID id=UUID.randomUUID();
   var mvc=MockMvcBuilders.standaloneSetup(new BuilderWorkflowController(service,flow)).build();
   mvc.perform(post("/api/v1/builder-workflow/cases/"+id+"/invitations")
     .contentType(MediaType.APPLICATION_JSON).content("{\"validDays\":0}"))
     .andExpect(status().isBadRequest());
   verifyNoInteractions(flow);
 }
}
