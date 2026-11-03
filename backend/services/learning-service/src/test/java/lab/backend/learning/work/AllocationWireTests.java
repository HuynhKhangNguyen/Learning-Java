package lab.backend.learning.work;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AllocationWireTests {
  @Test
  void canonicalFingerprintIgnoresFormattingButRejectsPrivilegedAndAmbiguousFields() {
    var first = AllocationWire.create(bytes(
        "{\"schemaVersion\":1,\"exerciseId\":\"exercise-1\",\"requestedMode\":\"assessment\"}"));
    var second = AllocationWire.create(bytes(
        "{ \"requestedMode\": \"assessment\", \"exerciseId\": \"exercise-1\", \"schemaVersion\": 1 }"));
    assertEquals(first.fingerprint(), second.fingerprint());
    for (String field : new String[]{"owner", "case", "mode", "graderVersion", "passed", "epoch"}) {
      assertThrows(RuntimeException.class, () -> AllocationWire.create(bytes(
          "{\"schemaVersion\":1,\"exerciseId\":\"exercise-1\",\"requestedMode\":\"assessment\",\""
              + field + "\":true}")));
    }
    assertThrows(RuntimeException.class, () -> AllocationWire.create(bytes(
        "{\"schemaVersion\":1,\"exerciseId\":\"a\",\"exerciseId\":\"b\",\"requestedMode\":\"assessment\"}")));
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
