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

    private XmlContractValidator() {
    }

    public static void validate(String xml, String classpathXsd) {
        if (xml == null || xml.isBlank()) {
            throw new XmlContractException("XML payload is required.");
        }
        if (classpathXsd == null || classpathXsd.isBlank()) {
            throw new XmlContractException("XSD path is required.");
        }

        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (SAXException ignored) {
            // Provider may not support these properties; secure processing is still enabled below.
        }
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (SAXException e) {
            throw new XmlContractException("XML validator does not support secure processing.", e);
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
            throw new XmlContractException("XML contract validation failed: " + e.getMessage(), e);
        }
    }
}
