package com.sakhtyar.quality;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in integration test: does NOT use an admin connection, SET ROLE, or mutate DB rows. */
class Phase02RestrictedIsolationProbeIT {
    @Test void independentRestrictedLoginAndGucSpoofing() throws Exception {
        String url=System.getenv("PHASE02_RESTRICTED_JDBC_URL");
        String username=System.getenv("PHASE02_RESTRICTED_USERNAME");
        String password=System.getenv("PHASE02_RESTRICTED_PASSWORD");
        String own=System.getenv("PHASE02_WORKSPACE_ID");
        String other=System.getenv("PHASE02_OTHER_WORKSPACE_ID");
        String evidence=System.getenv("PHASE02_OTHER_HAS_TEST_FIXTURES");
        Assumptions.assumeTrue(url!=null && username!=null && password!=null && own!=null &&
            other!=null && "1".equals(System.getenv("PHASE02_ENABLE_RESTRICTED_PROBE")),
            "Opt-in restricted physical PostgreSQL credentials not provided");
        assertTrue(url.startsWith("jdbc:postgresql:"));
        Properties props=new Properties();props.put("user",username);props.put("password",password);
        try(Connection c=DriverManager.getConnection(url,props)) {
            Phase02RestrictedIsolationProbe.Observation o=Phase02RestrictedIsolationProbe.inspect(
                c,UUID.fromString(own),UUID.fromString(other),"1".equals(evidence));
            assertTrue(o.restrictedRole());
            assertTrue(o.workspacePolicyVerified());
            assertTrue(o.otherCounts().values().stream().allMatch(n->n==0));
            assertTrue(o.realExposures().values().stream().allMatch(n->n==0));
            assertNotEquals("ISOLATION_VIOLATION",o.state());
        }
    }
}
