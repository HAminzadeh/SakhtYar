package com.sakhtyar.quality;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase08SafetyAdmissionTest {
    @Test void defaultAndExistingPhase02ReadinessBlockBeforeWrites() {
        UUID workspace=UUID.randomUUID();
        var denied=Phase08SafetyAdmission.assess(workspace,Map.of(
            "coreFixturePermitted",false,"physicalLoginIsolationEvidence","NOT_RUN",
            "runtimeDbRoleNonBypass",false,"fullApplicationIsolationVerified",false),
            Map.of("db_current_user","postgres","db_session_user","postgres","row_security","on"),
            false,"UNVERIFIED");
        assertFalse(denied.authorized());
        assertTrue(denied.blockers().contains("PHYSICAL_RESTRICTED_TEST_LOGIN_NOT_VERIFIED"));
        assertTrue(denied.blockers().contains("FULL_APPLICATION_SCOPE_NOT_VERIFIED"));
        assertEquals(false,denied.report().get("databaseWritesExecuted"));
    }
    @Test void expectedLoginMustMatchSessionUserAndWorkspace() {
        UUID workspace=UUID.randomUUID();
        String exact="sakhtyar_tws_"+workspace.toString().replace("-","");
        var readiness=completeReadiness();
        var wrongSession=Phase08SafetyAdmission.assess(workspace,readiness,
            Map.of("db_current_user",exact,"db_session_user","postgres","row_security","on"),
            false,"a".repeat(40));
        assertFalse(wrongSession.authorized());
        assertTrue(wrongSession.blockers().contains("PHYSICAL_RESTRICTED_TEST_LOGIN_NOT_VERIFIED"));
        var wrongWorkspace=Phase08SafetyAdmission.assess(UUID.randomUUID(),readiness,
            Map.of("db_current_user",exact,"db_session_user",exact,"row_security","on"),
            false,"a".repeat(40));
        assertFalse(wrongWorkspace.authorized());
    }
    @Test void evenPerfectSyntheticProofRejectsDevSeedOrUnknownSha() {
        UUID workspace=UUID.randomUUID();
        String exact="sakhtyar_tws_"+workspace.toString().replace("-","");
        var identity=Map.<String,Object>of("db_current_user",exact,"db_session_user",exact,"row_security","on");
        assertTrue(Phase08SafetyAdmission.assess(workspace,completeReadiness(),identity,true,
            "a".repeat(40)).blockers().contains("DEVELOPMENT_SEED_MUST_BE_DISABLED"));
        assertTrue(Phase08SafetyAdmission.assess(workspace,completeReadiness(),identity,false,
            "UNVERIFIED").blockers().contains("EMBEDDED_SOURCE_COMMIT_UNVERIFIED"));
        var fullySynthetic=Phase08SafetyAdmission.assess(workspace,completeReadiness(),identity,false,"a".repeat(40));
        assertTrue(fullySynthetic.authorized());
        assertEquals("PROOF_REVIEW_REQUIRED",fullySynthetic.report().get("state"));
    }
    private static Map<String,Object> completeReadiness() {
        var r=new HashMap<String,Object>();
        for(String key:new String[]{"coreFixturePermitted","runtimeDbRoleNonBypass",
                "fullApplicationIsolationVerified","externalSideEffectsGloballyGuarded",
                "businessE2eCatalogEnabled","testDataCleanupScoped","verifiedEvidenceStoreReady"}) r.put(key,true);
        r.put("physicalLoginIsolationEvidence","CORE_READ_PROBE_OBSERVED");
        return r;
    }
}
