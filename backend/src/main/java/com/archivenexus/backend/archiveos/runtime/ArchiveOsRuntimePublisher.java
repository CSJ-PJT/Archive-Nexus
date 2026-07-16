package com.archivenexus.backend.archiveos.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;

@Component
public class ArchiveOsRuntimePublisher {
    private final ArchiveOsRuntimeProperties properties; private final ObjectMapper mapper; private final HttpClient client=HttpClient.newBuilder().build();
    public ArchiveOsRuntimePublisher(ArchiveOsRuntimeProperties properties,ObjectMapper mapper){this.properties=properties;this.mapper=mapper;}
    public PublishOutcome publish(ArchiveOsRuntimeDeliveryEntity delivery) {
        if(!properties.enabled) return PublishOutcome.disabled();
        if(properties.token.isBlank()) return PublishOutcome.config("TOKEN_MISSING","ArchiveOS runtime ingest token is not configured");
        try {
            HttpRequest request=HttpRequest.newBuilder(URI.create(properties.baseUrl+"/api/live-flow/events/ingest"))
                    .timeout(properties.timeout).header("Authorization","Bearer "+properties.token)
                    .header("X-Archive-Source-System","archive-nexus").header("X-Archive-Service-Scope","runtime:ingest")
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(delivery.payloadJson())).build();
            HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString()); int code=response.statusCode();
            if(code>=200&&code<300){JsonNode root=mapper.readTree(response.body());JsonNode data=root.path("data");boolean accepted=data.path("accepted").asBoolean(false);boolean duplicate=data.path("duplicate").asBoolean(false);return accepted||duplicate?PublishOutcome.success():PublishOutcome.nonRetryable("REJECTED","ArchiveOS returned accepted=false and duplicate=false");}
            if(code==401||code==403)return PublishOutcome.config("HTTP_"+code,"ArchiveOS rejected runtime ingest identity");
            if(code==408||code==429||code>=500)return PublishOutcome.retryable("HTTP_"+code,"ArchiveOS runtime ingest unavailable");
            return PublishOutcome.nonRetryable("HTTP_"+code,"ArchiveOS runtime ingest rejected request");
        } catch(java.net.http.HttpTimeoutException ex){return PublishOutcome.retryable("TIMEOUT","ArchiveOS runtime ingest timed out");}
        catch(InterruptedException ex){Thread.currentThread().interrupt();return PublishOutcome.retryable("INTERRUPTED","ArchiveOS runtime ingest interrupted");}
        catch(Exception ex){return PublishOutcome.retryable("IO_ERROR",ex.getClass().getSimpleName());}
    }
    public record PublishOutcome(Kind kind,String code,String message){static PublishOutcome success(){return new PublishOutcome(Kind.SUCCESS,null,null);}static PublishOutcome disabled(){return new PublishOutcome(Kind.DISABLED,null,null);}static PublishOutcome config(String c,String m){return new PublishOutcome(Kind.CONFIG,c,m);}static PublishOutcome retryable(String c,String m){return new PublishOutcome(Kind.RETRY,c,m);}static PublishOutcome nonRetryable(String c,String m){return new PublishOutcome(Kind.NON_RETRYABLE,c,m);}}
    public enum Kind{SUCCESS,DISABLED,CONFIG,RETRY,NON_RETRYABLE}
}
