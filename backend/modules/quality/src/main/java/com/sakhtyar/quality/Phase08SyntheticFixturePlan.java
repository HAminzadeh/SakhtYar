package com.sakhtyar.quality;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** Immutable synthetic metadata only; no SQL, credentials, global IDs or real-case lookup. */
public final class Phase08SyntheticFixturePlan {
    private Phase08SyntheticFixturePlan() {}
    public record Blueprint(UUID workspaceId, UUID runId, UUID testCaseId, UUID testPropertyId,
            UUID ownerAId, UUID ownerBId, UUID nonMemberId, int landAreaSquareMetres,
            int ownerASharePercent, int ownerBSharePercent, boolean databaseWritesAuthorized) {
        public Map<String,Object> evidence() {
            return Map.of("workspaceId",workspaceId.toString(),"runId",runId.toString(),
                "syntheticFixtureOnly",true,"landAreaSquareMetres",landAreaSquareMetres,
                "ownershipSumPercent",ownerASharePercent+ownerBSharePercent,
                "databaseWritesAuthorized",databaseWritesAuthorized,
                "businessWorkflowExecuted",false);
        }
    }
    private static UUID key(UUID workspace, UUID run, String purpose) {
        return UUID.nameUUIDFromBytes(("sakhtyar:phase08:synthetic:v1:"+workspace+":"+run+":"+purpose)
            .getBytes(StandardCharsets.UTF_8));
    }
    public static Blueprint prepare(UUID workspace, UUID run) {
        if(workspace==null || run==null) throw new IllegalArgumentException("Workspace and run required");
        return new Blueprint(workspace,run,key(workspace,run,"case"),key(workspace,run,"property"),
            key(workspace,run,"owner60"),key(workspace,run,"owner40"),key(workspace,run,"nonmember"),
            300,60,40,false);
    }
}
