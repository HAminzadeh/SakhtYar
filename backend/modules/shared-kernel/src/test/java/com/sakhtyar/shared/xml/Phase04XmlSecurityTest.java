package com.sakhtyar.shared.xml;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class Phase04XmlSecurityTest {
    private static final String XSD = "/contracts/urban-query-v1.xsd";

    @Test void acceptsValidPersianAndUtcNeutralXml() {
        String xml = """
                <UrbanQuery xmlns="https://sakhtyar.ir/contracts/urban/v1" version="1.0">
                  <Intent>REGULATION_SEARCH</Intent><Language>fa-IR</Language>
                  <Property><LandAreaM2>123.450</LandAreaM2><ZoneCode>منطقه ۱۲</ZoneCode></Property>
                </UrbanQuery>
                """;
        assertDoesNotThrow(() -> XmlContractValidator.validate(xml, XSD));
    }

    @Test void rejectsExternalEntityAndNeverReturnsContents() {
        String xml = """
                <!DOCTYPE UrbanQuery [<!ENTITY xxefile SYSTEM "file:///etc/passwd">]>
                <UrbanQuery xmlns="https://sakhtyar.ir/contracts/urban/v1" version="1.0">
                  <Intent>REGULATION_SEARCH</Intent><Language>fa-IR</Language>
                  <Property><ZoneCode>&xxefile;</ZoneCode></Property>
                </UrbanQuery>
                """;
        XmlContractException ex = assertThrows(XmlContractException.class,
                () -> XmlContractValidator.validate(xml, XSD));
        assertFalse(ex.getMessage().contains("/etc/passwd"));
        assertFalse(ex.getMessage().contains("SYSTEM"));
    }

    @Test void rejectsOversizedXmlWithoutParsing() {
        assertThrows(XmlContractException.class,
                () -> XmlContractValidator.validate("x".repeat(1_048_577), XSD));
    }

    @Test void rejectsMissingSchemaAndMissingPayload() {
        assertThrows(XmlContractException.class, () -> XmlContractValidator.validate(" ", XSD));
        assertThrows(XmlContractException.class,
                () -> XmlContractValidator.validate("<not-urban/>", "/contracts/not-a-file.xsd"));
    }

    @Test void failsClosedOnInvalidXmlWithoutEchoingRawPayload() {
        XmlContractException ex = assertThrows(XmlContractException.class,
                () -> XmlContractValidator.validate("<root>SECRET_TOKEN_MARKER</root>", XSD));
        assertEquals("XML contract validation failed.", ex.getMessage());
    }
}
