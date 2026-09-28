package com.sakhtyar.shared.xml;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class XmlContractValidatorTest {

    private static final String XSD = "/contracts/urban-query-v1.xsd";

    @Test
    void acceptsValidUrbanQuery() {
        String xml = """
                <UrbanQuery xmlns="https://sakhtyar.ir/contracts/urban/v1" version="1.0">
                  <Intent>FEASIBILITY_ESTIMATE</Intent>
                  <Language>fa-IR</Language>
                  <Property>
                    <LandAreaM2>450</LandAreaM2>
                    <FrontageM>12</FrontageM>
                    <ZoneCode>R122</ZoneCode>
                  </Property>
                  <RequestedOutputs><Output>MAX_FLOORS</Output></RequestedOutputs>
                  <Confidence>0.96</Confidence>
                </UrbanQuery>
                """;
        assertDoesNotThrow(() -> XmlContractValidator.validate(xml, XSD));
    }

    @Test
    void rejectsUnknownIntent() {
        String xml = """
                <UrbanQuery xmlns="https://sakhtyar.ir/contracts/urban/v1" version="1.0">
                  <Intent>GUESS_SOMETHING</Intent>
                  <Language>fa-IR</Language>
                </UrbanQuery>
                """;
        assertThrows(XmlContractException.class,
                () -> XmlContractValidator.validate(xml, XSD));
    }
}
