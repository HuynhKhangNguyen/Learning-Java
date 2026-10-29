package lab.backend.identity.session;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lab.backend.identity.transport.WireCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Chỉ chứng minh policy/parser; không giả DB lock, session/CSRF hay mTLS thật. */
class CanonicalAdmissionWireTests {
  private static final String ORIGIN = "https://127.0.0.1";
  // Handle/CSRF cố định là dữ liệu giả, không phải credential thật từ lab/user.
  private static final String HANDLE = "C".repeat(43), CSRF = "D".repeat(43);
  private static final String NONCE = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
  private static final String CONTEXT = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
  private static final String DRAFT = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";

  private static Map<String,Object> read() {
    var value = new LinkedHashMap<String,Object>();
    value.put("schemaVersion", 1); value.put("action", "CONTENT_BODY_READ");
    value.put("audience", "content"); value.put("requestNonce", NONCE);
    value.put("sessionCredential", HANDLE); value.put("expectedContextId", CONTEXT);
    value.put("method", "GET"); value.put("canonicalPath", "/api/v1/topics/TOP-JAVA-COLLECTIONS/units/unit-1");
    value.put("query", ""); value.put("bodySha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    // Vector được tính độc lập từ exact text của contract, không gọi helper của parser.
    value.put("requestBinding", "c3c05e633cb6ea1d56f991e8ad8faf1b312213945fcd672991d311f61dd1ce55");
    return value;
  }

  private static Map<String,Object> mutation() {
    var value = read(); value.put("action", "LEARNING_DRAFT_SAVE"); value.put("audience", "learning");
    value.put("method", "PUT"); value.put("canonicalPath", "/api/v1/drafts/" + DRAFT);
    value.put("bodySha256", "7004feca5f5c4b62ee75b57a84b95b1b231b4abc7574a04e0a47ccae2fa0fb14");
    value.put("requestBinding", "1fd9e68ce4feec9b37f426dd73531c251d6253ca3e237bcccdb8836f964fb62a");
    value.put("csrfToken", CSRF); value.put("origin", ORIGIN);
    return value;
  }
  private static byte[] bytes(Map<String,Object> value) { return WireCodec.JSON.writeValueAsBytes(value); }

  @Test void readContractVectorRetainsNonceContextAndRequestBindingWithoutAuthority() {
    var request = CanonicalAdmissionWire.parse(bytes(read()), "content", ORIGIN);
    assertThat(request.caller()).isEqualTo("content"); assertThat(request.action()).isEqualTo("CONTENT_BODY_READ");
    assertThat(request.nonce()).isEqualTo(UUID.fromString(NONCE)); assertThat(request.context()).isEqualTo(UUID.fromString(CONTEXT));
    assertThat(request.binding()).isEqualTo(read().get("requestBinding"));
    assertThat(request.mutation()).isFalse(); assertThat(request.csrfToken()).isNull();
    assertThat(request.toString()).doesNotContain(HANDLE, CSRF);
  }

  @Test void mutationContractVectorRetainsMetadataButDoesNotVerifySynchronizerToken() {
    var request = CanonicalAdmissionWire.parse(bytes(mutation()), "learning", ORIGIN);
    assertThat(request.action()).isEqualTo("LEARNING_DRAFT_SAVE"); assertThat(request.mutation()).isTrue();
    assertThat(request.csrfToken()).isEqualTo(CSRF); assertThat(request.credential()).isEqualTo(HANDLE);
    assertThat(request.canonicalPath()).isEqualTo("/api/v1/drafts/" + DRAFT);
    assertThat(request.binding()).isEqualTo(mutation().get("requestBinding"));
    assertThat(request.toString()).doesNotContain(HANDLE, CSRF);
  }

  @ParameterizedTest @ValueSource(strings={"edge", "identity", "operator", "browser", "CONTENT", ""})
  void onlyVerifiedNamedParticipantMayRequestAdmission(String caller) {
    assertThatThrownBy(() -> CanonicalAdmissionWire.parse(bytes(read()), caller, ORIGIN))
        .isInstanceOf(WireCodec.Denied.class).hasMessage("ADMISSION_CALLER_DENIED");
  }
  @Test void missingVerifiedCallerDoesNotFallBackToAudienceInBody() {
    assertThatThrownBy(() -> CanonicalAdmissionWire.parse(bytes(read()), null, ORIGIN))
        .isInstanceOf(WireCodec.Denied.class).hasMessage("ADMISSION_CALLER_DENIED");
  }

  @ParameterizedTest @ValueSource(strings={"owner", "missingNonce", "uppercaseNonce", "numericContext",
      "scalarCredential", "shortCredential", "wrongAudience", "crossAction", "numericVersion", "futureVersion",
      "method", "query", "encodedPath", "traversal", "semicolon", "longTopic", "nonEmptyBody",
      "badBodyHash", "wrongBinding", "invalidBinding", "duplicate", "trailing"})
  void invalidReadNeverBecomesAnAllowedRequestShape(String kind) {
    var value = read();
    switch (kind) {
      case "owner" -> value.put("owner", UUID.randomUUID().toString());
      case "missingNonce" -> value.remove("requestNonce");
      case "uppercaseNonce" -> value.put("requestNonce", NONCE.toUpperCase(java.util.Locale.ROOT));
      case "numericContext" -> value.put("expectedContextId", 1);
      case "scalarCredential" -> value.put("sessionCredential", true);
      case "shortCredential" -> value.put("sessionCredential", "too-short");
      case "wrongAudience" -> value.put("audience", "learning");
      case "crossAction" -> value.put("action", "LEARNING_DRAFT_SAVE");
      case "numericVersion" -> value.put("schemaVersion", 1.0);
      case "futureVersion" -> value.put("schemaVersion", 2);
      case "method" -> value.put("method", "POST");
      case "query" -> value.put("query", "owner=forged");
      case "encodedPath" -> value.put("canonicalPath", "/api/v1/topics/%54OP/units/unit-1");
      case "traversal" -> value.put("canonicalPath", "/api/v1/topics/../units/unit-1");
      case "semicolon" -> value.put("canonicalPath", "/api/v1/topics/TOP;param/units/unit-1");
      case "longTopic" -> value.put("canonicalPath", "/api/v1/topics/" + "T".repeat(97) + "/units/unit-1");
      case "nonEmptyBody" -> value.put("bodySha256", mutation().get("bodySha256"));
      case "badBodyHash" -> value.put("bodySha256", "not-a-digest");
      case "wrongBinding" -> value.put("requestBinding", "0".repeat(64));
      case "invalidBinding" -> value.put("requestBinding", 123);
      default -> { }
    }
    // Kẻ gọi có thể tự tính lại SHA sau khi đổi route/method/body/query. Những
    // ca này phải bị policy chặn dù binding mới hoàn toàn đúng theo input xấu.
    if (java.util.Set.of("method", "query", "encodedPath", "traversal", "semicolon", "longTopic", "nonEmptyBody").contains(kind)) rebind(value);
    String raw = WireCodec.JSON.writeValueAsString(value);
    if (kind.equals("duplicate")) raw = raw.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1");
    if (kind.equals("trailing")) raw += "{}";
    final byte[] input = raw.getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> CanonicalAdmissionWire.parse(input, "content", ORIGIN)).isInstanceOf(WireCodec.Denied.class);
  }

