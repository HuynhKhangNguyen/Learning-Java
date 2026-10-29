package lab.backend.learning.contentreplica;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Controlled INT-03 response faults; không thay packaged mTLS trust evidence. */
class ContentBundleClientTests {
  private static final byte[] BUNDLE="{\"schemaVersion\":2,\"id\":\"CASE-X\",\"unitId\":\"UNIT-X\",\"expected\":{\"answer\":42},\"rubricTargets\":[\"CAP-COL-002-A\"]}".getBytes(StandardCharsets.UTF_8);
  private static final byte[] MANIFEST="[{\"capabilityId\":\"CAP-COL-002-A\",\"version\":\"1\",\"newAttemptAllowed\":true,\"submitAllowed\":true,\"finalizeAllowed\":true,\"currentEvidencePolicy\":\"RETAIN\",\"bodyAvailable\":true,\"reason\":\"fixture\"}]".getBytes(StandardCharsets.UTF_8);
  private final AtomicReference<String> fault=new AtomicReference<>("none");private HttpServer server;private ContentBundleClient client;

  @BeforeEach void start()throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/internal/v1/content-bundles/fx05-p2",this::respond);server.start();var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).build();client=new ContentBundleClient(http,"http://127.0.0.1:"+server.getAddress().getPort()+"/internal/v1/content-bundles/");}
  @AfterEach void stop(){if(client!=null)client.close();if(server!=null)server.stop(0);}

  @Test void acceptsExactBoundBundleAndManifest(){var verified=assertDoesNotThrow(()->client.pull(command()));assertArrayEquals(BUNDLE,verified.bundle());assertArrayEquals(MANIFEST,verified.manifest());}

  @Test void rejectsRedirectTruncatedOversizeDuplicateAndWrongMetadata(){for(String mode:List.of("redirect","truncated","oversize","duplicate-digest","wrong-version","wrong-media","set-cookie","manifest-length")){fault.set(mode);assertThrows(Exception.class,()->client.pull(command()),mode);}}

  private void respond(HttpExchange exchange){try{String mode=fault.get();boolean manifest=exchange.getRequestURI().getPath().endsWith("/manifest");byte[] body=manifest?MANIFEST:BUNDLE;if("redirect".equals(mode)&&!manifest){exchange.getResponseHeaders().set("Location","/elsewhere");exchange.sendResponseHeaders(302,-1);return;}if("oversize".equals(mode)&&!manifest)body=new byte[1_048_577];var h=exchange.getResponseHeaders();h.set("Cache-Control","no-store");h.set("Content-Type",manifest?"application/json":"application/octet-stream");h.set("X-Content-Contract-Version","wrong-version".equals(mode)?"2":"1");h.set("X-Content-Revision","2");h.set("Digest","sha-256="+(manifest?PublicationWire.sha256(MANIFEST):PublicationWire.sha256(BUNDLE)));if(manifest){h.set("X-Content-Bundle-Digest","sha-256="+PublicationWire.sha256(BUNDLE));}else{h.set("X-Content-Manifest-Digest","sha-256="+PublicationWire.sha256(MANIFEST));h.set("X-Content-Manifest-Length",Integer.toString(MANIFEST.length+("manifest-length".equals(mode)?1:0)));h.set("Link","</internal/v1/content-bundles/fx05-p2/manifest>; rel=\"manifest\"; type=\"application/json\"");}if("duplicate-digest".equals(mode)&&!manifest)h.add("Digest","sha-256="+PublicationWire.sha256(BUNDLE));if("wrong-media".equals(mode)&&!manifest)h.set("Content-Type","text/plain");if("set-cookie".equals(mode)&&!manifest)h.set("Set-Cookie","session=forbidden");long declared=body.length+("truncated".equals(mode)&&!manifest?7:0);exchange.sendResponseHeaders(200,declared);exchange.getResponseBody().write(body);}catch(Exception ignored){}finally{exchange.close();}}
  private static PublicationWire.Command command(){return new PublicationWire.Command(1,UUID.fromString("10000000-0000-4000-8000-000000000001"),"STAGE",1,2,"fx05-p2",PublicationWire.sha256(BUNDLE),PublicationWire.sha256(MANIFEST),UUID.fromString("20000000-0000-4000-8000-000000000001"),UUID.fromString("30000000-0000-4000-8000-000000000001"),UUID.fromString("40000000-0000-4000-8000-000000000001"),1,"a".repeat(64));}
}
