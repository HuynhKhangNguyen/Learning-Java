package lab.backend.identity.journal;

import static java.nio.charset.StandardCharsets.UTF_8;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Local-host lab adapter. File lock tuần tự hóa CAS giữa các process; durable ACK chỉ
 * được tạo sau khi đọc lại toàn bộ prefix tại head vừa publish.
 */
public final class FilesystemJournalStore implements JournalStore {
  private static final String ZERO = "0".repeat(64);
  private final Path root;

  public FilesystemJournalStore(Path explicitRoot) throws JournalException {
    if (explicitRoot == null || !explicitRoot.isAbsolute()) {
      throw failure(ErrorCode.INVALID_INPUT, "Journal root must be absolute");
    }
    try {
      Files.createDirectories(explicitRoot);
      root = explicitRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
      rejectReparse(root);
    } catch (IOException error) {
      throw failure(ErrorCode.IO_FAILURE, "Cannot own journal root", error);
    }
  }

  @Override
  public Receipt append(String namespace, String recordId, byte[] bytes) throws JournalException {
    validateKey(namespace, "namespace");
    validateKey(recordId, "recordId");
    if (bytes == null) throw failure(ErrorCode.INVALID_INPUT, "canonical bytes are required");
    Path space = owned(root.resolve(namespace));
    try {
      Files.createDirectories(space.resolve("records"));
      Files.createDirectories(space.resolve("nodes"));
      Files.createDirectories(space.resolve("heads"));
      rejectReparse(space);
      String recordDigest = sha(bytes);
      createImmutable(space.resolve("records").resolve(recordId + ".bin"), bytes, recordDigest);
      Path lockPath = space.resolve("head.lock");
      try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
          StandardOpenOption.WRITE); FileLock ignored = channel.lock()) {
        Head head = readHead(space);
        Inventory existing = verify(space, namespace, head);
        for (Entry entry : existing.entries()) {
          if (entry.recordId().equals(recordId)) {
            if (!entry.recordDigest().equals(recordDigest)) {
              throw failure(ErrorCode.CONFLICT, "record ID already binds different bytes");
            }
            return receipt(namespace, entry, head.digest());
          }
        }
        long sequence = Math.addExact(head.sequence(), 1);
        byte[] nodeBytes = nodeBytes(namespace, sequence, head.digest(), recordId, recordDigest);
        String nodeDigest = sha(nodeBytes);
        createImmutable(space.resolve("nodes").resolve(nodeDigest + ".node"), nodeBytes, nodeDigest);
        publishHead(space, new Head(sequence, nodeDigest));
        Inventory verified = verify(space, namespace, new Head(sequence, nodeDigest));
        Entry added = verified.entries().get(verified.entries().size() - 1);
        return receipt(namespace, added, nodeDigest);
      }
    } catch (JournalException error) {
      throw error;
    } catch (IOException | ArithmeticException error) {
      throw failure(ErrorCode.IO_FAILURE, "Journal append failed", error);
    }
  }

  @Override
  public Inventory inventory(String namespace, String fixedHeadDigest) throws JournalException {
    validateKey(namespace, "namespace");
    if (fixedHeadDigest == null || !fixedHeadDigest.matches("[0-9a-f]{64}")) {
      throw failure(ErrorCode.INVALID_INPUT, "fixed head digest is invalid");
    }
    Path space = owned(root.resolve(namespace));
    try {
      Head current = readHead(space);
      Head fixed = fixedHeadDigest.equals(ZERO) ? new Head(0, ZERO) : findHead(space, fixedHeadDigest);
      if (fixed.sequence() > current.sequence()) throw failure(ErrorCode.CORRUPT, "forged head");
      Inventory inventory = verify(space, namespace, fixed);
      if (!fixed.digest().equals(current.digest())) {
        return new Inventory(inventory.namespace(), inventory.fixedHeadDigest(),
            inventory.inventoryDigest(), inventory.entries(), List.of());
      }
      return inventory;
    } catch (JournalException error) {
      throw error;
    } catch (IOException error) {
      throw failure(ErrorCode.IO_FAILURE, "Inventory failed", error);
    }
  }

  private Inventory verify(Path space, String namespace, Head head) throws IOException, JournalException {
    List<Entry> reversed = new ArrayList<>();
    Set<String> covered = new HashSet<>();
    String cursor = head.digest();
    long expected = head.sequence();
    while (expected > 0) {
      Path nodePath = owned(space.resolve("nodes").resolve(cursor + ".node"));
      byte[] raw = Files.readAllBytes(nodePath);
      if (!sha(raw).equals(cursor)) throw failure(ErrorCode.CORRUPT, "node digest mismatch");
      String[] fields = new String(raw, UTF_8).split("\\n", -1);
      if (fields.length != 6 || !fields[0].equals(namespace)
          || Long.parseLong(fields[1]) != expected || !fields[5].isEmpty()) {
        throw failure(ErrorCode.CORRUPT, "malformed node or sequence gap");
      }
      byte[] record = Files.readAllBytes(owned(space.resolve("records").resolve(fields[3] + ".bin")));
      if (!sha(record).equals(fields[4])) throw failure(ErrorCode.CORRUPT, "record digest mismatch");
      if (!covered.add(fields[3])) throw failure(ErrorCode.CORRUPT, "duplicate record in prefix");
      reversed.add(new Entry(expected, fields[3], fields[4], cursor));
      cursor = fields[2];
      expected--;
    }
    if (!cursor.equals(ZERO)) throw failure(ErrorCode.CORRUPT, "wrong predecessor");
    List<Entry> entries = reversed.reversed();
    List<String> orphans = new ArrayList<>();
    if (Files.isDirectory(space.resolve("records"))) {
      try (var paths = Files.list(space.resolve("records"))) {
        paths.filter(path -> path.getFileName().toString().endsWith(".bin"))
            .map(path -> path.getFileName().toString().replaceFirst("\\.bin$", ""))
            .filter(id -> !covered.contains(id)).sorted().forEach(orphans::add);
      }
    }
    StringBuilder manifest = new StringBuilder(namespace).append('\n').append(head.digest()).append('\n');
    entries.forEach(entry -> manifest.append(entry.sequence()).append(':').append(entry.recordId())
        .append(':').append(entry.recordDigest()).append(':').append(entry.nodeDigest()).append('\n'));
    return new Inventory(namespace, head.digest(), sha(manifest.toString().getBytes(UTF_8)),
        List.copyOf(entries), List.copyOf(orphans));
  }

  private static byte[] nodeBytes(String namespace, long sequence, String previous,
      String recordId, String recordDigest) {
    return (namespace + "\n" + sequence + "\n" + previous + "\n" + recordId + "\n"
        + recordDigest + "\n").getBytes(UTF_8);
  }

  private static Receipt receipt(String namespace, Entry entry, String head) {
    return new Receipt(namespace, entry.recordId(), entry.recordDigest(), entry.sequence(),
        entry.nodeDigest(), head);
  }

  private static void createImmutable(Path target, byte[] bytes, String digest)
      throws IOException, JournalException {
    try {
      Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
          StandardOpenOption.DSYNC);
    } catch (java.nio.file.FileAlreadyExistsException exists) {
      if (!sha(Files.readAllBytes(target)).equals(digest)) {
        throw failure(ErrorCode.CONFLICT, "immutable object conflict");
      }
    }
  }

  private static void publishHead(Path space, Head head) throws IOException {
    byte[] bytes = (head.sequence() + "\n" + head.digest() + "\n").getBytes(UTF_8);
    Path target = space.resolve("heads").resolve(head.digest() + ".head");
    try { Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.DSYNC); }
    catch (java.nio.file.FileAlreadyExistsException exists) {
      if (!java.util.Arrays.equals(bytes, Files.readAllBytes(target))) throw exists;
    }
    try (FileChannel directory = FileChannel.open(space, StandardOpenOption.READ)) {
      directory.force(true);
    } catch (IOException unsupportedDirectoryFsync) {
      // Windows không cho mở directory channel; claim của adapter vẫn chỉ là local-host lab.
    }
  }

  private static Head readHead(Path space) throws IOException, JournalException {
    Path heads = space.resolve("heads");
    if (!Files.isDirectory(heads)) return new Head(0, ZERO);
    Head latest = new Head(0, ZERO);
    try (var paths = Files.list(heads)) {
      for (Path path : paths.toList()) {
        String[] fields = Files.readString(path, UTF_8).split("\\n", -1);
        if (fields.length != 3 || !fields[2].isEmpty() || !fields[1].matches("[0-9a-f]{64}")
            || !path.getFileName().toString().equals(fields[1] + ".head")) {
          throw failure(ErrorCode.CORRUPT, "malformed head");
        }
        long sequence;
        try { sequence = Long.parseLong(fields[0]); }
        catch (NumberFormatException error) { throw failure(ErrorCode.CORRUPT, "malformed head", error); }
        if (sequence > latest.sequence()) latest = new Head(sequence, fields[1]);
        else if (sequence == latest.sequence() && !fields[1].equals(latest.digest())) {
          throw failure(ErrorCode.CORRUPT, "competing head at same sequence");
        }
      }
    }
    return latest;
  }

  private static Head findHead(Path space, String digest) throws IOException, JournalException {
    String cursor = readHead(space).digest();
    long sequence = readHead(space).sequence();
    while (sequence > 0) {
      if (cursor.equals(digest)) return new Head(sequence, cursor);
      String[] fields = Files.readString(space.resolve("nodes").resolve(cursor + ".node"), UTF_8)
          .split("\\n", -1);
      if (fields.length != 6) throw failure(ErrorCode.CORRUPT, "malformed node");
      cursor = fields[2]; sequence--;
    }
    throw failure(ErrorCode.CORRUPT, "fixed head is not covered");
  }

  private Path owned(Path candidate) throws JournalException {
    Path normalized = candidate.toAbsolutePath().normalize();
    if (!normalized.startsWith(root)) throw failure(ErrorCode.INVALID_INPUT, "path escapes journal root");
    Path cursor = root;
    for (Path part : root.relativize(normalized)) {
      cursor = cursor.resolve(part);
      if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) rejectReparse(cursor);
    }
    return normalized;
  }

  private static void rejectReparse(Path path) throws JournalException {
    try {
      if (Files.isSymbolicLink(path)
          || Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class,
              LinkOption.NOFOLLOW_LINKS).isOther()) {
        throw failure(ErrorCode.INVALID_INPUT, "symlink/reparse path is not owned");
      }
    } catch (IOException error) {
      throw failure(ErrorCode.IO_FAILURE, "Cannot inspect owned path", error);
    }
  }

  private static void validateKey(String value, String label) throws JournalException {
    if (value == null || !value.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}")) {
      throw failure(ErrorCode.INVALID_INPUT, label + " is invalid");
    }
  }

  private static String sha(byte[] bytes) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }

  private static JournalException failure(ErrorCode code, String message) {
    return new JournalException(code, message);
  }
  private static JournalException failure(ErrorCode code, String message, Throwable cause) {
    return new JournalException(code, message, cause);
  }
  private record Head(long sequence, String digest) {}
}
