import com.sakhtyar.quality.phase2.Phase2OfflineContracts;
import com.sakhtyar.quality.phase2.Phase2ScenarioSpecification;
import java.util.Map;
public final class Phase2ContractsSmoke {
    public static void main(String[] args) {
        int passed=0;
        if(Phase2ScenarioSpecification.all().size()!=13) throw new AssertionError("Missing scenarios");
        if(Phase2ScenarioSpecification.acceptanceEvidenceComplete(Map.of())) throw new AssertionError("Empty evidence accepted");
        for(int i=7;i<=20;i++) {
            if(i==14)continue;
            String id=String.format("E2E-%02d",i);
            var def=Phase2ScenarioSpecification.get(id);
            if(def.requiredEvidence().size()!=7) throw new AssertionError("Insufficient required evidence: "+id);
            var result=Phase2OfflineContracts.exercise(id);
            if(!result.passed()) throw new AssertionError(id+" offline contract failed: "+result.observations());
            if(!Boolean.FALSE.equals(result.observations().get("businessWorkflowExecuted")))
                throw new AssertionError("Offline contract must not claim business coverage: "+id);
            passed++;
        }
        if(passed!=13)throw new AssertionError("Expected 13 contracts");
        System.out.println("PASS: 13 deterministic offline domain contracts; 0 BUSINESS E2E ACCEPTED");
    }
}
