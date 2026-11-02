package lab.backend.content.browser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import lab.backend.content.transport.WireCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Kiểm public DTO allowlist và wire guard bằng fixed immutable snapshot.
 *
 * <p>Input là mock HTTP request; output kiểm revision/cache/privacy/admission.
 * Fixture không ghi DB và mọi request mơ hồ phải fail trước khi trả content.
 */
class BrowserHandlerTests {
  private static BrowserHandler handler;

  @BeforeAll
  static void setup() throws Exception {
    byte[] bytes =
        Files.readAllBytes(
            Path.of(
                BrowserHandlerTests.class
                    .getResource("/contracts/content/public/fixtures/browser-snapshot-v1.json")
                    .toURI()));
    BrowserStore.View view =
        new BrowserStore.View(
            7,
            6,
            Instant.parse("2026-10-30T01:02:03.004Z"),
            BrowserSnapshot.parse(bytes));
    handler = new AllowedHandler(new FixedStore(view));
  }

  @Test
  void catalogIsAnonymousRevisionBoundAndContainsNoPrivateSentinel() throws Exception {
    var response = call("/api/v1/catalog");
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getHeader("Cache-Control")).contains("public", "must-revalidate");
    assertThat(response.getHeader("ETag")).isEqualTo("\"content-r7\"");
    var json = WireCodec.strict(response.getContentAsByteArray());
    assertThat(json.path("canonicalRevision").asString()).isEqualTo("7");
    assertThat(json.path("appliedRevision").asString()).isEqualTo("6");
    assertThat(json.path("learningSyncPending").asBoolean()).isTrue();
    assertThat(json.path("roadmaps").get(0).path("topics").get(0).path("id").asString())
        .isEqualTo("TOP-JAVA-01");
    assertThat(
            json.path("roadmaps")
                .get(0)
                .path("topics")
                .get(0)
                .path("capabilityIds")
                .get(0)
                .asString())
        .isEqualTo("CAP-JAVA-01");
    assertThat(json.path("roadmaps").get(1).path("topics").get(0).path("status").asString())
        .isEqualTo("PLANNED");
    assertThat(response.getContentAsString())
        .doesNotContain("expected", "rubric", "hint", "solution", "principal", "admission");
  }

  @Test
  void aliasOutlineUsesCanonicalTopicAndNeverContainsBodyBlocks() throws Exception {
    var response = call("/api/v1/topics/java-start");
    var json = WireCodec.strict(response.getContentAsByteArray());
    assertThat(json.path("topic").path("id").asString()).isEqualTo("TOP-JAVA-01");
    assertThat(json.path("topic").path("units").get(0).has("blocks")).isFalse();
  }

  @Test
  void plannedTopicIsExplicitAndProtectedBodyRequiresCanonicalContext() throws Exception {
    var planned = WireCodec.strict(call("/api/v1/topics/TOP-OPS-01").getContentAsByteArray());
    assertThat(planned.path("topic").path("status").asString()).isEqualTo("PLANNED");
    assertThat(planned.path("topic").path("units")).isEmpty();

    MockHttpServletRequest request =
        new MockHttpServletRequest(
            "GET", "/api/v1/topics/TOP-JAVA-01/units/UNIT-JAVA-01");
    MockHttpServletResponse response = new MockHttpServletResponse();
    assertThatThrownBy(() -> handler.handle(request, response))
        .isInstanceOfSatisfying(
            WireCodec.Denied.class,
            denied -> {
              assertThat(denied.status).isEqualTo(401);
              assertThat(denied.code).isEqualTo("SESSION_INVALID");
            });
  }

  @Test
  void protectedBodyUsesPrivateNoStoreAndNeverSerializesAdmissionOrPrincipal() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest(
            "GET", "/api/v1/topics/TOP-JAVA-01/units/UNIT-JAVA-01");
    request.addHeader("X-Session-Context", "00000000-0000-0000-0000-000000000010");
    request.addHeader("Cookie", "__Host-backend-lab-session=" + "a".repeat(43));
    MockHttpServletResponse response = new MockHttpServletResponse();
    handler.handle(request, response);
    assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");
    assertThat(response.getHeaders("Set-Cookie")).isEmpty();
    assertThat(response.getContentAsString())
        .contains("UNIT-JAVA-01", "Java uses static types.")
        .doesNotContain("principal", "admission", "credential", "expected", "rubric");
  }

  @Test
  void chunkedGetBodyIsRejectedBeforeCatalogRead() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/catalog");
    request.addHeader("Transfer-Encoding", "chunked");
    request.setContent("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    MockHttpServletResponse response = new MockHttpServletResponse();
    assertThatThrownBy(() -> handler.handle(request, response))
        .isInstanceOfSatisfying(
            WireCodec.Denied.class,
            denied -> {
              assertThat(denied.status).isEqualTo(400);
              assertThat(denied.code).isEqualTo("BAD_CONTENT_TYPE");
            });
  }

  private static MockHttpServletResponse call(String path) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    MockHttpServletResponse response = new MockHttpServletResponse();
    handler.handle(request, response);
    return response;
  }

  private static final class FixedStore extends BrowserStore {
    private final View view;

    FixedStore(View view) {
      super(null);
      this.view = view;
    }

    @Override
    View read() {
      return view;
    }

    @Override
    View read(java.sql.Connection connection) {
      return view;
    }
  }

  private static final class AllowedHandler extends BrowserHandler {
    AllowedHandler(BrowserStore store) {
      super(store, null);
    }

    @Override
    <T> T authorize(
        lab.backend.content.identityaccess.AdmissionWire.Request request,
        lab.backend.content.identityaccess.AdmissionGate.Work<T> work) {
      try {
        return work.run(null, java.util.UUID.fromString("00000000-0000-0000-0000-000000000011"));
      } catch (java.sql.SQLException failure) {
        throw new AssertionError(failure);
      }
    }
  }
}
