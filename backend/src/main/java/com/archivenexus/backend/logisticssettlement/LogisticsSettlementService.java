package com.archivenexus.backend.logisticssettlement;

import com.archivenexus.backend.audit.AuditService;
import com.archivenexus.backend.logisticssettlement.LogisticsSettlementModels.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class LogisticsSettlementService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private final LogisticsDailySettlementRepository repository; private final ObjectMapper mapper; private final AuditService audit;
    public LogisticsSettlementService(LogisticsDailySettlementRepository repository, ObjectMapper mapper, AuditService audit){this.repository=repository;this.mapper=mapper;this.audit=audit;}

    @Transactional
    public DailySettlementResponse receive(DailySettlementRequest request){
        validate(request); Instant now=Instant.now();
        Optional<LogisticsDailySettlementEntity> existing=repository.findBySettlementId(request.settlementId());
        if(existing.isEmpty()) existing=repository.findByIdempotencyKey(request.idempotencyKey());
        if(existing.isPresent()){
            LogisticsDailySettlementEntity duplicate=existing.get(); duplicate.recordDuplicate(now); repository.save(duplicate);
            audit.record("Archive-Logistics","LOGISTICS_DAILY_SETTLEMENT_DUPLICATE","Duplicate daily settlement ignored",null,request.settlementId(),null,Map.of("settlementId",request.settlementId(),"idempotencyKey",request.idempotencyKey(),"factoryId",request.factoryId()));
            return response(duplicate,true);
        }
        LogisticsDailySettlementEntity saved=repository.save(new LogisticsDailySettlementEntity(
                request.settlementId(),request.idempotencyKey(),value(request.source(),"Archive-Logistics"),
                request.schemaVersion()==null?1:request.schemaVersion(),request.settlementDate(),request.factoryId(),
                value(request.currency(),"KRW"),nonNegative(request.totalShipments()),nonNegative(request.delayedShipments()),
                nonNegative(request.heldShipments()),nonNegative(request.totalQuantity()),money(request.totalLogisticsCost()),
                money(request.manufacturingImpactCost()),rate(request.onTimeRate()),json(request.evidence()),json(request.payload()),
                request.occurredAt()==null?now:request.occurredAt(),now));
        audit.record("Archive-Logistics","LOGISTICS_DAILY_SETTLEMENT_RECEIVED","Daily manufacturing settlement received from Archive-Logistics",null,saved.settlementId(),null,Map.of("settlementId",saved.settlementId(),"factoryId",saved.factoryId(),"settlementDate",saved.settlementDate().toString(),"totalLogisticsCost",saved.totalLogisticsCost()));
        return response(saved,false);
    }

    @Transactional
    public BulkDailySettlementResponse receiveBulk(BulkDailySettlementRequest request){
        List<DailySettlementRequest> items=request==null||request.settlements()==null?List.of():request.settlements();
        List<DailySettlementResponse> results=new ArrayList<>(); int received=0,duplicates=0,failed=0;
        for(DailySettlementRequest item:items){try{DailySettlementResponse result=receive(item);results.add(result);if(result.duplicate())duplicates++;else received++;}catch(RuntimeException error){failed++;}}
        return new BulkDailySettlementResponse(items.size(),received,duplicates,failed,results);
    }

    @Transactional(readOnly = true)
    public List<DailySettlementResponse> recent(int limit,String factoryId){
        int safeLimit=Math.max(1,Math.min(limit,500)); PageRequest page=PageRequest.of(0,safeLimit);
        List<LogisticsDailySettlementEntity> entities=factoryId==null||factoryId.isBlank()?repository.findAllByOrderByReceivedAtDesc(page):repository.findAllByFactoryIdOrderBySettlementDateDescReceivedAtDesc(factoryId,page);
        return entities.stream().map(e->response(e,false)).toList();
    }

    @Transactional(readOnly = true)
    public Optional<DailySettlementResponse> find(String settlementId){return repository.findBySettlementId(settlementId).map(e->response(e,false));}

    @Transactional(readOnly = true)
    public SettlementSummary summary(){
        List<FactorySettlementSummary> factories=repository.distinctFactoryIds().stream().map(factoryId->new FactorySettlementSummary(factoryId,repository.countByFactoryId(factoryId))).toList();
        return new SettlementSummary(repository.count(),repository.countByProcessingStatus(SettlementProcessingStatus.RECEIVED),repository.countByProcessingStatus(SettlementProcessingStatus.INVALID),zero(repository.sumTotalLogisticsCost()),zero(repository.sumManufacturingImpactCost()),repository.maxReceivedAt(),factories);
    }

    private DailySettlementResponse response(LogisticsDailySettlementEntity entity,boolean duplicate){
        return new DailySettlementResponse(entity.settlementId(),entity.idempotencyKey(),duplicate,entity.source(),entity.schemaVersion(),entity.settlementDate(),entity.factoryId(),entity.processingStatus(),entity.currency(),entity.totalShipments(),entity.delayedShipments(),entity.heldShipments(),entity.totalQuantity(),entity.totalLogisticsCost(),entity.manufacturingImpactCost(),entity.onTimeRate(),map(entity.evidenceJson()),map(entity.payloadJson()),entity.occurredAt(),entity.receivedAt(),entity.processedAt(),entity.duplicateCount());
    }
    private void validate(DailySettlementRequest request){
        if(request==null)throw new ResponseStatusException(BAD_REQUEST,"request body is required");
        if(blank(request.settlementId()))throw new ResponseStatusException(BAD_REQUEST,"settlementId is required");
        if(blank(request.idempotencyKey()))throw new ResponseStatusException(BAD_REQUEST,"idempotencyKey is required");
        if(request.settlementDate()==null)throw new ResponseStatusException(BAD_REQUEST,"settlementDate is required");
        if(blank(request.factoryId()))throw new ResponseStatusException(BAD_REQUEST,"factoryId is required");
    }
    private String json(Map<String,Object> value){try{return mapper.writeValueAsString(value==null?Map.of():value);}catch(Exception error){return "{}";}}
    private Map<String,Object> map(String json){try{return mapper.readValue(json==null||json.isBlank()?"{}":json,MAP_TYPE);}catch(Exception error){return Map.of();}}
    private String value(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
    private boolean blank(String value){return value==null||value.isBlank();}
    private int nonNegative(Integer value){return value==null?0:Math.max(0,value);}
    private BigDecimal money(BigDecimal value){return value==null?BigDecimal.ZERO:value.max(BigDecimal.ZERO);}
    private BigDecimal rate(BigDecimal value){return value==null?BigDecimal.ZERO:value.max(BigDecimal.ZERO);}
    private BigDecimal zero(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
}
