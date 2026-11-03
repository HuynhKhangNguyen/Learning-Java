package lab.backend.learning.journal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.DSYNC;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Local-host lab adapter. Mỗi immutable head slot là một filesystem CAS:
 * writer chỉ thắng khi tạo mới được slot của sequence kế tiếp.
 */
public final class FilesystemJournalStore implements JournalStore {
  private static final String ZERO = "0".repeat(64);
  private static final String DIGEST_PATTERN = "[0-9a-f]{64}";
  private static final String KEY_PATTERN = "[a-z0-9][a-z0-9._-]{0,127}";
  private static final int MAX_NODE_BYTES = 1_024;
  private static final int MAX_HEAD_BYTES = 256;

  private final Path root;

  public FilesystemJournalStore(Path explicitRoot) throws JournalException {
    if (explicitRoot == null || !explicitRoot.isAbsolute()) {
      throw failure(ErrorCode.INVALID_INPUT, "Journal root must be absolute");
    }
    try {
      Files.createDirectories(explicitRoot);
      root = explicitRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
      rejectReparse(root);
    } catch (JournalException error) {
      throw error;
    } catch (IOException error) {
      throw failure(ErrorCode.IO_FAILURE, "Cannot own journal root", error);
    }
  }

  @Override
  public Receipt append(String namespace, String recordId, byte[] bytes) throws JournalException {
    validateKey(namespace, "namespace");
    validateKey(recordId, "recordId");
    if (bytes == null) {
      throw failure(ErrorCode.INVALID_INPUT, "canonical bytes are required");
    }

    Path space = ensureDirectory(root.resolve(namespace));
    Path records = ensureDirectory(space.resolve("records"));
    Path nodes = ensureDirectory(space.resolve("nodes"));
    ensureDirectory(space.resolve("heads"));

    String recordDigest = sha(bytes);
    createRecord(records.resolve(recordId + ".bin"), bytes);
    int casRetries = 0;

    while (true) {
      Head head = readHead(space);
      Inventory existing = verify(space, namespace, head);
      for (Entry entry : existing.entries()) {
        if (entry.recordId().equals(recordId)) {
          if (!entry.recordDigest().equals(recordDigest)) {
            throw failure(ErrorCode.CONFLICT, "record ID already binds different bytes");
          }
          return receipt(namespace, entry, head.digest(), casRetries);
        }
      }

      if (head.sequence() == Long.MAX_VALUE) {
        throw failure(ErrorCode.CONFLICT, "journal sequence exhausted");
      }
      long sequence = head.sequence() + 1;
      byte[] nodeBytes = nodeBytes(namespace, sequence, head.digest(), recordId, recordDigest);
      String nodeDigest = sha(nodeBytes);
      createNode(nodes.resolve(nodeDigest + ".node"), nodeBytes);

      Head next = new Head(sequence, nodeDigest, head.digest());
      if (!publishHeadCas(space, next)) {
        casRetries++;
        continue;
      }

      Inventory verified = verify(space, namespace, next);
      Entry added = verified.entries().get(verified.entries().size() - 1);
      return receipt(namespace, added, next.digest(), casRetries);
    }
  }

  @Override
  public Inventory inventory(String namespace, String fixedHeadDigest) throws JournalException {
    validateKey(namespace, "namespace");
    validateDigest(fixedHeadDigest, "fixed head");
    Path space = owned(root.resolve(namespace));
    if (!Files.exists(space, LinkOption.NOFOLLOW_LINKS)) {
      if (!fixedHeadDigest.equals(ZERO)) {
        throw failure(ErrorCode.CORRUPT, "fixed head is not covered");
      }
      return emptyInventory(namespace);
    }
    rejectReparse(space);

    Head current = readHead(space);
    Head fixed = fixedHeadDigest.equals(ZERO)
        ? new Head(0, ZERO, ZERO)
        : findHead(space, namespace, fixedHeadDigest);
    Inventory inventory = verify(space, namespace, fixed);
    if (!fixed.digest().equals(current.digest())) {
      return new Inventory(
          inventory.namespace(),
          inventory.fixedHeadDigest(),
          inventory.inventoryDigest(),
          inventory.entries(),
          List.of(),
          List.of());
    }
    return inventory;
  }

