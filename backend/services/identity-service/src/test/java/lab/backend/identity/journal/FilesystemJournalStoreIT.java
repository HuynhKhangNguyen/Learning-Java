package lab.backend.identity.journal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemJournalStoreIT {
  private static final String ZERO = "0".repeat(64);

  @TempDir
  Path temporary;

  @Test
  void immutableIdempotentSnapshotAndCorruptionFailClosed() throws Exception {
    var store = new FilesystemJournalStore(temporary.toAbsolutePath());
    var first = store.append("lab", "one", "alpha".getBytes(UTF_8));
    var idempotent = store.append("lab", "one", "alpha".getBytes(UTF_8));
    assertEquals(first.recordDigest(), idempotent.recordDigest());
    assertEquals(first.sequence(), idempotent.sequence());
    assertEquals(first.nodeDigest(), idempotent.nodeDigest());
    assertEquals(
        JournalStore.ErrorCode.CONFLICT,
        assertThrows(
            JournalStore.JournalException.class,
            () -> store.append("lab", "one", "changed".getBytes(UTF_8)))
            .code());

    var snapshot = store.inventory("lab", first.verifiedHeadDigest());
    store.append("lab", "two", "beta".getBytes(UTF_8));
    assertEquals(snapshot, store.inventory("lab", first.verifiedHeadDigest()));

    Files.writeString(temporary.resolve("lab/records/one.bin"), "corrupt", UTF_8);
    assertCorrupt(() -> store.inventory("lab", first.verifiedHeadDigest()));
    evidence(
        "snapshot fixedHead=" + first.verifiedHeadDigest()
            + " inventoryDigest=" + snapshot.inventoryDigest());
  }

  @Test
  void concurrentProcessesExerciseCasLoserRetryWithoutLostRecord() throws Exception {
    int writerCount = 12;
    Path barrier = Files.createDirectory(temporary.resolve("concurrent-barrier"));
    List<Process> writers = new ArrayList<>();
    for (int index = 0; index < writerCount; index++) {
      writers.add(start(temporary, "writer-" + index, barrier, "append"));
    }
    releaseBarrier(barrier, writerCount);

    int retries = 0;
    List<String> verifiedHeads = new ArrayList<>();
    for (Process writer : writers) {
      String output = waitFor(writer, 0);
      String[] fields = output.trim().split(":");
      assertEquals(3, fields.length, output);
      verifiedHeads.add(fields[1]);
      retries += Integer.parseInt(fields[2]);
    }

    var store = new FilesystemJournalStore(temporary.toAbsolutePath());
    String fixedHead = latestHeadDigest(temporary);
    var inventory = store.inventory("lab", fixedHead);
    assertEquals(writerCount, inventory.entries().size());
    assertEquals(
        writerCount,
        inventory.entries().stream().map(JournalStore.Entry::recordId).distinct().count());
    for (String verifiedHead : verifiedHeads) {
      assertTrue(
          store.inventory("lab", verifiedHead).entries().size() > 0,
          "mỗi process receipt phải bind một verified prefix còn được final head cover");
    }
    assertTrue(retries > 0, "barrier writers must exercise at least one CAS loser");
    assertTrue(
        inventory.orphanNodeDigests().size() > 0,
        "CAS-loser candidate nodes must remain outside the verified prefix");
    evidence(
        "concurrency fixedHead=" + fixedHead
            + " inventoryDigest=" + inventory.inventoryDigest()
            + " casRetries=" + retries
            + " orphanNodes=" + inventory.orphanNodeDigests().size()
            + " cleanup=true");
  }

  @Test
  void processCrashBoundariesRemainUnacknowledgedThenConverge() throws Exception {
    crashAndRetry("after-record", 71);
    crashAndRetry("after-node", 72);
    crashAndRetry("after-head", 73);
    evidence("crash faultPoints=after-record,after-node,after-head cleanup=true");
  }

  @Test
  void missingRecordAndNodeFailClosedAsTypedCorruption() throws Exception {
    Seed missingRecord = seed("missing-record");
    Files.delete(missingRecord.root().resolve("lab/records/item.bin"));
    assertCorrupt(() -> missingRecord.store().inventory("lab", missingRecord.receipt().verifiedHeadDigest()));

    Seed missingNode = seed("missing-node");
    Files.delete(
        missingNode.root().resolve("lab/nodes")
            .resolve(missingNode.receipt().nodeDigest() + ".node"));
    assertCorrupt(() -> missingNode.store().inventory("lab", missingNode.receipt().verifiedHeadDigest()));
    evidence("corruption outcomes=missing-record,missing-node");
  }

  @Test
  void malformedSequenceNamespaceAndPredecessorFailClosed() throws Exception {
    Seed sequence = seed("bad-sequence");
    replaceCurrentNode(sequence, "lab", "not-a-number", ZERO);
    assertCorrupt(() -> sequence.store().inventory("lab", latestHeadDigest(sequence.root())));

    Seed namespace = seed("bad-namespace");
    replaceCurrentNode(namespace, "other", "1", ZERO);
    assertCorrupt(() -> namespace.store().inventory("lab", latestHeadDigest(namespace.root())));

    Seed predecessor = seed("bad-predecessor");
    replaceCurrentNode(predecessor, "lab", "1", "1".repeat(64));
    assertCorrupt(() -> predecessor.store().inventory("lab", latestHeadDigest(predecessor.root())));

    Seed oversizedNode = seed("oversized-node");
    Files.write(
        oversizedNode.root().resolve("lab/nodes")
            .resolve(oversizedNode.receipt().nodeDigest() + ".node"),
        new byte[2_048]);
    assertCorrupt(() -> oversizedNode.store().inventory(
        "lab",
        oversizedNode.receipt().verifiedHeadDigest()));
    evidence(
        "corruption outcomes=malformed-sequence,wrong-namespace,wrong-predecessor,oversized-node");
  }

  @Test
  void forgedHeadAndGapFailClosed() throws Exception {
    Seed forged = seed("forged-head");
    assertCorrupt(() -> forged.store().inventory("lab", "f".repeat(64)));

    Path root = Files.createDirectory(temporary.resolve("head-gap"));
    Path heads = Files.createDirectories(root.resolve("lab/heads"));
    String digest = "a".repeat(64);
    Files.writeString(
        heads.resolve(headSlot(2)),
        "2\n" + digest + "\n" + ZERO + "\n",
        UTF_8);
    var store = new FilesystemJournalStore(root.toAbsolutePath());
    assertCorrupt(() -> store.inventory("lab", digest));

    Seed oversizedHead = seed("oversized-head");
    Files.write(
        oversizedHead.root().resolve("lab/heads").resolve(headSlot(1)),
        new byte[1_024]);
    assertCorrupt(() -> oversizedHead.store().inventory(
        "lab",
        oversizedHead.receipt().verifiedHeadDigest()));
    evidence("corruption outcomes=forged-head,head-gap,oversized-head");
  }

  @Test
  void traversalCollisionAndReparseEscapeAreRejected() throws Exception {
    Path root = Files.createDirectory(temporary.resolve("path-security"));
    var store = new FilesystemJournalStore(root.toAbsolutePath());
    assertInvalid(() -> store.append("../escape", "item", new byte[] {1}));
    assertInvalid(() -> store.append("Lab", "item", new byte[] {1}));
    assertInvalid(() -> store.append("lab", "C:-absolute", new byte[] {1}));

    Path reparse = Path.of(System.getProperty("journal.reparse.fixture", ""));
    assertTrue(Files.exists(reparse, java.nio.file.LinkOption.NOFOLLOW_LINKS));
    assertTrue(reparse.getFileName().toString().matches("[a-z0-9][a-z0-9._-]{0,127}"));
    var reparseStore = new FilesystemJournalStore(reparse.getParent().toAbsolutePath());
    assertInvalid(() -> reparseStore.append(
        reparse.getFileName().toString(),
        "escape",
        new byte[] {1}));
    evidence("path outcomes=traversal,collision,reparse");
  }

  @Test
  void orphanRecordAndNodeStayOutsideVerifiedPrefix() throws Exception {
    Path root = Files.createDirectory(temporary.resolve("orphan"));
    Path barrier = Files.createDirectory(root.resolve("barrier"));
    Process crash = start(root, "orphan-item", barrier, "after-node");
    releaseBarrier(barrier, 1);
    waitFor(crash, 72);

    var store = new FilesystemJournalStore(root.toAbsolutePath());
    var beforeRetry = store.inventory("lab", ZERO);
    assertEquals(List.of("orphan-item"), beforeRetry.orphanRecordIds());
    assertEquals(1, beforeRetry.orphanNodeDigests().size());
    assertEquals(0, beforeRetry.entries().size());

    var receipt = store.append("lab", "orphan-item", "payload-orphan-item".getBytes(UTF_8));
    var afterRetry = store.inventory("lab", receipt.verifiedHeadDigest());
    assertEquals(1, afterRetry.entries().size());
    assertTrue(afterRetry.orphanRecordIds().isEmpty());
    assertTrue(afterRetry.orphanNodeDigests().isEmpty());
    evidence(
        "orphan fixedHead=" + receipt.verifiedHeadDigest()
            + " inventoryDigest=" + afterRetry.inventoryDigest()
            + " beforeAckRecords=1 beforeAckNodes=1");
  }

  private void crashAndRetry(String mode, int expectedExit) throws Exception {
    Path root = Files.createDirectory(temporary.resolve(mode));
    Path barrier = Files.createDirectory(root.resolve("barrier"));
    String id = mode + "-item";
    Process crash = start(root, id, barrier, mode);
    releaseBarrier(barrier, 1);
    waitFor(crash, expectedExit);

    var store = new FilesystemJournalStore(root.toAbsolutePath());
    if (mode.equals("after-record")) {
      var inventory = store.inventory("lab", ZERO);
      assertEquals(List.of(id), inventory.orphanRecordIds());
      assertTrue(inventory.orphanNodeDigests().isEmpty());
    } else if (mode.equals("after-node")) {
      var inventory = store.inventory("lab", ZERO);
      assertEquals(List.of(id), inventory.orphanRecordIds());
      assertEquals(1, inventory.orphanNodeDigests().size());
    } else {
      var inventory = store.inventory("lab", latestHeadDigest(root));
      assertEquals(1, inventory.entries().size());
    }

    String publishedHead = mode.equals("after-head") ? latestHeadDigest(root) : null;
    var retry = store.append("lab", id, ("payload-" + id).getBytes(UTF_8));
    assertEquals(1, retry.sequence());
    if (publishedHead != null) {
      assertEquals(publishedHead, retry.verifiedHeadDigest());
    }
    assertEquals(1, store.inventory("lab", retry.verifiedHeadDigest()).entries().size());
  }

  private Seed seed(String name) throws Exception {
    Path root = Files.createDirectory(temporary.resolve(name));
    var store = new FilesystemJournalStore(root.toAbsolutePath());
    var receipt = store.append("lab", "item", ("payload-" + name).getBytes(UTF_8));
    return new Seed(root, store, receipt);
  }

  private void replaceCurrentNode(
      Seed seed,
      String namespace,
      String sequence,
      String previous) throws Exception {
    String recordDigest = seed.receipt().recordDigest();
    byte[] node = (namespace + "\n" + sequence + "\n" + previous + "\nitem\n"
        + recordDigest + "\n").getBytes(UTF_8);
    String digest = sha(node);
    Files.write(seed.root().resolve("lab/nodes").resolve(digest + ".node"), node);
    Files.writeString(
        seed.root().resolve("lab/heads").resolve(headSlot(1)),
        "1\n" + digest + "\n" + ZERO + "\n",
        UTF_8);
  }

  private Process start(Path root, String id, Path barrier, String mode) throws Exception {
    String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    return new ProcessBuilder(
        java,
        "-cp",
        System.getProperty("java.class.path"),
        JournalFixtureMain.class.getName(),
        root.toString(),
        id,
        barrier.toString(),
        mode)
        .redirectErrorStream(true)
        .start();
  }

  private void releaseBarrier(Path barrier, int expectedWriters) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (readyCount(barrier) != expectedWriters) {
      if (System.nanoTime() >= deadline) {
        throw new AssertionError("writers did not reach deterministic barrier");
      }
      Thread.onSpinWait();
    }
    Files.writeString(barrier.resolve("go"), "go", UTF_8);
  }

  private long readyCount(Path barrier) throws Exception {
    try (var paths = Files.list(barrier)) {
      return paths.filter(path -> path.getFileName().toString().endsWith(".ready")).count();
    }
  }

  private String waitFor(Process process, int expectedExit) throws Exception {
    if (!process.waitFor(30, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      process.waitFor(5, TimeUnit.SECONDS);
      throw new AssertionError("owned journal fixture did not exit");
    }
    String output = new String(process.getInputStream().readAllBytes(), UTF_8);
    assertEquals(expectedExit, process.exitValue(), output);
    return output;
  }

  private String latestHeadDigest(Path root) throws Exception {
    try (var paths = Files.list(root.resolve("lab/heads"))) {
      Path latest = paths
          .filter(path -> path.getFileName().toString().endsWith(".head"))
          .max(Comparator.comparing(path -> path.getFileName().toString()))
          .orElseThrow();
      return Files.readAllLines(latest, UTF_8).get(1);
    }
  }

  private static String headSlot(long sequence) {
    return String.format(java.util.Locale.ROOT, "%019d.head", sequence);
  }

  private static String sha(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private void evidence(String value) {
    String encodedRoot = Base64.getEncoder().encodeToString(
        temporary.toAbsolutePath().toString().getBytes(UTF_8));
    System.out.println(
        "R28A_EVIDENCE type=" + value + " fixtureRootBase64=" + encodedRoot);
  }

  private static void assertCorrupt(ThrowingCall call) {
    assertEquals(
        JournalStore.ErrorCode.CORRUPT,
        assertThrows(JournalStore.JournalException.class, call::run).code());
  }

  private static void assertInvalid(ThrowingCall call) {
    assertEquals(
        JournalStore.ErrorCode.INVALID_INPUT,
        assertThrows(JournalStore.JournalException.class, call::run).code());
  }

  @FunctionalInterface
  private interface ThrowingCall {
    void run() throws Exception;
  }

  private record Seed(
      Path root,
      FilesystemJournalStore store,
      JournalStore.Receipt receipt) {}
}
