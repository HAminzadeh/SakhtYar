package com.sakhtyar.shared.xml;

public class XmlContractException extends RuntimeException {
    public XmlContractException(String message) {
        super(message);
    }

    public XmlContractException(String message, Throwable cause) {
        super(message, cause);
    }
}
