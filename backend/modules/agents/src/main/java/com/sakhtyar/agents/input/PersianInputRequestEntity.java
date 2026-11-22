package com.sakhtyar.agents.input;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="persian_input_request")
public class PersianInputRequestEntity {

    @Id private UUID id;
    @Column(name="case_id") private UUID caseId;
    @Column(name="conversation_id") private UUID conversationId;
    @Column(name="raw_text",nullable=false,columnDefinition="text") private String rawText;
    @Column(name="normalized_text",nullable=false,columnDefinition="text") private String normalizedText;
    @Column(nullable=false,length=10) private String locale;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false,length=40)
    private InputGatewayStatus status;

    @Column(name="schema_version",nullable=false,length=20) private String schemaVersion;
    @Column(name="normalizer_version",nullable=false,length=20) private String normalizerVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="canonical_parameters",nullable=false,columnDefinition="jsonb")
    private Map<String,Object> canonicalParameters;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="recognized_terms",nullable=false,columnDefinition="jsonb")
    private Map<String,Object> recognizedTerms;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable=false,columnDefinition="jsonb")
    private Map<String,Object> metadata;

    @Column(name="created_by",nullable=false,length=150) private String createdBy;
    @Column(name="created_at",nullable=false) private Instant createdAt;

    protected PersianInputRequestEntity(){}

    public PersianInputRequestEntity(
            UUID id,UUID caseId,UUID conversationId,String rawText,String normalizedText,
            String locale,InputGatewayStatus status,String schemaVersion,String normalizerVersion,
            Map<String,Object> canonicalParameters,Map<String,Object> recognizedTerms,
            Map<String,Object> metadata,String createdBy,Instant createdAt
    ) {
        this.id=id;
        this.caseId=caseId;
        this.conversationId=conversationId;
        this.rawText=rawText;
        this.normalizedText=normalizedText;
        this.locale=locale;
        this.status=status;
        this.schemaVersion=schemaVersion;
        this.normalizerVersion=normalizerVersion;
        this.canonicalParameters=canonicalParameters==null?Map.of():Map.copyOf(canonicalParameters);
        this.recognizedTerms=recognizedTerms==null?Map.of():Map.copyOf(recognizedTerms);
        this.metadata=metadata==null?Map.of():Map.copyOf(metadata);
        this.createdBy=createdBy;
        this.createdAt=createdAt;
    }

    public UUID getId(){return id;}
    public UUID getCaseId(){return caseId;}
    public UUID getConversationId(){return conversationId;}
    public String getRawText(){return rawText;}
    public String getNormalizedText(){return normalizedText;}
    public String getLocale(){return locale;}
    public InputGatewayStatus getStatus(){return status;}
    public String getSchemaVersion(){return schemaVersion;}
    public String getNormalizerVersion(){return normalizerVersion;}
    public Map<String,Object> getCanonicalParameters(){return canonicalParameters==null?Map.of():Map.copyOf(canonicalParameters);}
    public Map<String,Object> getRecognizedTerms(){return recognizedTerms==null?Map.of():Map.copyOf(recognizedTerms);}
    public Map<String,Object> getMetadata(){return metadata==null?Map.of():Map.copyOf(metadata);}
    public String getCreatedBy(){return createdBy;}
    public Instant getCreatedAt(){return createdAt;}
}