import com.sakhtyar.quality.phase3.Phase3OfflineContractEngine;
import com.sakhtyar.quality.phase3.Phase3ScenarioSpecification;

public final class Phase3OfflineSmoke {
    public static void main(String[] args) {
        for(int n=21;n<=24;n++) {
            String id="E2E-"+n;
            if(!Phase3OfflineContractEngine.exercise(id).passed()) throw new AssertionError(id+" offline contract failed");
            if(Phase3ScenarioSpecification.get(id).requiredLiveEvidence().isBlank()) throw new AssertionError(id);
            System.out.println("PASS OFFLINE: "+id+"; BUSINESS: BLOCKED");
        }
    }
}
