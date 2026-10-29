package lab.backend.content.publication;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLParameters;
import lab.backend.content.transport.TransportPeerClient;
import lab.backend.content.transport.TransportSettings;

/** Fixed INT-04 Learning client; không nhận URL tùy ý từ CLI hoặc outbox. */
public final class PublicationPeerClient implements AutoCloseable {
  private static final String PREFIX = "/internal/v1/publication-operations/";
  private final HttpClient client;
  private final int port;

  public PublicationPeerClient(TransportSettings settings) throws Exception {
    if (!settings.canonicalLab()) {
      throw new IllegalArgumentException("PUBLICATION_CANONICAL_TOPOLOGY_REQUIRED");
    }
    port = settings.peerPrivatePort("learning");
    SSLParameters parameters = new SSLParameters();
    parameters.setEndpointIdentificationAlgorithm("HTTPS");
    parameters.setProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
    client = HttpClient.newBuilder()
        .sslContext(TransportPeerClient.tls(settings, settings.keyStore(), "learning"))
        .sslParameters(parameters)
        .connectTimeout(Duration.ofSeconds(1))
        .followRedirects(HttpClient.Redirect.NEVER)
        .version(HttpClient.Version.HTTP_1_1)
        .build();
  }

  public byte[] send(UUIDAndPhase target, byte[] exactCommand) throws Exception {
    String path = PREFIX + target.operationId() + "/phases/" + target.phase();
    HttpRequest request = HttpRequest.newBuilder(uri(path))
        .timeout(Duration.ofSeconds(5))
        .header("Content-Type", "application/json")
        .PUT(HttpRequest.BodyPublishers.ofByteArray(exactCommand))
        .build();
    return exchange(request);
  }

  public byte[] status(java.util.UUID operationId) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(uri(PREFIX + operationId))
        .timeout(Duration.ofSeconds(5))
        .GET()
        .build();
    return exchange(request);
  }

  private URI uri(String path) {
    return URI.create("https://127.0.0.1:" + port + path);
  }

  private byte[] exchange(HttpRequest request) throws Exception {
    BoundedBody subscriber = new BoundedBody();
    var future = client.sendAsync(request, ignored -> subscriber);
    try {
      HttpResponse<byte[]> response = future.get(5, TimeUnit.SECONDS);
      if (response.statusCode() != 200) {
        throw new IllegalStateException("PUBLICATION_PEER_DENIED");
      }
      String contentType = response.headers().firstValue("Content-Type").orElse("");
      String cacheControl = response.headers().firstValue("Cache-Control").orElse("");
      if (!contentType.matches("(?i)application/json(?:; ?charset=utf-8)?")
          || !"no-store".equalsIgnoreCase(cacheControl)) {
        throw new IllegalStateException("PUBLICATION_PEER_HEADERS_INVALID");
      }
      return response.body();
    } catch (Exception failure) {
      future.cancel(true);
      subscriber.cancel();
      throw failure;
    }
  }

  @Override
  public void close() {
    client.shutdownNow();
  }

  public record UUIDAndPhase(java.util.UUID operationId, String phase) {}

  private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private final java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
    private volatile Flow.Subscription subscription;

    @Override
    public CompletionStage<byte[]> getBody() {
      return body;
    }

    @Override
    public void onSubscribe(Flow.Subscription value) {
      subscription = value;
      value.request(1);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
      for (ByteBuffer buffer : buffers) {
        if (output.size() + buffer.remaining() > PublicationCoordinatorWire.MAX_BYTES) {
          cancel();
          body.completeExceptionally(
              new IllegalStateException("PUBLICATION_PEER_RESPONSE_TOO_LARGE"));
          return;
        }
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        output.writeBytes(bytes);
      }
      subscription.request(1);
    }

    @Override
    public void onError(Throwable failure) {
      body.completeExceptionally(failure);
    }

    @Override
    public void onComplete() {
      body.complete(output.toByteArray());
    }

    private void cancel() {
      if (subscription != null) {
        subscription.cancel();
      }
    }
  }
}
