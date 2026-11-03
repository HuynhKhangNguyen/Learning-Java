package lab.backend.learning.identityaccess;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import lab.backend.learning.transport.WireCodec;
import org.junit.jupiter.api.Test;

class AllocationAdmissionConsumerTests {
  @Test
  void readUsesExactReadFieldsAndRejectsMutationAllowOrCrossAction() {
    var request = AdmissionWire.Request.readAttempt("C".repeat(43), UUID.randomUUID(), UUID.randomUUID());
    var shape = WireCodec.strict(request.bytes());
    assertFalse(shape.has("csrfToken"));
    assertFalse(shape.has("origin"));
    assertEquals("GET", shape.path("method").asString());
    UUID realm = UUID.randomUUID();
    var allow = AdmissionWireTests.allow(request, realm);
    allow.put("action", "LEARNING_ATTEMPT_READ");
    allow.put("csrfVerified", false);
    Instant time = Instant.now();
    assertDoesNotThrow(() -> AdmissionWire.admission(
        WireCodec.JSON.writeValueAsBytes(allow), request, realm, time, time));
    allow.put("csrfVerified", true);
    assertThrows(WireCodec.Denied.class, () -> AdmissionWire.admission(
        WireCodec.JSON.writeValueAsBytes(allow), request, realm, time, time));
    allow.put("csrfVerified", false);
    allow.put("action", "LEARNING_DRAFT_SAVE");
    assertThrows(WireCodec.Denied.class, () -> AdmissionWire.admission(
        WireCodec.JSON.writeValueAsBytes(allow), request, realm, time, time));
  }
}
