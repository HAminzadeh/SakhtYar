package com.sakhtyar.scenario.domain;
import static org.junit.jupiter.api.Assertions.assertEquals; import org.junit.jupiter.api.Test;
class ScenarioEnumsTest {
 @Test void qualityLevels(){assertEquals(QualityLevel.STANDARD,QualityLevel.valueOf("STANDARD"));assertEquals(QualityLevel.LUXURY,QualityLevel.valueOf("LUXURY"));}
 @Test void scenarioStates(){assertEquals(ScenarioStatus.DRAFT,ScenarioStatus.valueOf("DRAFT"));assertEquals(ScenarioStatus.READY,ScenarioStatus.valueOf("READY"));}
}