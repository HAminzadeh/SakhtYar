package com.sakhtyar.globalization.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MasterDataStartup {
    private final GeoNamesImporter importer;
    private final boolean autoImport;
    public MasterDataStartup(GeoNamesImporter importer,
        @Value("${app.globalization.master-data.auto-import:false}") boolean autoImport){
        this.importer=importer;this.autoImport=autoImport;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ready(){
        // Startup never initiates external downloads or writes reference data.
        // Admin must invoke the explicit import API with outbound-enabled=true.
        if(autoImport) org.slf4j.LoggerFactory.getLogger(getClass()).warn(
            "Global master-data auto-import ignored; explicit admin action is required.");
    }
}