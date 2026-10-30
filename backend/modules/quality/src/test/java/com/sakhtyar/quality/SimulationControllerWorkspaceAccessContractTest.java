package com.sakhtyar.quality;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SimulationControllerWorkspaceAccessContractTest {
  private String controller() throws Exception {
    Path path=Path.of("src/main/java/com/sakhtyar/quality/SimulationController.java");
    if (!Files.exists(path)) path=Path.of("backend/src/main/java/com/sakhtyar/quality/SimulationController.java");
    if (!Files.exists(path)) path=Path.of("backend/modules/quality/src/main/java/com/sakhtyar/quality/SimulationController.java");
    if (!Files.exists(path)) path=Path.of("modules/quality/src/main/java/com/sakhtyar/quality/SimulationController.java");
    return Files.readString(path,StandardCharsets.UTF_8);
  }
  @Test void injectsServerDerivedWorkspaceAuthorization() throws Exception {
    assertTrue(controller().contains("SimulationWorkspaceAuthorizationService workspaceAuthorization"));
  }
  @Test void checksOwnershipOnWorkspaceEndpoints() throws Exception {
    String s=controller();
    assertTrue(s.contains("workspaceAuthorization.requireActiveOwner(workspaceId, a);"));
    assertTrue(s.contains("workspaceAuthorization.requireActiveOwner(req.workspaceId(), a);"));
  }
  @Test void checksRunOwnershipBeforeResults() throws Exception {
    String s=controller();
    assertTrue(s.contains("workspaceAuthorization.requireActiveOwner((UUID) result.get(\"workspace_id\"), a);"));
  }
  @Test void scopesListsToAuthenticatedUsername() throws Exception {
    String s=controller();
    assertTrue(s.contains("lower(owner_username)=lower(?)"));
    assertTrue(s.contains("lower(w.owner_username)=lower(?)"));
  }
}