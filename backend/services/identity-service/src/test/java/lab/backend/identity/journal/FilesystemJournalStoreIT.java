package lab.backend.identity.journal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
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
    int writerCount = 2;
    Path barrier = Files.createDirectory(temporary.resolve("concurrent-barrier"));
    Path space = Files.createDirectories(temporary.resolve("lab"));
    Path publicationLock = space.resolve("publish.lock");
    List<Process> writers = new ArrayList<>();
    boolean prepared = false;
    try (FileChannel lockChannel = FileChannel.open(
        publicationLock,
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.WRITE);
        FileLock ignored = lockChannel.lock()) {
      for (int index = 0; index < writerCount; index++) {
        writers.add(start(temporary, "writer-" + index, barrier, "append"));
      }
      releaseBarrier(barrier, writerCount);
      await(
          () -> countFiles(space.resolve("heads"), ".tmp") >= writerCount,
          "writers did not reach the same pre-publication head");
      prepared = true;
    } finally {
      if (!prepared) {
        writers.stream().filter(Process::isAlive).forEach(Process::destroyForcibly);
      }
    }

    int retries = 0;
    List<String> verifiedHeads = new ArrayList<>();
    try {
      for (Process writer : writers) {
        String output = waitFor(writer, 0);
        String[] fields = output.trim().split(":");
        assertEquals(3, fields.length, output);
        verifiedHeads.add(fields[1]);
        retries += Integer.parseInt(fields[2]);
      }
    } finally {
      writers.stream().filter(Process::isAlive).forEach(Process::destroyForcibly);
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
    retryAfterCrash(crashAfterRecordBeforeNode("crash-record", "after-record-item"), false);
    retryAfterCrash(crashAfterNodeBeforePublish("crash-node", "after-node-item"), false);
    retryAfterCrash(crashAfterPublishBeforeReceipt("crash-head", "after-head-item"), true);
    evidence(
        "crash faultPoints=after-record-before-node,after-node-before-publish,"
            + "after-publish-before-receipt cleanup=true");
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
    var store = new FilesystemJournalStore(root.toAbsolutePath());
    var seed = store.append("lab", "seed", "payload-seed-orphan".getBytes(UTF_8));
    byte[] payload = "payload-orphan-item".getBytes(UTF_8);
    Files.write(root.resolve("lab/records/orphan-item.bin"), payload);
    byte[] node = ("lab\n2\n" + seed.verifiedHeadDigest() + "\norphan-item\n"
        + sha(payload) + "\n").getBytes(UTF_8);
    Files.write(root.resolve("lab/nodes").resolve(sha(node) + ".node"), node);

    var beforeRetry = store.inventory("lab", seed.verifiedHeadDigest());
    assertEquals(List.of("orphan-item"), beforeRetry.orphanRecordIds());
    assertEquals(1, beforeRetry.orphanNodeDigests().size());
    assertEquals(1, beforeRetry.entries().size());

    var receipt = store.append("lab", "orphan-item", payload);
    var afterRetry = store.inventory("lab", receipt.verifiedHeadDigest());
    assertEquals(2, afterRetry.entries().size());
    assertTrue(afterRetry.orphanRecordIds().isEmpty());
    assertTrue(afterRetry.orphanNodeDigests().isEmpty());
    evidence(
        "orphan fixedHead=" + receipt.verifiedHeadDigest()
            + " inventoryDigest=" + afterRetry.inventoryDigest()
            + " beforeAckRecords=1 beforeAckNodes=1");
  }

  private CrashState crashAfterRecordBeforeNode(String name, String id) throws Exception {
    CrashState state = seededCrashState(name, id);
    Path root = state.root();
    Path barrier = Files.createDirectory(root.resolve("barrier"));
    Path currentHead = root.resolve("lab/heads").resolve(headSlot(1));
    try (FileChannel headChannel = FileChannel.open(
        currentHead,
        java.nio.file.StandardOpenOption.READ,
        java.nio.file.StandardOpenOption.WRITE);
        FileLock ignored = headChannel.lock()) {
      Process process = start(root, id, barrier, "append");
      releaseBarrier(barrier, 1);
      await(
          () -> recordMatches(root.resolve("lab/records").resolve(id + ".bin"), id),
          "actual adapter did not reach the after-record boundary");
      destroyAndWait(process);
    }

    var inventory = state.store().inventory("lab", state.seed().verifiedHeadDigest());
    assertEquals(List.of(id), inventory.orphanRecordIds());
    assertTrue(inventory.orphanNodeDigests().isEmpty());
    assertEquals(1, inventory.entries().size());
    return state;
  }

  private CrashState crashAfterNodeBeforePublish(String name, String id) throws Exception {
    CrashState state = seededCrashState(name, id);
    Path root = state.root();
    Path barrier = Files.createDirectory(root.resolve("barrier"));
    Path publicationLock = root.resolve("lab/publish.lock");
    try (FileChannel lockChannel = FileChannel.open(
        publicationLock,
        java.nio.file.StandardOpenOption.READ,
        java.nio.file.StandardOpenOption.WRITE);
        FileLock ignored = lockChannel.lock()) {
      Process process = start(root, id, barrier, "append");
      releaseBarrier(barrier, 1);
      await(
          () -> countFiles(root.resolve("lab/nodes"), ".node") >= 2
              && countFiles(root.resolve("lab/heads"), ".tmp") >= 1,
          "actual adapter did not stage a complete head before publication");
      destroyAndWait(process);
    }

    var inventory = state.store().inventory("lab", state.seed().verifiedHeadDigest());
    assertEquals(List.of(id), inventory.orphanRecordIds());
    assertEquals(1, inventory.orphanNodeDigests().size());
    assertEquals(1, inventory.entries().size());
    return state;
  }

  private CrashState crashAfterPublishBeforeReceipt(String name, String id) throws Exception {
    CrashState state = seededCrashState(name, id);
    Path root = state.root();
    Path barrier = Files.createDirectory(root.resolve("barrier"));
    Path currentHead = root.resolve("lab/heads").resolve(headSlot(1));
    Path record = root.resolve("lab/records").resolve(id + ".bin");
    try (FileChannel headChannel = FileChannel.open(
        currentHead,
        java.nio.file.StandardOpenOption.READ,
        java.nio.file.StandardOpenOption.WRITE);
        FileLock headLock = headChannel.lock()) {
      Process process = start(root, id, barrier, "after-publish");
      releaseBarrier(barrier, 1);
      await(() -> recordMatches(record, id), "actual adapter did not create the crash record");
      try (FileChannel recordChannel = openForLock(record);
          FileLock ignored = recordChannel.lock()) {
        headLock.release();
        waitFor(process, 73);
      }
    }

    String publishedHead = latestHeadDigest(root);
    var inventory = state.store().inventory("lab", publishedHead);
    assertEquals(2, inventory.entries().size());
    assertEquals(id, inventory.entries().get(1).recordId());
    return new CrashState(root, state.store(), state.seed(), id, publishedHead);
  }

  private CrashState seededCrashState(String name, String id) throws Exception {
    Path root = Files.createDirectory(temporary.resolve(name));
    var store = new FilesystemJournalStore(root.toAbsolutePath());
    var seed = store.append("lab", "seed", ("payload-seed-" + name).getBytes(UTF_8));
    return new CrashState(root, store, seed, id, null);
  }

  private void retryAfterCrash(CrashState state, boolean alreadyPublished) throws Exception {
    var retry = state.store().append(
        "lab",
        state.id(),
        ("payload-" + state.id()).getBytes(UTF_8));
    assertEquals(2, retry.sequence());
    if (alreadyPublished) {
      assertEquals(state.publishedHead(), retry.verifiedHeadDigest());
    }
    var inventory = state.store().inventory("lab", retry.verifiedHeadDigest());
    assertEquals(2, inventory.entries().size());
    assertEquals("seed", inventory.entries().get(0).recordId());
    assertEquals(state.id(), inventory.entries().get(1).recordId());
  }

  private FileChannel openForLock(Path path) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (true) {
      try {
        return FileChannel.open(
            path,
            java.nio.file.StandardOpenOption.READ,
            java.nio.file.StandardOpenOption.WRITE);
      } catch (IOException error) {
        if (System.nanoTime() >= deadline) {
          throw error;
        }
        Thread.onSpinWait();
      }
    }
  }

  private long countFiles(Path directory, String suffix) throws Exception {
    if (!Files.isDirectory(directory)) {
      return 0;
    }
    try (var paths = Files.list(directory)) {
      return paths.filter(path -> path.getFileName().toString().endsWith(suffix)).count();
    }
  }

  private boolean recordMatches(Path record, String id) {
    try {
      return Files.exists(record)
          && Arrays.equals(Files.readAllBytes(record), ("payload-" + id).getBytes(UTF_8));
    } catch (IOException incompleteWrite) {
      return false;
    }
  }

  private void await(CheckedCondition condition, String message) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
    while (!condition.evaluate()) {
      if (System.nanoTime() >= deadline) {
        throw new AssertionError(message);
      }
      Thread.onSpinWait();
    }
  }

  private void destroyAndWait(Process process) throws Exception {
    process.destroyForcibly();
    assertTrue(process.waitFor(10, TimeUnit.SECONDS), "owned crash process did not exit");
    assertTrue(process.exitValue() != 0, "forced crash unexpectedly returned success");
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
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
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

  @FunctionalInterface
  private interface CheckedCondition {
    boolean evaluate() throws Exception;
  }

  private record Seed(
      Path root,
      FilesystemJournalStore store,
      JournalStore.Receipt receipt) {}

  private record CrashState(
      Path root,
      FilesystemJournalStore store,
      JournalStore.Receipt seed,
      String id,
      String publishedHead) {}
}
