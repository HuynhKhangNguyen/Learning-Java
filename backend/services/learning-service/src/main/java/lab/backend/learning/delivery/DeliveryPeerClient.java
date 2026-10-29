package lab.backend.learning.delivery;

import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLParameters;
import lab.backend.learning.transport.*;

/** Owned-lab direct peer, fixed paths, pinned mTLS service identity, bounded exact byte response. */
public final class DeliveryPeerClient implements AutoCloseable {
  private final HttpClient client;
  private final String receiver;
  private final int port;
  public DeliveryPeerClient(TransportSettings settings,String receiver,int port)throws Exception{
    if(!DeliveryWire.SERVICES.contains(receiver)||receiver.equals(DeliveryWire.SERVICE)||port<1024||port>65535||port==8080)throw new IllegalArgumentException("Owned peer destination denied");
    // Fault fixtures cũ dùng port riêng; named canonical runtime chỉ được gửi
    // đúng endpoint của static service, không retarget outbox bằng config tùy ý.
    if(settings.canonicalLab()&&port!=settings.peerPrivatePort(receiver))throw new IllegalArgumentException("Canonical peer port denied");
    this.receiver=receiver;this.port=port;
    var parameters=new SSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");parameters.setProtocols(new String[]{"TLSv1.3","TLSv1.2"});
    client=HttpClient.newBuilder().sslContext(TransportPeerClient.tls(settings,settings.keyStore(),receiver)).sslParameters(parameters).connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build();
  }
  public byte[] send(DeliveryStore.Lease lease)throws Exception{
    var e=DeliveryWire.envelope(lease.envelope().bytes());
    if(!e.producer().equals(DeliveryWire.SERVICE)||!e.consumer().equals(receiver))throw new WireCodec.Denied(403,"DELIVERY_PEER_DENIED");
    return put(DeliveryWire.PATH+e.message(),e.bytes());
  }
  /** Identity-only INT02 lane uses the existing receiver path; no strict event cursor. */
  public byte[] fence(UUID operation,byte[] exactBytes)throws Exception{
    if(!DeliveryWire.SERVICE.equals("identity")||!Set.of("content","learning").contains(receiver))throw new WireCodec.Denied(403,"AUTH_DELIVERY_PEER_DENIED");
    return put("/internal/v1/session-fences/"+operation,exactBytes);
  }
  private byte[] put(String path,byte[] exactBytes)throws Exception{
    if(exactBytes.length>DeliveryWire.MAX_ENVELOPE)throw new WireCodec.Denied(413,"ENVELOPE_TOO_LARGE");
    var subscriber=new BoundedBody();
    var future=client.sendAsync(HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(5)).header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofByteArray(exactBytes)).build(),info->subscriber);
    try{
      var response=future.get(5,TimeUnit.SECONDS);
      if(response.statusCode()!=200)throw new WireCodec.Denied(response.statusCode(),"PEER_DELIVERY_DENIED");
      if(!response.headers().firstValue("Content-Type").orElse("").matches("(?i)application/json(?:; ?charset=utf-8)?"))throw new WireCodec.Denied(503,"PEER_CONTENT_TYPE_DENIED");
      WireCodec.strict(response.body());return response.body();
    }catch(Exception failed){
      future.cancel(true);subscriber.cancel();Throwable cause=failed;
      while((cause instanceof ExecutionException||cause instanceof CompletionException)&&cause.getCause()!=null)cause=cause.getCause();
      if(cause instanceof WireCodec.Denied denied)throw denied;
      throw failed;
    }
  }
  @Override public void close(){client.shutdownNow();}
  @Override public String toString(){return "DeliveryPeerClient[lab,secrets=REDACTED]";}
  private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]>{
    private final CompletableFuture<byte[]> body=new CompletableFuture<>();
    private final java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
    private volatile Flow.Subscription subscription;
    public CompletionStage<byte[]> getBody(){return body;}
    public void onSubscribe(Flow.Subscription value){subscription=value;value.request(1);}
    public void onNext(List<ByteBuffer> buffers){
      for(var b:buffers){if(output.size()+b.remaining()>DeliveryWire.MAX_ENVELOPE){cancel();body.completeExceptionally(new WireCodec.Denied(413,"PEER_DELIVERY_TOO_LARGE"));return;}byte[] bytes=new byte[b.remaining()];b.get(bytes);output.writeBytes(bytes);}subscription.request(1);
    }
    public void onError(Throwable failure){body.completeExceptionally(failure);}
    public void onComplete(){body.complete(output.toByteArray());}
    void cancel(){if(subscription!=null)subscription.cancel();}
  }
}
