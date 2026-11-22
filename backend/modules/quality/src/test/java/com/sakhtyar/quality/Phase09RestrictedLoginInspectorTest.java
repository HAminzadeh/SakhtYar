package com.sakhtyar.quality;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase09RestrictedLoginInspectorTest {
    @Test void defaultNeverUsesApplicationDatasource() {
        var probe=new Phase09RestrictedLoginInspector("","","");
        var result=probe.inspect(UUID.randomUUID());
        assertEquals("BLOCKED",result.get("state"));
        assertEquals("DEDICATED_TEST_PROBE_CREDENTIALS_NOT_CONFIGURED",result.get("blocker"));
        assertEquals(false,result.get("physicalRestrictedLogin"));
    }
    @Test void credentialsCannotBeReusedAcrossWorkspacesOrEmbeddedInJdbcUrl() {
        UUID workspace=UUID.randomUUID();
        var wrong=new Phase09RestrictedLoginInspector("jdbc:postgresql://localhost:5432/sakhtyar",
            "sakhtyar_tws_"+UUID.randomUUID().toString().replace("-",""),"not-printed");
        assertEquals("UNSAFE_PROBE_ENDPOINT_OR_ROLE_MISMATCH",wrong.inspect(workspace).get("blocker"));
        var unsafe=new Phase09RestrictedLoginInspector("jdbc:postgresql://localhost/db?password=plaintext",
            "sakhtyar_tws_"+workspace.toString().replace("-",""),"not-printed");
        assertEquals("UNSAFE_PROBE_ENDPOINT_OR_ROLE_MISMATCH",unsafe.inspect(workspace).get("blocker"));
    }
}