  private Inventory verify(Path space, String namespace, Head head) throws JournalException {
    List<Entry> reversed = new ArrayList<>();
    Set<String> coveredRecords = new HashSet<>();
    Set<String> coveredNodes = new HashSet<>();
    String cursor = head.digest();
    long expected = head.sequence();

    while (expected > 0) {
      Node node = readNode(space, namespace, cursor, expected);
      byte[] record = readRequired(
          space.resolve("records").resolve(node.recordId() + ".bin"),
          "record is missing");
      if (!sha(record).equals(node.recordDigest())) {
        throw failure(ErrorCode.CORRUPT, "record digest mismatch");
      }
      if (!coveredRecords.add(node.recordId())) {
        throw failure(ErrorCode.CORRUPT, "duplicate record in prefix");
      }
      coveredNodes.add(cursor);
      reversed.add(new Entry(expected, node.recordId(), node.recordDigest(), cursor));
      cursor = node.previous();
      expected--;
    }
    if (!cursor.equals(ZERO)) {
      throw failure(ErrorCode.CORRUPT, "wrong predecessor");
    }

    List<Entry> entries = reversed.reversed();
    List<String> orphanRecords = listOrphanRecords(space, coveredRecords);
    List<String> orphanNodes = listOrphanNodes(space, coveredNodes);
    StringBuilder manifest = new StringBuilder(namespace)
        .append('\n')
        .append(head.digest())
        .append('\n');
    entries.forEach(entry -> manifest
        .append(entry.sequence())
        .append(':')
        .append(entry.recordId())
        .append(':')
        .append(entry.recordDigest())
        .append(':')
        .append(entry.nodeDigest())
        .append('\n'));
    return new Inventory(
        namespace,
        head.digest(),
        sha(manifest.toString().getBytes(UTF_8)),
        List.copyOf(entries),
        List.copyOf(orphanRecords),
        List.copyOf(orphanNodes));
  }

  private Node readNode(Path space, String namespace, String digest, long expectedSequence)
      throws JournalException {
    validateDigest(digest, "node digest");
    byte[] raw = readMetadata(
        space.resolve("nodes").resolve(digest + ".node"),
        "node is missing",
        MAX_NODE_BYTES);
    if (!sha(raw).equals(digest)) {
      throw failure(ErrorCode.CORRUPT, "node digest mismatch");
    }

    String[] fields = new String(raw, UTF_8).split("\\n", -1);
    if (fields.length != 6 || !fields[5].isEmpty()) {
      throw failure(ErrorCode.CORRUPT, "malformed node");
    }
    validateStoredKey(fields[0], "node namespace");
    validateStoredKey(fields[3], "node record ID");
    validateStoredDigest(fields[2], "node predecessor");
    validateStoredDigest(fields[4], "node record digest");
    if (!fields[0].equals(namespace)) {
      throw failure(ErrorCode.CORRUPT, "wrong node namespace");
    }

    long sequence;
    try {
      sequence = Long.parseLong(fields[1]);
    } catch (NumberFormatException error) {
      throw failure(ErrorCode.CORRUPT, "malformed node sequence", error);
    }
    if (sequence != expectedSequence) {
      throw failure(ErrorCode.CORRUPT, "node sequence gap");
    }
    return new Node(sequence, fields[2], fields[3], fields[4]);
  }

  private List<String> listOrphanRecords(Path space, Set<String> covered)
      throws JournalException {
    Path records = owned(space.resolve("records"));
    if (!Files.isDirectory(records, LinkOption.NOFOLLOW_LINKS)) {
      return List.of();
    }
    try (var paths = Files.list(records)) {
      List<String> result = new ArrayList<>();
      for (Path path : paths.toList()) {
        owned(path);
        String name = path.getFileName().toString();
        if (!name.matches(KEY_PATTERN + "\\.bin")) {
          throw failure(ErrorCode.CORRUPT, "malformed record filename");
        }
        String recordId = name.substring(0, name.length() - 4);
        if (!covered.contains(recordId)) {
          result.add(recordId);
        }
      }
      result.sort(String::compareTo);
      return result;
    } catch (JournalException error) {
      throw error;
    } catch (IOException error) {
      throw failure(ErrorCode.IO_FAILURE, "Cannot inventory records", error);
    }
  }

