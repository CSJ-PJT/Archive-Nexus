package com.archivenexus.backend.notification;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
@Component public class DiscordCriticalNotifier {
 private static final Logger log=LoggerFactory.getLogger(DiscordCriticalNotifier.class);
 private final String webhookUrl; private final ObjectMapper mapper; private final HttpClient client;
 @Autowired public DiscordCriticalNotifier(@Value("${archive-nexus.notifications.discord-webhook-url:}") String url,ObjectMapper mapper){this(url,mapper,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());}
 DiscordCriticalNotifier(String url,ObjectMapper mapper,HttpClient client){this.webhookUrl=url==null?"":url.trim();this.mapper=mapper;this.client=client;}
 public boolean notifyCritical(String title,String message){if(webhookUrl.isBlank()){log.info("Discord critical notification skipped because webhook is not configured: {}",title);return false;}try{HttpRequest req=HttpRequest.newBuilder(URI.create(webhookUrl)).timeout(Duration.ofSeconds(5)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("content","🚨 **"+title+"**\n"+message)))).build();int status=client.send(req,HttpResponse.BodyHandlers.discarding()).statusCode();return status>=200&&status<300;}catch(Exception e){log.warn("Discord critical notification failed",e);return false;}}
}
