package com.sakhtyar.contract.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only database-level draft snapshot. Not an electronic signature. */
@Entity
@Table(name="contract_revision")
public class ContractRevisionEntity {
    @Id private UUID id;
    @Column(name="contract_id",nullable=false,updatable=false) private UUID contractId;
    @Column(name="revision_no",nullable=false,updatable=false) private int revisionNo;
    @Column(name="event_code",nullable=false,length=24,updatable=false) private String eventCode;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="snapshot",nullable=false,columnDefinition="jsonb",updatable=false)
    private Map<String,Object> snapshot;
    @Column(name="recorded_by",nullable=false,length=150,updatable=false) private String recordedBy;
    @Column(name="recorded_at",nullable=false,updatable=false) private Instant recordedAt;

    protected ContractRevisionEntity() { }
    public ContractRevisionEntity(UUID id,UUID contractId,int revisionNo,String eventCode,
            Map<String,Object> snapshot,String recordedBy,Instant recordedAt) {
        this.id=id;this.contractId=contractId;this.revisionNo=revisionNo;this.eventCode=eventCode;
        this.snapshot=new LinkedHashMap<>(snapshot);this.recordedBy=recordedBy;this.recordedAt=recordedAt;
    }
    public UUID getId(){return id;}
    public UUID getContractId(){return contractId;}
    public int getRevisionNo(){return revisionNo;}
    public String getEventCode(){return eventCode;}
    public Map<String,Object> getSnapshot(){return Collections.unmodifiableMap(snapshot);}
    public String getRecordedBy(){return recordedBy;}
    public Instant getRecordedAt(){return recordedAt;}
}
