package com.sakhtyar.crawler.application;

import com.sakhtyar.crawler.api.CrawlerDtos.JobResponse;
import com.sakhtyar.globalization.application.JurisdictionService;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class JurisdictionCrawlerService {
    private final JurisdictionService jurisdiction;
    private final IngestionPipeline pipeline;
    public JurisdictionCrawlerService(JurisdictionService jurisdiction,IngestionPipeline pipeline){
        this.jurisdiction=jurisdiction;this.pipeline=pipeline;
    }
    public List<JobResponse> runForCase(UUID caseId,Integer maxPages,Authentication auth){
        List<UUID> ids=jurisdiction.matchingCrawlerSources(caseId);
        if(ids.isEmpty())throw new IllegalStateException("No crawler source matches this case jurisdiction.");
        List<JobResponse> jobs=new ArrayList<>();
        for(UUID id:ids)jobs.add(pipeline.run(id,maxPages,auth));
        return List.copyOf(jobs);
    }
}