  @ParameterizedTest @ValueSource(strings={"missingCsrf", "malformedCsrf", "wrongOrigin", "missingConfiguredOrigin",
      "badDraftId", "uppercaseDraftId", "query", "extendedPath", "readAction", "wrongAudience", "method", "bodyHashNumber"})
  void mutationCannotUseReadPolicyOrUnboundOriginCsrfMetadata(String kind) {
    var value = mutation();
    switch (kind) {
      case "missingCsrf" -> value.remove("csrfToken");
      case "malformedCsrf" -> value.put("csrfToken", "bad-token");
      case "wrongOrigin" -> value.put("origin", "https://untrusted.example");
      case "badDraftId" -> value.put("canonicalPath", "/api/v1/drafts/not-uuid");
      case "uppercaseDraftId" -> value.put("canonicalPath", "/api/v1/drafts/" + DRAFT.toUpperCase(java.util.Locale.ROOT));
      case "query" -> value.put("query", "owner=forged");
      case "extendedPath" -> value.put("canonicalPath", "/api/v1/drafts/" + DRAFT + "/receipt");
      case "readAction" -> value.put("action", "CONTENT_BODY_READ");
      case "wrongAudience" -> value.put("audience", "content");
      case "method" -> value.put("method", "GET");
      case "bodyHashNumber" -> value.put("bodySha256", 1);
      default -> { }
    }
    if (java.util.Set.of("badDraftId", "uppercaseDraftId", "query", "extendedPath", "method").contains(kind)) rebind(value);
    String configured = kind.equals("missingConfiguredOrigin") ? null : ORIGIN;
    assertThatThrownBy(() -> CanonicalAdmissionWire.parse(bytes(value), "learning", configured)).isInstanceOf(WireCodec.Denied.class);
  }

  @Test void malformedUtf8AndOversizeBodyAreDeniedBeforeCanonicalWork() {
    assertThatThrownBy(() -> CanonicalAdmissionWire.parse(new byte[]{(byte)0xc3, 0x28}, "content", ORIGIN))
        .isInstanceOf(WireCodec.Denied.class);
    assertThatThrownBy(() -> CanonicalAdmissionWire.parse(new byte[8193], "content", ORIGIN))
        .isInstanceOf(WireCodec.Denied.class).hasMessage("PAYLOAD_TOO_LARGE");
    assertThatThrownBy(() -> CanonicalAdmissionWire.parse(null, "content", ORIGIN))
        .isInstanceOf(WireCodec.Denied.class).hasMessage("BAD_WIRE");
  }

  /** Chỉ dùng tạo input tấn công đã rebind, không dùng làm oracle cho positive vector. */
  private static void rebind(Map<String,Object> value) {
    String input = value.get("method") + "\n" + value.get("canonicalPath") + "\n" + value.get("query") + "\n" + value.get("bodySha256");
    try { value.put("requestBinding", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)))); }
    catch (java.security.NoSuchAlgorithmException unavailable) { throw new AssertionError(unavailable); }
  }
}