  private List<String> listOrphanNodes(Path space, Set<String> covered)
      throws JournalException {
    Path nodes = owned(space.resolve("nodes"));
    if (!Files.isDirectory(nodes, LinkOption.NOFOLLOW_LINKS)) {
      return List.of();
    }
    try (var paths = Files.list(nodes)) {
      List<String> result = new ArrayList<>();
      for (Path path : paths.toList()) {
        owned(path);
        String name = path.getFileName().toString();
        if (!name.matches(DIGEST_PATTERN + "\\.node")) {
          throw failure(ErrorCode.CORRUPT, "malformed node filename");
        }
        String digest = name.substring(0, name.length() - 5);
        if (!covered.contains(digest)) {
          result.add(digest);
        }
      }
      result.sort(String::compareTo);
      return result;
    } catch (JournalException error) {
      throw error;
    } catch (IOException error) {
      throw failure(ErrorCode.IO_FAILURE, "Cannot inventory nodes", error);
    }
  }

  private boolean publishHeadCas(Path space, Head head) throws JournalException {
    Path heads = ensureDirectory(space.resolve("heads"));
    byte[] bytes = headBytes(head);
    Path slot = owned(heads.resolve(headSlot(head.sequence())));
    try (FileChannel channel = FileChannel.open(
        slot,
        CREATE_NEW,
        WRITE,
        DSYNC,
        LinkOption.NOFOLLOW_LINKS);
        FileLock ignored = channel.lock()) {
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      while (buffer.hasRemaining()) {
        channel.write(buffer);
      }
      channel.force(true);
      forceDirectory(heads);
      return true;
    } catch (FileAlreadyExistsException conflict) {
      Head published = readPublishedHead(slot);
      if (published.sequence() != head.sequence()) {
        throw failure(ErrorCode.CORRUPT, "published head sequence changed");
      }
      return false;
    } catch (IOException error) {
      throw failure(ErrorCode.IO_FAILURE, "Head CAS failed", error);
    }
  }

