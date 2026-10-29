package lab.backend.content.transport;

import java.net.URI;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;

/** Fixed peer destination, strict chain/hostname/SAN and bounded no-redirect transport. */
public final class TransportPeerClient implements AutoCloseable {
  private final HttpClient client;
  private final URI destination;
  public TransportPeerClient(TransportSettings settings,String receiver)throws Exception {
    if(!Set.of("identity","content","learning").contains(receiver))throw new IllegalArgumentException("Unknown peer receiver");
    // Foundation giữ8443; canonical dùng static peer port và cùng pinned mTLS.
    destination=URI.create("https://127.0.0.1:"+settings.peerPrivatePort(receiver)+"/internal/v1/transport/probes");
    SSLParameters parameters=new SSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");parameters.setProtocols(new String[]{"TLSv1.3","TLSv1.2"});
    client=HttpClient.newBuilder().sslContext(tls(settings,settings.keyStore(),receiver)).sslParameters(parameters)
        .connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build();
  }
  public byte[] probe(byte[] request)throws Exception {
    return exchange(destination,request);
  }
  /** Fixed INT-01 path; port is selected only by validated owned-lab config. */
  public byte[] admission(byte[] request,int port)throws Exception {
    if(port<1024||port>65535)throw new IllegalArgumentException("Peer port denied");
    return exchange(URI.create("https://127.0.0.1:"+port+"/internal/v1/auth-context/admissions"),request);
  }
  private byte[] exchange(URI target,byte[] request)throws Exception {
    if(request.length>WireCodec.MAX_BYTES)throw new WireCodec.Denied(413,"PAYLOAD_TOO_LARGE");
    var subscriber=new BoundedBody();
    var future=client.sendAsync(HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(2)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(request)).build(),info->subscriber);
    try {
      var response=future.get(2,TimeUnit.SECONDS);
      if(response.statusCode()!=200)throw new WireCodec.Denied(response.statusCode(),"PEER_DENIED");
      if(target.getPath().equals("/internal/v1/auth-context/admissions") && !response.headers().firstValue("Content-Type").orElse("").matches("(?i)application/json(?:; ?charset=utf-8)?"))throw new WireCodec.Denied(503,"PEER_CONTENT_TYPE_DENIED");
      WireCodec.strict(response.body());return response.body();
    }catch(Exception e){future.cancel(true);subscriber.cancel();throw e;}
  }
  @Override public void close(){client.shutdownNow();}
  public static SSLContext tls(TransportSettings settings,Path clientStore,String receiver)throws Exception {
    KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    if(clientStore!=null)km.init(settings.store(clientStore),settings.storePassword().toCharArray());
    TrustManagerFactory tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(settings.store(settings.trustStore()));
    X509ExtendedTrustManager delegate=(X509ExtendedTrustManager)tm.getTrustManagers()[0];
    X509ExtendedTrustManager trusted=new X509ExtendedTrustManager(){
      private void expected(X509Certificate[] chain)throws CertificateException {
        try{if(!CertificateIdentity.identity(chain[0],"lab",false).equals(receiver))throw new Exception();}catch(Exception e){throw new CertificateException("Expected receiver identity denied");}
      }
      public X509Certificate[] getAcceptedIssuers(){return delegate.getAcceptedIssuers();}
      public void checkClientTrusted(X509Certificate[] c,String a)throws CertificateException{delegate.checkClientTrusted(c,a);}
      public void checkClientTrusted(X509Certificate[] c,String a,Socket s)throws CertificateException{delegate.checkClientTrusted(c,a,s);}
      public void checkClientTrusted(X509Certificate[] c,String a,SSLEngine e)throws CertificateException{delegate.checkClientTrusted(c,a,e);}
      public void checkServerTrusted(X509Certificate[] c,String a)throws CertificateException{delegate.checkServerTrusted(c,a);expected(c);}
      public void checkServerTrusted(X509Certificate[] c,String a,Socket s)throws CertificateException{delegate.checkServerTrusted(c,a,s);expected(c);}
      public void checkServerTrusted(X509Certificate[] c,String a,SSLEngine e)throws CertificateException{delegate.checkServerTrusted(c,a,e);expected(c);}
    };
    SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(clientStore==null?null:km.getKeyManagers(),new TrustManager[]{trusted},null);return ssl;
  }
  private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> body=new CompletableFuture<>();
    private final java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
    private volatile Flow.Subscription subscription;
    public java.util.concurrent.CompletionStage<byte[]> getBody(){return body;}
    public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
    public void onNext(List<ByteBuffer> buffers){
      for(ByteBuffer b:buffers){if(output.size()+b.remaining()>WireCodec.MAX_BYTES){cancel();body.completeExceptionally(new WireCodec.Denied(413,"PEER_PAYLOAD_TOO_LARGE"));return;}byte[] bytes=new byte[b.remaining()];b.get(bytes);output.writeBytes(bytes);}subscription.request(1);
    }
    public void onError(Throwable failure){body.completeExceptionally(failure);}
    public void onComplete(){body.complete(output.toByteArray());}
    void cancel(){if(subscription!=null)subscription.cancel();}
  }
}
