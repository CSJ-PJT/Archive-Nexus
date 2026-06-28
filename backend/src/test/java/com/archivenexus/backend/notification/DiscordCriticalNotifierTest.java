package com.archivenexus.backend.notification;
import com.fasterxml.jackson.databind.ObjectMapper;import com.sun.net.httpserver.HttpServer;import org.junit.jupiter.api.Test;import java.net.InetSocketAddress;import java.nio.charset.StandardCharsets;import java.util.concurrent.atomic.AtomicReference;import static org.assertj.core.api.Assertions.assertThat;
class DiscordCriticalNotifierTest {
 @Test void skipsWithoutWebhook(){assertThat(new DiscordCriticalNotifier("",new ObjectMapper()).notifyCritical("실패","내용")).isFalse();}
 @Test void postsCritical()throws Exception{AtomicReference<String> body=new AtomicReference<>();HttpServer server=HttpServer.create(new InetSocketAddress(0),0);server.createContext("/discord",e->{body.set(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));e.sendResponseHeaders(204,-1);e.close();});server.start();try{DiscordCriticalNotifier n=new DiscordCriticalNotifier("http://127.0.0.1:"+server.getAddress().getPort()+"/discord",new ObjectMapper());assertThat(n.notifyCritical("Agent 실패","TASK-1")).isTrue();assertThat(body.get()).contains("Agent 실패","TASK-1");}finally{server.stop(0);}}
}