  private Head readPublishedHead(Path requested) throws JournalException {
    Path slot = owned(requested);
    JournalException lastMalformed = null;
    for (int attempt = 0; attempt < 1_000; attempt++) {
      try (FileChannel channel = FileChannel.open(
          slot,
          java.nio.file.StandardOpenOption.READ,
          WRITE,
          LinkOption.NOFOLLOW_LINKS);
          FileLock ignored = channel.lock()) {
        long size = channel.size();
        if (size <= 0 || size > MAX_HEAD_BYTES) {
          throw failure(ErrorCode.CORRUPT, "malformed head size");
        }
        ByteBuffer buffer = ByteBuffer.allocate((int) size);
        while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
          // Đọc hết immutable slot sau khi CAS winner nhả lock.
        }
        return parseHead(buffer.array());
      } catch (JournalException malformed) {
        lastMalformed = malformed;
      } catch (IOException error) {
        throw failure(ErrorCode.IO_FAILURE, "Cannot read published head", error);
      }
      Thread.yield();
    }
    throw failure(ErrorCode.CORRUPT, "published head remained incomplete", lastMalformed);
  }

  private Head readHead(Path space) throws JournalException {
    Path heads = owned(space.resolve("heads"));
    if (!Files.isDirectory(heads, LinkOption.NOFOLLOW_LINKS)) {
      return new Head(0, ZERO, ZERO);
    }

    TreeMap<Long, Head> slots = new TreeMap<>();
    try (var paths = Files.list(heads)) {
      for (Path path : paths.toList()) {
        owned(path);
        String name = path.getFileName().toString();
        if (!name.matches("[0-9]{19}\\.head")) {
          throw failure(ErrorCode.CORRUPT, "malformed head filename");
        }
        long slotSequence = Long.parseLong(name.substring(0, 19));
        Head head = readPublishedHead(path);
        if (slotSequence != head.sequence() || slots.put(slotSequence, head) != null) {
          throw failure(ErrorCode.CORRUPT, "head slot collision");
        }
      }
    } catch (JournalException error) {
      throw error;
    } catch (NumberFormatException error) {
      throw failure(ErrorCode.CORRUPT, "malformed head sequence", error);
    } catch (IOException error) {
      throw failure(ErrorCode.IO_FAILURE, "Cannot read head inventory", error);
    }

    if (slots.isEmpty()) {
      return new Head(0, ZERO, ZERO);
    }
    long latestSequence = slots.lastKey();
    if (latestSequence != slots.size()) {
      throw failure(ErrorCode.CORRUPT, "head sequence gap");
    }
    String previous = ZERO;
    for (var entry : slots.entrySet()) {
      Head head = entry.getValue();
      if (!head.previous().equals(previous)) {
        throw failure(ErrorCode.CORRUPT, "head predecessor mismatch");
      }
      previous = head.digest();
    }
    return slots.lastEntry().getValue();
  }

  private Head parseHead(byte[] bytes) throws JournalException {
    String[] fields = new String(bytes, UTF_8).split("\\n", -1);
    if (fields.length != 4 || !fields[3].isEmpty()) {
      throw failure(ErrorCode.CORRUPT, "malformed head");
    }
    long sequence;
    try {
      sequence = Long.parseLong(fields[0]);
    } catch (NumberFormatException error) {
      throw failure(ErrorCode.CORRUPT, "malformed head sequence", error);
    }
    validateStoredDigest(fields[1], "head digest");
    validateStoredDigest(fields[2], "head predecessor");
    return new Head(sequence, fields[1], fields[2]);
  }

  private Head findHead(Path space, String namespace, String digest) throws JournalException {
    Head current = readHead(space);
    String cursor = current.digest();
    long sequence = current.sequence();
    while (sequence > 0) {
      if (cursor.equals(digest)) {
        Node node = readNode(space, namespace, cursor, sequence);
        return new Head(sequence, cursor, node.previous());
      }
      Node node = readNode(space, namespace, cursor, sequence);
      cursor = node.previous();
      sequence--;
    }
    throw failure(ErrorCode.CORRUPT, "fixed head is not covered");
  }

  private void createRecord(Path requested, byte[] bytes) throws JournalException {
    Path target = owned(requested);
    try {
      writeNew(target, bytes, ErrorCode.IO_FAILURE, "Cannot create record");
    } catch (JournalException error) {
      if (!(error.getCause() instanceof FileAlreadyExistsException)) {
        throw error;
      }
      byte[] stored = readRequired(target, "record disappeared");
      if (!Arrays.equals(stored, bytes)) {
        throw failure(ErrorCode.CONFLICT, "record ID already binds different bytes");
      }
    }
  }

  private void createNode(Path requested, byte[] bytes) throws JournalException {
    Path target = owned(requested);
    try {
      writeNew(target, bytes, ErrorCode.IO_FAILURE, "Cannot create node");
    } catch (JournalException error) {
      if (!(error.getCause() instanceof FileAlreadyExistsException)) {
        throw error;
      }
      byte[] stored = readRequired(target, "node disappeared");
      if (!Arrays.equals(stored, bytes)) {
        throw failure(ErrorCode.CORRUPT, "immutable node conflict");
      }
    }
  }

  private void writeNew(Path target, byte[] bytes, ErrorCode code, String message)
      throws JournalException {
    Path safe = owned(target);
    try (FileChannel channel = FileChannel.open(
        safe,
        CREATE_NEW,
        WRITE,
        DSYNC,
        LinkOption.NOFOLLOW_LINKS)) {
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      while (buffer.hasRemaining()) {
        channel.write(buffer);
      }
      channel.force(true);
    } catch (IOException error) {
      throw failure(code, message, error);
    }
    owned(safe);
  }

  private byte[] readRequired(Path requested, String missingMessage) throws JournalException {
    Path safe = owned(requested);
    try {
      byte[] bytes = Files.readAllBytes(safe);
      owned(safe);
      return bytes;
    } catch (NoSuchFileException error) {
      throw failure(ErrorCode.CORRUPT, missingMessage, error);
    } catch (IOException error) {
      throw failure(ErrorCode.IO_FAILURE, "Cannot read journal object", error);
    }
  }

  private byte[] readMetadata(Path requested, String missingMessage, int maximumBytes)
      throws JournalException {
    Path safe = owned(requested);
    try {
      long size = Files.size(safe);
      if (size <= 0 || size > maximumBytes) {
        throw failure(ErrorCode.CORRUPT, "journal metadata size is invalid");
      }
      return readRequired(safe, missingMessage);
    } catch (JournalException error) {
      throw error;
    } catch (NoSuchFileException error) {
      throw failure(ErrorCode.CORRUPT, missingMessage, error);
    } catch (IOException error) {
      throw failure(ErrorCode.IO_FAILURE, "Cannot inspect journal metadata", error);
    }
  }

  private Path ensureDirectory(Path requested) throws JournalException {
    Path safe = owned(requested);
    try {
      Files.createDirectory(safe);
    } catch (FileAlreadyExistsException exists) {
      // Kiểm tra type/reparse bên dưới; không chấp nhận collision với file/link.
    } catch (IOException error) {
      throw failure(ErrorCode.IO_FAILURE, "Cannot create journal directory", error);
    }
    safe = owned(safe);
    if (!Files.isDirectory(safe, LinkOption.NOFOLLOW_LINKS)) {
      throw failure(ErrorCode.INVALID_INPUT, "journal path collides with non-directory");
    }
    rejectReparse(safe);
    return safe;
  }

  private Path owned(Path candidate) throws JournalException {
    Path normalized = candidate.toAbsolutePath().normalize();
    if (!normalized.startsWith(root)) {
      throw failure(ErrorCode.INVALID_INPUT, "path escapes journal root");
    }
    Path cursor = root;
    for (Path part : root.relativize(normalized)) {
      cursor = cursor.resolve(part);
      if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
        rejectReparse(cursor);
      }
    }
    return normalized;
  }

  private static void rejectReparse(Path path) throws JournalException {
    try {
      var attributes = Files.readAttributes(
          path,
          java.nio.file.attribute.BasicFileAttributes.class,
          LinkOption.NOFOLLOW_LINKS);
      if (Files.isSymbolicLink(path) || attributes.isOther()) {
        throw failure(ErrorCode.INVALID_INPUT, "symlink/reparse path is not owned");
      }
    } catch (JournalException error) {
      throw error;
    } catch (IOException error) {
      throw failure(ErrorCode.IO_FAILURE, "Cannot inspect owned path", error);
    }
  }

  private static void forceDirectory(Path directory) {
    try (FileChannel channel = FileChannel.open(directory, java.nio.file.StandardOpenOption.READ)) {
      channel.force(true);
    } catch (IOException unsupportedDirectoryFsync) {
      // Windows không hỗ trợ directory channel; claim vẫn chỉ giới hạn local-host lab.
    }
  }

  private static Inventory emptyInventory(String namespace) {
    String manifest = namespace + "\n" + ZERO + "\n";
    return new Inventory(
        namespace,
        ZERO,
        sha(manifest.getBytes(UTF_8)),
        List.of(),
        List.of(),
        List.of());
  }

  private static byte[] nodeBytes(
      String namespace,
      long sequence,
      String previous,
      String recordId,
      String recordDigest) {
    return (namespace + "\n" + sequence + "\n" + previous + "\n" + recordId + "\n"
        + recordDigest + "\n").getBytes(UTF_8);
  }

  private static byte[] headBytes(Head head) {
    return (head.sequence() + "\n" + head.digest() + "\n" + head.previous() + "\n")
        .getBytes(UTF_8);
  }

  private static Receipt receipt(
      String namespace,
      Entry entry,
      String head,
      int casRetries) {
    return new Receipt(
        namespace,
        entry.recordId(),
        entry.recordDigest(),
        entry.sequence(),
        entry.nodeDigest(),
        head,
        casRetries);
  }

  private static String headSlot(long sequence) {
    return String.format(java.util.Locale.ROOT, "%019d.head", sequence);
  }

  private static void validateKey(String value, String label) throws JournalException {
    if (value == null || !value.matches(KEY_PATTERN)) {
      throw failure(ErrorCode.INVALID_INPUT, label + " is invalid");
    }
  }

  private static void validateDigest(String value, String label) throws JournalException {
    if (value == null || !value.matches(DIGEST_PATTERN)) {
      throw failure(ErrorCode.INVALID_INPUT, label + " digest is invalid");
    }
  }

  private static void validateStoredKey(String value, String label) throws JournalException {
    if (value == null || !value.matches(KEY_PATTERN)) {
      throw failure(ErrorCode.CORRUPT, label + " is malformed");
    }
  }

  private static void validateStoredDigest(String value, String label) throws JournalException {
    if (value == null || !value.matches(DIGEST_PATTERN)) {
      throw failure(ErrorCode.CORRUPT, label + " is malformed");
    }
  }

  private static String sha(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static JournalException failure(ErrorCode code, String message) {
    return new JournalException(code, message);
  }

  private static JournalException failure(
      ErrorCode code,
      String message,
      Throwable cause) {
    return new JournalException(code, message, cause);
  }

  private record Head(long sequence, String digest, String previous) {}

  private record Node(
      long sequence,
      String previous,
      String recordId,
      String recordDigest) {}
}
