
package com.fintech.controltower.service;

import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;

@Service
public class IsoMessageValidatorService {

    private static final String SCHEMA_RESOURCE =
            "iso20022/pacs.008.001.14.xsd";

    private final Schema schema;

    public IsoMessageValidatorService() {
        this.schema = loadSchema();
    }

    private Schema loadSchema() {

        try {
            URL schemaUrl = getClass()
                    .getClassLoader()
                    .getResource(SCHEMA_RESOURCE);

            if (schemaUrl == null) {
                throw new IllegalStateException(
                        "ISO 20022 XSD not found: " + SCHEMA_RESOURCE
                );
            }

            SchemaFactory factory = SchemaFactory.newInstance(
                    XMLConstants.W3C_XML_SCHEMA_NS_URI
            );

            // Restrict external resource access.
            factory.setFeature(
                    XMLConstants.FEATURE_SECURE_PROCESSING, true
            );
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            StreamSource schemaSource =
                    new StreamSource(schemaUrl.openStream());

            schemaSource.setSystemId(schemaUrl.toExternalForm());

            return factory.newSchema(schemaSource);

        } catch (SAXException | IOException e) {
            throw new IllegalStateException(
                    "Unable to load ISO 20022 pacs.008 schema", e
            );
        }
    }

    public void validate(String xmlMessage) {

        if (xmlMessage == null || xmlMessage.isBlank()) {
            throw new IllegalArgumentException(
                    "XML message must not be empty"
            );
        }

        try {
            // Validator instances are created per call.
            Validator validator = schema.newValidator();

            validator.setFeature(
                    XMLConstants.FEATURE_SECURE_PROCESSING, true
            );
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            StreamSource xmlSource =
                    new StreamSource(new StringReader(xmlMessage));

            validator.validate(xmlSource);

        } catch (SAXException | IOException e) {
            throw new IllegalStateException(
                    "Generated pacs.008 message failed XSD validation: "
                            + e.getMessage(),
                    e
            );
        }
    }
}
