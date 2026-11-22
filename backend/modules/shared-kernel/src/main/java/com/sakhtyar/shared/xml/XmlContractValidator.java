package com.sakhtyar.shared.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.xml.sax.SAXException;

public final class XmlContractValidator {

    // Bound the XML text before any parser is instantiated. Exceeding this limit fails closed.
    private static final int MAX_XML_CHARACTERS = 1_048_576;

    private XmlContractValidator() {
    }

    public static void validate(String xml, String classpathXsd) {
        if (xml == null || xml.isBlank()) {
            throw new XmlContractException("XML payload is required.");
        }
        if (xml.length() > MAX_XML_CHARACTERS) {
            throw new XmlContractException("XML payload exceeds the configured limit.");
        }
        // A contract payload never needs a DTD or entity declarations. Reject before parsing.
        if (xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY")) {
            throw new XmlContractException("DTD and entity declarations are prohibited.");
        }
        if (classpathXsd == null || classpathXsd.isBlank()) {
            throw new XmlContractException("XSD path is required.");
        }

        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            // Do not ignore an unsupported XML security property: security must be provable.
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (SAXException e) {
            throw new XmlContractException("XML security configuration is unsupported.", e);
        }

        try (InputStream xsd = XmlContractValidator.class.getResourceAsStream(classpathXsd)) {
            if (xsd == null) {
                throw new XmlContractException("XSD not found: " + classpathXsd);
            }
            Schema schema = factory.newSchema(new StreamSource(xsd));
            var validator = schema.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.validate(new StreamSource(new StringReader(xml)));
        } catch (SAXException | IOException e) {
            throw new XmlContractException("XML contract validation failed.", e);
        }
    }
}
