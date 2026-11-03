package lab.backend.identity.journal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemJournalStoreIT {
  @TempDir Path temporary;

  @Test
  void immutableIdempotentSnapshotAndCorruptionFailClosed() throws Exception {
    var store = new FilesystemJournalStore(temporary.toAbsolutePath());
    var first = store.append("lab", "one", "alpha".getBytes(UTF_8));
    assertEquals(first, store.append("lab", "one", "alpha".getBytes(UTF_8)));
    assertEquals(JournalStore.ErrorCode.CONFLICT,
        assertThrows(JournalStore.JournalException.class,
            () -> store.append("lab", "one", "changed".getBytes(UTF_8))).code());
    var snapshot = store.inventory("lab", first.verifiedHeadDigest());
    store.append("lab", "two", "beta".getBytes(UTF_8));
    assertEquals(snapshot, store.inventory("lab", first.verifiedHeadDigest()));
    Files.writeString(temporary.resolve("lab/records/one.bin"), "corrupt", UTF_8);
    assertEquals(JournalStore.ErrorCode.CORRUPT,
        assertThrows(JournalStore.JournalException.class,
            () -> store.inventory("lab", first.verifiedHeadDigest())).code());
  }

  @Test
  void twoProcessesConvergeAndLostResponseRetryIsDurable() throws Exception {
    Path barrier = Files.createDirectory(temporary.resolve("barrier"));
    Process a = start("writer-a", barrier, "append");
    Process b = start("writer-b", barrier, "append");
    assertTrue(a.waitFor(20, TimeUnit.SECONDS)); assertTrue(b.waitFor(20, TimeUnit.SECONDS));
    assertEquals(0, a.exitValue()); assertEquals(0, b.exitValue());
    var store = new FilesystemJournalStore(temporary.toAbsolutePath());
    var inventory = store.inventory("lab", head());
    assertEquals(2, inventory.entries().size());

    Files.delete(barrier.resolve("writer-a.ready")); Files.delete(barrier.resolve("writer-b.ready"));
    Process crash = start("lost-response", barrier, "after-head");
    Files.writeString(barrier.resolve("companion.ready"), "ready", UTF_8);
    assertTrue(crash.waitFor(20, TimeUnit.SECONDS)); assertEquals(73, crash.exitValue());
    var retry = store.append("lab", "lost-response", "payload-lost-response".getBytes(UTF_8));
    assertEquals("lost-response", retry.recordId());
  }

  @Test
  void processCrashAfterRecordAndNodeRemainUnacknowledgedThenConverge() throws Exception {
    crashAndRetry("after-record", 71);
    crashAndRetry("after-node", 72);
  }

  private void crashAndRetry(String mode, int expectedExit) throws Exception {
    Path root = Files.createDirectory(temporary.resolve(mode));
    Path barrier = Files.createDirectory(root.resolve("barrier"));
    Process crash = start(root, mode + "-id", barrier, mode);
    Files.writeString(barrier.resolve("companion.ready"), "ready", UTF_8);
    assertTrue(crash.waitFor(20, TimeUnit.SECONDS));
    assertEquals(expectedExit, crash.exitValue());
    var store = new FilesystemJournalStore(root.toAbsolutePath());
    var retry = store.append("lab", mode + "-id", ("payload-" + mode + "-id").getBytes(UTF_8));
    assertEquals(1, retry.sequence());
  }

  private Process start(String id, Path barrier, String mode) throws Exception {
    return start(temporary, id, barrier, mode);
  }

  private Process start(Path root, String id, Path barrier, String mode) throws Exception {
    String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    return new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
        JournalFixtureMain.class.getName(), root.toString(), id, barrier.toString(), mode)
        .redirectErrorStream(true).start();
  }

  private String head() throws Exception {
    try (var paths = Files.list(temporary.resolve("lab/heads"))) {
      Path latest = paths.max(java.util.Comparator.comparingLong(path -> {
        try { return Long.parseLong(Files.readAllLines(path, UTF_8).getFirst()); }
        catch (Exception error) { throw new IllegalStateException(error); }
      })).orElseThrow();
      return Files.readAllLines(latest, UTF_8).get(1);
    }
  }
}
