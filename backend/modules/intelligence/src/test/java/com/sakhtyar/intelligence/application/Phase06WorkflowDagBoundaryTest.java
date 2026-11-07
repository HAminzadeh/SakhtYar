package com.sakhtyar.intelligence.application;

import com.sakhtyar.intelligence.domain.WorkflowDefinition;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase06WorkflowDagBoundaryTest {
    private final WorkflowDagValidator validator=new WorkflowDagValidator();
    private WorkflowDefinition one(List<String> deps){
        return new WorkflowDefinition("w","1",List.of(new WorkflowDefinition.Step("first","safe.component",deps)));
    }
    @Test void validatesSingleStep(){assertDoesNotThrow(()->validator.validate(one(List.of())));}
    @Test void rejectsMissingDependency(){assertThrows(IllegalArgumentException.class,()->validator.validate(one(List.of("missing"))));}
    @Test void rejectsCycle(){assertThrows(IllegalArgumentException.class,()->validator.validate(one(List.of("first"))));}
    @Test void rejectsNullDependency(){assertThrows(IllegalArgumentException.class,()->validator.validate(one(null)));}
    @Test void rejectsEmpty(){assertThrows(IllegalArgumentException.class,()->validator.validate(new WorkflowDefinition("w","1",List.of())));}
    @Test void rejectsUnboundedWorkflows(){
        var list=IntStream.range(0,33).mapToObj(i->new WorkflowDefinition.Step("step"+i,"safe.component",List.of())).toList();
        assertThrows(IllegalArgumentException.class,()->validator.validate(new WorkflowDefinition("w","1",list)));
    }
}
