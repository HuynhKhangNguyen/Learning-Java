package lab.backend.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.apache.catalina.util.ServerInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("bootstrap")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
class IdentityBootstrapHttpTests {

  @LocalServerPort
  private int port;

  private final HttpClient client = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5)).build();

  @Test
  void reportsBootstrapWithoutClaimingBusinessReadiness() throws Exception {
    HttpResponse<String> response = get("/bootstrap/status");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
    assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    var status = JsonMapper.builder().build().readTree(response.body());
    assertThat(status.get("service").asString()).isEqualTo("identity-service");
    assertThat(status.get("phase").asString()).isEqualTo("bootstrap");
    assertThat(status.get("businessReady").asBoolean()).isFalse();
  }

  @ParameterizedTest
  @CsvSource({
      "GET,/api/v1/session",
      "POST,/api/v1/auth/login",
      "POST,/api/v1/auth/logout",
      "POST,/internal/v1/auth-context/admissions",
      "GET,/actuator/health"
  })
  void doesNotProvideBusinessPrivateOrManagementEndpoints(String method, String path) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(5))
        .method(method, HttpRequest.BodyPublishers.noBody()).build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
  }

  @Test
  void rejectsMutationOnTheBootstrapEndpoint() throws Exception {
    HttpRequest request = HttpRequest.newBuilder(uri("/bootstrap/status"))
        .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.noBody()).build();
    assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(405);
  }

  @Test
  void usesTheReviewedTomcatPatchAtRuntime() {
    assertThat(ServerInfo.getServerNumber()).isEqualTo("11.0.26.0");
  }

  private HttpResponse<String> get(String path) throws Exception {
    return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(5)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private URI uri(String path) {
    return URI.create("http://127.0.0.1:" + port + path);
  }
}
